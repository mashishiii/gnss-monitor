package io.github.mashishiii.gnssmonitor.ui.map

import androidx.compose.ui.graphics.Color
import io.github.mashishiii.gnssmonitor.data.ACCURACY_95_FACTOR
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

    @Test
    fun circleDrawModeGuardsHugeRadii() {
        assertEquals(CircleDrawMode.HIDDEN, circleDrawMode(3f, 1000f, 8f))
        assertEquals(CircleDrawMode.HIDDEN, circleDrawMode(Float.NaN, 1000f, 8f))
        assertEquals(CircleDrawMode.FILLED, circleDrawMode(300f, 1000f, 8f))
        assertEquals(CircleDrawMode.FILLED, circleDrawMode(1000f, 1000f, 8f))
        assertEquals(CircleDrawMode.OUTLINE, circleDrawMode(3000f, 1000f, 8f))
        assertEquals(CircleDrawMode.HIDDEN, circleDrawMode(50_000f, 1000f, 8f))
    }

    @Test
    fun circle95IsLargerThanCircle68() {
        // 68% の円が収まる大きさでも、95% 目安（×1.62）の円は輪郭だけになることがある
        val r68 = 700f
        assertEquals(CircleDrawMode.FILLED, circleDrawMode(r68, 1000f, 8f))
        assertEquals(CircleDrawMode.OUTLINE, circleDrawMode(r68 * ACCURACY_95_FACTOR, 1000f, 8f))
    }

    private fun lum(c: Color): Double {
        fun lin(v: Float): Double = v.toDouble().let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }

    private fun contrast(a: Color, b: Color) = (maxOf(lum(a), lum(b)) + 0.05) / (minOf(lum(a), lum(b)) + 0.05)

    @Test
    fun accuracyCircleLinesAreVisibleOnDayAndNightTiles() {
        // 95% の破線と 68% の縁は、昼の地図（明るい）にも夜の地図（反転して暗い）にも見えること
        val line = Color(AccuracyCircleColors.STROKE)
        assertTrue("昼", contrast(line, Color(AccuracyCircleColors.DAY_TILE)) >= 3.0)
        assertTrue("夜", contrast(line, Color(AccuracyCircleColors.NIGHT_TILE)) >= 3.0)
        // 精度モードの軌跡の 3 色も同様
        for (c in listOf(TrackColors.GOOD, TrackColors.MID, TrackColors.LOW)) {
            assertTrue(contrast(Color(c), Color(AccuracyCircleColors.NIGHT_TILE)) >= 3.0)
        }
    }
}
