package io.github.mashishiii.gnssmonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** カーナビ風ダークテーマの色。緑=使用 / 青=捕捉 / 灰=弱い。 */
object NavColors {
    val Background = Color(0xFF0A1017)
    val Bar = Color(0xFF17212C)
    val Panel = Color(0xFF111A24)
    val PanelBorder = Color(0xFF2B3A49)
    val GridLine = Color(0xFF243242)
    val TextPrimary = Color(0xFFE9EFF5)
    val TextSecondary = Color(0xFF8EA3B6)
    val ValueBox = Color(0xFF47535F)
    val Button = Color(0xFF212D3A)
    val ButtonOn = Color(0xFF35506B)
    val Warning = Color(0xFFF0A030)

    // 塗り（マーカー・バー）
    val Used = Color(0xFF1F9D5C)
    val Received = Color(0xFF2A62B0)
    val Weak = Color(0xFF59626C)

    // 文字用の明るい色
    val UsedText = Color(0xFF5BE09A)
    val ReceivedText = Color(0xFF86B8F5)
    val WeakText = Color(0xFF8D98A4)

    // 測位系ごとの縁取りの色
    val Gps = Color(0xFF8EC5FF)
    val Qzss = Color(0xFFFF7A7A)
    val Glonass = Color(0xFFFFB454)
    val Galileo = Color(0xFF6EE7C8)
    val BeiDou = Color(0xFFD6A2FF)
    val OtherConstellation = Color(0xFFCFD8E3)
}

private val NavDarkColorScheme = darkColorScheme(
    primary = NavColors.ButtonOn,
    secondary = NavColors.Received,
    background = NavColors.Background,
    surface = NavColors.Panel,
    onPrimary = Color.Black,
    onBackground = NavColors.TextPrimary,
    onSurface = NavColors.TextPrimary,
)

@Composable
fun GnssMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NavDarkColorScheme, content = content)
}

/** 測位系ごとの縁取りの色（GnssStatus.CONSTELLATION_* の値で引く）。 */
fun constellationColor(type: Int): Color = when (type) {
    android.location.GnssStatus.CONSTELLATION_GPS -> NavColors.Gps
    android.location.GnssStatus.CONSTELLATION_QZSS -> NavColors.Qzss
    android.location.GnssStatus.CONSTELLATION_GLONASS -> NavColors.Glonass
    android.location.GnssStatus.CONSTELLATION_GALILEO -> NavColors.Galileo
    android.location.GnssStatus.CONSTELLATION_BEIDOU -> NavColors.BeiDou
    else -> NavColors.OtherConstellation
}
