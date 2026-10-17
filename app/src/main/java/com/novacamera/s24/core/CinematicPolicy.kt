package com.novacamera.s24.core

import kotlin.math.abs
import kotlin.math.hypot

object CinematicPolicy {
    fun profile(width:Int,height:Int,fps:Int,stabilized:Boolean=false) = !stabilized && minOf(width,height)==1080 && maxOf(width,height)==1920 && fps==30
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
