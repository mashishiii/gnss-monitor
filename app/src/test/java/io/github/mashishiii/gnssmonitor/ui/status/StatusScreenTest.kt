package io.github.mashishiii.gnssmonitor.ui.status

import android.location.GnssStatus
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mashishiii.gnssmonitor.data.GnssUiState
import io.github.mashishiii.gnssmonitor.data.PositionInfo
import io.github.mashishiii.gnssmonitor.data.SatelliteInfo
import io.github.mashishiii.gnssmonitor.ui.theme.GnssMonitorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatusScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sat(c: Int, svid: Int, az: Float, el: Float, cn0: Float, used: Boolean, freq: Float? = null) =
        SatelliteInfo(c, svid, cn0, az, el, used, freq)

    private val fixState = GnssUiState(
        position = PositionInfo(35.6586, 139.7454, 4.2f, 42.0, 1795744748000L),
        satellites = listOf(
            sat(GnssStatus.CONSTELLATION_GPS, 25, 40f, 62f, 40f, true, 1575.42e6f),
            sat(GnssStatus.CONSTELLATION_GPS, 25, 40f, 62f, 33f, true, 1176.45e6f),
            sat(GnssStatus.CONSTELLATION_GPS, 7, 90f, 0f, 30f, true, 1575.42e6f),
            sat(GnssStatus.CONSTELLATION_QZSS, 195, 150f, 78f, 45f, true),
            sat(GnssStatus.CONSTELLATION_GLONASS, 4, 250f, 40f, 33f, true),
            sat(GnssStatus.CONSTELLATION_GALILEO, 3, 95f, 50f, 36f, true),
            sat(GnssStatus.CONSTELLATION_BEIDOU, 6, 60f, 30f, 31f, false),
        ),
    )

    private fun render(width: Dp, height: Dp, state: GnssUiState, selectedKey: String? = null, onSelect: (String?) -> Unit = {}) {
        composeRule.setContent {
            GnssMonitorTheme {
                Box(Modifier.requiredSize(width, height)) {
                    StatusScreen(state, selectedKey, onSelect, Modifier.requiredSize(width, height))
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun rendersPortraitPhone() {
        render(360.dp, 700.dp, fixState)
        composeRule.onNodeWithText("3D").assertExists()
        composeRule.onNodeWithText("5 / 6").assertExists()
        composeRule.onNodeWithText("±4.2 m").assertExists()
    }

    @Test
    fun rendersFoldedCoverPortraitAndUnfoldedLandscapeWide() {
        render(840.dp, 380.dp, fixState)
        composeRule.onNodeWithTag(TAG_SKY_PLOT).assertExists()
        composeRule.onNodeWithTag(TAG_SIGNAL_BARS).assertExists()
    }

    @Test
    fun rendersUnfoldedTabletSize() {
        render(700.dp, 840.dp, fixState)
        composeRule.onNodeWithTag(TAG_SKY_PLOT).assertExists()
    }

    @Test
    fun noFixShowsDashes() {
        render(360.dp, 700.dp, GnssUiState(position = null, satellites = emptyList()))
        composeRule.onNodeWithText("未測位").assertExists()
        composeRule.onNodeWithText("0 / 0").assertExists()
        composeRule.onNodeWithText("緯度").assertExists()
        val rows = statusRows(GnssUiState(position = null, satellites = emptyList()))
        // 日付・時刻・緯度・経度・速度・高度は「---」、測位は未測位、衛星 0 / 0、精度は「---」
        // v0.5 の行（平均 C/N0・デュアル周波数・水平精度 68%・95% 目安・垂直精度・速度精度）も「---」
        assertEquals(
            listOf("---", "---", "---", "---", "---", "---", "未測位", "0 / 0", "---", "---", "---", "---", "---", "---"),
            rows.map { it.second },
        )
        composeRule.onNodeWithText("水平精度(68%)").assertExists()
        composeRule.onNodeWithText("平均C/N0").assertExists()
        composeRule.onNodeWithText("デュアル周波数").assertExists()
        composeRule.onNodeWithText("速度").assertExists()
    }

    @Test
    fun infoPanelShowsNavUStyleRowsAndSpeed() {
        val moving = fixState.copy(
            position = PositionInfo(35.6586, 139.7454, 4.2f, 42.0, 1795744748000L, speedMps = 8.4f, bearingDegrees = 62f),
        )
        render(360.dp, 700.dp, moving)
        composeRule.onNodeWithText("速度").assertExists()
        composeRule.onNodeWithText("30 km/h").assertExists()
        composeRule.onNodeWithText("GPS Level").assertExists()
        val labels = statusRows(moving).map { it.first }
        assertEquals(
            listOf(
                "日付", "時刻", "緯度", "経度", "速度", "高度", "測位", "衛星",
                "平均C/N0", "デュアル周波数", "水平精度(68%)", "95%目安×1.62", "垂直精度", "速度精度",
            ),
            labels,
        )
        assertEquals("N 35°39'31\"", statusRows(moving)[2].second)
    }

    @Test
    fun v05RowsShowAverageDualAndAccuracies() {
        val state = fixState.copy(
            position = PositionInfo(
                35.6586, 139.7454, 4.2f, 42.0, 1795744748000L,
                verticalAccuracyMeters = 6.5f, speedAccuracyMps = 0.5f,
            ),
            satellites = fixState.satellites +
                // GPS 7 にも L5 を足して、L1+L5 が使用中の衛星を 2 機にする（デュアル周波数が点灯）
                sat(GnssStatus.CONSTELLATION_GPS, 7, 90f, 0f, 28f, true, 1176.45e6f),
        )
        val rows = statusRows(state).toMap()
        // 使用中 6 機（GPS 25=40, GPS 7=30, QZSS 195=45, GLONASS 4=33, Galileo 3=36）の平均。BeiDou は未使用
        assertEquals("36.8 dB-Hz", rows["平均C/N0"])
        assertEquals("点灯 2機", rows["デュアル周波数"])
        assertEquals("±4.2 m", rows["水平精度(68%)"])
        assertEquals("±6.8 m", rows["95%目安×1.62"])
        assertEquals("±6.5 m", rows["垂直精度"])
        assertEquals("±1.8 km/h", rows["速度精度"])
        render(360.dp, 700.dp, state)
        composeRule.onNodeWithText("36.8 dB-Hz").assertExists()
        composeRule.onNodeWithText("点灯 2機").assertExists()
        composeRule.onNodeWithTag(TAG_BAND_CHIPS).assertExists()
        composeRule.onNodeWithTag(TAG_SPARKLINE).assertExists()
        composeRule.onNodeWithTag(TAG_CONSTELLATION_STATS).assertExists()
    }

    @Test
    fun dualFreqIsDashWhenNoFrequencyIsReported() {
        val noFreq = fixState.copy(satellites = fixState.satellites.map { it.copy(carrierFrequencyHz = null) })
        assertEquals("---", statusRows(noFreq).toMap()["デュアル周波数"])
        // 平均 C/N0 は周波数が無くても出る
        assertTrue(statusRows(noFreq).toMap()["平均C/N0"]!!.endsWith("dB-Hz"))
        render(360.dp, 700.dp, noFreq)
        composeRule.onNodeWithText("帯域 ---").assertExists()
    }

    @Test
    fun constellationStatText() {
        assertEquals("0/0 --", constellationStatText(null, large = false))
        val stat = io.github.mashishiii.gnssmonitor.data.ConstellationStat(1, 6, 5, 32.46f)
        assertEquals("5/6 32", constellationStatText(stat, large = false))
        assertEquals("5/6 32.5", constellationStatText(stat, large = true))
    }

    @Test
    fun speedIsDashWithoutSpeedOrFix() {
        // 速度が取れていない（Location に speed なし）
        assertEquals("---", statusRows(fixState).first { it.first == "速度" }.second)
        // 測位前は、速度があっても出さない
        val noFix = GnssUiState(position = PositionInfo(35.0, 139.0, 5f, 1.0, 1795744748000L, speedMps = 5f), satellites = emptyList())
        assertEquals("---", statusRows(noFix).first { it.first == "速度" }.second)
    }

    @Test
    fun wideLayoutShowsLevelPanelSkyAndInfoSideBySide() {
        render(840.dp, 560.dp, fixState)
        composeRule.onNodeWithTag(TAG_LEVEL_PANEL).assertExists()
        composeRule.onNodeWithTag(TAG_SKY_PLOT).assertExists()
        composeRule.onNodeWithTag(TAG_INFO_PANEL).assertExists()
        val level = composeRule.onNodeWithTag(TAG_LEVEL_PANEL).fetchSemanticsNode().positionInRoot.x
        val sky = composeRule.onNodeWithTag(TAG_SKY_PLOT).fetchSemanticsNode().positionInRoot.x
        val info = composeRule.onNodeWithTag(TAG_INFO_PANEL).fetchSemanticsNode().positionInRoot.x
        assertTrue("左から 凡例 → スカイプロット → 情報", level < sky && sky < info)
    }

    @Test
    fun manyDualFrequencySatellitesDoNotCrash() {
        val many = (1..45).flatMap { svid ->
            listOf(
                sat(GnssStatus.CONSTELLATION_GPS, svid, svid * 8f, (svid * 2f) % 90, 20f + svid % 25, svid % 2 == 0, 1575.42e6f),
                sat(GnssStatus.CONSTELLATION_GPS, svid, svid * 8f, (svid * 2f) % 90, 15f + svid % 25, false, 1176.45e6f),
            )
        } + sat(GnssStatus.CONSTELLATION_GPS, 1, Float.NaN, Float.NaN, Float.NaN, false)
        render(360.dp, 700.dp, GnssUiState(satellites = many))
        composeRule.onNodeWithTag(TAG_SIGNAL_BARS).assertExists()
    }

    @Test
    fun tappingSkyPlotMarkerSelectsThatSatellite() {
        var selected by mutableStateOf<String?>(null)
        composeRule.setContent {
            GnssMonitorTheme {
                Box(Modifier.requiredSize(360.dp, 700.dp)) {
                    StatusScreen(fixState, selected, { selected = it }, Modifier.requiredSize(360.dp, 700.dp))
                }
            }
        }
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag(TAG_SKY_PLOT)
        val size = node.fetchSemanticsNode().size
        val density = composeRule.density.density
        val layout = skyLayout(size.width.toFloat(), size.height.toFloat(), 18f * density)
        // GPS 7 は方位 90°・仰角 0° = 右の外周
        val p = skyPosition(90f, 0f, layout)
        node.performTouchInput { click(Offset(p.x, p.y)) }
        composeRule.waitForIdle()
        assertEquals("1-7", selected)
        composeRule.onNodeWithText("GPS 7", substring = true).assertIsDisplayed()

        // 同じマーカーをもう一度タップすると選択が外れる
        node.performTouchInput { click(Offset(p.x, p.y)) }
        composeRule.waitForIdle()
        assertEquals(null, selected)

        // マーカーの無い場所では何も選ばれない
        val empty = skyPosition(315f, 10f, layout)
        node.performTouchInput { click(Offset(empty.x, empty.y)) }
        composeRule.waitForIdle()
        assertEquals(null, selected)
    }

    @Test
    fun tappingBarSelectsThatSatelliteAndDetailShowsBothBands() {
        var selected by mutableStateOf<String?>(null)
        composeRule.setContent {
            GnssMonitorTheme {
                Box(Modifier.requiredSize(360.dp, 700.dp)) {
                    StatusScreen(fixState, selected, { selected = it }, Modifier.requiredSize(360.dp, 700.dp))
                }
            }
        }
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag(TAG_SIGNAL_BARS)
        val density = composeRule.density.density
        // 並びは GPS 7, GPS 25, みちびき 195, ... なので 2 本目が GPS 25（L1 + L5）
        val slot = maxOf(26f * density, (node.fetchSemanticsNode().size.width - 24f * density) / 6f)
        node.performTouchInput { click(Offset(24f * density + slot * 1.5f, 20f * density)) }
        composeRule.waitForIdle()
        assertEquals("1-25", selected)
        composeRule.onNodeWithText("L1:40.0 L5:33.0", substring = true).assertExists()
    }
}
