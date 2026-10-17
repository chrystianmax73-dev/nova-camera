package com.novacamera.s24.processing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.Size

/** A textura do OverlayEffect persiste: redesenhar só quando a geometria muda, não 60 vezes/s. */
class VideoSignatureOverlay(private val style: SignatureStyle) {
    private data class Geometry(val crop: Rect, val rotation: Int, val mirrored: Boolean, val buffer: Size)
    private var geometry: Geometry? = null
    @Volatile var drawings = 0
        private set

    fun draw(crop: Rect, rotation: Int, mirrored: Boolean, buffer: Size = Size(crop.right, crop.bottom), canvas: () -> Canvas): Boolean {
        if (crop.isEmpty) return false
        val next = Geometry(Rect(crop), rotation, mirrored, buffer)
        if (geometry == next) return false
        val (matrix, width, height) = Signature.uprightToBuffer(crop, rotation, mirrored)
        val target = canvas()
        target.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val save = target.save()
        try {
            target.concat(matrix)
            Signature.drawOn(target, width, height, style)
        } finally { target.restoreToCount(save) }
        geometry = next
        drawings++
        return true
    }
}
