package io.github.mashishiii.gnssmonitor.ui.status

import io.github.mashishiii.gnssmonitor.data.MarkerShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SkyGeometryTest {
    private val layout = SkyLayout(cx = 150f, cy = 150f, radius = 100f)
    private val eps = 0.01f

    @Test
    fun zenithIsCenter() {
        val p = skyPosition(123f, 90f, layout)
        assertEquals(150f, p.x, eps)
        assertEquals(150f, p.y, eps)
    }

    @Test
    fun horizonNorthIsTopEdge() {
        val p = skyPosition(0f, 0f, layout)
        assertEquals(150f, p.x, eps)
        assertEquals(50f, p.y, eps)
    }

    @Test
    fun horizonEastIsRightEdgeAndSouthIsBottomWestIsLeft() {
        val e = skyPosition(90f, 0f, layout)
        assertEquals(250f, e.x, eps); assertEquals(150f, e.y, eps)
        val s = skyPosition(180f, 0f, layout)
        assertEquals(150f, s.x, eps); assertEquals(250f, s.y, eps)
        val w = skyPosition(270f, 0f, layout)
        assertEquals(50f, w.x, eps); assertEquals(150f, w.y, eps)
    }

    @Test
    fun elevation30IsTwoThirdsOut() {
        val p = skyPosition(0f, 30f, layout)
        assertEquals(150f - 100f * 60f / 90f, p.y, eps)
        assertEquals(elevationRingRadius(30f, layout), 100f * 60f / 90f, eps)
    }

    @Test
    fun outOfRangeAndNaNAreClamped() {
        val low = skyPosition(0f, -20f, layout)
        assertEquals(50f, low.y, eps)
        val high = skyPosition(0f, 200f, layout)
        assertEquals(150f, high.y, eps)
        val nan = skyPosition(Float.NaN, Float.NaN, layout)
        assertEquals(150f, nan.x, eps)
        assertEquals(50f, nan.y, eps)
    }

    @Test
    fun layoutFitsInsideSmallerSideWithMargin() {
        val l = skyLayout(400f, 300f, 20f)
        assertEquals(200f, l.cx, eps); assertEquals(150f, l.cy, eps)
        assertEquals(130f, l.radius, eps)
        assertEquals(1f, skyLayout(10f, 10f, 50f).radius, eps)
    }

    @Test
    fun hitTestPicksNearestWithinRadius() {
        val markers = listOf("a" to Point(100f, 100f), "b" to Point(110f, 100f), "c" to Point(300f, 300f))
        assertEquals("b", hitTest(markers, 108f, 101f, 20f))
        assertEquals("a", hitTest(markers, 95f, 100f, 20f))
        assertNull(hitTest(markers, 200f, 200f, 20f))
        assertEquals("c", hitTest(markers, 305f, 295f, 20f))
    }

    @Test
    fun hitTestHandlesEmptyList() {
        assertNull(hitTest(emptyList(), 1f, 1f, 20f))
    }

    @Test
    fun barIndexMapsXToSlot() {
        assertEquals(0, barIndexAt(0f, 26f, 5))
        assertEquals(0, barIndexAt(25.9f, 26f, 5))
        assertEquals(1, barIndexAt(26f, 26f, 5))
        assertEquals(4, barIndexAt(129f, 26f, 5))
        assertNull(barIndexAt(130f, 26f, 5))
        assertNull(barIndexAt(-1f, 26f, 5))
        assertNull(barIndexAt(10f, 26f, 0))
        assertNull(barIndexAt(Float.NaN, 26f, 5))
    }

    @Test
    fun markerVerticesMatchShape() {
        assertNull(markerVertices(MarkerShape.CIRCLE, 10f))
        assertEquals(4, markerVertices(MarkerShape.DIAMOND, 10f)!!.size)
        assertEquals(4, markerVertices(MarkerShape.SQUARE, 10f)!!.size)
        assertEquals(3, markerVertices(MarkerShape.TRIANGLE, 10f)!!.size)
        assertEquals(6, markerVertices(MarkerShape.HEXAGON, 10f)!!.size)
        assertNotNull(markerVertices(MarkerShape.HEXAGON, 10f))
    }
}
