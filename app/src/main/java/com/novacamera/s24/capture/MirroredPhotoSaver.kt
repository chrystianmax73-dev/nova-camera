package com.novacamera.s24.capture

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

/**
 * Salva a selfie "como visualizada": aplica o recorte do visor, a rotação e o espelhamento
 * nos pixels (não depende de visualizadores respeitarem orientações EXIF espelhadas).
 * Mantém a resolução capturada e copia os metadados técnicos do JPEG original.
 */
object MirroredPhotoSaver {
    private val EXIF_TAGS = listOf(
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_SHUTTER_SPEED_VALUE,
    )

    /** Matriz aplicada ao recorte: rotação para a orientação real e espelho horizontal (como no visor). */
    fun transform(rotationDegrees: Int): Matrix = Matrix().apply {
        postRotate(rotationDegrees.toFloat())
        postScale(-1f, 1f)
    }

    fun save(
        resolver: ContentResolver,
        jpeg: ByteArray,
        crop: Rect?,
        rotationDegrees: Int,
        values: ContentValues,
        quality: Int = 95,
    ): Uri? {
        val original = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        val exifIn = runCatching { ExifInterface(ByteArrayInputStream(jpeg)) }.getOrNull()
        val r = crop?.takeIf { it.width() > 0 && it.height() > 0 && it.right <= original.width && it.bottom <= original.height }
            ?: Rect(0, 0, original.width, original.height)
        // Só a rotação do CameraX: para JPEG ela já é derivada da orientação EXIF do arquivo.
        // (A 0.5 somava as duas e podia girar a selfie 180° em firmwares que gravam EXIF.)
        val out = Bitmap.createBitmap(original, r.left, r.top, r.width(), r.height(), transform(rotationDegrees % 360), true)
        if (out !== original) original.recycle()

        values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                ?: error("sem fluxo de saída")
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exifOut = ExifInterface(pfd.fileDescriptor)
                exifIn?.let { src -> EXIF_TAGS.forEach { tag -> src.getAttribute(tag)?.let { exifOut.setAttribute(tag, it) } } }
                exifOut.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                exifOut.setAttribute(ExifInterface.TAG_SOFTWARE, com.novacamera.s24.processing.MediaWriter.software)
                exifOut.saveAttributes()
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        } finally {
            out.recycle()
        }
    }
}
