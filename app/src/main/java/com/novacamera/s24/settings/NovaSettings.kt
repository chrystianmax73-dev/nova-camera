package com.novacamera.s24.settings

import android.content.Context
import android.content.SharedPreferences
import com.novacamera.s24.core.CaptureMode

enum class AspectRatioOption(val label: String) { RATIO_4_3("4:3"), RATIO_16_9("16:9"), RATIO_1_1("1:1"), FULL("Tela") }
/** HDR da FOTO: desligado, fusão própria de exposições (Nova) ou extensão do fabricante. */
enum class HdrOption(val label: String) { OFF("Desligado"), NOVA("Automático (Nova)"), OEM("Fabricante") }
enum class FlashOption(val label: String) { OFF("Desligado"), AUTO("Automático"), ON("Ligado") }
enum class VideoQualityOption(val label: String) { AUTO("Automática"), MAX("Máxima"), UHD("4K UHD"), FHD("Full HD"), HD("HD") }

/** Preferências locais. Nada é sincronizado ou enviado para fora do aparelho. */
class NovaSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = prefs.getBoolean(key, def)
    private fun int(key: String, def: Int) = prefs.getInt(key, def)
    private fun put(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    private fun put(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    private fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    private inline fun <reified E : Enum<E>> enum(key: String, def: E): E =
        prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: def

    // Foto
    var mirrorFrontPhoto get() = bool("mirror_front_photo", true); set(v) = put("mirror_front_photo", v)
    var shutterSound get() = bool("shutter_sound", true); set(v) = put("shutter_sound", v)
    var volumeKeyShutter get() = bool("volume_shutter", true); set(v) = put("volume_shutter", v)
    var grid get() = bool("grid", false); set(v) = put("grid", v)
    var aspect get() = enum("aspect", AspectRatioOption.RATIO_4_3); set(v) = put("aspect", v.name)
    var flash get() = enum("flash", FlashOption.OFF); set(v) = put("flash", v.name)
    var timerSeconds get() = int("timer", 0); set(v) = put("timer", v)
    var hdr get() = enum("hdr", if (bool("oem_hdr", false)) HdrOption.OEM else HdrOption.OFF); set(v) = put("hdr", v.name)
    /** Megapixels escolhidos por câmera (id do Camera2); ausente = resolução padrão. */
    fun photoPixels(cameraId: String?): Long? = prefs.getLong("photo_px_$cameraId", -1L).takeIf { it > 0 }
    fun setPhotoPixels(cameraId: String?, pixels: Long?) =
        prefs.edit().apply { if (pixels == null) remove("photo_px_$cameraId") else putLong("photo_px_$cameraId", pixels) }.apply()
    /** Assinatura das escolhas de resolução (para saber quando reconfigurar). */
    val photoPixelsSignature get() = prefs.all.filterKeys { it.startsWith("photo_px_") }.toSortedMap().toString()

    // Processamento Nova
    /** Retrato/Noturno com a extensão do fabricante em vez do motor próprio (só se disponível). */
    var oemPortrait get() = bool("oem_portrait", false); set(v) = put("oem_portrait", v)
    var oemNight get() = bool("oem_night", false); set(v) = put("oem_night", v)
    /** Guarda o original sem processamento em "Nova Camera/Originais". */
    var keepOriginals get() = bool("keep_originals", true); set(v) = put("keep_originals", v)
    /**
     * FOTO: ajuste automático e adaptativo (só quando melhora a foto em medidas objetivas). A foto
     * da galeria vira a versão pronta; o original fica em "Originais".
     */
    var autoProcess get() = bool("auto_process", true); set(v) = put("auto_process", v)

    // Assinatura
    var signatureEnabled get() = bool("signature", false); set(v) = put("signature", v)
    var signaturePosition get() = enum("signature_pos", com.novacamera.s24.processing.SignaturePosition.LEFT); set(v) = put("signature_pos", v.name)
    var signatureSize get() = enum("signature_size", com.novacamera.s24.processing.SignatureSize.MEDIUM); set(v) = put("signature_size", v.name)
    var signatureOpacity get() = int("signature_opacity", 85) / 100f; set(v) = put("signature_opacity", (v * 100).toInt())
    /** RAW (DNG) junto do JPEG no modo Pro, quando a câmera oferece. */
    var raw get() = bool("raw", false); set(v) = put("raw", v)

    // Vídeo
    var mirrorFrontVideo get() = bool("mirror_front_video", true); set(v) = put("mirror_front_video", v)
    var videoQuality get() = enum("video_quality", VideoQualityOption.AUTO); set(v) = put("video_quality", v.name)
    var videoFps get() = int("video_fps", 30); set(v) = put("video_fps", v)
    var slowMotionFps get() = int("slow_motion_fps", 120); set(v) = put("slow_motion_fps", v)
    var videoStabilization get() = bool("video_stabilization", true); set(v) = put("video_stabilization", v)
    var recordAudio get() = bool("record_audio", true); set(v) = put("record_audio", v)
    /** Vídeo sem ponto escolhido mede a exposição pelo centro (menos saltos ao aproximar). */
    var videoCenterMetering get() = bool("video_center_metering", true); set(v) = put("video_center_metering", v)

    var timelapseIntervalMs get() = prefs.getLong("timelapse_interval", 1000L); set(v) = prefs.edit().putLong("timelapse_interval", v).apply()
    /** Duração máxima da captura do Timelapse (s); 0 = até parar. */
    var timelapseMaxSeconds get() = int("timelapse_max", 0); set(v) = put("timelapse_max", v)

    // Foco e exposição
    var facePriority get() = bool("face_priority", true); set(v) = put("face_priority", v)
    /** Segundos que o foco por toque é mantido antes de voltar ao contínuo; 0 = até novo toque. */
    var tapFocusHoldSeconds get() = int("tap_focus_hold", 4); set(v) = put("tap_focus_hold", v)
    var lockWhiteBalanceWithAeAf get() = bool("lock_awb", true); set(v) = put("lock_awb", v)

    // Interface e energia
    var haptics get() = bool("haptics", true); set(v) = put("haptics", v)
    /** Pausa a câmera após inatividade (s); 0 = nunca. */
    var idleTimeoutSeconds get() = int("idle_timeout", 120); set(v) = put("idle_timeout", v)
    var lastMode get() = enum("last_mode", CaptureMode.FOTO); set(v) = put("last_mode", v.name)
}
