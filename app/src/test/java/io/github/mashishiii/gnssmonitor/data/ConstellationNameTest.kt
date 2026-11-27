package io.github.mashishiii.gnssmonitor.data

import android.location.GnssStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ConstellationNameTest {
    @Test
    fun mapsKnownConstellations() {
        assertEquals("GPS", constellationName(GnssStatus.CONSTELLATION_GPS))
        assertEquals("みちびき", constellationName(GnssStatus.CONSTELLATION_QZSS))
        assertEquals("GLONASS", constellationName(GnssStatus.CONSTELLATION_GLONASS))
        assertEquals("Galileo", constellationName(GnssStatus.CONSTELLATION_GALILEO))
        assertEquals("BeiDou", constellationName(GnssStatus.CONSTELLATION_BEIDOU))
        assertEquals("SBAS", constellationName(GnssStatus.CONSTELLATION_SBAS))
        assertEquals("NavIC", constellationName(GnssStatus.CONSTELLATION_IRNSS))
    }

    @Test
    fun unknownConstellationFallsBack() {
        assertEquals("不明", constellationName(GnssStatus.CONSTELLATION_UNKNOWN))
        assertEquals("不明", constellationName(999))
    }
}
