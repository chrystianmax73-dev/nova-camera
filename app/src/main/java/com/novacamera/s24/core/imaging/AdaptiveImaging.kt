package com.novacamera.s24.core.imaging

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Dados do aparelho atual. Ausência é null; resultados de prévia não viram EXIF de captura. */
data class ImagingFacts(
    val device: String = "unknown",
    val cameraId: String? = null,
    val physicalId: String? = null,
    val focalMm: Float? = null,
    val sensorWidthMm: Float? = null,
    val sensorHeightMm: Float? = null,
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val captureVerified: Boolean = false,
) {
    val lensKey get() = "$device/${cameraId ?: "unknown"}/${physicalId ?: "unknown"}"
    fun toMeta(): Map<String, String> = buildMap {
        put("nai.device", device)
        cameraId?.let { put("nai.camera", it) }; physicalId?.let { put("nai.physical", it) }
        focalMm?.let { put("nai.focal", it.toString()) }
        sensorWidthMm?.let { put("nai.sensorW", it.toString()) }; sensorHeightMm?.let { put("nai.sensorH", it.toString()) }
        iso?.let { put("nai.iso", it.toString()) }; exposureNs?.let { put("nai.exposure", it.toString()) }
        put("nai.captureVerified", captureVerified.toString())
    }
    companion object {
        fun fromMeta(meta: Map<String, String>) = ImagingFacts(meta["nai.device"] ?: "unknown",
            meta["nai.camera"], meta["nai.physical"], meta["nai.focal"]?.toFloatOrNull(),
            meta["nai.sensorW"]?.toFloatOrNull(), meta["nai.sensorH"]?.toFloatOrNull(),
            meta["nai.iso"]?.toIntOrNull(), meta["nai.exposure"]?.toLongOrNull(), meta["nai.captureVerified"] == "true")
    }
}

enum class ImagingScene { BALANCED, LOW_LIGHT, HIGH_DYNAMIC_RANGE, GREEN_DOMINANT, ZOOM_5X, ZOOM_10X }

/** Registro local preparado para calibrações medidas por lente/cena. Nenhuma calibração óptica importada. */
data class LensCalibration(val id: String, val lensKey: String, val scene: ImagingScene,
    val chromaProtection: Float, val evidenceId: String)

class CalibrationRegistry(private val entries: List<LensCalibration> = emptyList()) {
    fun resolve(facts: ImagingFacts, scene: ImagingScene): LensCalibration? =
        if (!facts.captureVerified) null else entries.firstOrNull {
            it.lensKey == facts.lensKey && it.scene == scene && it.evidenceId.isNotBlank() &&
                it.chromaProtection.isFinite() && it.chromaProtection in 0f..1f
        }
}

/** Complemento determinístico ao motor existente, automático na FOTO. Sem LUT de outra câmera ou rede. */
object AdaptiveImaging {
    data class Decision(val scene: ImagingScene, val lensKey: String, val calibration: String, val protection: Float)
    @Volatile var lastReport = "Nova Adaptive Imaging: nenhuma foto analisada"
        private set

    fun green(c: Int): Boolean = Px.g(c) > Px.r(c) && Px.g(c) > Px.b(c)

    /** Conserva o resultado antigo nos verdes. Nos demais, reduz desvio cromático causado pelo ajuste de preto. */
    fun protect(original: Int, baseline: Int, strength: Float): Int {
        if (strength <= 0f || green(original) || original == baseline) return baseline
        val l = Px.luma(original)
        val peak = max(Px.r(original), max(Px.g(original), Px.b(original)))
        val low = min(Px.r(original), min(Px.g(original), Px.b(original)))
        if (l < 0.04f || peak == 0) return baseline
        // Transição gradual: não cria um contorno de máscara na passagem de tons quase neutros.
        val saturation = (peak - low).toFloat() / peak
        val weight = ((saturation - 0.15f) / 0.35f).coerceIn(0f, 1f) * strength
        if (weight <= 0f) return baseline
        val gain = min(Px.luma(baseline) / l, 255f / peak)
        val candidate = Px.argb(
            (Px.r(baseline) + (Px.r(original) * gain - Px.r(baseline)) * weight + 0.5f).toInt(),
            (Px.g(baseline) + (Px.g(original) * gain - Px.g(baseline)) * weight + 0.5f).toInt(),
            (Px.b(baseline) + (Px.b(original) * gain - Px.b(baseline)) * weight + 0.5f).toInt())
        // A proteção vale também nos pixels de resolução cheia, não só na amostra A/B.
        return if (abs(Px.luma(candidate) - Px.luma(baseline)) <= 1.5f / 255f &&
            clippedChannels(candidate) <= clippedChannels(baseline)) candidate else baseline
    }

    private fun clippedChannels(c: Int): Int = (if (Px.r(c) == 255) 1 else 0) +
        (if (Px.g(c) == 255) 1 else 0) + (if (Px.b(c) == 255) 1 else 0)

    fun scene(stats: SceneStats, greenFraction: Float, zoom: Float, facts: ImagingFacts): ImagingScene = when {
        zoom >= 9f -> ImagingScene.ZOOM_10X
        zoom >= 4.5f -> ImagingScene.ZOOM_5X
        stats.p95 - stats.p05 > 0.7f && stats.shadowFraction > 0.15f -> ImagingScene.HIGH_DYNAMIC_RANGE
        (facts.iso ?: 0) >= 800 || stats.p50 < 0.12f -> ImagingScene.LOW_LIGHT
        greenFraction > 0.25f -> ImagingScene.GREEN_DOMINANT
        else -> ImagingScene.BALANCED
    }

    /** A/B no mesmo preview: manter luminância/textura, não aumentar clipping RGB nem alterar verdes. */
    fun safeAgainstBaseline(original: IntArray, baseline: IntArray, candidate: IntArray, w: Int, h: Int): Boolean {
        require(original.size == w * h && baseline.size == original.size && candidate.size == original.size)
        var baseClip = 0; var newClip = 0
        var baseTexture = 0.0; var newTexture = 0.0
        for (i in original.indices) {
            if (green(original[i]) && baseline[i] != candidate[i]) return false
            if (abs(Px.luma(baseline[i]) - Px.luma(candidate[i])) > 1.5f / 255f) return false
            baseClip += clippedChannels(baseline[i]); newClip += clippedChannels(candidate[i])
            val x = i % w; val y = i / w
            if (x in 1 until w - 1 && y in 1 until h - 1) {
                fun lap(px: IntArray) = abs(4 * Px.luma(px[i]) - Px.luma(px[i - 1]) - Px.luma(px[i + 1]) - Px.luma(px[i - w]) - Px.luma(px[i + w]))
                baseTexture += lap(baseline); newTexture += lap(candidate)
            }
        }
        return newClip <= baseClip && newTexture >= baseTexture * 0.995
    }

    fun plan(px: IntArray, w: Int, h: Int, intent: ToneIntent, zoom: Float = 1f,
        facts: ImagingFacts = ImagingFacts(), registry: CalibrationRegistry = CalibrationRegistry()): Adaptive.Plan {
        val legacy = Adaptive.plan(px, w, h, intent, zoom)
        if (intent != ToneIntent.LIGHT) return legacy
        val stats = legacy.stats ?: return legacy
        val scene = scene(stats, px.count(::green).toFloat() / px.size, zoom, facts)
        val calibration = registry.resolve(facts, scene)
        val calibrationId = calibration?.id ?: "identity-unmeasured"
        var protection = 0f
        if (!legacy.skip && legacy.params.blackLevel > 0.005f) {
            val baseline = px.copyOf()
            ToneMap(legacy.base, legacy.params, w, h, legacy.mid).applyTile(baseline, w, h, 0, 0, legacy.strength)
            val maxStrength = calibration?.chromaProtection ?: 1f
            for (weight in floatArrayOf(1f, 0.5f, 0.25f)) {
                val candidate = IntArray(px.size) { protect(px[it], baseline[it], weight * maxStrength) }
                if (safeAgainstBaseline(px, baseline, candidate, w, h)) { protection = weight * maxStrength; break }
            }
        }
        val decision = Decision(scene, facts.lensKey, calibrationId, protection)
        lastReport = "Nova Adaptive Imaging: cena=$scene, lente=${facts.lensKey}, calibração=$calibrationId, " +
            "proteção=$protection, ISO captura=${facts.iso}, exposição captura=${facts.exposureNs} ns, " +
            "focal prévia=${facts.focalMm} mm, sensor disponível=${facts.sensorWidthMm}×${facts.sensorHeightMm} mm, " +
            "lente verificada na captura=${facts.captureVerified}"
        return Adaptive.Plan(legacy.params, legacy.strength, legacy.base, legacy.mid, legacy.reason, stats, decision)
    }
}
