package com.novacamera.s24.processing

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect

/** O compositor pode produzir buffer compartilhado ainda deitado. A marca percorre as
 * mesmas coordenadas sensor→saída da imagem, compensando a rotação que restará ao player.
 * Usa apenas geometria pública CameraX, sem escolher orientação pela razão largura/altura.
 */
object CinematicSignature {
    class Input(crop:Rect,val rotation:Int,val mirrored:Boolean,sensorToBuffer:Matrix) {
        val crop=Rect(crop)
        val sensorToBuffer=Matrix(sensorToBuffer)
    }
    fun draw(canvas:Canvas,input:Input,sensorToOutput:Matrix,style:SignatureStyle):Boolean {
        if(input.crop.isEmpty)return false
        val toSensor=Matrix()
        if(!input.sensorToBuffer.invert(toSensor))return false
        val (matrix,width,height)=Signature.uprightToBuffer(input.crop,input.rotation,input.mirrored)
        matrix.postConcat(toSensor)
        matrix.postConcat(sensorToOutput)
        val save=canvas.save()
        try { canvas.concat(matrix);Signature.drawOn(canvas,width,height,style) }
        finally { canvas.restoreToCount(save) }
        return true
    }
}
