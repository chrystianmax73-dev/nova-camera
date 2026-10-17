package com.novacamera.s24.camera

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.camera2.params.MeteringRectangle

/** Usa a transformação pública do PreviewView, incluindo recorte/zoom e espelho frontal. */
object TrackingMetering {
    fun region(sensorToView: Matrix, sensor: Rect, viewW: Int, viewH: Int,
        x: Float, y: Float, side: Float): MeteringRectangle? {
        if (viewW <= 0 || viewH <= 0) return null
        val inverse = Matrix()
        if (!sensorToView.invert(inverse)) return null
        val half = minOf(viewW, viewH) * side.coerceIn(0.08f, 0.4f) / 2
        val rect = RectF(x * viewW - half, y * viewH - half, x * viewW + half, y * viewH + half)
        inverse.mapRect(rect)
        val pixels = Rect(); rect.roundOut(pixels)
        if (!pixels.intersect(sensor) || pixels.width() < 2 || pixels.height() < 2) return null
        return MeteringRectangle(pixels, MeteringRectangle.METERING_WEIGHT_MAX)
    }
}
