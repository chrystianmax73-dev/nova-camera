package com.novacamera.s24.processing

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

/** Gravação de JPEGs processados na galeria (MediaStore) ou em arquivos privados do app. */
object MediaWriter {
    const val GALLERY = "Pictures/Nova Camera"
    const val ORIGINALS = "Pictures/Nova Camera/Originais"
    /** Nome e versão reais do app no EXIF (definido em [identify]; antes era fixo em "0.9"). */
    @Volatile var software = "Nova Camera"
        private set

    fun identify(context: android.content.Context) {
        appContext = context.applicationContext
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()?.let { software = "Nova Camera $it" }
    }

    @Volatile private var appContext: android.content.Context? = null

    /**
     * Depois de regravar um arquivo que já está na galeria (FOTO comum: a foto da câmera vira a versão Nova
     * no mesmo arquivo; IA em segundo plano; "Ajuste salvo"), avisa o Android: o scanner relê o arquivo
     * (tamanho, data) e a galeria do aparelho descarta a imagem que tinha guardado. Sem isso a galeria da
     * Samsung podia continuar mostrando a versão antiga, como se o processamento não tivesse acontecido.
     */
    fun refresh(resolver: ContentResolver, uri: Uri) {
        runCatching { resolver.notifyChange(uri, null) }
        val ctx = appContext ?: return
        val path = runCatching {
            @Suppress("DEPRECATION")
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: return
        runCatching { android.media.MediaScannerConnection.scanFile(ctx, arrayOf(path), arrayOf("image/jpeg"), null) }
    }

    /** Relê o que ficou de fato no arquivo da galeria (descrição gravada e tamanho), para o diagnóstico. */
    fun verify(resolver: ContentResolver, uri: Uri): String = runCatching {
        val size = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        val desc = resolver.openInputStream(uri)?.use { ExifInterface(it).getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION) }
        "galeria conferida: ${if (desc?.startsWith("Nova") == true) "versão Nova" else "SEM marca Nova"} · ${size / 1024} KB · \"${desc?.take(60) ?: "sem descrição"}\""
    }.getOrElse { "galeria não conferida: ${it.javaClass.simpleName}" }

    private val EXIF_TAGS = listOf(
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        // Local (só existe quando a opção "Salvar local" estava ligada no disparo).
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_TIMESTAMP,
    )

    /** Metadados técnicos do JPEG original (data, ISO, exposição, lente), para copiar na versão processada. */
    fun exifOf(jpeg: ByteArray): Map<String, String> = runCatching {
        val src = ExifInterface(ByteArrayInputStream(jpeg))
        EXIF_TAGS.mapNotNull { t -> src.getAttribute(t)?.let { t to it } }.toMap()
    }.getOrDefault(emptyMap())

    private fun writeExif(exif: ExifInterface, tags: Map<String, String>, description: String?) {
        tags.forEach { (k, v) -> exif.setAttribute(k, v) }
        // Os pixels já estão na orientação real (rotação/espelho aplicados).
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        // Identificação do app; fabricante/modelo/lente reais vêm do JPEG da câmera (copiados acima).
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, software)
        description?.let { exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, it) }
        exif.saveAttributes()
    }

    /** Nova entrada na galeria. */
    fun insert(
        resolver: ContentResolver, bmp: Bitmap, displayName: String, relativePath: String,
        tags: Map<String, String>, description: String?, quality: Int = 95,
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("galeria indisponível")
        try {
            write(resolver, uri, bmp, tags, description, quality)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    /** Grava os bytes do JPEG como estão (original da câmera, sem recompressão nem perda). */
    fun insertBytes(resolver: ContentResolver, jpeg: ByteArray, displayName: String, relativePath: String): Uri? = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { it.write(jpeg) } ?: error("sem fluxo de saída")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) { runCatching { resolver.delete(uri, null, null) }; throw e }
    }.getOrNull()

    /** Regrava uma entrada que o próprio app criou (ex.: nova intensidade escolhida na revisão). */
    fun write(resolver: ContentResolver, uri: Uri, bmp: Bitmap, tags: Map<String, String>, description: String?, quality: Int = 95) {
        // Comprimir antes de truncar: a entrada da galeria fica vazia só durante a cópia dos bytes,
        // não durante a compressão inteira (troca em segundo plano / reedição).
        val jpeg = java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        synchronized(LiveClips.lock) {
            resolver.openOutputStream(uri, "wt")?.use { jpeg.writeTo(it) }
                ?: error("sem fluxo de saída")
            resolver.openFileDescriptor(uri, "rw")?.use { pfd -> writeExif(ExifInterface(pfd.fileDescriptor), tags, description) }
        }
        // Nova Live: a regravação apagou o vídeo do fim do arquivo; anexa de novo (depois do EXIF).
        LiveClips.reattach(resolver, uri)
        refresh(resolver, uri)
    }

    /** Arquivo privado (sessão de revisão); não aparece na galeria. */
    fun writeFile(file: File, bmp: Bitmap, quality: Int = 95) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}
