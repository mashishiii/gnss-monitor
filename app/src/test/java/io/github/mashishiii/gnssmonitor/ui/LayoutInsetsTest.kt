package io.github.mashishiii.gnssmonitor.ui

import android.location.GnssStatus
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.mashishiii.gnssmonitor.data.GnssUiState
import io.github.mashishiii.gnssmonitor.data.PositionInfo
import io.github.mashishiii.gnssmonitor.data.SatelliteInfo
import io.github.mashishiii.gnssmonitor.data.TrackRecorder
import io.github.mashishiii.gnssmonitor.ui.status.narrowFitsWithoutScroll
import androidx.compose.ui.test.performScrollTo
import io.github.mashishiii.gnssmonitor.ui.theme.GnssMonitorTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.2 の不具合（下のボタンが凡例・高度の行に重なる）の回帰テスト。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LayoutInsetsTest {
    @get:Rule
    val rule = createComposeRule()

    private val state = GnssUiState(
        position = PositionInfo(35.68, 139.76, 4f, 42.0, 1795744748000L),
        satellites = (1..12).map { SatelliteInfo(GnssStatus.CONSTELLATION_GPS, it, 30f + it, it * 30f, 20f + it * 5, it < 9) },
    )

    private fun assertAltitudeRowAboveNav(scrollFirst: Boolean = false) {
        rule.setContent { GnssMonitorTheme { AppContent(state, TrackRecorder.State()) } }
        rule.waitForIdle()
        if (scrollFirst) {
            rule.onNodeWithText("高度").performScrollTo()
            rule.waitForIdle()
        }
        // boundsInRoot はスクロール領域で切り取られるので、切り取り前の位置（positionInRoot + size）で比べる
        fun bottomOf(text: String): Float {
            val n = rule.onNodeWithText(text).fetchSemanticsNode()
            return n.positionInRoot.y + n.size.height
        }
        val navTop = rule.onNodeWithTag("nav-STATUS").fetchSemanticsNode().positionInRoot.y
        assertTrue("高度の行 ${bottomOf("高度")} がボタン $navTop より上", bottomOf("高度") <= navTop)
        assertTrue("凡例 ${bottomOf("BeiDou")} がボタン $navTop より上", bottomOf("BeiDou") <= navTop)
    }

    /** Pixel Fold 折りたたみ時（外画面・縦）でステータスバー等を除いた高さ相当。v0.2 ではここで重なっていた。 */
    @Test
    @Config(qualifiers = "w411dp-h700dp-xxhdpi")
    fun foldedPortraitFitsAboveNav() = assertAltitudeRowAboveNav()

    /** 一画面に収まらない低い画面でも、スクロールすれば高度の行がボタンより上に出る（隠れたままにならない）。 */
    @Test
    @Config(qualifiers = "w411dp-h560dp-xxhdpi")
    fun shortPortraitScrollsAboveNav() = assertAltitudeRowAboveNav(scrollFirst = true)

    /** Pixel Fold 展開時（内画面・横）相当。 */
    @Test
    @Config(qualifiers = "w841dp-h650dp-xhdpi")
    fun unfoldedWideFitsAboveNav() = assertAltitudeRowAboveNav()

    /** 展開時・縦。 */
    @Test
    @Config(qualifiers = "w673dp-h841dp-xhdpi")
    fun unfoldedPortraitFitsAboveNav() = assertAltitudeRowAboveNav()

    @Test
    fun narrowFitDecision() {
        assertTrue(narrowFitsWithoutScroll(600.dp))
        assertTrue(!narrowFitsWithoutScroll(500.dp))
    }
}
