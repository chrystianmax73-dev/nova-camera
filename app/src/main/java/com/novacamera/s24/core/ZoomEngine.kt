package com.novacamera.s24.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Curva de zoom com duração limitada (em vez de aproximação exponencial "sem fim").
 *
 * - Interpolação em log: 1×→2× leva o mesmo tempo percebido que 5×→10×.
 * - Duração proporcional ao número de "dobras" de zoom, com teto: toque em 10× chega em ~0,3 s (foto).
 * - Interrompível: um novo alvo parte do valor atual, sem voltar ao início nem saltar.
 */
class ZoomCurve(private val photo: Boolean) {
    private var from = 1f
    private var to = 1f
    private var startMs = 0L
    private var durationMs = 0L

    val target get() = to

    fun durationFor(fromZoom: Float, toZoom: Float): Long {
        val doublings = abs(ln(toZoom / fromZoom)) / LN2
        val (base, perDoubling, max) = if (photo) Triple(90.0, 70.0, 320.0) else Triple(220.0, 210.0, 900.0)
        return (base + perDoubling * doublings).coerceAtMost(max).toLong()
    }

    /** Novo alvo animado a partir do valor exibido agora. */
    fun animateTo(current: Float, target: Float, nowMs: Long, duration: Long = durationFor(current, target)) {
        from = current
        to = target
        startMs = nowMs
        durationMs = duration
    }

    /** Alvo imediato (pinça/arraste): o dedo já é a animação. */
    fun jumpTo(target: Float) {
        from = target; to = target; durationMs = 0
    }

    fun valueAt(nowMs: Long): Float {
        if (durationMs <= 0 || from <= 0f || to <= 0f) return to
        val t = ((nowMs - startMs).toFloat() / durationMs).coerceIn(0f, 1f)
        // ease-out cúbica: resposta imediata, chegada suave.
        val eased = 1f - (1f - t) * (1f - t) * (1f - t)
        return exp(ln(from) + (ln(to) - ln(from)) * eased)
    }

    fun finished(nowMs: Long) = durationMs <= 0 || nowMs - startMs >= durationMs

    private companion object { val LN2 = ln(2.0).toFloat() }
}

/**
 * Controle de vazão das requisições de zoom à câmera: no máximo uma em voo.
 * Enquanto a anterior não é aplicada, só o valor mais recente é guardado (nunca empilha).
 */
class ZoomRequestGate {
    private var inFlight = false
    private var pending: Float? = null
    private var lastSent = 0f

    /** Retorna o valor a enviar agora, ou null se deve aguardar a requisição em voo. */
    fun offer(value: Float): Float? {
        if (inFlight) { pending = value; return null }
        if (lastSent > 0f && abs(value / lastSent - 1f) < 0.0015f) return null
        inFlight = true
        lastSent = value
        return value
    }

    /** Chamado quando a câmera concluiu (ou descartou) a requisição; devolve a próxima, se houver. */
    fun completed(): Float? {
        inFlight = false
        val next = pending ?: return null
        pending = null
        return offer(next)
    }

    fun reset() { inFlight = false; pending = null; lastSent = 0f }
}

/** Registro do comportamento real do zoom (latência, lente física, quadros perdidos). */
class ZoomTelemetry(private val capacity: Int = 120) {
    data class Event(val atMs: Long, val text: String)

    private val events = ArrayDeque<Event>()
    private var commandAtMs = 0L
    private var commandTarget = 0f
    private var lastFrameNs = 0L
    private var expectedFrameNs = 33_333_333L
    var droppedFrames = 0; private set
    var frames = 0; private set
    var lastPhysicalId: String? = null; private set
    var lastLatencyMs: Long? = null; private set

    fun command(target: Float, nowMs: Long) {
        commandAtMs = nowMs
        commandTarget = target
    }

    fun setExpectedFps(fps: Int) { if (fps > 0) expectedFrameNs = 1_000_000_000L / fps }

    /**
     * Um resultado de captura. [zoomRatio] = CONTROL_ZOOM_RATIO aplicado (API 30+), [physicalId] = lente física ativa.
     * Retorna true quando a lente física mudou (para avisar a interface).
     */
    fun frame(timestampNs: Long, zoomRatio: Float?, physicalId: String?, nowMs: Long): Boolean {
        frames++
        if (lastFrameNs != 0L && timestampNs > lastFrameNs) {
            val gap = timestampNs - lastFrameNs
            if (gap > expectedFrameNs * 17 / 10) droppedFrames += (gap / expectedFrameNs - 1).toInt().coerceAtLeast(1)
        }
        lastFrameNs = timestampNs
        if (zoomRatio != null && commandAtMs != 0L && abs(zoomRatio / commandTarget - 1f) < 0.01f) {
            lastLatencyMs = nowMs - commandAtMs
            log(nowMs, "zoom ${"%.2f".format(java.util.Locale.US, zoomRatio)}× aplicado em ${lastLatencyMs} ms")
            commandAtMs = 0L
        }
        var changed = false
        if (physicalId != null && physicalId != lastPhysicalId) {
            if (lastPhysicalId != null) log(nowMs, "lente física $lastPhysicalId → $physicalId (zoom ${zoomRatio ?: "?"})")
            changed = lastPhysicalId != null
            lastPhysicalId = physicalId
        }
        return changed
    }

    fun log(nowMs: Long, text: String) {
        events.addLast(Event(nowMs, text))
        while (events.size > capacity) events.removeFirst()
    }

    fun report(): String = buildString {
        appendLine("Quadros analisados: $frames · perdidos estimados: $droppedFrames")
        lastLatencyMs?.let { appendLine("Última latência comando→zoom aplicado: $it ms") }
        appendLine("Lente física ativa: ${lastPhysicalId ?: "não informada pela câmera"}")
        val t0 = events.firstOrNull()?.atMs ?: 0L
        events.forEach { appendLine("+${it.atMs - t0} ms  ${it.text}") }
    }
}
