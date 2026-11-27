package io.github.mashishiii.gnssmonitor.ui.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SparklineTest {
    @Test
    fun maxHasAFloorAndRoundsUp() {
        assertEquals(5f, sparklineMax(emptyList()), 0f)
        assertEquals(5f, sparklineMax(listOf(null, 1.2f)), 0f)
        assertEquals(13f, sparklineMax(listOf(3f, 12.4f, null)), 0f)
    }

    @Test
    fun gapsSplitTheLineIntoSegments() {
        val segs = sparklineSegments(listOf(2f, 3f, null, null, 4f), width = 118f, height = 10f, maxValue = 10f, slots = 60)
        assertEquals(2, segs.size)
        assertEquals(2, segs[0].size)
        assertEquals(1, segs[1].size)
    }

    @Test
    fun newestValueIsRightAlignedAndYIsInverted() {
        val segs = sparklineSegments(listOf(10f, 0f), width = 59f, height = 20f, maxValue = 10f, slots = 60)
        val pts = segs.single()
        assertEquals(59f, pts.last().x, 0.001f)
        assertEquals(20f, pts.last().y, 0.001f) // 0 は下端
        assertEquals(0f, pts.first().y, 0.001f) // 最大は上端
        assertTrue(pts.first().x < pts.last().x)
    }

    @Test
    fun emptyOrDegenerateInputGivesNoSegments() {
        assertTrue(sparklineSegments(emptyList(), 100f, 10f, 5f).isEmpty())
        assertTrue(sparklineSegments(listOf(1f), 0f, 10f, 5f).isEmpty())
        assertTrue(sparklineSegments(listOf(null, null), 100f, 10f, 5f).isEmpty())
    }
}
