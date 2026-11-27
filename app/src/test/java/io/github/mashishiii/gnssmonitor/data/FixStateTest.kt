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
        fun sat(svid: Int, used: Boolean, cn0: Float) = SatelliteInfo(1, svid, cn0, 0f, 0f, used)
        val state = GnssUiState(satellites = listOf(sat(1, true, 30f), sat(2, true, 25f), sat(3, true, 22f), sat(4, true, 40f), sat(5, false, 10f)))
        assertEquals(5, state.capturedCount)
        assertEquals(4, state.usedCount)
        assertEquals(FixState.FIX_3D, state.fixState)
    }

    @Test
    fun multiFrequencyRowsAreCountedOncePerSatellite() {
        fun sat(svid: Int, freq: Float, used: Boolean) = SatelliteInfo(1, svid, 30f, 0f, 0f, used, freq)
        val state = GnssUiState(
            satellites = listOf(
                sat(5, 1575.42e6f, true), sat(5, 1176.45e6f, true),
                sat(7, 1575.42e6f, false), sat(7, 1176.45e6f, false),
            ),
        )
        assertEquals(2, state.capturedCount)
        assertEquals(1, state.usedCount)
    }
}
