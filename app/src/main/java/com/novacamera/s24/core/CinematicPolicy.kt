package com.novacamera.s24.core

import kotlin.math.abs
import kotlin.math.hypot

object CinematicPolicy {
    /** Vídeo normal jamais abre profundidade, mesmo com a preferência do Retrato ligada. */
    fun canStart(requested:Boolean,bound:Boolean,maskUsable:Boolean) = (!requested && !bound) || (bound && maskUsable)
    fun enabled(preference:Boolean,portraitOrigin:Boolean,commonChosen:Boolean) = preference && portraitOrigin && !commonChosen
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
        val blur=enabled && (ready || (expiring && blend>.001f))
        return RenderWork(blur || (enabled && calibrationPending),blur)
    }
    fun fresh(now:Long,at:Long) = at>0 && now>=at && now-at<750
    /** Pessoa que vira assunto no vídeo: 3% do quadro deixava figura de pôster/TV no escuro desfocar o objeto real. */
    const val MIN_PERSON_SUBJECT = .12f
    fun personSubject(coverage:Float,tapOnPerson:Boolean) = tapOnPerson || coverage>=MIN_PERSON_SUBJECT
    /** A inferência tem orçamento próprio; movimento invalida cache sem contornar esse limite. */
    fun depthInterval(thermal:Int,still:Boolean) = if(thermal>=2) 1500L else if(still) 2500L else 900L
}

class CinematicSceneGuard {
    private var previous:FloatArray?=null
    private var tap:Pair<Float,Float>?=null
    /** Último [moved] foi só troca de assunto (cena parada): a profundidade continua valendo. */
    var selectionOnly=false;private set
    /** Último [moved] foi troca de cena (não tremor da mão): a profundidade anterior não serve mais. */
    var sceneCut=false;private set
    fun moved(luma:FloatArray,target:Pair<Float,Float>?):Boolean {
        val old=previous
        val diff=if(old==null) 0.0 else if(old.size!=luma.size) 1.0 else old.indices.sumOf { abs(old[it]-luma[it]).toDouble() }/luma.size
        val movement=diff>0.045
        sceneCut=diff>SCENE_CUT
        val previousTap=tap
        val selection=(previousTap==null)!=(target==null) || (previousTap!=null && target!=null && hypot(previousTap.first-target.first,previousTap.second-target.second)>.04f)
        previous=luma.copyOf(); if(selection || tap==null) tap=target
        selectionOnly=selection && !movement
        return movement || selection
    }
    companion object {
        /** Diferença média (0..1) acima da qual é outra cena. Tremor na tele (3×) passava de 0,045 a cada análise. */
        const val SCENE_CUT=0.15
    }
}
