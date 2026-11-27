package io.github.mashishiii.gnssmonitor.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLogicTest {
    @Test
    fun userAgentIdentifiesAppAndProject() {
        val ua = MapConfig.userAgent("0.3.0")
        assertEquals("GNSSMonitor/0.3.0 (+https://github.com/mashishiii/gnss-monitor)", ua)
        // osmdroid の既定値（パッケージ名）や汎用 UA ではないこと
        assertTrue(!ua.contains("osmdroid", ignoreCase = true))
    }

    @Test
    fun elapsedFormatting() {
        assertEquals("00:00", formatElapsed(0))
        assertEquals("01:05", formatElapsed(65_000))
        assertEquals("59:59", formatElapsed(3_599_000))
        assertEquals("1:00:00", formatElapsed(3_600_000))
        assertEquals("00:00", formatElapsed(-5))
    }
}
