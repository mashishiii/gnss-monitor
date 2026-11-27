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
    val levelStrong: Color,
    val levelMedium: Color,
    val levelWeak: Color,
    val dialLine: Color,
    val dialRing: Color,
)

/*
 * v0.4.1: nav-u の説明書の画面（測位情報）に合わせた配色。
 * 夜 = ほぼ黒の背景・えんじ色のタイトル帯・濃いグレーの角丸パネル・紫の文字盤・白い LCD 風の文字。
 * 昼 = 同じ構成で、背景とパネルだけ明るくした版（タイトル帯のえんじと文字盤の紫は共通）。
 */
private val NightPalette = NavPalette(
    night = true,
    background = Color(0xFF09090A),
    barTop = Color(0xFF1B1B1E),
    barBottom = Color(0xFF111113),
    panelTop = Color(0xFF232326),
    panelBottom = Color(0xFF19191C),
    panelBorder = Color(0xFF3E3E44),
    gridLine = Color(0xFF3A3A40),
    textPrimary = Color(0xFFF4F4F6),
    textSecondary = Color(0xFFB4B4BC),
    valueBox = Color(0xFF2B2B30),
    buttonTop = Color(0xFF3B3B41),
    buttonBottom = Color(0xFF2A2A2F),
    buttonOnTop = Color(0xFF7A2A3D),
    buttonOnBottom = Color(0xFF5A1D2D),
    onButtonOn = Color.White,
    titleTop = Color(0xFF6E1B2D),
    titleBottom = Color(0xFF5A1323),
    onTitle = Color.White,
    dialCenter = Color(0xFF12052A),
    dialEdge = Color(0xFF7B4DC4),
    bezelLight = Color.White,
    bezelDark = Color.White,
    rimTop = Color.White.copy(alpha = 0.28f),
    rimBottom = Color.Black.copy(alpha = 0.55f),
    overlayBg = Color(0xE0151517),
    warning = Color(0xFFF0A030),
    used = Color(0xFF9BDB5A),
    received = Color(0xFF7B4DC4),
    weak = Color(0xFF9A9EA6),
    usedText = Color(0xFFA9DE6E),
    receivedText = Color(0xFFC9B0F0),
    weakText = Color(0xFFA4A4AC),
    gps = Color(0xFF9CCBFF),
    qzss = Color(0xFFFF8A8A),
    glonass = Color(0xFFFFBD66),
    galileo = Color(0xFF7BEBCF),
    beiDou = Color(0xFFDDB4FF),
    otherConstellation = Color(0xFFD5D9E0),
    levelStrong = Color(0xFFF08A1C),
    levelMedium = Color(0xFFB4BC2A),
    levelWeak = Color(0xFFA9ADB4),
    dialLine = Color.White.copy(alpha = 0.85f),
    dialRing = Color.White.copy(alpha = 0.50f),
)

private val DayPalette = NavPalette(
    night = false,
    background = Color(0xFFE9E7EB),
    barTop = Color(0xFFF3F1F4),
    barBottom = Color(0xFFD2CFD6),
    panelTop = Color(0xFFFFFFFF),
    panelBottom = Color(0xFFEFEDF2),
    panelBorder = Color(0xFFA9A4B2),
    gridLine = Color(0xFFC3BFCB),
    textPrimary = Color(0xFF17171C),
    textSecondary = Color(0xFF4F4C58),
    valueBox = Color(0xFFF7F6F9),
    buttonTop = Color(0xFFF7F6F8),
    buttonBottom = Color(0xFFD6D3DA),
    buttonOnTop = Color(0xFF8C2C42),
    buttonOnBottom = Color(0xFF6E1E32),
    onButtonOn = Color.White,
    titleTop = Color(0xFF7C2236),
    titleBottom = Color(0xFF671829),
    onTitle = Color.White,
    dialCenter = Color(0xFFEFE6FA),
    dialEdge = Color(0xFFA687DB),
    bezelLight = Color(0xFF3F2C66),
    bezelDark = Color(0xFF3F2C66),
    rimTop = Color.White.copy(alpha = 0.95f),
    rimBottom = Color(0xFF7B7584).copy(alpha = 0.75f),
    overlayBg = Color(0xE6F7F6F9),
    warning = Color(0xFF8F4F00),
    used = Color(0xFF3F7F10),
    received = Color(0xFF6B3FB0),
    weak = Color(0xFF5E626A),
    usedText = Color(0xFF2F6A06),
    receivedText = Color(0xFF53328F),
    weakText = Color(0xFF55555E),
    gps = Color(0xFF2468B8),
    qzss = Color(0xFFC62E2E),
    glonass = Color(0xFF9E5600),
    galileo = Color(0xFF087060),
    beiDou = Color(0xFF7A3FBE),
    otherConstellation = Color(0xFF555C66),
    levelStrong = Color(0xFFF08A1C),
    levelMedium = Color(0xFF98A116),
    levelWeak = Color(0xFF9EA2AA),
    dialLine = Color(0xFF3F2C66).copy(alpha = 0.75f),
    dialRing = Color(0xFF3F2C66).copy(alpha = 0.45f),
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
    val LevelStrong get() = palette.levelStrong
    val LevelMedium get() = palette.levelMedium
    val LevelWeak get() = palette.levelWeak
    val DialLine get() = palette.dialLine
    val DialRing get() = palette.dialRing
    /** 球の中の SVID の文字色（nav-u の画面と同じ暗い文字）。 */
    val OnSphere = Color(0xFF1A1A1E)

    /** nav-u の自車マークと同じ意味づけ（赤=測位中 / 橙=捕捉中 / 灰=未受信）。 */
    val LampFix = Color(0xFFE5392F)
    val LampAcquiring = Color(0xFFF08A1C)
    val LampNone = Color(0xFF8B949E)
}

private val NavDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7A2A3D),
    secondary = Color(0xFF7B4DC4),
    background = Color(0xFF09090A),
    surface = Color(0xFF19191C),
    onPrimary = Color.White,
    onBackground = Color(0xFFF4F4F6),
    onSurface = Color(0xFFF4F4F6),
)

private val NavLightColorScheme = lightColorScheme(
    primary = Color(0xFF8C2C42),
    secondary = Color(0xFF6B3FB0),
    background = Color(0xFFE9E7EB),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF17171C),
    onSurface = Color(0xFF17171C),
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
