package com.novacamera.s24.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import kotlin.math.min
import kotlin.math.roundToInt

/** Onde a assinatura fica na foto. */
enum class SignaturePosition(val label: String) { LEFT("Esquerda"), CENTER("Centro"), RIGHT("Direita") }

/** Tamanho relativo ao lado menor da foto. */
enum class SignatureSize(val label: String, val fraction: Float) { SMALL("Pequena", 0.018f), MEDIUM("Média", 0.024f), LARGE("Grande", 0.032f) }

data class SignatureStyle(
    val position: SignaturePosition = SignaturePosition.LEFT,
    val size: SignatureSize = SignatureSize.MEDIUM,
    /** 0.3..1 */
    val opacity: Float = 0.85f,
    val device: String = Signature.deviceName(),
) {
    fun toMeta(): String = "${position.name};${size.name};$opacity;$device"

    companion object {
        fun fromMeta(s: String?): SignatureStyle? = s?.split(';')?.takeIf { it.size == 4 }?.let {
            runCatching { SignatureStyle(SignaturePosition.valueOf(it[0]), SignatureSize.valueOf(it[1]), it[2].toFloat(), it[3]) }.getOrNull()
        }
    }
}

/**
 * Assinatura "NOVA CAMERA · Shot on Galaxy S24 Ultra" desenhada no canto inferior da versão final.
 * Nunca é aplicada ao original guardado em "Originais". Discreta: texto branco com sombra suave,
 * sem faixa nem moldura que cubra a foto.
 */
object Signature {
    /** Nome comercial do aparelho a partir do modelo real (os demais usam o modelo do sistema). */
    fun deviceName(model: String = Build.MODEL ?: "", manufacturer: String = Build.MANUFACTURER ?: ""): String {
        val m = model.uppercase()
        val galaxy = mapOf(
            "SM-S928" to "Galaxy S24 Ultra", "SM-S926" to "Galaxy S24+", "SM-S921" to "Galaxy S24",
            "SM-S938" to "Galaxy S25 Ultra", "SM-S936" to "Galaxy S25+", "SM-S931" to "Galaxy S25",
        )
        galaxy.entries.firstOrNull { m.startsWith(it.key) }?.let { return it.value }
        val brand = manufacturer.replaceFirstChar { it.uppercase() }
        return if (brand.isNotEmpty() && !model.startsWith(brand, ignoreCase = true)) "$brand $model" else model
    }

    fun text(style: SignatureStyle) = "NOVA CAMERA" to "Shot on ${style.device}"

    /** Desenha a assinatura no lugar ([bmp] precisa ser mutável). */
    fun draw(bmp: Bitmap, style: SignatureStyle) {
        val canvas = Canvas(bmp)
        val short = min(bmp.width, bmp.height)
        val h = short * style.size.fraction
        val margin = short * 0.035f
        val alpha = (style.opacity.coerceIn(0.3f, 1f) * 255).roundToInt()
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); this.alpha = alpha
            textSize = h; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.22f
            setShadowLayer(h * 0.35f, 0f, h * 0.06f, ((alpha * 0.55f).roundToInt() shl 24))
        }
        val sub = Paint(brand).apply {
            textSize = h * 0.62f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); letterSpacing = 0.06f
        }
        val (t1, t2) = text(style)
        val w1 = brand.measureText(t1); val w2 = sub.measureText(t2)
        val block = maxOf(w1, w2)
        val left = when (style.position) {
            SignaturePosition.LEFT -> margin
            SignaturePosition.CENTER -> (bmp.width - block) / 2f
            SignaturePosition.RIGHT -> bmp.width - margin - block
        }
        fun x(w: Float) = when (style.position) {
            SignaturePosition.LEFT -> left
            SignaturePosition.CENTER -> (bmp.width - w) / 2f
            SignaturePosition.RIGHT -> left + block - w
        }
        val baseline2 = bmp.height - margin
        val baseline1 = baseline2 - sub.textSize * 1.35f
        canvas.drawText(t1, x(w1), baseline1, brand)
        canvas.drawText(t2, x(w2), baseline2, sub)
    }
}
