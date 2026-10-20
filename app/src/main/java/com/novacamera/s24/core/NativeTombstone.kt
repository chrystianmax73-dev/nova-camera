package com.novacamera.s24.core

/**
 * Leitura do tombstone nativo (protobuf `tombstone.proto` do AOSP, entregue pelo ApplicationExitInfo
 * na API 31+) sem biblioteca de protobuf: sinal, mensagem do abort, a thread que caiu com as funções
 * do topo da pilha e as últimas linhas de log de erro. Antes só se viam textos soltos de todas as
 * threads, sem saber qual caiu nem por quê (crash de 19/10 20:01, MediaPipe).
 */
object NativeTombstone {
    class Report(val signal: String?, val abort: String?, val thread: String?, val frames: List<String>, val logs: List<String>) {
        val usable get() = signal != null || abort != null || frames.isNotEmpty()
        fun lines(): List<String> = buildList {
            signal?.let { add("sinal: $it") }
            abort?.let { add("abort: ${it.take(300)}") }
            thread?.let { add("thread que caiu: $it") }
            frames.forEach { add("  em $it") }
            if (logs.isNotEmpty()) { add("log antes da queda:"); logs.forEach { add("  $it") } }
        }
    }

    private class Reader(val b: ByteArray, var pos: Int, val end: Int) {
        fun more() = pos < end
        fun varint(): Long {
            var shift = 0; var r = 0L
            while (true) {
                if (pos >= end || shift > 63) throw IllegalStateException("varint")
                val c = b[pos++].toInt() and 0xFF
                r = r or ((c and 0x7F).toLong() shl shift)
                if (c and 0x80 == 0) return r
                shift += 7
            }
        }
        /** (campo, tipo); para tipo 2 devolve o intervalo em [sub]. */
        fun next(): Pair<Int, Int> { val k = varint(); return (k ushr 3).toInt() to (k and 7).toInt() }
        fun bytes(): IntRange { val n = varint().toInt(); val s = pos; if (n < 0 || s + n > end) throw IllegalStateException("len"); pos = s + n; return s until s + n }
        fun skip(type: Int) { when (type) { 0 -> varint(); 1 -> pos += 8; 2 -> bytes(); 5 -> pos += 4; else -> throw IllegalStateException("tipo $type") } }
        fun str(r: IntRange) = String(b, r.first, r.last - r.first + 1, Charsets.UTF_8)
        fun sub(r: IntRange) = Reader(b, r.first, r.last + 1)
    }

    fun parse(bytes: ByteArray, maxFrames: Int = 8, maxLogs: Int = 8): Report? = runCatching {
        val top = Reader(bytes, 0, bytes.size)
        var tid = -1L; var abort: String? = null; var signal: String? = null
        val threads = HashMap<Long, IntRange>()
        val logs = ArrayList<String>()
        while (top.more()) {
            val (f, t) = top.next()
            when {
                f == 6 && t == 0 -> tid = top.varint()
                f == 14 && t == 2 -> abort = top.str(top.bytes()).ifBlank { null }
                f == 10 && t == 2 -> { // Signal { number=1, name=2, code_name=4 }
                    val s = top.sub(top.bytes()); var name: String? = null; var code: String? = null
                    while (s.more()) { val (sf, st) = s.next(); when { sf == 2 && st == 2 -> name = s.str(s.bytes()); sf == 4 && st == 2 -> code = s.str(s.bytes()); else -> s.skip(st) } }
                    signal = listOfNotNull(name, code).joinToString(" ").ifBlank { null }
                }
                f == 16 && t == 2 -> { // map<uint32, Thread>: entrada { key=1, value=2 }
                    val e = top.sub(top.bytes()); var key = -1L; var value: IntRange? = null
                    while (e.more()) { val (ef, et) = e.next(); when { ef == 1 && et == 0 -> key = e.varint(); ef == 2 && et == 2 -> value = e.bytes(); else -> e.skip(et) } }
                    if (key >= 0 && value != null) threads[key] = value
                }
                f == 18 && t == 2 -> { // LogBuffer { name=1, logs=2: LogMessage { priority=4, tag=5, message=6 } }
                    val lb = top.sub(top.bytes())
                    while (lb.more()) {
                        val (lf, lt) = lb.next()
                        if (lf == 2 && lt == 2) {
                            val m = lb.sub(lb.bytes()); var prio = 0L; var tag = ""; var msg = ""
                            while (m.more()) { val (mf, mt) = m.next(); when { mf == 4 && mt == 0 -> prio = m.varint(); mf == 5 && mt == 2 -> tag = m.str(m.bytes()); mf == 6 && mt == 2 -> msg = m.str(m.bytes()); else -> m.skip(mt) } }
                            if (prio >= 6 || INTERESTING.containsMatchIn(tag) || INTERESTING.containsMatchIn(msg)) logs += "$tag: ${msg.trim().take(200)}"
                        } else lb.skip(lt)
                    }
                }
                else -> top.skip(t)
            }
        }
        var threadName: String? = null
        val frames = ArrayList<String>()
        threads[tid]?.let { range -> // Thread { id=1, name=2, current_backtrace=4: BacktraceFrame { function_name=4, file_name=6 } }
            val th = top.sub(range)
            while (th.more()) {
                val (f, t) = th.next()
                when {
                    f == 2 && t == 2 -> threadName = th.str(th.bytes())
                    f == 4 && t == 2 -> {
                        val fr = th.sub(th.bytes()); var fn = ""; var file = ""
                        while (fr.more()) { val (ff, ft) = fr.next(); when { ff == 4 && ft == 2 -> fn = fr.str(fr.bytes()); ff == 6 && ft == 2 -> file = fr.str(fr.bytes()); else -> fr.skip(ft) } }
                        if (frames.size < maxFrames) frames += listOf(fn.ifBlank { "?" }.take(120), file.substringAfterLast('/')).filter { it.isNotBlank() }.joinToString(" · ")
                    }
                    else -> th.skip(t)
                }
            }
        }
        Report(signal, abort, threadName?.let { "$it ($tid)" } ?: tid.takeIf { it >= 0 }?.let { "tid $it" }, frames, logs.takeLast(maxLogs))
    }.getOrNull()?.takeIf { it.usable }

    private val INTERESTING = Regex("mediapipe|tflite|ncnn|vulkan|Check failed|nova", RegexOption.IGNORE_CASE)
}
