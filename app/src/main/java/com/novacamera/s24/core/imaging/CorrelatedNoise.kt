package com.novacamera.s24.core.imaging

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Estima ruído espacialmente correlacionado sem filtrar a imagem.
 * Só aceita aumento se a diferença cresce de 1 para 2px e já estabiliza de 2 para 4px.
 * Rampas/contornos, cujo erro continua crescendo com a distância, não justificam aumento. */
object CorrelatedNoise {
    inline fun sigma(w:Int,h:Int,base:Float,step:Int=3,value:(Int,Int)->Float):Float {
        if(w<12 || h<12)return base
        val stride=max(step,max(1,kotlin.math.ceil(sqrt(w.toDouble()*h/16_384.0)).toInt()))
        val n=((w+stride-1)/stride)*((h+stride-1)/stride)*2
        val samples=FloatArray(n)
        val estimates=FloatArray(3)
        for((k,lag) in intArrayOf(1,2,4).withIndex()) {
            var count=0
            var y=1
            while(y<h-lag) {var x=1
                while(x<w-lag) {
                    val v=value(x,y)
                    samples[count++]=abs(v-value(x+lag,y));samples[count++]=abs(v-value(x,y+lag))
                    x+=stride
                };y+=stride
            }
            if(count<64)return base
            java.util.Arrays.sort(samples,0,count)
            estimates[k]=samples[count/4]/(.3186f*1.4142f)
        }
        val near=estimates[0];val medium=estimates[1];val far=estimates[2]
        if(medium <= max(base,near)*1.6f || far<medium*.8f || far>medium*1.35f)return base
        return max(base,kotlin.math.min(medium,far))
    }
}
