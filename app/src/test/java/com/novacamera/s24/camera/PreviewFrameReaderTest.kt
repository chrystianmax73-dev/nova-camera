package com.novacamera.s24.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.camera.view.PreviewView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewFrameReaderTest {
    private fun view(): Pair<PreviewView,SurfaceView> {
        val c=RuntimeEnvironment.getApplication<android.app.Application>()
        val preview=PreviewView(c); preview.layout(0,0,1080,1920)
        val surface=SurfaceView(c); preview.addView(surface); surface.layout(0,0,3840,2160)
        return preview to surface
    }
    @Test fun copiaJaReduzidaNaoBloqueiaERecusaSegundaSolicitacao() {
        val (preview,_)=view()
        var callback: ((Int)->Unit)?=null
        var target:Bitmap?=null; var delivered:Bitmap?=null
        val reader=PreviewFrameReader(preview) { _,b,done -> target=b; callback=done }
        assertTrue(reader.read { b,_ -> delivered=b })
        assertNull(delivered)
        assertTrue(target!!.width<=320 && target!!.height<=320)
        assertFalse(reader.read { _,_ -> fail("segunda cópia em voo") })
        target!!.eraseColor(Color.GREEN); callback!!(PixelCopy.SUCCESS)
        assertNotNull(delivered); assertTrue(delivered!!.width<=320 && delivered!!.height<=320)
        delivered!!.recycle(); reader.close()
    }
    @Test fun fechamentoAguardaCallbackAntesDeLiberarDestinoEmVoo() {
        val (preview,_)=view(); var callback:((Int)->Unit)?=null; var target:Bitmap?=null
        var delivered=0
        val reader=PreviewFrameReader(preview) { _,b,done -> target=b; callback=done }
        reader.read { b,_ -> assertNull(b); delivered++ }
        reader.close(); assertFalse(target!!.isRecycled)
        callback!!(PixelCopy.SUCCESS)
        assertTrue(target!!.isRecycled); assertEquals(1,delivered)
        assertFalse(reader.read { _,_ -> fail() })
    }
    @Test fun erroDeCopiaNaoEntregaImagemPretaComoSeFosseValida() {
        val (preview,_)=view()
        val reader=PreviewFrameReader(preview) { _,_,done -> done(PixelCopy.ERROR_SOURCE_NO_DATA) }
        var delivered=0
        assertTrue(reader.read { b,_ -> assertNull(b); delivered++ })
        assertEquals(1,delivered); reader.close()
    }
    @Test fun recorteEscalaEEspelhoMantemCoordenadasDoVisor() {
        val child=Matrix().apply { setValues(floatArrayOf(-2f,0f,300f,0f,2f,-30f,0f,0f,1f)) }
        val m=PreviewFrameReader.map(100,100,100,100,200,200,100,100,child,0f)
        val points=floatArrayOf(50f,50f); m.mapPoints(points)
        assertEquals(100f,points[0],.01f); assertEquals(35f,points[1],.01f)
    }
}
