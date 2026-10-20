package com.novacamera.s24.core

import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class StabilityTest {
    @Test fun altaResolucaoUsaARamDoAparelhoNaoOHeapJava() {
        val out49 = 6120L * 8160 * 4 // ~200 MB
        assertTrue(OutputMemory.fits(out49, 5_000_000_000, 300_000_000, false)) // S24: GBs livres
        assertFalse(OutputMemory.fits(out49, 600_000_000, 300_000_000, false))
        assertFalse(OutputMemory.fits(out49, 5_000_000_000, 300_000_000, true))
    }

    // Codificador mínimo de protobuf para montar um tombstone de teste.
    private class Pb { val o = ByteArrayOutputStream()
        fun v(x: Long) { var n = x; while (true) { val b = (n and 0x7F).toInt(); n = n ushr 7; if (n == 0L) { o.write(b); return }; o.write(b or 0x80) } }
        fun int(f: Int, x: Long) = apply { v((f shl 3).toLong()); v(x) }
        fun bytes(f: Int, b: ByteArray) = apply { v(((f shl 3) or 2).toLong()); v(b.size.toLong()); o.write(b) }
        fun str(f: Int, s: String) = bytes(f, s.toByteArray())
        fun msg(f: Int, m: Pb) = bytes(f, m.o.toByteArray())
        fun done() = o.toByteArray()
    }

    @Test fun tombstoneMostraSinalAbortThreadQueCaiuEPilha() {
        fun thread(id: Long, name: String, fn: String) = Pb().int(1, id).str(2, name)
            .msg(4, Pb().str(4, fn).str(6, "/data/app/x/lib/arm64/libmediapipe_tasks_vision_jni.so"))
        val ts = Pb()
            .str(2, "samsung/e3q") // build_fingerprint (ignorado)
            .int(5, 8000).int(6, 8564)
            .msg(10, Pb().int(1, 6).str(2, "SIGABRT").str(4, "SI_TKILL"))
            .str(14, "F0000 calculator_graph.cc:900] Check failed: graph_ ")
            .msg(16, Pb().int(1, 8100).msg(2, thread(8100, "nova-proc-1", "ncnn::Net::forward")))
            .msg(16, Pb().int(1, 8564).msg(2, thread(8564, "mediapipe/8564", "mediapipe::CalculatorGraph::Run")))
            .msg(18, Pb().str(1, "main").msg(2, Pb().int(4, 6).str(5, "tflite").str(6, "Failed to allocate tensors")))
            .done()
        val r = NativeTombstone.parse(ts)!!
        assertEquals("SIGABRT SI_TKILL", r.signal)
        assertTrue(r.abort!!.contains("Check failed"))
        assertEquals("mediapipe/8564 (8564)", r.thread)
        assertTrue(r.frames.single().startsWith("mediapipe::CalculatorGraph::Run"))
        assertEquals(listOf("tflite: Failed to allocate tensors"), r.logs)
    }

    @Test fun lixoNaoQuebraODiagnostico() {
        assertNull(NativeTombstone.parse(byteArrayOf(0x7F, 0x01, 0x02)))
        assertNull(NativeTombstone.parse(ByteArray(0)))
    }
}

class NativeGateTest {
    @Test fun umMotorPorVezEAoVivoPulaSemTravar() {
        val inside = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val t = Thread { NativeGate.run("foto") { inside.countDown(); release.await() } }
        t.start(); inside.await()
        org.junit.Assert.assertNull(NativeGate.tryRun("prévia") { 1 }) // ocupado: pula o quadro
        release.countDown(); t.join()
        org.junit.Assert.assertEquals(1, NativeGate.tryRun("prévia") { 1 })
        org.junit.Assert.assertEquals(2, NativeGate.run("a") { NativeGate.run("b") { 2 } }) // reentrante
    }
}
