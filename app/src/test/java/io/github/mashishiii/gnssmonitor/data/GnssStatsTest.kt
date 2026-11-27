package io.github.mashishiii.gnssmonitor.data

import android.location.GnssStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssStatsTest {
    private val gps = GnssStatus.CONSTELLATION_GPS
    private val gal = GnssStatus.CONSTELLATION_GALILEO
    private val bds = GnssStatus.CONSTELLATION_BEIDOU
    private val L1 = 1575.42e6f
    private val L5 = 1176.45e6f
    private val L2 = 1227.6e6f

    private fun s(c: Int, svid: Int, hz: Float?, used: Boolean, cn0: Float) = SatelliteInfo(c, svid, cn0, 0f, 45f, used, hz)

    @Test
    fun multiFrequencySatelliteCountsOnceButInBothBands() {
        val stats = computeGnssStats(
            listOf(
                s(gps, 3, L1, true, 40f), s(gps, 3, L5, true, 30f),
                s(gps, 4, L1, true, 35f),
                s(gal, 1, L1, false, 25f), s(gal, 1, L5, false, 22f),
            ),
        )
        val gpsStat = stats.constellations.first { it.constellationType == gps }
        assertEquals(2, gpsStat.captured)
        assertEquals(2, gpsStat.used)
        assertEquals(3, stats.band(BandFamily.L1)!!.captured)
        assertEquals(2, stats.band(BandFamily.L1)!!.used)
        assertEquals(2, stats.band(BandFamily.L5)!!.captured)
        assertEquals(1, stats.band(BandFamily.L5)!!.used)
    }

    @Test
    fun averageCn0UsesUsedSatellitesAndTheirStrongestSignalOnly() {
        val stats = computeGnssStats(
            listOf(
                s(gps, 3, L1, true, 40f), s(gps, 3, L5, true, 30f), // 1 機 = 40（2 回平均しない）
                s(gps, 4, L1, true, 30f),
                s(gps, 5, L1, false, 50f), // 未使用は含めない
            ),
        )
        assertEquals(35f, stats.avgCn0DbHz!!, 0.001f)
        assertEquals(35f, stats.constellations.single().avgCn0DbHz!!, 0.001f)
    }

    @Test
    fun averageCn0IsNullWithNoUsedSatellites() {
        assertNull(computeGnssStats(emptyList()).avgCn0DbHz)
        val unused = computeGnssStats(listOf(s(gps, 3, L1, false, 40f)))
        assertNull(unused.avgCn0DbHz)
        assertNull(unused.constellations.single().avgCn0DbHz)
    }

    @Test
    fun perConstellationAverageIsSeparate() {
        val stats = computeGnssStats(listOf(s(gps, 3, L1, true, 40f), s(gal, 1, L1, true, 30f), s(gal, 2, L1, true, 20f)))
        assertEquals(40f, stats.constellations.first { it.constellationType == gps }.avgCn0DbHz!!, 0.001f)
        assertEquals(25f, stats.constellations.first { it.constellationType == gal }.avgCn0DbHz!!, 0.001f)
        assertEquals(30f, stats.avgCn0DbHz!!, 0.001f)
    }

    private fun dualSat(c: Int, svid: Int, hzA: Float, hzB: Float) =
        listOf(s(c, svid, hzA, true, 30f), s(c, svid, hzB, true, 30f))

    @Test
    fun dualFreqNeedsTwoSatellitesWithL1AndL5BothUsed() {
        assertFalse(computeGnssStats(emptyList()).dualFreq)
        val one = computeGnssStats(dualSat(gps, 3, L1, L5))
        assertEquals(1, one.dualFreqSatellites)
        assertFalse("1 機では消灯", one.dualFreq)
        val two = computeGnssStats(dualSat(gps, 3, L1, L5) + dualSat(gps, 4, L1, L5))
        assertEquals(2, two.dualFreqSatellites)
        assertTrue("N=2 で点灯", two.dualFreq)
        assertEquals(setOf("$gps-3", "$gps-4"), two.dualFreqKeys)
    }

    @Test
    fun galileoE1E5aAndBeidouB1cB2aCountAsDual() {
        val stats = computeGnssStats(dualSat(gal, 1, L1, L5) + dualSat(bds, 6, 1575.42e6f, 1176.45e6f))
        assertEquals(2, stats.dualFreqSatellites)
        assertTrue(stats.dualFreq)
    }

    @Test
    fun l1PlusL2OnlyIsNotDual() {
        val stats = computeGnssStats(dualSat(gps, 3, L1, L2) + dualSat(gps, 4, L1, L2) + dualSat(gps, 5, L1, L2))
        assertEquals(0, stats.dualFreqSatellites)
        assertFalse(stats.dualFreq)
        assertEquals(mapOf(BandFamily.L2 to 3), stats.usedOtherBands)
    }

    @Test
    fun unusedL5SignalIsNotDual() {
        val sats = listOf(s(gps, 3, L1, true, 40f), s(gps, 3, L5, false, 20f), s(gps, 4, L1, true, 40f), s(gps, 4, L5, false, 20f))
        assertEquals(0, computeGnssStats(sats).dualFreqSatellites)
    }

    @Test
    fun noFrequencyInfoMeansNoBandsAndNoDual() {
        val stats = computeGnssStats(listOf(s(gps, 3, null, true, 40f), s(gps, 4, null, true, 30f)))
        assertFalse(stats.hasFrequencyInfo)
        assertTrue(stats.bands.isEmpty())
        assertFalse(stats.dualFreq)
        assertEquals("---", stats.dualFreqLabel())
        // 衛星数と平均 C/N0 は従来どおり
        assertEquals(2, stats.constellations.single().used)
        assertEquals(35f, stats.avgCn0DbHz!!, 0.001f)
    }

    @Test
    fun dualFreqLabelTexts() {
        assertEquals("消灯", computeGnssStats(listOf(s(gps, 3, L1, true, 30f))).dualFreqLabel())
        assertEquals("点灯 2機", computeGnssStats(dualSat(gps, 3, L1, L5) + dualSat(gps, 4, L1, L5)).dualFreqLabel())
    }

    @Test
    fun accuracy95IsFactorTimes68() {
        assertEquals(ACCURACY_95_FACTOR, 1.62f, 0f)
        assertEquals(6.48f, horizontalAccuracy95(4f)!!, 0.001f)
        assertEquals(0f, horizontalAccuracy95(0f)!!, 0f)
        assertNull(horizontalAccuracy95(null))
        assertNull(horizontalAccuracy95(Float.NaN))
        assertNull(horizontalAccuracy95(-1f))
    }

    @Test
    fun accuracyHistoryKeepsLastSixtyAndDropsBadValues() {
        var h = emptyList<Float?>()
        for (i in 1..70) h = appendAccuracy(h, i.toFloat())
        assertEquals(ACCURACY_HISTORY_SIZE, h.size)
        assertEquals(11f, h.first()!!, 0f)
        assertEquals(70f, h.last()!!, 0f)
        h = appendAccuracy(h, Float.NaN)
        assertNull(h.last())
        h = appendAccuracy(h, null)
        assertNull(h.last())
        assertEquals(ACCURACY_HISTORY_SIZE, h.size)
    }

    @Test
    fun trackColorModeAndAccuracyQuality() {
        assertEquals(TrackColorMode.SATELLITES, TrackColorMode.fromName(null))
        assertEquals(TrackColorMode.SATELLITES, TrackColorMode.fromName("bogus"))
        assertEquals(TrackColorMode.ACCURACY, TrackColorMode.fromName("ACCURACY"))
        assertEquals(TrackColorMode.ACCURACY, TrackColorMode.SATELLITES.next())
        assertEquals(TrackColorMode.SATELLITES, TrackColorMode.ACCURACY.next())
        assertEquals(TrackQuality.GOOD, accuracyQuality(4.9f))
        assertEquals(TrackQuality.MID, accuracyQuality(5f))
        assertEquals(TrackQuality.MID, accuracyQuality(15f))
        assertEquals(TrackQuality.LOW, accuracyQuality(15.1f))
        assertEquals(TrackQuality.LOW, accuracyQuality(null))
    }
}
