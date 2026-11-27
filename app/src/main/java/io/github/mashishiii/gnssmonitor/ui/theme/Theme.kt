package io.github.mashishiii.gnssmonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** 昼夜の表示モード。nav-u の「自動/昼/夜」の切り替えに倣う。AUTO は端末のダークテーマに従う。 */
enum class DisplayMode(val label: String) {
    AUTO("自動"),
    DAY("昼"),
    NIGHT("夜"),
    ;

    fun next(): DisplayMode = entries[(ordinal + 1) % entries.size]

    /** 端末がダークテーマかどうかを受けて、夜の配色にするかを決める。 */
    fun isNight(systemDark: Boolean): Boolean = when (this) {
        AUTO -> systemDark
        DAY -> false
        NIGHT -> true
    }

    companion object {
        fun fromName(name: String?): DisplayMode = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * 画面の配色一式。昼用と夜用の 2 種類。
 * 上下 2 色のものは、光沢のあるボタンやパネルの縦グラデーション（上=明るい / 下=暗い）に使う。
 */
class NavPalette(
    val night: Boolean,
    val background: Color,
    val barTop: Color,
    val barBottom: Color,
    val panelTop: Color,
    val panelBottom: Color,
    val panelBorder: Color,
    val gridLine: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val valueBox: Color,
    val buttonTop: Color,
    val buttonBottom: Color,
    val buttonOnTop: Color,
    val buttonOnBottom: Color,
    val onButtonOn: Color,
    val titleTop: Color,
    val titleBottom: Color,
    val onTitle: Color,
    val dialCenter: Color,
    val dialEdge: Color,
    val bezelLight: Color,
    val bezelDark: Color,
    val rimTop: Color,
    val rimBottom: Color,
    val overlayBg: Color,
    val warning: Color,
    val used: Color,
    val received: Color,
    val weak: Color,
    val usedText: Color,
    val receivedText: Color,
    val weakText: Color,
    val gps: Color,
    val qzss: Color,
    val glonass: Color,
    val galileo: Color,
    val beiDou: Color,
    val otherConstellation: Color,
)

private val NightPalette = NavPalette(
    night = true,
    background = Color(0xFF0A1017),
    barTop = Color(0xFF2A3A4B),
    barBottom = Color(0xFF111A24),
    panelTop = Color(0xFF1A2836),
    panelBottom = Color(0xFF0F1822),
    panelBorder = Color(0xFF33475A),
    gridLine = Color(0xFF243242),
    textPrimary = Color(0xFFE9EFF5),
    textSecondary = Color(0xFF8EA3B6),
    valueBox = Color(0xFF47535F),
    buttonTop = Color(0xFF42586F),
    buttonBottom = Color(0xFF1B2836),
    buttonOnTop = Color(0xFF3F78B0),
    buttonOnBottom = Color(0xFF1F4B78),
    onButtonOn = Color.White,
    titleTop = Color(0xFF356697),
    titleBottom = Color(0xFF16314E),
    onTitle = Color.White,
    dialCenter = Color(0xFF1A2D40),
    dialEdge = Color(0xFF0A131C),
    bezelLight = Color(0xFF86A9CB),
    bezelDark = Color(0xFF1B2A38),
    rimTop = Color.White.copy(alpha = 0.50f),
    rimBottom = Color.Black.copy(alpha = 0.65f),
    overlayBg = Color(0xE00E1822),
    warning = Color(0xFFF0A030),
    used = Color(0xFF178A4E),
    received = Color(0xFF2A62B0),
    weak = Color(0xFF59626C),
    usedText = Color(0xFF5BE09A),
    receivedText = Color(0xFF86B8F5),
    weakText = Color(0xFF8D98A4),
    gps = Color(0xFF8EC5FF),
    qzss = Color(0xFFFF7A7A),
    glonass = Color(0xFFFFB454),
    galileo = Color(0xFF6EE7C8),
    beiDou = Color(0xFFD6A2FF),
    otherConstellation = Color(0xFFCFD8E3),
)

private val DayPalette = NavPalette(
    night = false,
    background = Color(0xFFE6EDF4),
    barTop = Color(0xFFF8FBFE),
    barBottom = Color(0xFFBFCDDB),
    panelTop = Color(0xFFFFFFFF),
    panelBottom = Color(0xFFDCE6F0),
    panelBorder = Color(0xFF93A8BC),
    gridLine = Color(0xFFB3C3D2),
    textPrimary = Color(0xFF14202B),
    textSecondary = Color(0xFF4A5E72),
    valueBox = Color(0xFFF6F9FC),
    buttonTop = Color(0xFFFAFCFE),
    buttonBottom = Color(0xFFB9C9D9),
    buttonOnTop = Color(0xFFA6D2F7),
    buttonOnBottom = Color(0xFF5B98D6),
    onButtonOn = Color(0xFF06223F),
    titleTop = Color(0xFF3A78B5),
    titleBottom = Color(0xFF1F5690),
    onTitle = Color.White,
    dialCenter = Color(0xFFFFFFFF),
    dialEdge = Color(0xFFD3E0EC),
    bezelLight = Color(0xFFFFFFFF),
    bezelDark = Color(0xFF7890A8),
    rimTop = Color.White.copy(alpha = 0.95f),
    rimBottom = Color(0xFF6F879F).copy(alpha = 0.85f),
    overlayBg = Color(0xE6F4F8FC),
    warning = Color(0xFF8F4F00),
    used = Color(0xFF178A4E),
    received = Color(0xFF2A62B0),
    weak = Color(0xFF59626C),
    usedText = Color(0xFF096B3A),
    receivedText = Color(0xFF1B5AA6),
    weakText = Color(0xFF55616D),
    gps = Color(0xFF2E7CD1),
    qzss = Color(0xFFD83A3A),
    glonass = Color(0xFFB36500),
    galileo = Color(0xFF087A62),
    beiDou = Color(0xFF8A4FC7),
    otherConstellation = Color(0xFF5F6B78),
)

/**
 * 現在の配色（昼 or 夜）。各画面は従来どおり `NavColors.Panel` のように参照する。
 * 中身は Compose の状態なので、昼夜を切り替えると画面（描画も含む）が自動で更新される。
 */
object NavColors {
    private var palette by mutableStateOf(NightPalette)

    /** 配色を切り替える。[GnssMonitorTheme] から呼ばれる。 */
    internal fun use(night: Boolean) {
        val next = if (night) NightPalette else DayPalette
        if (palette !== next) palette = next
    }

    val isNight: Boolean get() = palette.night

    val Background get() = palette.background
    val BarTop get() = palette.barTop
    val BarBottom get() = palette.barBottom
    val PanelTop get() = palette.panelTop
    val PanelBottom get() = palette.panelBottom
    val Panel get() = palette.panelBottom
    val PanelBorder get() = palette.panelBorder
    val GridLine get() = palette.gridLine
    val TextPrimary get() = palette.textPrimary
    val TextSecondary get() = palette.textSecondary
    val ValueBox get() = palette.valueBox
    val ButtonTop get() = palette.buttonTop
    val ButtonBottom get() = palette.buttonBottom
    val ButtonOnTop get() = palette.buttonOnTop
    val ButtonOnBottom get() = palette.buttonOnBottom
    val OnButtonOn get() = palette.onButtonOn
    val TitleTop get() = palette.titleTop
    val TitleBottom get() = palette.titleBottom
    val OnTitle get() = palette.onTitle
    val DialCenter get() = palette.dialCenter
    val DialEdge get() = palette.dialEdge
    val BezelLight get() = palette.bezelLight
    val BezelDark get() = palette.bezelDark
    val RimTop get() = palette.rimTop
    val RimBottom get() = palette.rimBottom
    val OverlayBg get() = palette.overlayBg
    val Warning get() = palette.warning

    // 塗り（マーカー・バー）。緑=使用 / 青=捕捉 / 灰=弱い
    val Used get() = palette.used
    val Received get() = palette.received
    val Weak get() = palette.weak

    // 文字用の色
    val UsedText get() = palette.usedText
    val ReceivedText get() = palette.receivedText
    val WeakText get() = palette.weakText

    // 測位系ごとの縁取りの色
    val Gps get() = palette.gps
    val Qzss get() = palette.qzss
    val Glonass get() = palette.glonass
    val Galileo get() = palette.galileo
    val BeiDou get() = palette.beiDou
    val OtherConstellation get() = palette.otherConstellation

    /** nav-u の自車マークと同じ意味づけ（赤=測位中 / 橙=捕捉中 / 灰=未受信）。 */
    val LampFix = Color(0xFFE5392F)
    val LampAcquiring = Color(0xFFF08A1C)
    val LampNone = Color(0xFF8B949E)
}

private val NavDarkColorScheme = darkColorScheme(
    primary = Color(0xFF5B95CC),
    secondary = Color(0xFF2A62B0),
    background = Color(0xFF0A1017),
    surface = Color(0xFF0F1822),
    onPrimary = Color.Black,
    onBackground = Color(0xFFE9EFF5),
    onSurface = Color(0xFFE9EFF5),
)

private val NavLightColorScheme = lightColorScheme(
    primary = Color(0xFF3A76B6),
    secondary = Color(0xFF2A62B0),
    background = Color(0xFFE6EDF4),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF14202B),
    onSurface = Color(0xFF14202B),
)

/** カーナビ風テーマ。[night] が true なら夜（暗い）配色、false なら昼（明るい）配色。 */
@Composable
fun GnssMonitorTheme(night: Boolean = true, content: @Composable () -> Unit) {
    // 子が読む前に配色を決める（同じ値なら何も起きない）
    remember(night) { NavColors.use(night); night }
    MaterialTheme(colorScheme = if (night) NavDarkColorScheme else NavLightColorScheme, content = content)
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
