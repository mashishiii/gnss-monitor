package io.github.mashishiii.gnssmonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** カーナビ風ダークテーマの色。緑=使用 / 青=捕捉 / 灰=弱い。 */
object NavColors {
    val Background = Color(0xFF0B1018)
    val Panel = Color(0xFF1B222C)
    val PanelBorder = Color(0xFF3A4452)
    val GridLine = Color(0xFF2A3340)
    val TextPrimary = Color(0xFFE6EDF5)
    val TextSecondary = Color(0xFF8B97A6)
    val Used = Color(0xFF3DDC84)
    val Received = Color(0xFF4FA3FF)
    val Weak = Color(0xFF6B7684)
    val Warning = Color(0xFFFFB84D)
}

private val NavDarkColorScheme = darkColorScheme(
    primary = NavColors.Used,
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
