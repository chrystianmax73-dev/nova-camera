package com.novacamera.s24.settings

import android.content.Context
import android.content.SharedPreferences
import com.novacamera.s24.core.CaptureMode

enum class AspectRatioOption(val label: String) { RATIO_4_3("4:3"), RATIO_16_9("16:9"), FULL("Tela") }
enum class FlashOption(val label: String) { OFF("Desligado"), AUTO("Automático"), ON("Ligado") }
enum class VideoQualityOption(val label: String) { AUTO("Automática"), UHD("4K UHD"), FHD("Full HD"), HD("HD") }

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
    var oemHdr get() = bool("oem_hdr", false); set(v) = put("oem_hdr", v)

    // Vídeo
    var mirrorFrontVideo get() = bool("mirror_front_video", true); set(v) = put("mirror_front_video", v)
    var videoQuality get() = enum("video_quality", VideoQualityOption.AUTO); set(v) = put("video_quality", v.name)
    var videoFps get() = int("video_fps", 30); set(v) = put("video_fps", v)
    var videoStabilization get() = bool("video_stabilization", true); set(v) = put("video_stabilization", v)
    var recordAudio get() = bool("record_audio", true); set(v) = put("record_audio", v)

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
