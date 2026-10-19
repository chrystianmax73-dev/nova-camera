package com.novacamera.s24.core

/**
 * Nova Live: foto com movimento no formato Google Motion Photo v1 (JPEG + XMP + MP4 no fim do
 * arquivo). O dono testou 3 variações no S24 (Google, trailer Samsung, combinado) e as três tocaram
 * na Galeria; fica a do Google, que também é lida pelo Google Fotos e não depende de formato privado.
 * Formato: developer.android.com/media/platform/motion-photo-format.
 */
object MotionPhoto {
    private val XMP_NS = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.ISO_8859_1)

    /** Motivo para não anexar (null = pode). JPEG com XMP próprio ou Ultra HDR (MPF) fica como está. */
    fun refuse(jpeg: ByteArray): String? {
        if (jpeg.size < 4 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return "não é JPEG"
        var i = 2
        while (i + 4 <= jpeg.size && jpeg[i] == 0xFF.toByte()) {
            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) break // início da imagem: acabaram os segmentos de metadados
            val len = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            if (len < 2 || i + 2 + len > jpeg.size) return "JPEG malformado"
            if (marker == 0xE1 && startsWith(jpeg, i + 4, XMP_NS)) return "já tem XMP"
            if (marker == 0xE2 && startsWith(jpeg, i + 4, "MPF\u0000".toByteArray(Charsets.ISO_8859_1))) return "Ultra HDR (MPF)"
            i += 2 + len
        }
        return null
    }

    fun build(jpeg: ByteArray, mp4: ByteArray, presentationUs: Long): ByteArray {
        refuse(jpeg)?.let { throw IllegalArgumentException(it) }
        val xmp = xmp(mp4.size, presentationUs.coerceAtLeast(0)).toByteArray(Charsets.UTF_8)
        val payload = XMP_NS + xmp
        require(payload.size + 2 <= 0xFFFF) { "XMP grande demais" }
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), ((payload.size + 2) shr 8).toByte(), (payload.size + 2).toByte()) + payload
        val out = java.io.ByteArrayOutputStream(jpeg.size + app1.size + mp4.size)
        out.write(jpeg, 0, 2); out.write(app1); out.write(jpeg, 2, jpeg.size - 2); out.write(mp4)
        return out.toByteArray()
    }

    private fun xmp(videoLength: Int, presentationUs: Long) =
        """<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Nova Camera"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">""" +
        """<rdf:Description rdf:about="" xmlns:GCamera="http://ns.google.com/photos/1.0/camera/" """ +
        """xmlns:Container="http://ns.google.com/photos/1.0/container/" xmlns:Item="http://ns.google.com/photos/1.0/container/item/" """ +
        """GCamera:MotionPhoto="1" GCamera:MotionPhotoVersion="1" GCamera:MotionPhotoPresentationTimestampUs="$presentationUs" """ +
        """GCamera:MicroVideo="1" GCamera:MicroVideoVersion="1" GCamera:MicroVideoOffset="$videoLength" GCamera:MicroVideoPresentationTimestampUs="$presentationUs">""" +
        """<Container:Directory><rdf:Seq>""" +
        """<rdf:li rdf:parseType="Resource"><Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/></rdf:li>""" +
        """<rdf:li rdf:parseType="Resource"><Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="$videoLength" Item:Padding="0"/></rdf:li>""" +
        """</rdf:Seq></Container:Directory></rdf:Description></rdf:RDF></x:xmpmeta>"""

    private fun startsWith(a: ByteArray, at: Int, p: ByteArray) = at + p.size <= a.size && p.indices.all { a[at + it] == p[it] }
}
