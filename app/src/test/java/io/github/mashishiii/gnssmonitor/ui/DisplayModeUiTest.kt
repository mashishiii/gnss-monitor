package io.github.mashishiii.gnssmonitor.ui

import android.location.GnssStatus
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mashishiii.gnssmonitor.data.GnssUiState
import io.github.mashishiii.gnssmonitor.data.PositionInfo
import io.github.mashishiii.gnssmonitor.data.SatelliteInfo
import io.github.mashishiii.gnssmonitor.data.TrackRecorder
import io.github.mashishiii.gnssmonitor.ui.theme.DisplayMode
import io.github.mashishiii.gnssmonitor.ui.theme.NavColors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.4: 画面タイトル帯と、下の「表示」ボタンによる昼夜の切り替え。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h740dp-xxhdpi")
class DisplayModeUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val state = GnssUiState(
        position = PositionInfo(35.68, 139.76, 4f, 42.0, 1795744748000L),
        satellites = (1..8).map { SatelliteInfo(GnssStatus.CONSTELLATION_GPS, it, 35f, it * 40f, 30f + it * 5, it < 6) },
    )

    @Test
    fun titleBarShowsScreenNameAndGpsLevel() {
        rule.setContent { AppContent(state, TrackRecorder.State(), initialMode = DisplayMode.NIGHT) }
        rule.onNodeWithText("GPS情報（測位状況）").assertIsDisplayed()
        rule.onNodeWithText("測位中").assertIsDisplayed()
        rule.onNodeWithTag("nav-LIST").performClick()
        rule.onNodeWithText("GPS情報（衛星一覧）").assertIsDisplayed()
        rule.onNodeWithTag("nav-MAP").performClick()
        rule.onNodeWithText("現在地マップ").assertIsDisplayed()
    }

    @Test
    fun noFixShowsGrayLampLabel() {
        rule.setContent { AppContent(GnssUiState(), TrackRecorder.State(), initialMode = DisplayMode.NIGHT) }
        rule.onNodeWithText("未受信").assertIsDisplayed()
    }

    @Test
    fun displayButtonCyclesAutoDayNightAndRestylesEverything() {
        rule.setContent { AppContent(state, TrackRecorder.State(), initialMode = DisplayMode.AUTO) }
        // Robolectric の端末は昼（ダークテーマではない）なので、自動 = 昼
        rule.onNodeWithText("表示 自動").assertIsDisplayed()
        assertFalse(NavColors.isNight)
        rule.onNodeWithTag("nav-DISPLAY").performClick()
        rule.onNodeWithText("表示 昼").assertIsDisplayed()
        assertFalse(NavColors.isNight)
        rule.onNodeWithTag("nav-DISPLAY").performClick()
        rule.onNodeWithText("表示 夜").assertIsDisplayed()
        assertTrue(NavColors.isNight)
        rule.onNodeWithTag("nav-DISPLAY").performClick()
        rule.onNodeWithText("表示 自動").assertIsDisplayed()
        assertFalse(NavColors.isNight)
    }
}
