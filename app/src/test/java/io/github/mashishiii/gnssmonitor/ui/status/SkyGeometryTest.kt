package io.github.mashishiii.gnssmonitor.ui.status

import io.github.mashishiii.gnssmonitor.data.MarkerShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ---- v0.6 ヘディングアップ
    @Test
    fun headingUpPutsHeadingDirectionAtTop() {
        // 進行方向が東（90°）: 東の水平線の衛星が上、北は左
        val east = skyPosition(90f, 0f, layout, 90f)
        assertEquals(150f, east.x, eps); assertEquals(50f, east.y, eps)
        val north = skyPosition(0f, 0f, layout, 90f)
        assertEquals(50f, north.x, eps); assertEquals(150f, north.y, eps)
        // 天頂は回っても中心
        val z = skyPosition(33f, 90f, layout, 77f)
        assertEquals(150f, z.x, eps); assertEquals(150f, z.y, eps)
    }

    @Test
    fun headingZeroMatchesNorthUp() {
        for (az in listOf(0f, 45f, 133f, 300f)) {
            val a = skyPosition(az, 40f, layout)
            val b = skyPosition(az, 40f, layout, 0f)
            assertEquals(a.x, b.x, eps); assertEquals(a.y, b.y, eps)
        }
    }

    @Test
    fun rimLabelNIsOnTopInNorthUpAndRotatesInHeadingUp() {
        val n0 = skyRimPosition(0f, layout, 9f)
        assertEquals(150f, n0.x, eps); assertEquals(150f - 109f, n0.y, eps)
        // 東向き（90°）に走ると、真北は左へ回る
        val n90 = skyRimPosition(0f, layout, 9f, 90f)
        assertEquals(150f - 109f, n90.x, eps); assertEquals(150f, n90.y, eps)
        // 南向き: N は下
        val n180 = skyRimPosition(0f, layout, 9f, 180f)
        assertEquals(150f, n180.x, eps); assertEquals(150f + 109f, n180.y, eps)
        // E は N の 90° 先
        val e90 = skyRimPosition(90f, layout, 9f, 90f)
        assertEquals(150f, e90.x, eps); assertEquals(150f - 109f, e90.y, eps)
    }

    @Test
    fun rimLabelsStayInsideCanvasForAllHeadings() {
        // 400×400 のキャンバス（余白 18dp 相当で 36px）に、ラベルの半径 14px が収まる
        val canvas = skyLayout(400f, 400f, 36f)
        for (h in 0..359 step 15) {
            for (deg in listOf(0f, 90f, 180f, 270f)) {
                val p = skyRimPosition(deg, canvas, 18f, h.toFloat())
                assertTrue("x ${p.x} h=$h", p.x - 14f >= 0f && p.x + 14f <= 400f)
                assertTrue("y ${p.y} h=$h", p.y - 14f >= 0f && p.y + 14f <= 400f)
            }
        }
    }

    @Test
    fun tapOnRimNToggleswhileSatelliteHitSelects() {
        val markers = listOf("a" to Point(100f, 100f))
        val n = skyRimPosition(0f, layout, 9f, 90f)
        assertEquals(SkyTap.ToggleOrientation, skyTapAction(markers, n, n.x + 3f, n.y, 20f, 14f, null))
        // 回った後の位置を使う: 元の（真上の）N 位置では切り替わらない
        val oldN = skyRimPosition(0f, layout, 9f)
        assertEquals(SkyTap.Select(null), skyTapAction(markers, n, oldN.x, oldN.y, 20f, 14f, null))
        assertEquals(SkyTap.Select("a"), skyTapAction(markers, n, 101f, 99f, 20f, 14f, null))
        assertEquals(SkyTap.Select(null), skyTapAction(markers, n, 101f, 99f, 20f, 14f, "a"))
    }

    @Test
    fun hitTestFollowsRotatedMarkers() {
        val h = 90f
        val pts = listOf("x" to skyPosition(90f, 20f, layout, h), "y" to skyPosition(270f, 20f, layout, h))
        // 進行方向が東なので、東の衛星 x は真上側、西の衛星 y は下側
        assertEquals("x", hitTest(pts, pts[0].second.x, pts[0].second.y - 2f, 10f))
        assertEquals("y", hitTest(pts, pts[1].second.x, pts[1].second.y + 2f, 10f))
        assertTrue(pts[0].second.y < 150f && pts[1].second.y > 150f)
    }
}
