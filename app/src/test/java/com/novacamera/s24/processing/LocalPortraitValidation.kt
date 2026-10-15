package com.novacamera.s24.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.novacamera.s24.core.imaging.Plane
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.DataInputStream
import java.io.File

/**
 * Validação local do Retrato (não roda no CI): PORTRAIT_DIR com photo.jpg e as máscaras da
 * segmentação (person.bin, protect.bin: largura, altura e floats big-endian). Grava a máscara
 * final (matte.png) e o resultado (out.jpg). Fotos pessoais nunca entram no repositório.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LocalPortraitValidation {
    private fun plane(f: File) = DataInputStream(f.inputStream().buffered()).use { i ->
        val w = i.readInt(); val h = i.readInt(); Plane(w, h, FloatArray(w * h) { i.readFloat() })
    }

    @Test
    fun runPortraitOnLocalPhoto() {
        val dir = System.getenv("PORTRAIT_DIR")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val bmp = BitmapFactory.decodeFile(File(dir, "photo.jpg").path, BitmapFactory.Options().apply { inMutable = true })
        val old = if (System.getenv("PORTRAIT_OLD") == "1") {
            // Máscara da 0.9.3 (só luminância + transição larga em todo o contorno), para A/B.
            val luma = Engine.luma(bmp, Engine.matteFactor(bmp.width, bmp.height))
            val p = com.novacamera.s24.core.imaging.Portrait.refineMatte(plane(File(dir, "person.bin")), luma)
            val hp = com.novacamera.s24.core.imaging.Portrait.refineMatte(plane(File(dir, "protect.bin")), luma)
            Plane(p.w, p.h, FloatArray(p.data.size) { i ->
                val t0 = ((hp.data[i] - 0.2f) / 0.4f).coerceIn(0f, 1f)
                val a = maxOf(p.data[i], t0 * t0 * (3f - 2f * t0))
                val t = ((a - 0.08f) / 0.47f).coerceIn(0f, 1f); t * t * (3f - 2f * t)
            })
        } else null
        // Mesmo caminho do app: pessoa (se houver person.bin) + profundidade (depth.bin, saída crua do modelo).
        val subject = if (old != null) null else Engine.portraitSubject(bmp,
            File(dir, "person.bin").takeIf { it.exists() }?.let(::plane),
            File(dir, "hair.bin").takeIf { it.exists() }?.let(::plane),
            File(dir, "depth.bin").takeIf { it.exists() }?.let(::plane))
        val matte = old ?: subject?.matte ?: error("sem assunto")
        val blurMap = subject?.blur
        blurMap?.let { b ->
            val bp = IntArray(b.w * b.h) { val v = (b.data[it] * 255).toInt().coerceIn(0, 255); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
            File(dir, "blur.png").outputStream().use { Bitmap.createBitmap(bp, b.w, b.h, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val tag = if (old != null) "_old" else ""
        val px = IntArray(matte.w * matte.h) { val v = (matte.data[it] * 255).toInt().coerceIn(0, 255); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        File(dir, "matte$tag.png").outputStream().use { Bitmap.createBitmap(px, matte.w, matte.h, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val strength = System.getenv("PORTRAIT_BLUR")?.toFloatOrNull() ?: 0.6f
        Engine.portraitInPlace(bmp, matte, strength, CancelToken(), blurMap) {}
        File(dir, "out$tag.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }
}
