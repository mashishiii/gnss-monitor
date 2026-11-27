package io.github.mashishiii.gnssmonitor.data

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class NavFormatsTest {
    private val tokyo: TimeZone = TimeZone.getTimeZone("Asia/Tokyo")

    @Test
    fun signalLevelThresholds() {
        assertEquals(SignalLevel.STRONG, signalLevelFrom(35f))
        assertEquals(SignalLevel.STRONG, signalLevelFrom(48f))
        assertEquals(SignalLevel.MEDIUM, signalLevelFrom(34.9f))
        assertEquals(SignalLevel.MEDIUM, signalLevelFrom(25f))
        assertEquals(SignalLevel.WEAK, signalLevelFrom(24.9f))
        assertEquals(SignalLevel.WEAK, signalLevelFrom(0f))
        assertEquals(SignalLevel.WEAK, signalLevelFrom(Float.NaN))
    }

    @Test
    fun signalLevelBarsMatchNavULegend() {
        // 橙 = 3 本 / 黄緑 = 2 本 / 灰 = 1 本
        assertEquals(listOf(3, 2, 1), SignalLevel.entries.map { it.bars })
    }

    @Test
    fun dmsNavFormat() {
        assertEquals("N 35°37'19\"", formatDmsNav(35.0 + 37 / 60.0 + 19 / 3600.0, 'N', 'S'))
        assertEquals("E 139°44'43\"", formatDmsNav(139.0 + 44 / 60.0 + 43 / 3600.0, 'E', 'W'))
        assertEquals("S 1°0'0\"", formatDmsNav(-1.0, 'N', 'S'))
        assertEquals("W 0°30'0\"", formatDmsNav(-0.5, 'E', 'W'))
    }

    @Test
    fun dateAndTimeNavFormat() {
        // 2011/6/10 14:03:03 JST
        val millis = java.util.Calendar.getInstance(tokyo).apply { clear(); set(2011, 5, 10, 14, 3, 3) }.timeInMillis
        assertEquals("2011/ 6/10", formatDateNav(millis, tokyo))
        assertEquals("2:03:03 PM", formatTimeNav(millis, tokyo))
        val nov = java.util.Calendar.getInstance(tokyo).apply { clear(); set(2026, 10, 27, 0, 5, 9) }.timeInMillis
        assertEquals("2026/11/27", formatDateNav(nov, tokyo))
        assertEquals("12:05:09 AM", formatTimeNav(nov, tokyo))
    }

    @Test
    fun speedKmh() {
        assertEquals("0 km/h", formatSpeedKmh(0f))
        assertEquals("30 km/h", formatSpeedKmh(8.4f))
        assertEquals("100 km/h", formatSpeedKmh(27.8f))
        assertEquals("---", formatSpeedKmh(null))
        assertEquals("---", formatSpeedKmh(Float.NaN))
        assertEquals("---", formatSpeedKmh(-1f))
    }
}
