package com.novacamera.s24.core.imaging

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class NightReferenceNoiseTest {
    @Test fun ruidoCorrelacionadoNaoPodeContarComoNitidezParaTrocarReferencia() {
        val w=160;val h=120;val rng=Random(263)
        val clean=Plane(w,h,FloatArray(w*h){i->.24f+if(i%w in 45..115 && i/w in 30..90).12f else 0f})
        val noise=FloatArray(w*h/4){(rng.nextGaussian()*.04).toFloat()}
        val noisy=Plane(w,h,FloatArray(w*h){i->clean.data[i]+noise[(i/w/2)*(w/2)+(i%w)/2]})
        val scores=listOf(FrameQuality.score(clean),FrameQuality.score(noisy))
        println("NIGHT_REFERENCE clean=${scores[0].detail} noisy=${scores[1].detail} best=${FrameQuality.best(scores)}")
        assertEquals("ruído não é detalhe capturado",0,FrameQuality.best(scores))
    }
}
