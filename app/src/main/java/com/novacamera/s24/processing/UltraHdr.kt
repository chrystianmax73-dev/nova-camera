package com.novacamera.s24.processing

import android.graphics.Bitmap
import android.graphics.Gainmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.novacamera.s24.core.imaging.HdrGain
import com.novacamera.s24.core.imaging.Plane
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Anexa o gainmap Ultra HDR (Android 14+) à foto final de uma sessão HDR, quando a fusão registrou
 * faixa acima do branco SDR. Bitmap.compress grava então um JPEG Ultra HDR: telas SDR e apps
 * antigos veem a foto normal; a galeria do S24 mostra os realces mais claros na tela HDR.
 * Chamado depois do acabamento e da assinatura, sempre a partir da base (nunca acumula).
 */
object UltraHdr {
    private const val TAG = "NovaUltraHdr"

    /** A câmera anuncia captura Ultra HDR própria (JPEG_R) a apps de terceiros. Só diagnóstico. */
    @Volatile var deviceReport = "Ultra HDR do aparelho: câmera ainda não vinculada"

    fun attach(session: Session, bmp: Bitmap) {
        if (Build.VERSION.SDK_INT < 34) return
        val scene = session.loadHdr() ?: return
        runCatching { Api34.attach(bmp, scene) }.onFailure { Log.w(TAG, "gainmap não anexado", it) }
    }

    @RequiresApi(34)
    private object Api34 {
        fun attach(bmp: Bitmap, scene: Plane) {
            val factor = max(1, bmp.width / scene.w)
            val ratio = HdrGain.ratio(scene, Engine.luma(bmp, factor))
            val peak = HdrGain.peak(ratio)
            if (peak < 1.1f) { HdrGain.lastReport = "Ultra HDR: a última foto HDR não tinha realces além do branco SDR"; return }
            val bytes = HdrGain.encode(ratio, peak)
            val g = Bitmap.createBitmap(scene.w, scene.h, Bitmap.Config.ALPHA_8)
            val buf = ByteBuffer.allocate(g.rowBytes * g.height)
            for (y in 0 until scene.h) { buf.position(y * g.rowBytes); buf.put(bytes, y * scene.w, scene.w) }
            buf.rewind()
            g.copyPixelsFromBuffer(buf)
            bmp.gainmap = Gainmap(g).apply {
                setRatioMin(1f, 1f, 1f); setRatioMax(peak, peak, peak); setGamma(1f, 1f, 1f)
                setEpsilonSdr(0f, 0f, 0f); setEpsilonHdr(0f, 0f, 0f)
                minDisplayRatioForHdrTransition = 1f; displayRatioForFullHdr = peak
            }
            HdrGain.lastReport = HdrGain.describe(peak, null)
        }
    }
}
