package com.novacamera.s24.core

enum class CaptureMode(val label: String) {
    NOITE("NOITE"), RETRATO("RETRATO"), FOTO("FOTO"), VIDEO("VÍDEO"), PRO("PRO");

    val isVideo get() = this == VIDEO
}

/** Qual motor executa o modo neste aparelho e nesta câmera. */
enum class ModeEngine {
    /** Captura padrão CameraX/Camera2. */
    STANDARD,
    /** Extensão do fabricante (CameraX Extensions). */
    OEM_EXTENSION,
    /** Implementação própria da Nova Camera (versão básica, descrita em [ModeSupport.note]). */
    NOVA_BASIC,
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
)

object ModeResolver {
    fun resolve(mode: CaptureMode, facts: DeviceModeFacts): ModeSupport = when (mode) {
        CaptureMode.FOTO, CaptureMode.VIDEO -> ModeSupport(mode, ModeEngine.STANDARD, "")
        CaptureMode.PRO -> ModeSupport(mode, ModeEngine.STANDARD, "Controles manuais conforme o sensor permitir.")
        CaptureMode.RETRATO ->
            if (facts.oemBokeh) ModeSupport(mode, ModeEngine.OEM_EXTENSION, "Desfoque do fabricante.")
            else ModeSupport(mode, ModeEngine.UNAVAILABLE,
                "O fabricante não libera o Retrato para outros apps nesta câmera. " +
                    "O Retrato próprio (segmentação e profundidade locais) ainda está em desenvolvimento.")
        CaptureMode.NOITE -> when {
            facts.oemNight -> ModeSupport(mode, ModeEngine.OEM_EXTENSION, "Noite do fabricante.")
            facts.extendedExposureMinFps != null -> ModeSupport(mode, ModeEngine.NOVA_BASIC,
                "Noite básica: permite exposições de até 1/${facts.extendedExposureMinFps} s. " +
                    "Fusão multiquadro ainda em desenvolvimento.")
            else -> ModeSupport(mode, ModeEngine.UNAVAILABLE,
                "Sem extensão Noite do fabricante e sem faixa de exposição estendida nesta câmera.")
        }
    }

    /** Escolhe a faixa de fps de AE que mais estende a exposição sem passar de 30 fps. */
    fun extendedExposureMinFps(ranges: List<Pair<Int, Int>>): Int? =
        ranges.filter { (lo, hi) -> hi in 15..30 && lo in 1 until 15 }.minOfOrNull { it.first }
}
