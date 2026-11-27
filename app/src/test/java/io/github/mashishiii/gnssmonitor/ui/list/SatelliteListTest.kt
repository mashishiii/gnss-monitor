package io.github.mashishiii.gnssmonitor.ui.list

import android.location.GnssStatus
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.mashishiii.gnssmonitor.data.SatelliteInfo
import io.github.mashishiii.gnssmonitor.ui.theme.GnssMonitorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 実機クラッシュの再現・回帰テスト（Robolectric 上で実際の LazyColumn を描画する）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SatelliteListTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sat(
        svid: Int,
        freqHz: Float? = null,
        constellation: Int = GnssStatus.CONSTELLATION_GPS,
        cn0: Float = 30f,
        used: Boolean = true,
    ) = SatelliteInfo(constellation, svid, cn0, 120f, 45f, used, freqHz)

    private fun render(list: List<SatelliteInfo>) {
        composeRule.setContent { GnssMonitorTheme { SatelliteList(list) } }
        composeRule.waitForIdle()
    }

    /** 多周波端末: 同じ GPS の SVID 5 が L1 と L5 で 2 行届く。 */
    @Test
    fun sameSvidOnTwoFrequenciesDoesNotCrash() {
        render(listOf(sat(5, 1575.42e6f), sat(5, 1176.45e6f), sat(7, 1575.42e6f)))
        composeRule.onNodeWithText("測位系").assertExists()
    }

    /** 周波数が取れず、測位系・SVID も同じ行が重複しても落ちない。 */
    @Test
    fun fullyIdenticalEntriesDoNotCrash() {
        render(listOf(sat(5), sat(5), sat(5)))
        composeRule.onNodeWithText("測位系").assertExists()
    }

    @Test
    fun badValuesDoNotCrash() {
        render(
            listOf(
                sat(1, cn0 = Float.NaN),
                sat(2, cn0 = Float.POSITIVE_INFINITY),
                sat(3, cn0 = -5f),
                sat(4, cn0 = 0f, used = false),
            ),
        )
        composeRule.onNodeWithText("測位系").assertExists()
    }
}
