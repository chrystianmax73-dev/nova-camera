package com.novacamera.s24.core

import kotlin.math.abs

/** Primeiro acabamento: leve abertura de sombras, constante no clipe e sem nitidez/denoise inventados. */
object VideoFinishPolicy {
    fun lift(mean:Double):Float = ((0.28-mean)*0.02).coerceIn(0.0,0.003).toFloat()
    fun matrix(lift:Float):FloatArray {
        val b=lift.coerceIn(0f,.003f)
        return floatArrayOf(1-b,0f,0f,0f,0f,1-b,0f,0f,0f,0f,1-b,0f,b,b,b,1f)
    }
    fun timeline(a:LongArray,b:LongArray):Boolean {
        if(a.isEmpty() || a.size!=b.size)return false
        val x=a.sortedArray();val y=b.sortedArray()
        return x.indices.all { abs((x[it]-x[0])-(y[it]-y[0]))<=1000 }
    }
    fun sync(videoA:Long,audioA:Long,videoB:Long,audioB:Long) = abs((audioA-videoA)-(audioB-videoB))<=2000
    fun displayed(w:Int,h:Int,rotation:Int) = if(rotation%180==0) w to h else h to w
}
