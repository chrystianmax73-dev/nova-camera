package com.novacamera.s24.core

/**
 * Modos da câmera. Os quatro primeiros ficam na faixa principal; os demais no painel MAIS.
 * Um modo só captura quando há implementação real no aparelho (ver [ModeResolver]).
 */
enum class CaptureMode(val label: String, val primary: Boolean = false) {
    RETRATO("RETRATO", true), FOTO("FOTO", true), VIDEO("VÍDEO", true),
    PRO("PRO"), VIDEO_PRO("VÍDEO PRO"), NOITE("NOTURNO"), CAMERA_LENTA("CÂMERA LENTA"),
    PANORAMA("PANORÂMICA"), TIMELAPSE("TIMELAPSE"), FOOD("FOOD"), VIDEO_RETRATO("VÍDEO RETRATO");

    val isVideo get() = this == VIDEO || this == VIDEO_PRO || this == CAMERA_LENTA || this == VIDEO_RETRATO
    val isPro get() = this == PRO || this == VIDEO_PRO
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
    /** Maior taxa de alta velocidade suportada (120/240), ou null. */
    val highSpeedFps: Int? = null,
)

object ModeResolver {
    private const val IN_DEVELOPMENT = "Em desenvolvimento: ainda não há implementação real, por isso o modo não captura."

    fun resolve(mode: CaptureMode, facts: DeviceModeFacts): ModeSupport = when (mode) {
        CaptureMode.FOTO, CaptureMode.VIDEO -> ModeSupport(mode, ModeEngine.STANDARD, "")
        CaptureMode.PRO -> ModeSupport(mode, ModeEngine.STANDARD, "Controles manuais conforme o sensor permitir.")
        CaptureMode.VIDEO_PRO -> ModeSupport(mode, ModeEngine.STANDARD, "Vídeo com ISO, velocidade, foco e balanço manuais.")
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
        CaptureMode.CAMERA_LENTA ->
            if (facts.highSpeedFps != null) ModeSupport(mode, ModeEngine.STANDARD,
                "Gravação a ${facts.highSpeedFps} fps, reproduzida em câmera lenta.")
            else ModeSupport(mode, ModeEngine.UNAVAILABLE, "Esta câmera não oferece gravação em alta velocidade para outros apps.")
        CaptureMode.PANORAMA, CaptureMode.TIMELAPSE, CaptureMode.FOOD, CaptureMode.VIDEO_RETRATO ->
            ModeSupport(mode, ModeEngine.UNAVAILABLE, IN_DEVELOPMENT)
    }

    /** Escolhe a faixa de fps de AE que mais estende a exposição sem passar de 30 fps. */
    fun extendedExposureMinFps(ranges: List<Pair<Int, Int>>): Int? =
        ranges.filter { (lo, hi) -> hi in 15..30 && lo in 1 until 15 }.minOfOrNull { it.first }
}
