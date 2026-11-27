package io.github.mashishiii.gnssmonitor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SatelliteSafetyTest {
    private fun sat(svid: Int, freq: Float? = null, cn0: Float = 30f, az: Float = 0f, el: Float = 0f) =
        SatelliteInfo(1, svid, cn0, az, el, true, freq)

    @Test
    fun keysAreUniqueEvenForIdenticalEntries() {
        val keys = uniqueSatelliteKeys(listOf(sat(5), sat(5), sat(5, 1176.45e6f), sat(5, 1176.45e6f), sat(6)))
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun sameSvidDifferentFrequencyGivesDifferentKeys() {
        val keys = uniqueSatelliteKeys(listOf(sat(5, 1575.42e6f), sat(5, 1176.45e6f)))
        assertTrue(keys[0] != keys[1])
    }

    @Test
    fun sanitizeReplacesNonFiniteAndClamps() {
        val s = sanitizeSatellite(sat(1, Float.NaN, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY))
        assertEquals(0f, s.cn0DbHz, 0f)
        assertEquals(0f, s.azimuthDegrees, 0f)
        assertEquals(0f, s.elevationDegrees, 0f)
        assertEquals(null, s.carrierFrequencyHz)
        assertEquals(99f, sanitizeSatellite(sat(1, cn0 = 500f)).cn0DbHz, 0f)
        assertEquals(0f, sanitizeSatellite(sat(1, cn0 = -3f)).cn0DbHz, 0f)
        assertEquals(-90f, sanitizeSatellite(sat(1, el = -200f)).elevationDegrees, 0f)
    }

    @Test
    fun bandLabelMapsCommonFrequencies() {
        assertEquals("L1", bandLabel(1575.42e6f))
        assertEquals("L1", bandLabel(1602.0e6f))
        assertEquals("L5", bandLabel(1176.45e6f))
        assertEquals("L2", bandLabel(1227.6e6f))
        assertEquals("", bandLabel(null))
        assertEquals("", bandLabel(Float.NaN))
        // v0.5: 周波数だけのときは帯域族の名前。E5b は L2 に紛れない
        assertEquals("E5b", bandLabel(1207.14e6f))
        assertEquals("G2", bandLabel(1246.0e6f))
        assertEquals("B3", bandLabel(1268.52e6f))
        assertEquals("E6", bandLabel(1278.75e6f))
        assertEquals("-", bandLabel(900e6f))
    }
}
