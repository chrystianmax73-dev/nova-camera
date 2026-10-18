package com.novacamera.s24.processing

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.novacamera.s24.core.imaging.Plane

/**
 * Profundidade relativa para o Retrato: Depth Anything V2 **Small** (Apache-2.0; as versões maiores
 * são de uso não comercial e não entram) convertido para ncnn no CI (tools/depth/export_ncnn.py).
 * Uma inferência por foto, numa cópia reduzida e quadrada; o modelo é liberado em seguida.
 * Sem o modelo na build, [available] é falso e o Retrato usa só a segmentação.
 */
object DepthEstimator {
    private const val TAG = "NovaDepth"
    private const val PARAM = "depth/depth.param"
    private const val BIN = "depth/depth.bin"
    /** Lado da entrada fixado na exportação (múltiplo de 14). */
    const val SIZE = 392

    private val libLoaded = runCatching { System.loadLibrary("novadenoise") }.onFailure { Log.w(TAG, "biblioteca nativa ausente", it) }.isSuccess
    @Volatile var report = "Profundidade: ainda não executada"
        private set

    @JvmStatic private external fun nativeLoad(assets: AssetManager, param: String, bin: String, useGpu: Boolean): Long
    @JvmStatic private external fun nativeUsesGpu(handle: Long): Boolean
    @JvmStatic private external fun nativeRun(handle: Long, input: Bitmap, output: FloatArray): Boolean
    @JvmStatic private external fun nativeRelease(handle: Long)

    fun available(context: Context): Boolean =
        libLoaded && runCatching { context.assets.list("depth")?.toSet()?.containsAll(listOf("depth.param", "depth.bin")) == true }.getOrDefault(false)

    /**
     * Profundidade inversa relativa (maior = mais perto) da foto inteira, num plano [SIZE]×[SIZE]
     * esticado sobre a foto (o chamador redimensiona para a proporção dela). Null se indisponível.
     */
    @Synchronized
    fun estimate(context: Context, photo: Bitmap): Plane? {
        if (!available(context)) { report = "Profundidade: modelo não incluído nesta build"; return null }
        val t0 = SystemClock.elapsedRealtime()
        // Carregar o modelo custava ~2,5 s a cada Retrato (diagnóstico 0.26.4: carga 2482–3558 ms para
        // 1,4 s de inferência). Fica carregado entre fotos e é liberado depois de [IDLE_MS] sem uso.
        val reused = cached != null
        val session = cached ?: Session.open(context)?.also { cached = it }
            ?: run { report = "Profundidade: falha ao carregar o modelo"; return null }
        val tLoad = SystemClock.elapsedRealtime() - t0
        val t1 = SystemClock.elapsedRealtime()
        val out = session.run(photo)
        lastUsedMs = SystemClock.elapsedRealtime()
        report = "Profundidade (Depth Anything V2 Small): ${if (out != null) "ok" else "falhou"} · backend=" +
            "${if (session.gpu) "Vulkan" else "CPU"}, carga=${if (reused) "0ms (modelo já carregado)" else "${tLoad}ms"}, " +
            "inferência=${SystemClock.elapsedRealtime() - t1}ms"
        Log.i(TAG, report)
        return out
    }

    /** Sem uso por este tempo, o modelo da foto é liberado (memória da GPU/CPU). */
    const val IDLE_MS = 60_000L
    private var cached: Session? = null
    private var lastUsedMs = 0L

    /** Libera o modelo mantido entre fotos (pressão de memória, calor ou tempo sem uso). */
    @Synchronized
    fun release() { cached?.close(); cached = null }

    /**
     * Mesma sessão para a prévia ao vivo do Retrato e para a foto: antes cada uma carregava a sua
     * (modelo duas vezes na memória e o 1º Retrato pagava ~2,5 s de carga com a prévia já ligada).
     * O chamador não fecha a sessão; quem libera é [release]/[releaseIfIdle].
     */
    @Synchronized
    fun shared(context: Context): Session? {
        if (!available(context)) return null
        val session = cached ?: Session.open(context)?.also { cached = it } ?: return null
        lastUsedMs = SystemClock.elapsedRealtime()
        return session
    }

    @Synchronized
    fun releaseIfIdle(nowMs: Long) { if (cached != null && nowMs - lastUsedMs >= IDLE_MS) release() }

    /** Modelo carregado uma vez (prévia ao vivo do Retrato); [close] obrigatório. */
    class Session private constructor(private val handle: Long,val loadMs:Long) : java.io.Closeable {
        val gpu = nativeUsesGpu(handle)
        @Volatile private var closed = false
        private val out = FloatArray(SIZE * SIZE)
        var prepareMs=0L;private set
        var nativeMs=0L;private set
        var resultMs=0L;private set

        @Synchronized
        fun run(photo: Bitmap): Plane? {
            if (closed) return null
            val start=SystemClock.elapsedRealtime()
            val input = Bitmap.createScaledBitmap(photo, SIZE, SIZE, true)
            val beforeNative=SystemClock.elapsedRealtime();prepareMs=beforeNative-start
            try {
                val ok = nativeRun(handle, input, out)
                val afterNative=SystemClock.elapsedRealtime();nativeMs=afterNative-beforeNative
                return (if (ok && out.any { it.isFinite() && it != out[0] }) Plane(SIZE, SIZE, out.copyOf()) else null)
                    .also { resultMs=SystemClock.elapsedRealtime()-afterNative }
            } finally { if (input !== photo) input.recycle() }
        }

        @Synchronized
        override fun close() { if (!closed) { closed = true; nativeRelease(handle) } }

        companion object {
            fun open(context: Context): Session? {
                if (!available(context)) return null
                val at=SystemClock.elapsedRealtime()
                com.novacamera.s24.core.ModelLoadWindow.begin("profundidade", SystemClock.uptimeMillis())
                val h = try { nativeLoad(context.assets, PARAM, BIN, true) }
                    finally { com.novacamera.s24.core.ModelLoadWindow.end(SystemClock.uptimeMillis()) }
                return if (h == 0L) null else Session(h,SystemClock.elapsedRealtime()-at)
            }
        }
    }
}
