package com.novacamera.s24.core

import kotlin.math.abs
import kotlin.math.hypot

object CinematicPolicy {
    fun profile(width:Int,height:Int,fps:Int,stabilized:Boolean=false) = !stabilized && minOf(width,height)==1080 && maxOf(width,height)==1920 && fps==30
    /** Calor severo bloqueia inferência; aguardar máscara nessa condição não resolve. */
    fun unavailableReason(thermal:Int,finishing:Boolean,ready:Boolean):String? = when {
        thermal>=3 -> "Cinematográfico pausado: aparelho quente. Aguarde esfriar ou escolha vídeo comum nesta sessão"
        finishing -> "Profundidade pausada durante o acabamento de outro vídeo"
        !ready -> "A máscara ainda não está pronta ou perdeu validade"
        else -> null
    }
    data class RenderWork(val copy:Boolean,val blur:Boolean)
    /** Calibração sem máscara precisa de uma cópia, não dos quatro passes de desfoque.
     * Sem análise disponível (ex.: pausa térmica), não há calibração para executar.
     */
    fun renderWork(enabled:Boolean,expiring:Boolean,ready:Boolean,blend:Float,calibrationPending:Boolean):RenderWork {
        val blur=enabled && (!expiring || ready || blend>.001f)
        return RenderWork(blur || (enabled && calibrationPending),blur)
    }
    fun fresh(now:Long,at:Long) = at>0 && now>=at && now-at<750
    /** A inferência tem orçamento próprio; movimento invalida cache sem contornar esse limite. */
    fun depthInterval(thermal:Int,still:Boolean) = if(thermal>=2) 1500L else if(still) 2500L else 900L
}

class CinematicSceneGuard {
    private var previous:FloatArray?=null
    private var tap:Pair<Float,Float>?=null
    fun moved(luma:FloatArray,target:Pair<Float,Float>?):Boolean {
        val old=previous
        val movement=old!=null && (old.size!=luma.size || old.indices.sumOf { abs(old[it]-luma[it]).toDouble() }/luma.size>0.045)
        val previousTap=tap
        val selection=(previousTap==null)!=(target==null) || (previousTap!=null && target!=null && hypot(previousTap.first-target.first,previousTap.second-target.second)>.04f)
        previous=luma.copyOf(); if(selection || tap==null) tap=target
        return movement || selection
    }
}
