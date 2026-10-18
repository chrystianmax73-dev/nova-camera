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
        val sigma=Merge.estimateNoise(ref,w,h,correlated=true)
        val planes=frames.map { px->Plane(w,h,FloatArray(px.size){Px.luma(px[it])}) }
        val noise=Consistency.noiseOf(planes[0],correlated=true)
        val weights=planes.drop(1).map { Consistency.reliability(planes[0],it,Array(12*16){Shift(0,0)},12,16,ExposureLut.IDENTITY,noise).data }
        val accepted=weights.count{data->Consistency.unsafeFraction(Plane(w,h,data),planes[0])<.22f}
        val out=Merge.mergeTile(ref,frames.drop(1),sigma,frameWeights=weights).pixels
        println("NIGHT_CORRELATED sigma=$sigma alignSigma=$noise accepted=$accepted/4 RMS=${error(ref)} -> ${error(out)}; sintético, não S24")
        assertTrue("cena estática ruidosa não é movimento; aceitos=$accepted",accepted>=3)
        assertTrue("fusão deve reduzir ruído temporal sem borrar textura ${error(ref)} -> ${error(out)}",error(out)<error(ref)*.7)
    }
    @Test fun ruídoIndependenteNaoGanhaToleranciaArbitraria() {
        val rng=Random(9);val px=IntArray(w*h){val v=(64+rng.nextGaussian()*10).toInt();Px.argb(v,v,v)}
        assertEquals(Merge.estimateNoise(px,w,h),Merge.estimateNoise(px,w,h,correlated=true),.01f)
    }
    @Test fun rampasEBordasPeriodicasNaoSaoConfundidasComCorrelacao() {
        for(mode in 0..1) {
            val base=.002f
            val got=CorrelatedNoise.sigma(w,h,base) { x,y ->
                if(mode==0)(x+y)*.002f else if(((x/2+y/2)%2)==0).2f else .7f }
            assertEquals(base,got,0f)
        }
    }
    @Test fun reducaoTemporalPreservaCorAtmosferaEContrasteDoObjeto() {
        val frames=List(5){frame(50L+it).map{c->val v=Px.r(c);Px.argb(v+15,v+7,v)}.toIntArray()}
        val out=Merge.mergeTile(frames[0],frames.drop(1),Merge.estimateNoise(frames[0],w,h,correlated=true)).pixels
        val r=out.map{Px.r(it)}.average();val g=out.map{Px.g(it)}.average();val b=out.map{Px.b(it)}.average()
        assertEquals(15.0,r-b,.02);assertEquals(7.0,g-b,.02)
        val expected=IntArray(w*h){clean(it)}.average()
        assertEquals(expected,b,1.2)
        val inside=out.indices.filter{it%w in 40..85 && it/w in 30..65}.map{Px.b(out[it])}.average()
        val outside=out.indices.filter{it%w in 5..20 && it/w in 5..15}.map{Px.b(out[it])}.average()
        assertTrue("contraste capturado preservado",inside-outside>23)
    }
    @Test fun objetoMovelNaoDeixaFantasmaNemHalo() {
        val frames=List(5){frame(50L+it)}
        for(k in 1..4)for(y in 30..50)for(x in 40..70)frames[k][y*w+x]=Px.argb(220,220,220)
        val out=Merge.mergeTile(frames[0],frames.drop(1),Merge.estimateNoise(frames[0],w,h,correlated=true)).pixels
        for(y in 30..50)for(x in 40..70) assertEquals(frames[0][y*w+x],out[y*w+x])
    }

}
