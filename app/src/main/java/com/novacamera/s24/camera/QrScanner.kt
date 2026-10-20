package com.novacamera.s24.camera

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.view.PreviewView
import com.novacamera.s24.core.QrContent
import com.novacamera.s24.core.QrDedupe
import java.util.concurrent.Executors

/**
 * QR e códigos de barras sempre ligados na FOTO (pedido do dono, 20/10), como a câmera da Samsung. Lê a
 * própria prévia (sem stream de câmera extra) ~2,5×/s; com calor ou economia, 1×/s; pausa enquanto a foto é
 * capturada. zxing-cpp (Apache-2.0). Nunca abre nada sozinho.
 *
 * Duas leituras por passada (~20 ms cada): a prévia inteira reduzida a 960 px e o centro (metade da largura
 * e da altura) na resolução da tela. O QR pequeno da garrafa de Coca-Cola (20/10) não saía na inteira a 960 px
 * e sai no centro (testado com a foto do dono); ler a prévia cheia de uma vez custaria ~3× mais por passada.
 * A posição do código (4 cantos, em pontos da prévia) vai para a moldura animada do visor.
 */
class QrScanner(
    private val preview: PreviewView,
    private val enabled: () -> Boolean,
    private val slow: () -> Boolean,
    private val onFound: (QrContent) -> Unit,
    private val onGone: () -> Unit,
    /** Cantos do código (x0,y0…x3,y3) em pontos do PreviewView, a cada leitura que o encontra. */
    private val onSeen: ((FloatArray) -> Unit)? = null,
    /** Mesmo quadro, na thread do leitor, antes de liberar (aviso de lente suja). */
    private val inspect: ((android.graphics.Bitmap) -> Unit)? = null,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val reader = PreviewFrameReader(preview, 1920f)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "nova-qr").apply { priority = Thread.MIN_PRIORITY } }
    private val zxing by lazy { zxingcpp.BarcodeReader().apply { options.tryInvert = true } }
    private val dedupe = QrDedupe()
    private var busy = false
    private var running = false
    private var closed = false
    @Volatile var lastReadMs = 0.0
        private set

    fun start() { if (closed || running) return; running = true; handler.post(tick) }
    fun stop() { running = false; handler.removeCallbacks(tick); dedupe.reset(); onGone() }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (!running || closed) return
            val next = if (slow()) 1000L else 400L
            if (!enabled() || busy || preview.previewStreamState.value != PreviewView.StreamState.STREAMING) {
                handler.postDelayed(this, next); return
            }
            busy = true
            val accepted = reader.read { bmp, _ ->
                if (bmp == null || closed) { busy = false; bmp?.recycle(); handler.postDelayed(this, next); return@read }
                worker.execute {
                    val t = SystemClock.elapsedRealtimeNanos()
                    // Pontos do PreviewView por pixel do snapshot (o snapshot já está nas coordenadas da prévia).
                    val toView = preview.width.toFloat() / bmp.width
                    var found: Pair<String, FloatArray>? = null
                    val small = runCatching {
                        val k = minOf(1f, 960f / maxOf(bmp.width, bmp.height))
                        if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
                    }.getOrNull()
                    if (small != null) {
                        found = readOne(small, 0, 0, toView * bmp.width / small.width)
                        inspect?.let { runCatching { it(small) } }
                    }
                    if (found == null) {
                        val cw = bmp.width / 2; val ch = bmp.height / 2
                        val cx = (bmp.width - cw) / 2; val cy = (bmp.height - ch) / 2
                        runCatching { Bitmap.createBitmap(bmp, cx, cy, cw, ch) }.getOrNull()?.let { center ->
                            found = readOne(center, cx, cy, toView)
                            center.recycle()
                        }
                    }
                    lastReadMs = (SystemClock.elapsedRealtimeNanos() - t) / 1e6
                    if (small != null && small !== bmp) small.recycle()
                    bmp.recycle()
                    handler.post {
                        busy = false
                        if (!running || closed) return@post
                        val now = SystemClock.elapsedRealtime()
                        val hit = found
                        if (hit != null) {
                            if (dedupe.fresh(hit.first, now)) onFound(QrContent.classify(hit.first))
                            onSeen?.invoke(hit.second)
                        } else if (dedupe.gone(now)) { dedupe.reset(); onGone() }
                        handler.postDelayed(this, next)
                    }
                }
            }
            if (!accepted) { busy = false; handler.postDelayed(this, next) }
        }
    }

    /** Primeiro código com texto em [img]; cantos levados à prévia: (ponto + deslocamento) × [scale]. */
    private fun readOne(img: Bitmap, dx: Int, dy: Int, scale: Float): Pair<String, FloatArray>? {
        val r = runCatching { zxing.read(img, android.graphics.Rect(0, 0, img.width, img.height), 0) }
            .getOrNull()?.firstOrNull { !it.text.isNullOrBlank() } ?: return null
        val p = r.position
        val pts = listOf(p.topLeft, p.topRight, p.bottomRight, p.bottomLeft)
        return r.text!! to FloatArray(8) { i -> val q = pts[i / 2]; ((if (i % 2 == 0) q.x + dx else q.y + dy)) * scale }
    }

    override fun close() {
        if (closed) return
        closed = true; running = false; handler.removeCallbacks(tick)
        reader.close(); worker.shutdown()
    }

}
