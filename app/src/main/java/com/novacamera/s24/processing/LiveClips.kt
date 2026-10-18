package com.novacamera.s24.processing

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.novacamera.s24.core.MotionPhoto
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Nova Live: liga o clipe curto de [com.novacamera.s24.camera.LiveRecorder] à foto final na galeria.
 * A foto pode ser regravada depois (acabamento, IA, revisão): [MediaWriter.write] chama [reattach] e o
 * vídeo volta a ser anexado. Tudo sob [lock] para nunca ler um JPEG pela metade.
 */
object LiveClips {
    class Clip {
        @Volatile var file: File? = null; private set
        @Volatile var presentationUs = 0L; private set
        @Volatile var finished = false; private set
        private val waiters = mutableListOf<() -> Unit>()
        fun complete(file: File?, presentationUs: Long) {
            val run = synchronized(this) {
                if (finished) return
                this.file = file; this.presentationUs = presentationUs; finished = true
                waiters.toList().also { waiters.clear() }
            }
            run.forEach { it() }
        }
        fun onFinished(block: () -> Unit) {
            val now = synchronized(this) { if (finished) true else { waiters += block; false } }
            if (now) block()
        }
    }

    val lock = Any()
    private val byUri = ConcurrentHashMap<Uri, Clip>()
    @Volatile var report = "Nova Live: nenhuma foto com movimento nesta sessão"; private set

    fun bind(resolver: ContentResolver, uri: Uri, clip: Clip) {
        byUri[uri] = clip
        clip.onFinished { runCatching { reattach(resolver, uri) } }
    }

    /** Anexa (de novo) o vídeo ao JPEG atual da [uri], se ela tem um clipe pronto. Idempotente. */
    fun reattach(resolver: ContentResolver, uri: Uri) {
        val clip = byUri[uri]?.takeIf { it.finished } ?: return
        val video = clip.file
        if (video == null || !video.exists()) { report = "Nova Live: clipe não ficou pronto; foto salva sem movimento"; return }
        synchronized(lock) {
            val jpeg = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            MotionPhoto.refuse(jpeg)?.let { reason ->
                if (reason != "já tem XMP") report = "Nova Live não anexado: $reason"
                return
            }
            val out = runCatching { MotionPhoto.build(jpeg, video.readBytes(), clip.presentationUs) }
                .onFailure { Log.w("NovaLive", "falha ao montar", it) }.getOrNull() ?: return
            resolver.openOutputStream(uri, "wt")?.use { it.write(out) }
            report = "Nova Live: última foto com ${video.length() / 1024} KB de vídeo (${clip.presentationUs / 1000} ms antes do disparo)"
        }
    }
}
