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
    /** Incluir a linha com os dados reais da captura (EXIF), quando a câmera os gravou. */
    val captureInfo: Boolean = false,
    /** Linha já resolvida do EXIF da foto; null = sem dados (vídeo, ou opção desligada). */
    val details: String? = null,
) {
    fun toMeta(): String = "${position.name};${size.name};$opacity;$device;$captureInfo"

    /** Resolve a linha de dados a partir do EXIF desta foto (só se a opção estiver ligada). */
    fun withExif(exif: Map<String, String>): SignatureStyle =
        if (!captureInfo) this else copy(details = com.novacamera.s24.core.CaptureInfo.line(exif))

    companion object {
        fun fromMeta(s: String?): SignatureStyle? = s?.split(';')?.takeIf { it.size == 4 || it.size == 5 }?.let {
            runCatching {
                SignatureStyle(SignaturePosition.valueOf(it[0]), SignatureSize.valueOf(it[1]), it[2].toFloat(), it[3],
                    it.getOrNull(4)?.toBoolean() ?: false)
            }.getOrNull()
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
    fun draw(bmp: Bitmap, style: SignatureStyle) = drawOn(Canvas(bmp), bmp.width, bmp.height, style)

    /** Desenha numa tela [width]×[height] já na orientação de quem vê (foto ou quadro de vídeo). */
    fun drawOn(canvas: Canvas, width: Int, height: Int, style: SignatureStyle) {
        val short = min(width, height)
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
        val t3 = style.details
        val info = Paint(sub).apply { textSize = h * 0.55f; this.alpha = (alpha * 0.85f).roundToInt(); letterSpacing = 0.04f }
        val w1 = brand.measureText(t1); val w2 = sub.measureText(t2); val w3 = t3?.let { info.measureText(it) } ?: 0f
        val block = maxOf(w1, w2, w3)
        val left = when (style.position) {
            SignaturePosition.LEFT -> margin
            SignaturePosition.CENTER -> (width - block) / 2f
            SignaturePosition.RIGHT -> width - margin - block
        }
        fun x(w: Float) = when (style.position) {
            SignaturePosition.LEFT -> left
            SignaturePosition.CENTER -> (width - w) / 2f
            SignaturePosition.RIGHT -> left + block - w
        }
        // Com dados da captura: marca, aparelho e a linha técnica, de cima para baixo.
        val baseline3 = height - margin
        val baseline2 = if (t3 != null) baseline3 - info.textSize * 1.45f else baseline3
        val baseline1 = baseline2 - sub.textSize * 1.35f
        canvas.drawText(t1, x(w1), baseline1, brand)
        canvas.drawText(t2, x(w2), baseline2, sub)
        t3?.let { canvas.drawText(it, x(w3), baseline3, info) }
    }

    /**
     * Matriz que leva coordenadas "de pé" (como o vídeo será visto) para o quadro bruto do
     * codificador: recorte visível [crop] e rotação [rotationDegrees] que o player aplicará.
     * Devolve a matriz e o tamanho da área de pé.
     */
    fun uprightToBuffer(crop: android.graphics.Rect, rotationDegrees: Int): Triple<android.graphics.Matrix, Int, Int> {
        val r = ((rotationDegrees % 360) + 360) % 360
        val uw = if (r % 180 == 0) crop.width() else crop.height()
        val uh = if (r % 180 == 0) crop.height() else crop.width()
        val m = android.graphics.Matrix()
        m.postRotate(-r.toFloat())
        val bounds = android.graphics.RectF(0f, 0f, uw.toFloat(), uh.toFloat())
        m.mapRect(bounds)
        m.postTranslate(-bounds.left + crop.left, -bounds.top + crop.top)
        return Triple(m, uw, uh)
    }
}
