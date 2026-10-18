package com.novacamera.s24.core.imaging

/** Mesmos cálculos do motor Kotlin em laços nativos, sem fast-math/FMA ou redução de amostras.
 * O caminho Kotlin permanece fallback e referência de regressão. Sem threads/modelos novos. */
object PhotoKernels {
    @Volatile var available=runCatching { System.loadLibrary("novadenoise") }.isSuccess;private set
    internal fun loadHost(path:String) { System.load(path);available=true }
    internal fun useNativeForTest(value:Boolean) { available=value }
    @JvmStatic external fun smooth(px:IntArray,w:Int,h:Int):FloatArray
    @JvmStatic external fun estimate(ref:FloatArray,mov:FloatArray,geometry:IntArray,gx:Int,gy:Int,radius:Int):FloatArray
    @JvmStatic external fun detail(px:IntArray,w:Int,h:Int,amount:Float,sigma:Float,top:Int,bottom:Int):Int
    @JvmStatic external fun search(ref:FloatArray,mov:FloatArray,w:Int,h:Int,radius:Int,px:Int,py:Int,
        x0:Int,y0:Int,x1:Int,y1:Int,step:Int):IntArray
    @JvmStatic external fun merge(pixels:Array<IntArray>,geometry:IntArray,shifts:FloatArray,
        outW:Int,outH:Int,ox:Int,oy:Int,factor:Float,cropX:Float,cropY:Float,noise:Float,kernel:FloatArray):IntArray
}
