package com.novacamera.s24.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Exposição de cada quadro do Noturno. O automático da câmera prioriza tempo curto (ex.: 1/24 s com
 * ISO 6539); a fusão de quadros assim reduz pouco o ruído. Aqui a mesma luz (+[BOOST_EV]) é
 * redistribuída em quadros mais longos com ISO mais baixo, até o limite de tremido estimado pelo
 * giroscópio: ruído de leitura e de disparo caem antes da fusão.
 */
object NightExposure {
    /** A noite fica um pouco mais clara já na captura (luz real, não ganho digital). */
    const val BOOST_EV = 0.5f
    /** Tremido tolerado por quadro, em pixels da foto de 12 MP (a fusão alinha o resto). */
    private const val BLUR_BUDGET_PX = 2f
    /** Quanto a OIS reduz o tremido da mão (conservador). */
    private const val OIS_GAIN = 4f
    /** Sensor parado (apoiado): o giroscópio ainda mede ruído próprio. */
    private const val GYRO_FLOOR = 0.004f
    /** Distância focal em pixels da principal (1×) na foto de 12 MP (~75° de campo horizontal). */
    const val MAIN_FOCAL_PX = 2600f
    const val MAX_FRAME_NS = 500_000_000L
    /** Apoiado (giroscópio quase parado): quadros de até 1 s, como o modo de tripé dos topos de linha. */
    const val STEADY_MAX_FRAME_NS = 1_000_000_000L
    /** Tempo total de captura aceitável (segurar firme); apoiado, o dobro. */
    private const val BUDGET_NS = 2_000_000_000L
    private const val STEADY_BUDGET_NS = 4_000_000_000L

    data class Plan(val iso: Int, val exposureNs: Long, val frames: Int, val steady: Boolean) {
        val label get() = "$frames quadros de ${CameraMath.shutterLabel(exposureNs).let { if (it.endsWith(" s")) it else "$it s" }}, ISO $iso" +
            if (steady) " (aparelho apoiado)" else ""
    }

    /**
     * Tempo máximo por quadro sem tremido visível. [gyroRms] em rad/s (null = sem giroscópio:
     * assume mão comum); [zoom] equivalente (a tele amplia o tremido).
     */
    fun maxFrameNs(gyroRms: Float?, zoom: Float, ois: Boolean): Long {
        val w = max(gyroRms ?: 0.03f, GYRO_FLOOR)
        val focal = MAIN_FOCAL_PX * zoom.coerceAtLeast(0.5f)
        val seconds = BLUR_BUDGET_PX * (if (ois) OIS_GAIN else 1f) / (w * focal)
        return (seconds * 1e9).roundToLong().coerceIn(1_000_000L, if (steady(gyroRms)) STEADY_MAX_FRAME_NS else MAX_FRAME_NS)
    }

    fun steady(gyroRms: Float?) = gyroRms != null && gyroRms < 0.012f

    /**
     * Null quando não há ganho (cena clara, ou o automático já usa um tempo perto do limite):
     * o Noturno segue com a exposição automática.
     */
    fun plan(
        aeIso: Int, aeExposureNs: Long, isoMin: Int, isoMax: Int, exposureMaxNs: Long,
        maxFrameNs: Long, maxFrames: Int, steady: Boolean = false,
    ): Plan? {
        if (aeIso <= 0 || aeExposureNs <= 0 || maxFrames < 1) return null
        // Cena clara: o automático já está com ISO baixo — nada a ganhar.
        if (aeIso < 400) return null
        val light = aeIso.toDouble() * aeExposureNs * 2.0.pow(BOOST_EV.toDouble())
        val limit = min(maxFrameNs, exposureMaxNs)
        if (limit < aeExposureNs * 1.4) return null
        var exp = min(limit.toDouble(), light / isoMin)
        val iso = (light / exp).roundToInt().coerceIn(isoMin, isoMax)
        exp = min(limit.toDouble(), light / iso)
        if (exp < aeExposureNs * 1.4) return null
        // ISO limitado pelo sensor pode tornar o plano subexposto; não trocar AE por isso.
        if (iso.toDouble() * exp < light * 0.97) return null
        val expNs = exp.roundToLong()
        val frames = ((if (steady) STEADY_BUDGET_NS else BUDGET_NS) / expNs).toInt().coerceIn(min(3, maxFrames), maxFrames)
        return Plan(iso, expNs, frames, steady)
    }
}
