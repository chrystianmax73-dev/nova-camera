package com.novacamera.s24.core

/**
 * Pistas legíveis de um tombstone nativo (protobuf do ApplicationExitInfo, API 31+) sem biblioteca
 * de protobuf: os textos (sinal, thread, .so e funções da pilha) ficam gravados como UTF-8 cru.
 */
object NativeCrashHints {
    private val KEEP = Regex("SIG[A-Z]+|\\.so\\b|Java_|::|nova-|mediapipe|ncnn|vulkan|Abort|abort|signal|fault", RegexOption.IGNORE_CASE)

    fun extract(bytes: ByteArray, limit: Int = 30): List<String> {
        val out = LinkedHashSet<String>()
        val run = StringBuilder()
        fun flush() {
            if (run.length >= 5) {
                val s = run.toString().trim()
                if (KEEP.containsMatchIn(s)) out += s.take(160)
            }
            run.setLength(0)
        }
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) run.append(c.toChar()) else flush()
            if (out.size >= limit) break
        }
        flush()
        return out.take(limit)
    }
}
