package io.github.mashishiii.gnssmonitor.data

import android.location.GnssStatus
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class SatelliteMergeTest {
    private fun sat(c: Int, svid: Int, cn0: Float, used: Boolean, freq: Float? = null) =
        SatelliteInfo(c, svid, cn0, 100f, 40f, used, freq)

    @Test
    fun mergesDualFrequencyIntoOneWithStrongerCn0() {
        val merged = mergeSatellites(
            listOf(
                sat(GnssStatus.CONSTELLATION_GPS, 25, 38f, true, 1575.42e6f),
                sat(GnssStatus.CONSTELLATION_GPS, 25, 31f, false, 1176.45e6f),
            ),
        )
        assertEquals(1, merged.size)
        val m = merged.single()
        assertEquals(38f, m.cn0DbHz, 0f)
        assertEquals(true, m.usedInFix)
        assertEquals("L1/L5", m.bands)
        assertEquals("1-25", m.key)
        assertEquals(2, m.signals.size)
    }

    @Test
    fun sameSvidInDifferentConstellationsStaysSeparate() {
        val merged = mergeSatellites(
            listOf(sat(GnssStatus.CONSTELLATION_GPS, 3, 30f, true), sat(GnssStatus.CONSTELLATION_GALILEO, 3, 30f, true)),
        )
        assertEquals(2, merged.size)
        assertEquals(2, merged.map { it.key }.toSet().size)
    }

    @Test
    fun ordersGpsQzssGlonassGalileoBeiDouThenSvid() {
        val merged = mergeSatellites(
            listOf(
                sat(GnssStatus.CONSTELLATION_BEIDOU, 6, 30f, true),
                sat(GnssStatus.CONSTELLATION_GALILEO, 3, 30f, true),
                sat(GnssStatus.CONSTELLATION_GPS, 30, 30f, true),
                sat(GnssStatus.CONSTELLATION_GPS, 5, 30f, true),
                sat(GnssStatus.CONSTELLATION_GLONASS, 4, 30f, true),
                sat(GnssStatus.CONSTELLATION_QZSS, 195, 30f, true),
            ),
        )
        assertEquals(
            listOf("GPS" to 5, "GPS" to 30, "みちびき" to 195, "GLONASS" to 4, "Galileo" to 3, "BeiDou" to 6),
            merged.map { it.constellationName to it.svid },
        )
    }

    @Test
    fun markerShapesPerConstellation() {
        assertEquals(MarkerShape.CIRCLE, markerShapeFor(GnssStatus.CONSTELLATION_GPS))
        assertEquals(MarkerShape.DIAMOND, markerShapeFor(GnssStatus.CONSTELLATION_QZSS))
        assertEquals(MarkerShape.SQUARE, markerShapeFor(GnssStatus.CONSTELLATION_GLONASS))
        assertEquals(MarkerShape.TRIANGLE, markerShapeFor(GnssStatus.CONSTELLATION_GALILEO))
        assertEquals(MarkerShape.HEXAGON, markerShapeFor(GnssStatus.CONSTELLATION_BEIDOU))
        assertEquals(MarkerShape.CIRCLE, markerShapeFor(GnssStatus.CONSTELLATION_SBAS))
        assertEquals(MarkerShape.CIRCLE, markerShapeFor(GnssStatus.CONSTELLATION_UNKNOWN))
    }

    @Test
    fun dmsFormatting() {
        assertEquals("35° 39′ 31″ N", formatDms(35.0 + 39.0 / 60 + 31.0 / 3600, 'N', 'S'))
        assertEquals("139° 44′ 41″ E", formatDms(139.0 + 44.0 / 60 + 41.0 / 3600, 'E', 'W'))
        assertEquals("33° 51′ 00″ S", formatDms(-33.85, 'N', 'S'))
        assertEquals("0° 00′ 00″ N", formatDms(0.0, 'N', 'S'))
    }

    @Test
    fun dateAndTimeFormattingInTokyo() {
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        // 2026-11-27 10:59:08 JST = 01:59:08 UTC
        val millis = 1795744748000L
        assertEquals("2026年11月27日", formatDateJa(millis, tokyo))
        assertEquals("10時59分08秒", formatTimeJa(millis, tokyo))
    }
}
