package com.novacamera.s24.camera

import android.graphics.Matrix
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class TrackingMeteringTest {
    @Test fun espelhoFrontalMapeiaRegiaoParaOSensorCorreto() {
        val m=Matrix().apply { setValues(floatArrayOf(-.1f,0f,400f,0f,.1f,0f,0f,0f,1f)) }
        val r=TrackingMetering.region(m,Rect(0,0,4000,3000),400,300,.25f,.5f,.2f)!!.rect
        assertEquals(3000,r.centerX()); assertEquals(1500,r.centerY())
        assertEquals(600,r.width()); assertEquals(600,r.height())
    }
    @Test fun rotacaoERecorteMapeiamRegiaoSemTrocarOsEixos() {
        val m=Matrix().apply { setValues(floatArrayOf(0f,-.1f,300f,.1f,0f,-50f,0f,0f,1f)) }
        val r=TrackingMetering.region(m,Rect(0,0,4000,3000),300,300,.25f,.5f,.2f)!!.rect
        assertEquals(2000,r.centerX()); assertEquals(2250,r.centerY())
    }
    @Test fun recortaNoSensorERecusaTransformacaoInvalida() {
        val sensor=Rect(0,0,400,300)
        val r=TrackingMetering.region(Matrix(),sensor,400,300,0f,0f,.2f)!!.rect
        assertTrue(sensor.contains(r)); assertEquals(0,r.left)
        val singular=Matrix().apply { setScale(0f,0f) }
        assertNull(TrackingMetering.region(singular,sensor,400,300,.5f,.5f,.2f))
        assertNull(TrackingMetering.region(Matrix(),sensor,0,300,.5f,.5f,.2f))
    }
}
