package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class NativePhotoPolicyTest {
    @Test fun maximumJpegNeedsCapabilityAndKeepsStandard() {
        val regular=listOf(4000 to 3000)
        val max=listOf(16320 to 12240)
        assertEquals(listOf("12 MP"),PhotoSizes.nativeOptions(regular,emptyList(),max,4f/3f,false).map { it.label })
        val enabled=PhotoSizes.nativeOptions(regular,emptyList(),max,4f/3f,true)
        assertEquals(listOf("200 MP","12 MP"),enabled.map { it.label })
        assertTrue(enabled.first().maximumPixelMode)
        assertEquals(regular.first().first,PhotoSizes.standard(enabled)?.width)
        assertTrue(PhotoSizes.nativeOptions(regular,emptyList(),max,16f/9f,true).isEmpty())
    }
    @Test fun physicalTeleWinsOverLogicalAndReturnsToAutoInLowLight() {
        val logical=LensOption("0",1f,0.6f,30f,true)
        val tele=LensOption("0",5f,1f,6f,false,"4",18f,115f)
        val lenses=listOf(logical,tele)
        assertEquals(LensChoice("0@4",2f),LensSelection.choose(lenses,"0","0",10f,true))
        assertEquals(LensChoice("0",10f),LensSelection.choose(lenses,"0@4","0",10f,false))
    }
    @Test fun automaticSceneDecisionAvoidsUnnecessaryFusionAndHeat() {
        assertEquals(AutoPhotoPolicy.Path.SINGLE,AutoPhotoPolicy.choose(100,10_000_000,0.001f,0.01f,false))
        assertEquals(AutoPhotoPolicy.Path.HDR,AutoPhotoPolicy.choose(100,10_000_000,0.03f,0.2f,false))
        assertEquals(AutoPhotoPolicy.Path.NIGHT,AutoPhotoPolicy.choose(1600,66_000_000,0.01f,0.2f,false))
        assertEquals(AutoPhotoPolicy.Path.SINGLE,AutoPhotoPolicy.choose(1600,66_000_000,0.03f,0.2f,true))
        assertEquals(AutoPhotoPolicy.Path.SINGLE,AutoPhotoPolicy.choose(null,null,0.001f,0.01f,false))
    }
    @Test fun roundedFiveTimesStopUsesActualS928bTeleInsteadOfThreeTimesCrop() {
        val logical=LensOption("0",1f,0.6f,10f,true)
        val tele3=LensOption("0",2.7f,1f,10f/2.7f,false,"6",7.9f,61f)
        val tele5=LensOption("0",5.1f,1f,10f/5.1f,false,"7",18.6f,113f)
        val lenses=listOf(logical,tele3,tele5)
        assertEquals(LensChoice("0@7",1f),LensSelection.choose(lenses,"0","0",5f,true))
        assertEquals("0@7",LensSelection.choose(lenses,"0","0",10f,true)?.cameraId)
        assertEquals("0@6",LensSelection.choose(lenses,"0","0",4f,true)?.cameraId)
        assertEquals("0",LensSelection.choose(lenses,"0","0",5f,false)?.cameraId)
        assertEquals("0",LensSelection.choose(lenses,"0@7","0",1f,true)?.cameraId)
    }
    @Test fun coldVulkanCostIsPaidOnceAndSteadyCostStillMustFit() {
        // Dados de inicialização observados no aparelho; os tempos sustentados são
        // cenários de teste, não medições nem promessa de desempenho.
        assertTrue(DenoisePolicy.remainingFits(2155,476,20,190))
        assertFalse(DenoisePolicy.remainingFits(2555,876,200,190))
        assertFalse(DenoisePolicy.remainingFits(8000,1000,1,1))
        assertFalse(DenoisePolicy.remainingFits(3000,6000,1,1))
        assertFalse(DenoisePolicy.remainingFits(2000,500,1,-1))
    }
}
