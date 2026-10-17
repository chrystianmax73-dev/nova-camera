package com.novacamera.s24.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView

/** Snapshot reduzido, sem getBitmap() bloqueante do PreviewView, sem stream de câmera adicional. */
class PreviewFrameReader(
    private val preview: PreviewView,
    private val copy: (SurfaceView, Bitmap, (Int) -> Unit) -> Unit = { view, bitmap, done ->
        PixelCopy.request(view, bitmap, { result -> done(result) }, Handler(Looper.getMainLooper()))
    },
) : AutoCloseable {
    private var pending = false
    private var closed = false
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private fun find(view: View): View? {
        if (view is SurfaceView || view is TextureView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
        return null
    }

    /** Callback na principal; o consumidor libera apenas o bitmap reduzido entregue. */
    fun read(done: (Bitmap?, Double) -> Unit): Boolean {
        if (closed || pending || preview.width <= 0 || preview.height <= 0) return false
        val child = find(preview) ?: return false
        if (child.width <= 0 || child.height <= 0) return false
        val scale = minOf(1f, 320f / maxOf(child.width, child.height))
        val w = (child.width * scale).toInt().coerceAtLeast(1)
        val h = (child.height * scale).toInt().coerceAtLeast(1)
        val outScale = minOf(1f, 320f / maxOf(preview.width, preview.height))
        val ow = (preview.width*outScale).toInt().coerceAtLeast(48)
        val oh = (preview.height*outScale).toInt().coerceAtLeast(48)
        val transform = geometry(child, w, h, ow, oh)
        val raw = try { Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888) }
            catch (_: OutOfMemoryError) { done(null,0.0); return true }
        pending = true
        val start = SystemClock.elapsedRealtimeNanos()
        var completed = false
        fun finish(ok: Boolean) {
            if (completed) return
            completed = true
            pending = false
            val elapsed = (SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0
            var result: Bitmap? = null
            try {
                if (ok && !closed) {
                    result = Bitmap.createBitmap(ow,oh,Bitmap.Config.ARGB_8888)
                    Canvas(result!!).drawBitmap(raw,transform,paint)
                }
            } catch (_: RuntimeException) { result?.recycle(); result=null }
            catch (_: OutOfMemoryError) { result?.recycle(); result=null }
            finally { raw.recycle() }
            done(result,elapsed)
        }
        try {
            when (child) {
                is SurfaceView -> copy(child,raw) { finish(it==PixelCopy.SUCCESS) }
                is TextureView -> {
                    // getBitmap(Bitmap) copia diretamente no destino pequeno; sem bitmap de 4K.
                    val ok=child.getBitmap(raw)!=null
                    Handler(Looper.getMainLooper()).post { finish(ok) }
                }
                else -> finish(false)
            }
        } catch (_: RuntimeException) { finish(false) }
        return true
    }

    private fun geometry(child: View, w: Int, h: Int, ow: Int, oh: Int): Matrix {
        val toView=Matrix()
        child.transformMatrixToGlobal(toView)
        preview.transformMatrixToLocal(toView)
        // PixelCopy/TextureView screenshot usa a orientação natural da superfície.
        // Corrige a rotação restante de display antes da escala/recorte das Views CameraX.
        val degrees=when(child.display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> -90f; Surface.ROTATION_180 -> -180f
            Surface.ROTATION_270 -> -270f; else -> 0f
        }
        return map(w,h,child.width,child.height,preview.width,preview.height,ow,oh,toView,degrees)
    }

    override fun close() { closed=true } // Bitmap em voo só é liberado no callback do PixelCopy.

    companion object {
        internal fun map(w:Int,h:Int,sw:Int,sh:Int,vw:Int,vh:Int,ow:Int,oh:Int,
            childToView:Matrix,degrees:Float):Matrix {
            val correction=Matrix().apply { setRotate(degrees,sw/2f,sh/2f) }
            val bounds=RectF(0f,0f,sw.toFloat(),sh.toFloat()).also { correction.mapRect(it) }
            val fit=Matrix().apply { setRectToRect(bounds,RectF(0f,0f,sw.toFloat(),sh.toFloat()),Matrix.ScaleToFit.FILL) }
            correction.postConcat(fit)
            return Matrix().apply {
                setScale(sw.toFloat()/w,sh.toFloat()/h)
                postConcat(correction); postConcat(childToView)
                postScale(ow.toFloat()/vw,oh.toFloat()/vh)
            }
        }
    }
}
