package com.novacamera.s24.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import com.novacamera.s24.ReviewActivity
import com.novacamera.s24.core.imaging.Plane
import com.novacamera.s24.processing.JobKind
import com.novacamera.s24.processing.MediaWriter
import com.novacamera.s24.processing.ProcessingQueue
import com.novacamera.s24.processing.Session
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Tela de revisão com uma sessão sintética: compara, reaplica intensidade/desfoque e desenha. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ReviewScreenRenderTest {
    private fun scene(w: Int, h: Int, dark: Boolean): Bitmap {
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val inPerson = ((x - w / 2f) / (w * 0.22f)).let { it * it } + ((y - h * 0.62f) / (h * 0.42f)).let { it * it } < 1f
            val stripes = if ((x / 24 + y / 24) % 2 == 0) 40 else 0
            var r = if (inPerson) 190 else 60 + stripes; var g = if (inPerson) 140 else 120 + stripes; var b = if (inPerson) 120 else 80 + stripes
            if (dark) { r /= 3; g /= 3; b /= 3 }
            px[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    @Test
    fun reviewRendersPortraitSession() {
        val app = RuntimeEnvironment.getApplication()
        val dir = File(ProcessingQueue.sessionsDir(app), "1700000000000").apply { mkdirs() }
        val session = Session(dir)
        val w = 600; val h = 800
        MediaWriter.writeFile(session.original, scene(w, h, dark = true))
        MediaWriter.writeFile(session.base, scene(w, h, dark = true))
        val matte = Plane(150, 200)
        for (y in 0 until 200) for (x in 0 until 150) {
            val d = ((x - 75f) / 33f).let { it * it } + ((y - 124f) / 84f).let { it * it }
            matte[x, y] = if (d < 1f) 1f else 0f
        }
        session.saveMatte(matte)
        session.kind = JobKind.PORTRAIT
        session.hasPerson = true
        session.note = "Exemplo sintético"
        session.save()

        val activity = Robolectric.buildActivity(ReviewActivity::class.java,
            Intent(app, ReviewActivity::class.java).putExtra(ReviewActivity.EXTRA_SESSION, dir.name)).setup().get()
        // O carregamento e o acabamento rodam em segundo plano.
        val compare = findCompare(activity.window.decorView)
        assertNotNull(compare)
        val deadline = System.currentTimeMillis() + 15_000
        while (compare!!.enhanced == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(50)
        }
        assertNotNull("acabamento não terminou", compare.enhanced)
        val root = activity.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val out = File("build/ui-renders").apply { mkdirs() }
        File(out, "revisao-retrato.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Fundo desfocado: as listras de alto contraste do fundo praticamente somem.
        val e = compare.enhanced!!
        fun range(b: Bitmap, x0: Int, y0: Int, size: Int): Int {
            var lo = 255; var hi = 0
            for (y in y0 until y0 + size) for (x in x0 until x0 + size) {
                val g = (b.getPixel(x, y) shr 8) and 0xFF
                lo = minOf(lo, g); hi = maxOf(hi, g)
            }
            return hi - lo
        }
        val baseRange = range(scene(w, h, true), 40, 40, 48)
        val outRange = range(e, 40 * e.width / w, 40 * e.height / h, 48 * e.width / w)
        assertTrue("fundo não desfocou: $baseRange → $outRange", outRange < baseRange * 0.6f)
    }

    private fun findCompare(v: View): CompareView? {
        if (v is CompareView) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findCompare(v.getChildAt(i))?.let { return it }
        return null
    }
}
