package com.novacamera.s24.core

/**
 * Modos da câmera. [primary] = faixa principal (Retrato · Foto · Vídeo · Noturno · Pro; a ordem da faixa
 * está em MainActivity.PRIMARY_MODES); os demais ficam no painel MAIS / Ferramentas › Mais modos.
 * Um modo só captura quando há implementação real no aparelho (ver [ModeResolver]).
 */
enum class CaptureMode(val label: String, val primary: Boolean = false) {
    RETRATO("RETRATO", true), FOTO("FOTO", true), VIDEO("VÍDEO", true),
    PRO("PRO", true), VIDEO_PRO("VÍDEO PRO"), NOITE("NOTURNO", true), CAMERA_LENTA("CÂMERA LENTA"),
    PANORAMA("PANORÂMICA"), TIMELAPSE("TIMELAPSE"), FOOD("FOOD"), VIDEO_RETRATO("VÍDEO RETRATO");

    val isVideo get() = this == VIDEO || this == VIDEO_PRO || this == CAMERA_LENTA || this == VIDEO_RETRATO || this == TIMELAPSE
    val isPro get() = this == PRO || this == VIDEO_PRO
}

/** Qual motor executa o modo neste aparelho e nesta câmera. */
enum class ModeEngine {
    /** Captura padrão CameraX/Camera2. */
    STANDARD,
    /** Extensão do fabricante (CameraX Extensions). */
    OEM_EXTENSION,
    /** Processamento próprio da Nova Camera (núcleo core/imaging), descrito em [ModeSupport.note]. */
    NOVA,
    /** Não há implementação real disponível: o modo não captura. */
    UNAVAILABLE,
}

data class ModeSupport(val mode: CaptureMode, val engine: ModeEngine, val note: String) {
    val canCapture get() = engine != ModeEngine.UNAVAILABLE
}

data class DeviceModeFacts(
    val oemBokeh: Boolean,
    val oemNight: Boolean,
    /** Menor fps mínimo de AE suportado (permite exposições mais longas); null se não for menor que 15. */
    val extendedExposureMinFps: Int?,
    /** Maior taxa de alta velocidade suportada (120/240), ou null. */
    val highSpeedFps: Int? = null,
)

/** Preferência do usuário entre o motor próprio (padrão) e a extensão do fabricante. */
data class EnginePrefs(val oemPortrait: Boolean = false, val oemNight: Boolean = false)

object ModeResolver {
    const val NIGHT_FRAMES = 5

    fun resolve(mode: CaptureMode, facts: DeviceModeFacts, prefs: EnginePrefs = EnginePrefs()): ModeSupport = when (mode) {
        CaptureMode.FOTO, CaptureMode.VIDEO -> ModeSupport(mode, ModeEngine.STANDARD, "")
        CaptureMode.PRO -> ModeSupport(mode, ModeEngine.STANDARD, "Controles manuais conforme o sensor permitir.")
        CaptureMode.VIDEO_PRO -> ModeSupport(mode, ModeEngine.STANDARD, "Vídeo com ISO, velocidade, foco e balanço manuais.")
        CaptureMode.RETRATO ->
            if (prefs.oemPortrait && facts.oemBokeh) ModeSupport(mode, ModeEngine.OEM_EXTENSION,
                "Desfoque do fabricante (pode suavizar a pele; o Retrato Nova não suaviza).")
            else ModeSupport(mode, ModeEngine.NOVA,
                "Retrato Nova: separa a pessoa e desfoca só o fundo, sem suavizar pele, barba ou cabelo. " +
                    "Ajuste o desfoque depois, tocando na miniatura.")
        CaptureMode.NOITE ->
            if (prefs.oemNight && facts.oemNight) ModeSupport(mode, ModeEngine.OEM_EXTENSION, "Noite do fabricante.")
            else ModeSupport(mode, ModeEngine.NOVA,
                "Noturno Nova: $NIGHT_FRAMES quadros alinhados e fundidos para reduzir ruído" +
                    (facts.extendedExposureMinFps?.let { ", exposições de até 1/$it s" } ?: "") + ". Segure firme.")
        CaptureMode.FOOD -> ModeSupport(mode, ModeEngine.NOVA,
            "Food: cores vivas sem estourar as já saturadas e foco no centro.")
        CaptureMode.CAMERA_LENTA ->
            if (facts.highSpeedFps != null) ModeSupport(mode, ModeEngine.STANDARD,
                "Gravação a ${facts.highSpeedFps} fps, reproduzida em câmera lenta.")
            else ModeSupport(mode, ModeEngine.UNAVAILABLE, "Esta câmera não oferece gravação em alta velocidade para outros apps.")
        CaptureMode.PANORAMA -> ModeSupport(mode, ModeEngine.UNAVAILABLE,
            "Em desenvolvimento: a captura guiada com costura própria ainda não foi implementada, por isso o modo não captura.")
        CaptureMode.TIMELAPSE -> ModeSupport(mode, ModeEngine.NOVA,
            "Timelapse: um quadro por intervalo (toque no botão de intervalo para mudar), vídeo a 30 fps sem áudio. " +
                "Apoie o aparelho e mantenha o app aberto.")
        CaptureMode.VIDEO_RETRATO -> ModeSupport(mode, ModeEngine.UNAVAILABLE,
            "Em desenvolvimento: o desfoque de fundo em vídeo exige segmentação a 30 fps; ainda não implementado.")
    }

    /** Escolhe a faixa de fps de AE que mais estende a exposição sem passar de 30 fps. */
    fun extendedExposureMinFps(ranges: List<Pair<Int, Int>>): Int? =
        ranges.filter { (lo, hi) -> hi in 15..30 && lo in 1 until 15 }.minOfOrNull { it.first }

    /**
     * Vídeo a 30 ou 60 fps com pouca luz: faixa variável (ex.: 15–30, 15–60) para o automático alongar
     * a exposição em vez de escurecer. Com luz boa a câmera continua no alvo; o quadro só fica mais
     * longo quando falta luz (tele 3×/5×/10× escura: a 60 fps fixos cada quadro tinha no máximo 1/60 s).
     * Mínimo de 15 fps — abaixo disso o movimento trava. 24 fps fica fixo (escolha de cinema).
     */
    fun autoVideoFps(ranges: List<Pair<Int, Int>>, target: Int = 30): Pair<Int, Int>? {
        if (target != 30 && target != 60) return null
        return ranges.filter { (lo, hi) -> hi == target && lo in 15..(target * 4 / 5) }.minByOrNull { it.first }
    }

    /** Índices de compensação para o bracketing do HDR Nova (0, ≈−2 EV, ≈+1 EV), dentro da faixa. */
    fun hdrBracket(stepNum: Int, stepDen: Int, min: Int, max: Int): List<Int> {
        if (stepNum <= 0 || stepDen <= 0) return listOf(0)
        val perEv = stepDen.toFloat() / stepNum
        val under = Math.round(-2f * perEv).coerceIn(min, 0)
        val over = Math.round(1f * perEv).coerceIn(0, max)
        return listOf(0, under, over).distinct().filter { it == 0 || kotlin.math.abs(it) >= perEv * 0.6f }
    }
}
