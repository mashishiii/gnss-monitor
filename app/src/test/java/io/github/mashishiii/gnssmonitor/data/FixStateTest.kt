package io.github.mashishiii.gnssmonitor.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FixStateTest {
    @Test
    fun fewerThanThreeUsedIsNoFix() {
        assertEquals(FixState.NO_FIX, fixStateFrom(gpsEnabled = true, usedCount = 0))
        assertEquals(FixState.NO_FIX, fixStateFrom(gpsEnabled = true, usedCount = 2))
    }

    @Test
    fun threeUsedIs2D() {
        assertEquals(FixState.FIX_2D, fixStateFrom(gpsEnabled = true, usedCount = 3))
    }

    @Test
    fun fourOrMoreUsedIs3D() {
        assertEquals(FixState.FIX_3D, fixStateFrom(gpsEnabled = true, usedCount = 4))
        assertEquals(FixState.FIX_3D, fixStateFrom(gpsEnabled = true, usedCount = 12))
    }

    @Test
    fun gpsDisabledIsAlwaysNoFix() {
        assertEquals(FixState.NO_FIX, fixStateFrom(gpsEnabled = false, usedCount = 8))
    }

    @Test
    fun uiStateCountsSatellites() {
        fun sat(used: Boolean, cn0: Float) = SatelliteInfo(1, 1, cn0, 0f, 0f, used)
        val state = GnssUiState(satellites = listOf(sat(true, 30f), sat(true, 25f), sat(true, 22f), sat(true, 40f), sat(false, 10f)))
        assertEquals(5, state.capturedCount)
        assertEquals(4, state.usedCount)
        assertEquals(FixState.FIX_3D, state.fixState)
    }
}
