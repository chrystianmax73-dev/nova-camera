package com.novacamera.s24.core.imaging

import org.junit.Assert.*
import org.junit.Test
import java.util.Random
import kotlin.math.sqrt

/** Ruído correlacionado como hipótese reproduzível de JPEG/ISP; não representa medição S24. */
class NightNoiseRegressionTest {
    private val w=128;private val h=96
    private fun frame(seed:Long):IntArray {
        val rng=Random(seed);val blocks=IntArray(w*h/4){(rng.nextGaussian()*12).toInt()}
        return IntArray(w*h){i->val x=i%w;val y=i/w
            val base=if(x in 32..95 && y in 24..71) 76 else 48
            val v=(base+blocks[(y/2)*(w/2)+x/2]).coerceIn(0,255);Px.argb(v,v,v)}
    }
    private fun clean(i:Int)=if(i%w in 32..95 && i/w in 24..71)76 else 48
    private fun error(px:IntArray):Double=sqrt(px.indices.sumOf{val d=Px.r(px[it])-clean(it);(d*d).toDouble()}/px.size)
    @Test fun correlacaoEspacialNaoDeveImpedirFusaoDeCenaEstatica() {
        val frames=List(5){frame(50L+it)};val ref=frames[0]
        val sigma=Merge.estimateNoise(ref,w,h)
        val planes=frames.map { px->Plane(w,h,FloatArray(px.size){Px.luma(px[it])}) }
        val noise=Consistency.noiseOf(planes[0])
        val weights=planes.drop(1).map { Consistency.reliability(planes[0],it,Array(12*16){Shift(0,0)},12,16,ExposureLut.IDENTITY,noise).data }
        val accepted=weights.count{data->Consistency.unsafeFraction(Plane(w,h,data),planes[0])<.22f}
        val out=Merge.mergeTile(ref,frames.drop(1),sigma,frameWeights=weights).pixels
        println("NIGHT_CORRELATED sigma=$sigma alignSigma=$noise accepted=$accepted/4 RMS=${error(ref)} -> ${error(out)}; sintético, não S24")
        assertTrue("cena estática ruidosa não é movimento; aceitos=$accepted",accepted>=3)
        assertTrue("fusão deve reduzir ruído temporal sem borrar textura ${error(ref)} -> ${error(out)}",error(out)<error(ref)*.7)
    }
}
