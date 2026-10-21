package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class RecordingPowerPolicyTest {
    @Test fun escureceSoGravandoSemToqueEMaisCedoQuente() {
        assertFalse("sem gravação não escurece", RecordingPowerPolicy.shouldDim(false, false, 60_000, 0))
        assertFalse(RecordingPowerPolicy.shouldDim(true, false, 19_000, 0))
        assertTrue(RecordingPowerPolicy.shouldDim(true, false, 20_000, 0))
        assertFalse("já escurecida", RecordingPowerPolicy.shouldDim(true, true, 60_000, 0))
        assertTrue("aquecido: 5 s", RecordingPowerPolicy.shouldDim(true, false, 6_000, ThermalPolicy.MODERATE))
    }
}
