package io.github.mashishiii.gnssmonitor.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalClassTest {
    @Test
    fun usedSatelliteIsUsedRegardlessOfStrength() {
        assertEquals(SignalClass.USED, classifySignal(true, 5f))
    }

    @Test
    fun strongUnusedSatelliteIsReceived() {
        assertEquals(SignalClass.RECEIVED, classifySignal(false, WEAK_CN0_DB_HZ))
        assertEquals(SignalClass.RECEIVED, classifySignal(false, 35f))
    }

    @Test
    fun weakUnusedSatelliteIsWeak() {
        assertEquals(SignalClass.WEAK, classifySignal(false, 0f))
        assertEquals(SignalClass.WEAK, classifySignal(false, WEAK_CN0_DB_HZ - 0.1f))
    }
}
