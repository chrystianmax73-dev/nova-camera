package com.novacamera.s24.core

import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** Chamar apenas na thread GL. Agrega sem alocação por quadro; texto no máximo 1Hz.
 * Tempos wall de submissão/bloqueio, nunca tempo/ocupação da GPU. */
class GlPerformance(private val clock:()->Long={System.nanoTime()}) {
    private val token=generation.incrementAndGet()
    private val since=clock()
    private var published=since
    private val count=LongArray(9);private val total=LongArray(9);private val max=LongArray(9)
    var frames=0L;private set
    var fboBuilds=0L
    var maskAllocations=0L
    var maskUploadBytes=0L
    var readbackBytes=0L
    fun record(stage:Int,ns:Long) { count[stage]++;total[stage]+=ns;max[stage]=maxOf(max[stage],ns) }
    fun frame(label:String="ativo") { frames++;if(clock()-published>=1_000_000_000L)publish(label) }
    fun publish(state:String) {
        if(token!=generation.get())return
        published=clock()
        val seconds=(published-since)/1_000_000_000.0
        report=buildString {
            append(String.format(Locale.US,"Compositor ES2 #%d: %s · %d quadros/%.1fs (%.1f/s); FBO builds=%d · máscara alloc=%d/uploads bytes=%d · readback bytes=%d",token,state,frames,seconds,if(seconds>0)frames/seconds else 0.0,fboBuilds,maskAllocations,maskUploadBytes,readbackBytes))
            for(i in names.indices)if(count[i]>0)append(String.format(Locale.US,"\n  %s: n=%d · média/máx %.3f/%.3fms",names[i],count[i],total[i]/count[i]/1e6,max[i]/1e6))
            append("\n  Wall de submissão/espera (etapas podem se conter); não mede GPU/energia/alocações internas do driver nem FPS do MP4.")
        }
    }
    companion object {
        const val INIT=0;const val FRAME=1;const val UPDATE=2;const val PREPARE=3
        const val COPY=4;const val BLUR=5;const val COMPOSE=6;const val SWAP=7;const val UPLOAD=8
        private val names=arrayOf("preparação EGL/programas","render total","updateTexImage","FBO/preparo","cópia/calibração","blur4+copy1","composição/saída sem swap","eglSwapBuffers","upload máscara")
        private val generation=AtomicLong()
        @Volatile var report="Compositor ES2: ainda não medido";private set
    }
}

/** Localizações pertencem ao programa/contexto; uma consulta por nome/programa, inclusive -1. */
class GlLocationCache(private val resolve:(Int,String)->Int) {
    private val values=HashMap<Int,MutableMap<String,Int>>()
    fun get(program:Int,name:String):Int=values.getOrPut(program){HashMap()}.getOrPut(name){resolve(program,name)}
}

/** Dimensão é a única condição de realocação; conteúdo continua atualizado em cada máscara. */
class MaskTextureStorage {
    private var width=0;private var height=0
    fun needsAllocation(w:Int,h:Int)=w!=width || h!=height
    fun allocated(w:Int,h:Int) { require(w>0 && h>0);width=w;height=h }
}
