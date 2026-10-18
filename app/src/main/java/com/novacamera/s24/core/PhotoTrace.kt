package com.novacamera.s24.core

import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** Tempos wall por disparo; não são tempo de GPU nem comparação de qualidade. Sem imagens. */
class PhotoTrace(val label:String, private val clock:()->Long = { System.nanoTime() },originNs:Long?=null) {
    val id=sequence.incrementAndGet()
    private val start=originNs ?: clock()
    private val events=ArrayList<String>()
    private var previous=start
    private var previousSensor:Long?=null
    private var sensorFrames=0
    @Synchronized fun mark(name:String) {
        val now=clock()
        if(events.size<80)events += "$name@${ms(now-start)}ms(+${ms(now-previous)})"
        previous=now
    }
    @Synchronized fun sensor(timestampNs:Long) {
        val old=previousSensor;sensorFrames++;previousSensor=timestampNs
        if(events.size<80)events += "timestamp buffer$sensorFrames: intervalo="+
            (if(old!=null && timestampNs>old) ms(timestampNs-old)+"ms" else "não comparável/primeiro")
    }
    fun <T> measure(name:String,block:()->T):T {
        val at=clock()
        try { return block() } finally { duration(name,clock()-at) }
    }
    @Synchronized private fun duration(name:String,ns:Long) {
        if(events.size<80)events += "$name=${ms(ns)}ms"
    }
    @Synchronized fun publish(state:String) {
        val entry="#$id $label · $state · total=${ms(clock()-start)}ms\n  "+events.joinToString(" · ")
        synchronized(records) { records[id]=entry;while(records.size>8)records.remove(records.keys.first()) }
    }
    private fun ms(ns:Long)=String.format(Locale.US,"%.1f",ns/1_000_000.0)
    companion object {
        private val sequence=AtomicLong()
        private val records=linkedMapOf<Long,String>()
        fun report():String=synchronized(records) {
            "Etapas fotográficas (wall; @desde o disparo, +desde evento anterior; sem GPU/qualidade):\n"+
                if(records.isEmpty()) "  nenhuma captura instrumentada" else records.values.joinToString("\n")
        }
    }
}
