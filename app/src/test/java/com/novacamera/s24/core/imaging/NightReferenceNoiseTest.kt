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
    @Test fun bordasRealmenteMaisDefinidasContinuamPodendoSubstituirReferenciaMaisLimpa() {
        val w=160;val h=120;val rng=Random(264)
        val sharp=Plane(w,h,FloatArray(w*h){i->if(i%w/10%2==0).25f else .65f})
        val blurred=Planes.boxBlur(sharp,3)
        val noisy=Plane(w,h,FloatArray(w*h){i->sharp.data[i]+(rng.nextGaussian()*.025).toFloat()})
        assertEquals(1,FrameQuality.best(listOf(FrameQuality.score(blurred),FrameQuality.score(noisy))))
    }
    @Test fun custoReferenciaEmPlanoReduzidoNaoRepresentaAutonomia() {
        val w=768;val h=576;val rng=Random(265)
        val p=Plane(w,h,FloatArray(w*h){.2f+rng.nextFloat()*.05f})
        FrameQuality.score(p)
        val start=System.nanoTime();repeat(3){FrameQuality.score(p)}
        println("REFERENCE_HOST score3x768x576=${(System.nanoTime()-start)/1e6}ms; sem modelo/GPU, não mede S24/energia")
    }
}
