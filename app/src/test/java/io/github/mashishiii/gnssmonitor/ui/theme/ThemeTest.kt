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
            check("$tag $n on 背景", c, NavColors.Background, 4.0)
        }
        // 受信強度バーの下の測位系の頭文字（背景に直接描く）と、球につける目印の文字
        for ((n, c) in listOf(
            "GPS" to NavColors.Gps,
            "みちびき" to NavColors.Qzss,
            "GLONASS" to NavColors.Glonass,
            "Galileo" to NavColors.Galileo,
            "BeiDou" to NavColors.BeiDou,
            "その他" to NavColors.OtherConstellation,
        )) {
            check("$tag $n の頭文字 on 背景", c, NavColors.Background, 3.5)
            val badgeText = if (night) Color(0xFF111114) else Color.White
            check("$tag $n の目印の文字", badgeText, c, 3.5)
        }
        // 球の中の SVID（暗い文字）が、強さ 3 段階のどの色の上でも読めること
        for ((n, c) in listOf("強い" to NavColors.LevelStrong, "中" to NavColors.LevelMedium, "弱い" to NavColors.LevelWeak)) {
            check("$tag 球 $n の番号", NavColors.OnSphere, c, 6.0)
        }
        // 球と文字盤の見分け（球には濃い縁取りと陰影があるので、中央の部分で確認）
        for ((n, c) in listOf("強い" to NavColors.LevelStrong, "中" to NavColors.LevelMedium, "弱い" to NavColors.LevelWeak)) {
            check("$tag 球 $n on 文字盤中央", c, NavColors.DialCenter, 2.0)
        }
        // v0.5: 内訳チップ（選択色 = デュアル周波数の点灯）・情報パネルの新しい行・スパークラインの線とラベル
        check("$tag 点灯チップ 上", NavColors.OnButtonOn, NavColors.ButtonOnTop, 4.5)
        check("$tag 点灯チップ 下", NavColors.OnButtonOn, NavColors.ButtonOnBottom, 4.5)
        check("$tag スパークラインの線 on パネル", NavColors.TextPrimary, NavColors.PanelBottom, 6.5)
        check("$tag スパークラインのラベル on パネル", NavColors.TextSecondary, NavColors.PanelTop, 4.5)
        check("$tag 地図の凡例・状態ボックスの補助文字", NavColors.TextSecondary, NavColors.OverlayBg.copy(alpha = 1f), 4.5)
        // 文字盤の上の「N」
        check("$tag N on 背景", NavColors.TextPrimary, NavColors.Background, 6.5)
        // v0.6: 方位バッジの N・キャプション、動きボタンの文字、スカイプロットの N/E/S/W、「方位なし」
        check("$tag 方位バッジの N on 文字盤中央", NavColors.TextPrimary, NavColors.DialCenter, 4.5)
        check("$tag 方位バッジの N on 文字盤縁", NavColors.TextPrimary, NavColors.DialEdge, 4.5)
        check("$tag 方位バッジのキャプション on 地図の重ね表示", NavColors.TextPrimary, NavColors.OverlayBg.copy(alpha = 1f), 6.5)
        check("$tag 動きボタン上（レトロ）", NavColors.TextPrimary, NavColors.ButtonTop, 4.5)
        check("$tag 動きボタン下（レトロ）", NavColors.TextPrimary, NavColors.ButtonBottom, 4.5)
        check("$tag 動きボタン上（スムーズ）", NavColors.OnButtonOn, NavColors.ButtonOnTop, 4.5)
        check("$tag 動きボタン下（スムーズ）", NavColors.OnButtonOn, NavColors.ButtonOnBottom, 4.5)
        check("$tag スカイプロットの N/E/S/W on 背景", NavColors.TextPrimary, NavColors.Background, 6.5)
        check("$tag 「方位なし」on 背景", NavColors.TextSecondary, NavColors.Background, 4.5)
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
        assertTrue(luminance(NavColors.Background) < 0.02)
    }

    @Test
    fun titleBarIsMaroonInBothModes() {
        // nav-u の画面と同じ、えんじ色のタイトル帯（赤みが強く、青みが少ない暗い色）。昼夜で共通
        for (night in listOf(true, false)) {
            NavColors.use(night)
            for (c in listOf(NavColors.TitleTop, NavColors.TitleBottom)) {
                assertTrue("赤が優勢", c.red > c.green * 2.5f && c.red > c.blue * 1.8f)
                assertTrue("暗い色", luminance(c) < 0.06)
            }
        }
    }

    @Test
    fun noBluePrimaryButtonsLeft() {
        // v0.4.0 の青い光沢ボタンはやめた（選択中はえんじ色、通常は無彩色に近い濃いグレー）
        for (night in listOf(true, false)) {
            NavColors.use(night)
            for (c in listOf(NavColors.ButtonOnTop, NavColors.ButtonOnBottom)) assertTrue("選択ボタンは青くない", c.blue < c.red)
            for (c in listOf(NavColors.ButtonTop, NavColors.ButtonBottom)) {
                assertTrue("通常ボタンはほぼ無彩色", maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue) < 0.06f)
            }
        }
    }

    @Test
    fun dialIsPurpleGradientDarkCenterInNight() {
        NavColors.use(true)
        val c = NavColors.DialCenter
        val e = NavColors.DialEdge
        assertTrue("中心は暗い", luminance(c) < 0.02)
        assertTrue("縁は紫（赤と青が緑より強い）", e.blue > e.green * 1.5f && e.red > e.green * 1.3f)
        assertTrue("縁のほうが明るい", luminance(e) > luminance(c))
    }

    @Test
    fun levelColorsFollowNavULegend() {
        for (night in listOf(true, false)) {
            NavColors.use(night)
            val s = NavColors.LevelStrong
            val m = NavColors.LevelMedium
            val w = NavColors.LevelWeak
            assertTrue("強い=橙", s.red > 0.8f && s.green in 0.4f..0.65f && s.blue < 0.3f)
            assertTrue("中=黄緑（赤と緑が強く青が弱い）", m.red > 0.55f && m.green > 0.6f && m.blue < 0.3f)
            assertTrue("弱い=灰（ほぼ無彩色）", maxOf(w.red, w.green, w.blue) - minOf(w.red, w.green, w.blue) < 0.08f)
        }
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
