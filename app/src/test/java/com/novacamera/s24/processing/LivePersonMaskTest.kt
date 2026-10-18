package com.novacamera.s24.processing
import java.nio.FloatBuffer
import org.junit.Assert.*
import org.junit.Test
class LivePersonMaskTest {
    @Test fun extracaoLeveMantemExatamenteOsPixelsDoCaminhoCompleto() {
        val bg=FloatArray(256*256){(it%137)/100f-.1f}
        val expected=FloatArray(bg.size){1f-bg[it].coerceIn(0f,1f)}
        val source=FloatBuffer.wrap(bg).apply {position(20)}
        val actual=PersonSegmenter.personFromBackground(source,256,256)
        assertArrayEquals(expected,actual.data,0f)
        assertEquals(expected.count{it>.5f},actual.data.count{it>.5f})
        // Não altera o buffer fornecido pelo modelo, só sua posição de leitura.
        assertEquals(-.1f,bg[0],0f)
    }
}
