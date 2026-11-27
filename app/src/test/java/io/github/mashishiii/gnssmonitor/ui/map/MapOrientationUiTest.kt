package io.github.mashishiii.gnssmonitor.ui.map

import android.content.Context
import android.location.GnssStatus
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.mashishiii.gnssmonitor.data.GnssUiState
import io.github.mashishiii.gnssmonitor.data.MapOrientation
import io.github.mashishiii.gnssmonitor.data.MotionMode
import io.github.mashishiii.gnssmonitor.data.PositionInfo
import io.github.mashishiii.gnssmonitor.data.SatelliteInfo
import io.github.mashishiii.gnssmonitor.data.TrackRecorder
import io.github.mashishiii.gnssmonitor.ui.AppContent
import io.github.mashishiii.gnssmonitor.ui.AppScreen
import io.github.mashishiii.gnssmonitor.ui.theme.DisplayMode
import io.github.mashishiii.gnssmonitor.ui.status.TAG_NO_HEADING
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.6: 方位バッジ・動きボタン・設定の保存、地図とスカイプロットで向きが共通であること。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h700dp-xxhdpi")
class MapOrientationUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val state = GnssUiState(
        position = PositionInfo(35.68, 139.76, 4f, 42.0, 1795744748000L, speedMps = 8f, bearingDegrees = 90f),
        satellites = (1..8).map { SatelliteInfo(GnssStatus.CONSTELLATION_GPS, it, 35f, it * 40f, 30f + it * 5, it < 6) },
        heading = 90f,
    )

    private fun prefs() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(MAP_PREFS, Context.MODE_PRIVATE)

    @Test
    fun settingsDefaultToHeadingUpAndRetro() {
        val settings = MapSettings.load(ApplicationProvider.getApplicationContext())
        assertEquals(MapOrientation.HEADING_UP, settings.orientation)
        assertEquals(MotionMode.RETRO, settings.motion)
    }

    @Test
    fun settingsPersistAcrossLoads() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val s = MapSettings.load(ctx)
        s.toggleOrientation()
        s.nextMotion()
        val again = MapSettings.load(ctx)
        assertEquals(MapOrientation.NORTH_UP, again.orientation)
        assertEquals(MotionMode.SMOOTH, again.motion)
        assertEquals("NORTH_UP", prefs().getString(KEY_ORIENTATION, null))
        assertEquals("SMOOTH", prefs().getString(KEY_MOTION, null))
    }

    @Test
    fun compassBadgeTogglesAndPersists() {
        rule.setContent { AppContent(state, TrackRecorder.State(), initialScreen = AppScreen.MAP, initialMode = DisplayMode.NIGHT) }
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_COMPASS_BADGE).assertIsDisplayed()
        rule.onNodeWithText("進行方向が上").assertIsDisplayed()
        rule.onNodeWithTag(TAG_COMPASS_BADGE).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("北が上").assertIsDisplayed()
        assertEquals("NORTH_UP", prefs().getString(KEY_ORIENTATION, null))
        rule.onNodeWithTag(TAG_COMPASS_BADGE).performClick()
        rule.waitForIdle()
        assertEquals("HEADING_UP", prefs().getString(KEY_ORIENTATION, null))
    }

    @Test
    fun motionButtonCyclesRetroAndSmooth() {
        rule.setContent { AppContent(state, TrackRecorder.State(), initialScreen = AppScreen.MAP, initialMode = DisplayMode.NIGHT) }
        rule.waitForIdle()
        rule.onNodeWithText("動き: レトロ").assertIsDisplayed()
        rule.onNodeWithTag(TAG_MOTION_BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("動き: スムーズ").assertIsDisplayed()
        assertEquals("SMOOTH", prefs().getString(KEY_MOTION, null))
        rule.onNodeWithTag(TAG_MOTION_BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("動き: レトロ").assertIsDisplayed()
    }

    @Test
    fun orientationIsSharedBetweenMapAndStatusScreens() {
        // 地図で北が上にすると、測位状況へ戻っても北が上（方位なしの案内も出ない）。1 つの設定だけを持つ
        val noHeading = state.copy(heading = null)
        rule.setContent { AppContent(noHeading, TrackRecorder.State(), initialScreen = AppScreen.MAP, initialMode = DisplayMode.NIGHT) }
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_COMPASS_BADGE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("nav-BACK").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_NO_HEADING).assertDoesNotExist()
        assertEquals("NORTH_UP", prefs().getString(KEY_ORIENTATION, null))
    }

    @Test
    fun mapScreenStillRendersWithoutAnyFix() {
        rule.setContent { AppContent(GnssUiState(), TrackRecorder.State(), initialScreen = AppScreen.MAP, initialMode = DisplayMode.DAY) }
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_COMPASS_BADGE).assertIsDisplayed()
    }
}
