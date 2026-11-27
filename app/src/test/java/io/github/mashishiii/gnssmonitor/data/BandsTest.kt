package io.github.mashishiii.gnssmonitor.data

import android.location.GnssStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BandsTest {
    private val gps = GnssStatus.CONSTELLATION_GPS
    private val gal = GnssStatus.CONSTELLATION_GALILEO
    private val bds = GnssStatus.CONSTELLATION_BEIDOU
    private val glo = GnssStatus.CONSTELLATION_GLONASS
    private val qzss = GnssStatus.CONSTELLATION_QZSS

    @Test
    fun familiesByCarrierFrequency() {
        assertEquals(BandFamily.L5, bandFamily(1176.45e6f))
        assertEquals(BandFamily.L1, bandFamily(1575.42e6f))
        assertEquals(BandFamily.L1, bandFamily(1561.098e6f))
        assertEquals(BandFamily.L1, bandFamily(1602.0e6f))
        assertEquals(BandFamily.E5B, bandFamily(1207.14e6f))
        assertEquals(BandFamily.L2, bandFamily(1227.6e6f))
        assertEquals(BandFamily.G2, bandFamily(1246.0e6f))
        assertEquals(BandFamily.B3, bandFamily(1268.52e6f))
        assertEquals(BandFamily.E6, bandFamily(1278.75e6f))
        assertEquals(BandFamily.UNKNOWN, bandFamily(null))
        assertEquals(BandFamily.UNKNOWN, bandFamily(Float.NaN))
        assertEquals(BandFamily.UNKNOWN, bandFamily(0f))
        assertEquals(BandFamily.UNKNOWN, bandFamily(900e6f))
    }

    @Test
    fun l5FrequencyIsNamedPerConstellation() {
        assertEquals("L5", bandLabelFor(gps, 1176.45e6f))
        assertEquals("E5a", bandLabelFor(gal, 1176.45e6f))
        assertEquals("B2a", bandLabelFor(bds, 1176.45e6f))
        assertEquals("L5", bandLabelFor(qzss, 1176.45e6f))
    }

    @Test
    fun l1FrequencyIsNamedPerConstellation() {
        assertEquals("L1", bandLabelFor(gps, 1575.42e6f))
        assertEquals("E1", bandLabelFor(gal, 1575.42e6f))
        assertEquals("B1C", bandLabelFor(bds, 1575.42e6f))
        assertEquals("B1I", bandLabelFor(bds, 1561.098e6f))
        assertEquals("G1", bandLabelFor(glo, 1602.0e6f))
    }

    @Test
    fun e5bIsNotMixedIntoL2() {
        assertEquals("E5b", bandLabelFor(gal, 1207.14e6f))
        assertEquals("B2", bandLabelFor(bds, 1207.14e6f))
        assertEquals("L2", bandLabelFor(gps, 1227.6e6f))
        assertEquals("L2", bandLabelFor(qzss, 1227.6e6f))
        assertEquals("G2", bandLabelFor(glo, 1246.0e6f))
        assertEquals("B3", bandLabelFor(bds, 1268.52e6f))
        assertEquals("E6", bandLabelFor(gal, 1278.75e6f))
        assertEquals("L6", bandLabelFor(qzss, 1278.75e6f))
    }

    @Test
    fun missingFrequencyGivesEmptyLabel() {
        assertEquals("", bandLabelFor(gps, null))
        assertEquals("", bandLabelFor(gal, Float.NaN))
        assertEquals("-", bandLabelFor(gps, 900e6f))
    }
}
