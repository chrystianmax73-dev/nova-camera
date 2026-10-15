package com.novacamera.s24.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import com.novacamera.s24.core.imaging.Adaptive
import com.novacamera.s24.core.imaging.ToneMap
import java.io.File

/** Codec local BSD/IJG, sequencial. Falha deixa o JPEG original como fotografia final. */
object JpegBands {
    val available = runCatching { System.loadLibrary("novadenoise") }.isSuccess
    private external fun open(input: String, output: String): Long
    private external fun dimensions(handle: Long): IntArray
    private external fun read(handle: Long, target: IntArray, rows: Int): Int
    private external fun write(handle: Long, source: IntArray, rows: Int): Boolean
    private external fun close(handle: Long, complete: Boolean): Boolean

    fun process(input: File, output: File, plan: Adaptive.Plan, intensity: Float,
        signature: SignatureStyle?, cancel: CancelToken, tags: Map<String,String>,
        style: com.novacamera.s24.core.imaging.PhotoStyle = com.novacamera.s24.core.imaging.PhotoStyle(), progress: (Float)->Unit): Boolean {
        if(!available)return false
        val handle=open(input.path,output.path)
        if(handle==0L){output.delete();return false}
        var live=true
        try {
            val (w,h)=dimensions(handle)
            val rows=minOf(64,h)
            val pixels=IntArray(w*rows)
            val tone=ToneMap(plan.base,plan.params,w,h,plan.mid)
            val orientation=ExifInterface(input).rotationDegrees
            val signatureTransform=Signature.uprightToBuffer(Rect(0,0,w,h),orientation)
            val deadline=android.os.SystemClock.elapsedRealtime()+10_000
            var y=0
            while(y<h) {
                if(cancel.cancelled)throw CancelledException()
                check(android.os.SystemClock.elapsedRealtime()<deadline) { "Orçamento de processamento excedido" }
                val count=read(handle,pixels,minOf(rows,h-y));check(count>0)
                if(!plan.skip) tone.applyTile(pixels,w,count,0,y,plan.strength*intensity,plan.imaging?.protection ?: 0f)
                if(!style.neutral) for(i in 0 until w*count) pixels[i]=style.apply(pixels[i])
                if(signature!=null) {
                    val strip=Bitmap.createBitmap(w,count,Bitmap.Config.ARGB_8888).apply { setPixels(pixels,0,w,0,0,w,count) }
                    try {
                        val canvas=Canvas(strip);canvas.translate(0f,-y.toFloat());canvas.concat(signatureTransform.first)
                        Signature.drawOn(canvas,signatureTransform.second,signatureTransform.third,signature)
                        strip.getPixels(pixels,0,w,0,0,w,count)
                    } finally { strip.recycle() }
                }
                check(write(handle,pixels,count));y+=count;progress(y.toFloat()/h)
            }
            live=false;check(close(handle,true))
            // Conservar orientação e EXIF técnico da captura. Não copiar localização para metadados novos.
            val original=ExifInterface(input)
            ExifInterface(output).apply {
                tags.forEach { (k,v)->setAttribute(k,v) }
                setAttribute(ExifInterface.TAG_ORIENTATION,original.getAttribute(ExifInterface.TAG_ORIENTATION) ?: "1")
                original.getAttribute(ExifInterface.TAG_COLOR_SPACE)?.let { setAttribute(ExifInterface.TAG_COLOR_SPACE,it) }
                setAttribute(ExifInterface.TAG_SOFTWARE,MediaWriter.SOFTWARE);saveAttributes()
            }
            return true
        } catch(e:CancelledException) { throw e }
        catch(e:Exception) { return false }
        finally { if(live)close(handle,false);if(live)output.delete() }
    }
}
