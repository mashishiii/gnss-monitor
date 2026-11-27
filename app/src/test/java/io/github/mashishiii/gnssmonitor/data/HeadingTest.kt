package io.github.mashishiii.gnssmonitor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.6: 進行方向の決め方、地図の回転、マーカーの位置、レトロ / スムーズ、設定の既定値。 */
class HeadingTest {
    private fun pos(speed: Float?, bearing: Float?) =
        PositionInfo(35.0, 139.0, 4f, speedMps = speed, bearingDegrees = bearing)

    // ---- 進行方向の決め方
    @Test
    fun headingFollowsBearingAtOneMpsOrFaster() {
        assertEquals(90f, nextHeading(null, pos(1.0f, 90f))!!, 1e-4f)
        assertEquals(270f, nextHeading(10f, pos(8.5f, 270f))!!, 1e-4f)
        assertEquals(0f, nextHeading(10f, pos(3f, 360f))!!, 1e-4f)
        assertEquals(350f, nextHeading(10f, pos(3f, -10f))!!, 1e-4f)
    }

    @Test
    fun headingIsHeldBelowOneMps() {
        assertEquals(123f, nextHeading(123f, pos(0.99f, 45f)))
        assertEquals(123f, nextHeading(123f, pos(0f, 45f)))
        // 速度が無い・方位が無い・測位が無い、でも直前の値を保持
        assertEquals(123f, nextHeading(123f, pos(null, 45f)))
        assertEquals(123f, nextHeading(123f, pos(5f, null)))
        assertEquals(123f, nextHeading(123f, null))
        assertEquals(123f, nextHeading(123f, pos(5f, Float.NaN)))
    }

    @Test
    fun noHeadingEverStaysNull() {
        assertNull(nextHeading(null, pos(0.5f, 90f)))
        assertNull(nextHeading(null, null))
    }

    // ---- 地図の回転
    @Test
    fun northUpNeverRotates() {
        assertEquals(0f, mapRotationFor(MapOrientation.NORTH_UP, 123f), 0f)
        assertEquals(0f, mapRotationFor(MapOrientation.NORTH_UP, null), 0f)
    }

    @Test
    fun headingUpRotatesByMinusHeadingModulo360() {
        assertEquals(0f, mapRotationFor(MapOrientation.HEADING_UP, 0f), 1e-4f)
        assertEquals(270f, mapRotationFor(MapOrientation.HEADING_UP, 90f), 1e-4f)
        assertEquals(180f, mapRotationFor(MapOrientation.HEADING_UP, 180f), 1e-4f)
        assertEquals(90f, mapRotationFor(MapOrientation.HEADING_UP, 270f), 1e-4f)
        assertEquals(1f, mapRotationFor(MapOrientation.HEADING_UP, 359f), 1e-4f)
    }

    @Test
    fun headingUpWithoutHeadingStaysNorthUp() {
        assertEquals(0f, mapRotationFor(MapOrientation.HEADING_UP, null), 0f)
        assertEquals(false, headingUpActive(MapOrientation.HEADING_UP, null))
        assertEquals(true, headingUpActive(MapOrientation.HEADING_UP, 0f))
        assertEquals(0f, skyHeadingFor(MapOrientation.HEADING_UP, null), 0f)
        assertEquals(90f, skyHeadingFor(MapOrientation.HEADING_UP, 90f), 1e-4f)
        assertEquals(0f, skyHeadingFor(MapOrientation.NORTH_UP, 90f), 0f)
    }

    @Test
    fun followOffKeepsRotation() {
        // 追従オフ（指で動かした後）は、新しい方位が来ても外した瞬間の角度のまま
        assertEquals(45f, mapRotationUpdate(false, MapOrientation.HEADING_UP, 200f, 45f), 0f)
        assertEquals(45f, mapRotationUpdate(false, MapOrientation.NORTH_UP, null, 45f), 0f)
        // 追従中は設定どおり
        assertEquals(160f, mapRotationUpdate(true, MapOrientation.HEADING_UP, 200f, 45f), 1e-4f)
        assertEquals(0f, mapRotationUpdate(true, MapOrientation.NORTH_UP, 200f, 45f), 0f)
    }

    // ---- 現在地マーカーの位置
    @Test
    fun markerSitsAtThirtyFivePercentFromBottomInHeadingUp() {
        // 中央（50%）から下へ 15% ずらす = 下から 35%
        assertEquals(150, markerCenterOffsetPx(MapOrientation.HEADING_UP, 90f, true, 1000))
        assertEquals(105, markerCenterOffsetPx(MapOrientation.HEADING_UP, 90f, true, 700))
        val h = 733
        val offset = markerCenterOffsetPx(MapOrientation.HEADING_UP, 10f, true, h)
        val fromBottom = (h / 2f - offset) / h
        assertEquals(0.35f, fromBottom, 0.01f)
    }

    @Test
    fun markerIsCenteredInNorthUpAndWhenFollowIsOffOrNoHeading() {
        assertEquals(0, markerCenterOffsetPx(MapOrientation.NORTH_UP, 90f, true, 1000))
        assertEquals(0, markerCenterOffsetPx(MapOrientation.HEADING_UP, 90f, false, 1000))
        assertEquals(0, markerCenterOffsetPx(MapOrientation.HEADING_UP, null, true, 1000))
        assertEquals(0, markerCenterOffsetPx(MapOrientation.HEADING_UP, 90f, true, 0))
    }

    // ---- レトロ / スムーズ
    private val a = DisplayPose(35.0, 139.0, 350f)
    private val b = DisplayPose(35.001, 139.002, 10f)

    @Test
    fun retroJumpsToLatestFix() {
        assertEquals(b, displayPose(MotionMode.RETRO, a, b, 0f))
        assertEquals(b, displayPose(MotionMode.RETRO, a, b, 0.5f))
        assertEquals(b, displayPose(MotionMode.RETRO, null, b, 0.5f))
    }

    @Test
    fun smoothInterpolatesPositionAndHeadingTheShortWay() {
        val mid = displayPose(MotionMode.SMOOTH, a, b, 0.5f)
        assertEquals(35.0005, mid.latitude, 1e-9)
        assertEquals(139.001, mid.longitude, 1e-9)
        // 350° → 10° は 20° の短い回り方（180° 戻らない）
        assertEquals(0f, mid.heading!!, 1e-3f)
        assertEquals(a, displayPose(MotionMode.SMOOTH, a, b, 0f))
        assertEquals(b, displayPose(MotionMode.SMOOTH, a, b, 1f))
        assertEquals(b, displayPose(MotionMode.SMOOTH, null, b, 0.3f))
    }

    @Test
    fun smoothWithoutHeadingKeepsNoHeading() {
        val noH = DisplayPose(35.0, 139.0, null)
        assertNull(displayPose(MotionMode.SMOOTH, noH, noH.copy(latitude = 35.001), 0.5f).heading)
        // 方位が初めて取れたときは補間せずその値
        assertEquals(10f, displayPose(MotionMode.SMOOTH, noH, b, 0.5f).heading!!, 1e-4f)
    }

    @Test
    fun lerpAngleWrapsAround() {
        assertEquals(355f, lerpAngle(350f, 0f, 0.5f), 1e-3f)
        assertEquals(5f, lerpAngle(10f, 0f, 0.5f), 1e-3f)
        assertTrue(sameAngle(lerpAngle(90f, 270f, 0f), 90f))
        assertNotEquals(0f, lerpAngle(0f, 90f, 0.5f))
    }

    // ---- 設定の既定値
    @Test
    fun settingsFallBackToApprovedDefaults() {
        assertEquals(MapOrientation.HEADING_UP, MapOrientation.fromName(null))
        assertEquals(MapOrientation.HEADING_UP, MapOrientation.fromName("???"))
        assertEquals(MapOrientation.NORTH_UP, MapOrientation.fromName("NORTH_UP"))
        assertEquals(MotionMode.RETRO, MotionMode.fromName(null))
        assertEquals(MotionMode.RETRO, MotionMode.fromName("???"))
        assertEquals(MotionMode.SMOOTH, MotionMode.fromName("SMOOTH"))
        assertEquals(MapOrientation.NORTH_UP, MapOrientation.HEADING_UP.toggled())
        assertEquals(MotionMode.SMOOTH, MotionMode.RETRO.next())
    }
}
