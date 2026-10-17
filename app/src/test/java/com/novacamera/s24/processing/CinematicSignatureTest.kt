package com.novacamera.s24.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CinematicSignatureTest {
    @Test fun marcaFicaDePeComCropRotacaoEspelhoESaidaCompartilhadaOuJaOrientada() {
        val style=SignatureStyle(device="S24 Ultra")
        val crop=Rect(20,10,660,370)
        val sensorToInput=Matrix().apply { setScale(.5f,.5f);postTranslate(-40f,-20f) }
        for(rotation in listOf(0,90,180,270)) for(mirror in listOf(false,true)) {
            val (uprightToInput,w,h)=Signature.uprightToBuffer(crop,rotation,mirror)
            val inputToUpright=Matrix();assertTrue(uprightToInput.invert(inputToUpright))
            val expected=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            Signature.draw(expected,style)
            val input=CinematicSignature.Input(crop,rotation,mirror,sensorToInput)
            // Superfície já orientada: input→upright após a transformação do sensor.
            val sensorToUpright=Matrix(sensorToInput).apply { postConcat(inputToUpright) }
            val actual=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            assertTrue(CinematicSignature.draw(Canvas(actual),input,sensorToUpright,style))
            assertPixelsNear(expected,actual,"saída orientada $rotation/$mirror")
            // Superfície compartilhada: câmera ainda deitada; consumidor gira/espelha depois.
            val shared=Bitmap.createBitmap(680,380,Bitmap.Config.ARGB_8888)
            assertTrue(CinematicSignature.draw(Canvas(shared),input,sensorToInput,style))
            val displayed=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            Canvas(displayed).drawBitmap(shared,inputToUpright,null)
            assertPixelsNear(expected,displayed,"saída compartilhada $rotation/$mirror")
            expected.recycle();actual.recycle();shared.recycle();displayed.recycle()
        }
    }
    @Test fun geometriaSingularNaoDesenhaMarcaEmLugarInventado() {
        val bmp=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888)
        val singular=Matrix().apply { setScale(0f,0f) }
        assertFalse(CinematicSignature.draw(Canvas(bmp),CinematicSignature.Input(Rect(0,0,100,100),90,false,singular),Matrix(),SignatureStyle()))
        bmp.recycle()
    }
    private fun assertPixelsNear(a:Bitmap,b:Bitmap,case:String) {
        var marked=0;var different=0
        for(y in 0 until a.height)for(x in 0 until a.width) {
            val p=a.getPixel(x,y);val q=b.getPixel(x,y)
            if((p ushr 24)>20 || (q ushr 24)>20) {
                marked++
                if(kotlin.math.abs((p ushr 24)-(q ushr 24))>4)different++
            }
        }
        assertTrue("marca deve ter pixels",marked>100)
        assertTrue("$case: divergência da marca $different de $marked",different<marked*.03)
    }
}
