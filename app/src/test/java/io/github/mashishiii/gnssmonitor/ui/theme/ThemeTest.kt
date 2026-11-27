package io.github.mashishiii.gnssmonitor.ui.theme

import androidx.compose.ui.graphics.Color
import io.github.mashishiii.gnssmonitor.data.FixState
import io.github.mashishiii.gnssmonitor.data.GpsLevel
import io.github.mashishiii.gnssmonitor.data.gpsLevelFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {

    private fun luminance(c: Color): Double {
        fun lin(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else Math.pow((d + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun check(name: String, fg: Color, bg: Color, min: Double) {
        val c = contrast(fg, bg)
        assertTrue("$name のコントラスト比 %.2f が %.1f 未満（昼夜どちらも読めること）".format(c, min), c >= min)
    }

    private fun assertReadable(night: Boolean) {
        NavColors.use(night)
        val tag = if (night) "夜" else "昼"
        // 本文
        for ((n, bg) in listOf(
            "背景" to NavColors.Background,
            "パネル上" to NavColors.PanelTop,
            "パネル下" to NavColors.PanelBottom,
            "値ボックス" to NavColors.ValueBox,
            "地図の重ね表示" to NavColors.OverlayBg.copy(alpha = 1f),
        )) {
            check("$tag 本文 on $n", NavColors.TextPrimary, bg, 6.5)
            // 値ボックスの中は本文色だけを使うので、補助文字は対象外
            if (n != "値ボックス") check("$tag 補助文字 on $n", NavColors.TextSecondary, bg, 4.5)
        }
        // ボタン・タイトル帯
        check("$tag ボタン上", NavColors.TextPrimary, NavColors.ButtonTop, 4.5)
        check("$tag ボタン下", NavColors.TextPrimary, NavColors.ButtonBottom, 4.5)
        check("$tag 選択ボタン上", NavColors.OnButtonOn, NavColors.ButtonOnTop, 4.5)
        check("$tag 選択ボタン下", NavColors.OnButtonOn, NavColors.ButtonOnBottom, 4.5)
        check("$tag タイトル上", NavColors.OnTitle, NavColors.TitleTop, 4.5)
        check("$tag タイトル下", NavColors.OnTitle, NavColors.TitleBottom, 4.5)
        // 状態の色文字・警告
        for ((n, c) in listOf(
            "使用中" to NavColors.UsedText,
            "受信中" to NavColors.ReceivedText,
            "弱い" to NavColors.WeakText,
            "警告" to NavColors.Warning,
        )) {
            check("$tag $n on パネル", c, NavColors.PanelBottom, 4.5)
            check("$tag $n on 値ボックス背景", c, NavColors.Background, 4.0)
        }
        // スカイプロットの縁取り（文字盤の中央・縁どちらでも見えること）
        for ((n, c) in listOf(
            "GPS" to NavColors.Gps,
            "みちびき" to NavColors.Qzss,
            "GLONASS" to NavColors.Glonass,
            "Galileo" to NavColors.Galileo,
            "BeiDou" to NavColors.BeiDou,
            "その他" to NavColors.OtherConstellation,
        )) {
            check("$tag $n on 文字盤中央", c, NavColors.DialCenter, 3.0)
            check("$tag $n on 文字盤縁", c, NavColors.DialEdge, 3.0)
        }
        // マーカーの白い番号が塗りの上で読めること
        for ((n, c) in listOf("使用" to NavColors.Used, "受信" to NavColors.Received, "弱い" to NavColors.Weak)) {
            check("$tag マーカー $n の番号", Color.White, c, 4.0)
        }
    }

    @Test
    fun nightPaletteIsReadable() = assertReadable(night = true)

    @Test
    fun dayPaletteIsReadable() = assertReadable(night = false)

    @Test
    fun useSwitchesPalette() {
        NavColors.use(false)
        assertFalse(NavColors.isNight)
        val dayBg = NavColors.Background
        NavColors.use(true)
        assertTrue(NavColors.isNight)
        assertTrue("昼夜で背景色が違うこと", dayBg != NavColors.Background)
        // 昼は明るく、夜は暗い
        assertTrue(luminance(dayBg) > 0.6)
        assertTrue(luminance(NavColors.Background) < 0.05)
    }

    @Test
    fun displayModeCyclesAutoDayNight() {
        assertEquals(DisplayMode.DAY, DisplayMode.AUTO.next())
        assertEquals(DisplayMode.NIGHT, DisplayMode.DAY.next())
        assertEquals(DisplayMode.AUTO, DisplayMode.NIGHT.next())
    }

    @Test
    fun displayModeResolvesNight() {
        assertTrue(DisplayMode.AUTO.isNight(systemDark = true))
        assertFalse(DisplayMode.AUTO.isNight(systemDark = false))
        assertFalse(DisplayMode.DAY.isNight(systemDark = true))
        assertTrue(DisplayMode.NIGHT.isNight(systemDark = false))
    }

    @Test
    fun displayModeFromSavedName() {
        assertEquals(DisplayMode.NIGHT, DisplayMode.fromName("NIGHT"))
        assertEquals(DisplayMode.AUTO, DisplayMode.fromName(null))
        assertEquals(DisplayMode.AUTO, DisplayMode.fromName("broken"))
    }

    @Test
    fun gpsLevelFollowsFixAndCapture() {
        assertEquals(GpsLevel.FIX, gpsLevelFrom(FixState.FIX_3D, 12))
        assertEquals(GpsLevel.FIX, gpsLevelFrom(FixState.FIX_2D, 3))
        assertEquals(GpsLevel.ACQUIRING, gpsLevelFrom(FixState.NO_FIX, 5))
        assertEquals(GpsLevel.NONE, gpsLevelFrom(FixState.NO_FIX, 0))
    }

    @Test
    fun lampColorsFollowNavU() {
        // nav-u の自車マーク: 赤=測位 / 橙=推測・捕捉中 / 灰=未受信
        assertEquals(NavColors.LampFix, lampColor(GpsLevel.FIX))
        assertEquals(NavColors.LampAcquiring, lampColor(GpsLevel.ACQUIRING))
        assertEquals(NavColors.LampNone, lampColor(GpsLevel.NONE))
        assertTrue(lampColor(GpsLevel.FIX).red > 0.8f && lampColor(GpsLevel.FIX).green < 0.3f)
    }
}
