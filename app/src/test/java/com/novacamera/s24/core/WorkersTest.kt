package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkersTest {
    @Test fun frioUsaNucleosGrandesComTeto() {
        assertEquals(4, Workers.count(ThermalPolicy.NONE, false, 8))
        assertEquals(2, Workers.count(ThermalPolicy.LIGHT, false, 4))
        assertEquals(1, Workers.count(ThermalPolicy.NONE, false, 2))
    }

    @Test fun calorOuEconomiaReduzem() {
        assertEquals(2, Workers.count(ThermalPolicy.MODERATE, false, 8))
        assertEquals(1, Workers.count(ThermalPolicy.SEVERE, false, 8))
        assertEquals(1, Workers.count(ThermalPolicy.NONE, true, 8))
    }
}
