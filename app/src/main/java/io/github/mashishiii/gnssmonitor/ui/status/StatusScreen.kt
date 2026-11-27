package io.github.mashishiii.gnssmonitor.ui.status

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mashishiii.gnssmonitor.data.FixState
import io.github.mashishiii.gnssmonitor.data.GnssUiState
import io.github.mashishiii.gnssmonitor.data.MAX_CN0_DB_HZ
import io.github.mashishiii.gnssmonitor.data.MergedSatellite
import io.github.mashishiii.gnssmonitor.data.PositionInfo
import io.github.mashishiii.gnssmonitor.data.SignalLevel
import io.github.mashishiii.gnssmonitor.data.signalLevelFrom
import io.github.mashishiii.gnssmonitor.data.formatDateNav
import io.github.mashishiii.gnssmonitor.data.formatDmsNav
import io.github.mashishiii.gnssmonitor.data.formatSpeedKmh
import io.github.mashishiii.gnssmonitor.data.formatTimeNav
import io.github.mashishiii.gnssmonitor.ui.theme.darken
import io.github.mashishiii.gnssmonitor.data.BandFamily
import io.github.mashishiii.gnssmonitor.data.GnssStats
import io.github.mashishiii.gnssmonitor.data.computeGnssStats
import io.github.mashishiii.gnssmonitor.data.dualFreqLabel
import io.github.mashishiii.gnssmonitor.data.formatSpeedAccuracyKmh
import io.github.mashishiii.gnssmonitor.data.formatVerticalAccuracy
import io.github.mashishiii.gnssmonitor.data.horizontalAccuracy95
import io.github.mashishiii.gnssmonitor.data.ACCURACY_95_FACTOR
import android.location.GnssStatus
import io.github.mashishiii.gnssmonitor.data.mergeSatellites
import io.github.mashishiii.gnssmonitor.ui.theme.NavColors
import io.github.mashishiii.gnssmonitor.ui.theme.constellationColor
import io.github.mashishiii.gnssmonitor.ui.theme.lighten
import io.github.mashishiii.gnssmonitor.ui.theme.panelSurface
import androidx.compose.ui.graphics.Brush
import java.util.Locale

/** この幅（dp）以上なら、スカイプロットと情報パネルを横並びにする。折りたたみ展開時や横向き用。 */
private val WideThreshold = 600.dp

internal const val TAG_SKY_PLOT = "skyPlot"
internal const val TAG_SIGNAL_BARS = "signalBars"
internal const val TAG_LEVEL_PANEL = "levelPanel"
internal const val TAG_INFO_PANEL = "infoPanel"
internal const val TAG_BAND_CHIPS = "bandChips"
internal const val TAG_SPARKLINE = "accuracySparkline"
internal const val TAG_CONSTELLATION_STATS = "constellationStats"

/** 左の「GPS Level」パネルの幅（縦長・横長とも）。 */
private val LevelPanelWidthNarrow = 96.dp
private val LevelPanelWidthWide = 132.dp

/** 測位状況画面（既定の画面）。nav-u の「測位情報」画面に倣い、左に凡例、中央にスカイプロット、右に情報パネル。 */
@Composable
fun StatusScreen(
    state: GnssUiState,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sats = remember(state.satellites) { mergeSatellites(state.satellites) }
    val selected = sats.firstOrNull { it.key == selectedKey }
    val stats = remember(state.satellites) { computeGnssStats(state.satellites) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= WideThreshold) {
            WideLayout(state, stats, sats, selected, onSelect, maxWidth, maxHeight)
        } else {
            NarrowLayout(state, stats, sats, selected, onSelect, maxWidth, maxHeight)
        }
    }
}

/**
 * 縦長画面でスカイプロット以外（詳細・強度バー・情報パネル）に必要な高さの目安。
 * 残りが [NarrowMinSky] 以上あれば、スクロールなしで全部が見えるようにスカイプロットを縮める。
 */
private val NarrowReservedHeight = 396.dp
private val NarrowMinSky = 200.dp

/** スクロールなしで収める配置にできるか。 */
internal fun narrowFitsWithoutScroll(maxHeight: Dp): Boolean = maxHeight - NarrowReservedHeight >= NarrowMinSky

@Composable
private fun NarrowLayout(
    state: GnssUiState,
    stats: GnssStats,
    sats: List<MergedSatellite>,
    selected: MergedSatellite?,
    onSelect: (String?) -> Unit,
    maxWidth: Dp,
    maxHeight: Dp,
) {
    if (narrowFitsWithoutScroll(maxHeight)) {
        // スカイプロットが残りの高さを使う（下のボタンと重ならず、情報パネルの最後の行まで一画面に収まる）
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LevelPanel(Modifier.width(LevelPanelWidthNarrow).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    SkyPlot(
                        sats, selected?.key, onSelect,
                        Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).testTag(TAG_SKY_PLOT),
                    )
                }
            }
            Readout(selected)
            SignalBars(sats, selected?.key, onSelect, barHeight = 88.dp)
            BreakdownRow(stats, state.accuracyHistory)
            InfoPanel(state, stats, twoColumns = true)
        }
    } else {
        // 高さが足りない（横向きのスマホなど）ときはスクロール
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val sky = minOf(maxWidth - LevelPanelWidthNarrow - 28.dp, 320.dp)
            Row(Modifier.fillMaxWidth().height(sky), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LevelPanel(Modifier.width(LevelPanelWidthNarrow).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    SkyPlot(sats, selected?.key, onSelect, Modifier.size(sky).testTag(TAG_SKY_PLOT))
                }
            }
            Readout(selected)
            SignalBars(sats, selected?.key, onSelect, barHeight = 88.dp)
            BreakdownRow(stats, state.accuracyHistory)
            InfoPanel(state, stats, twoColumns = true)
        }
    }
}

/** 広い画面で、受信強度バーと詳細に確保する高さの目安。 */
private val WideBottomHeight = 176.dp

@Composable
private fun WideLayout(
    state: GnssUiState,
    stats: GnssStats,
    sats: List<MergedSatellite>,
    selected: MergedSatellite?,
    onSelect: (String?) -> Unit,
    maxWidth: Dp,
    maxHeight: Dp,
) {
    val topHeight = maxHeight - WideBottomHeight - 16.dp
    val fits = topHeight >= 230.dp
    val skySize = if (fits) minOf(topHeight, maxWidth - LevelPanelWidthWide - 250.dp) else 260.dp
    val body: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().then(if (fits) Modifier.height(topHeight) else Modifier.height(skySize)),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LevelPanel(Modifier.width(LevelPanelWidthWide).fillMaxHeight(), large = true)
            Box(Modifier.width(skySize).fillMaxHeight(), contentAlignment = Alignment.Center) {
                SkyPlot(sats, selected?.key, onSelect, Modifier.size(skySize).testTag(TAG_SKY_PLOT))
            }
            InfoPanel(state, stats, twoColumns = false, modifier = Modifier.weight(1f).fillMaxHeight())
        }
        // 下段: 左に詳細と受信強度バー、右に帯域の内訳と精度の履歴
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Readout(selected)
                SignalBars(sats, selected?.key, onSelect, barHeight = 90.dp)
            }
            Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BandChips(stats, Modifier.fillMaxWidth().weight(1f), maxLines = 3)
                ConstellationStats(stats, Modifier.fillMaxWidth(), large = true)
                AccuracySparkline(state.accuracyHistory, Modifier.fillMaxWidth().height(56.dp))
            }
        }
    }
    if (fits) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { body() }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { body() }
    }
}

// ---------------------------------------------------------------- 凡例パネル（GPS Level）

private fun levelColor(level: SignalLevel): Color = when (level) {
    SignalLevel.STRONG -> NavColors.LevelStrong
    SignalLevel.MEDIUM -> NavColors.LevelMedium
    SignalLevel.WEAK -> NavColors.LevelWeak
}

/** 光沢のある 3D の球。 */
private fun DrawScope.drawSphere(center: Offset, r: Float, base: Color, alpha: Float = 1f) {
    val brush = Brush.radialGradient(
        listOf(lighten(base, 0.7f), base, darken(base, 0.5f)),
        center = Offset(center.x - r * 0.35f, center.y - r * 0.4f),
        radius = r * 1.55f,
    )
    drawCircle(brush, r, center, alpha = alpha)
    drawCircle(Color.Black.copy(alpha = 0.35f), r, center, alpha = alpha, style = Stroke(0.8f))
    drawCircle(Color.White.copy(alpha = 0.55f), r * 0.17f, Offset(center.x - r * 0.38f, center.y - r * 0.42f), alpha = alpha)
}

/** 測位系の小さな丸い目印（頭文字つき）。 */
private fun DrawScope.drawBadge(measurer: TextMeasurer, letter: String, center: Offset, r: Float, color: Color, alpha: Float = 1f) {
    drawCircle(color, r, center, alpha = alpha)
    drawCircle(Color.Black.copy(alpha = 0.55f), r, center, alpha = alpha, style = Stroke(0.8f))
    val textColor = if (NavColors.isNight) Color(0xFF111114) else Color.White
    drawCentered(
        measurer, letter, center,
        TextStyle(color = textColor, fontSize = (r * 1.15f / density).sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun LevelRow(level: SignalLevel, label: String, large: Boolean) {
    Row(Modifier.height(if (large) 24.dp else 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(if (large) 20.dp else 15.dp)) {
            drawSphere(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f - 1f, levelColor(level))
        }
        // 電波の強さの棒（強い=3 本、中=2 本、弱い=1 本）
        Canvas(Modifier.size(width = if (large) 20.dp else 15.dp, height = if (large) 16.dp else 12.dp)) {
            val w = size.width / 5f
            for (i in 0 until 3) {
                val h = size.height * (0.45f + 0.275f * i)
                drawRect(
                    if (i < level.bars) NavColors.TextPrimary else NavColors.TextPrimary.copy(alpha = 0.18f),
                    Offset(i * 2f * w, size.height - h), androidx.compose.ui.geometry.Size(w * 1.4f, h),
                )
            }
        }
        Text(label, color = NavColors.TextSecondary, fontSize = if (large) 11.sp else 9.sp, lineHeight = if (large) 13.sp else 11.sp, maxLines = 1)
    }
}

/** 左の凡例パネル。上は nav-u の「GPS Level」（球の色 = 強さ）、下は測位系の目印（頭文字つきの丸）。 */
@Composable
private fun LevelPanel(modifier: Modifier = Modifier, large: Boolean = false) {
    val measurer = rememberTextMeasurer()
    Column(
        modifier
            .testTag(TAG_LEVEL_PANEL)
            .panelSurface(RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(if (large) 8.dp else 2.dp)) {
            Text("GPS Level", color = NavColors.TextPrimary, fontSize = if (large) 14.sp else 11.sp, lineHeight = if (large) 17.sp else 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            LevelRow(SignalLevel.STRONG, "強い", large)
            LevelRow(SignalLevel.MEDIUM, "中", large)
            LevelRow(SignalLevel.WEAK, "弱い", large)
        }
        Column(verticalArrangement = Arrangement.spacedBy(if (large) 5.dp else 2.dp)) {
            val items = listOf(
                Triple("G", "GPS", NavColors.Gps),
                Triple("J", "みちびき", NavColors.Qzss),
                Triple("R", "GLONASS", NavColors.Glonass),
                Triple("E", "Galileo", NavColors.Galileo),
                Triple("C", "BeiDou", NavColors.BeiDou),
            )
            for ((letter, name, color) in items) {
                Row(Modifier.height(if (large) 18.dp else 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Canvas(Modifier.size(if (large) 16.dp else 12.dp)) {
                        drawBadge(measurer, letter, Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f, color)
                    }
                    Text(name, color = NavColors.TextSecondary, fontSize = if (large) 11.sp else 9.sp, lineHeight = if (large) 13.sp else 11.sp, maxLines = 1)
                }
            }
            Text("うすい球＝未使用", color = NavColors.TextSecondary, fontSize = if (large) 10.sp else 8.sp, lineHeight = if (large) 12.sp else 10.sp, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- スカイプロット

private fun DrawScope.drawCentered(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    style: TextStyle,
) {
    val layout = measurer.measure(text, style, maxLines = 1, softWrap = false)
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

@Composable
internal fun SkyPlot(
    sats: List<MergedSatellite>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val currentSats by rememberUpdatedState(sats)
    val currentSelected by rememberUpdatedState(selectedKey)
    val currentOnSelect by rememberUpdatedState(onSelect)

    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                val margin = 18.dp.toPx()
                val layout = skyLayout(size.width.toFloat(), size.height.toFloat(), margin)
                val hits = currentSats.map { it.key to skyPosition(it.azimuthDegrees, it.elevationDegrees, layout) }
                val key = hitTest(hits, tap.x, tap.y, 20.dp.toPx())
                // もう一度タップすると選択を外す
                currentOnSelect(if (key == null || key == currentSelected) null else key)
            }
        },
    ) {
        val layout = skyLayout(size.width, size.height, 18.dp.toPx())
        val center = Offset(layout.cx, layout.cy)
        val r = layout.radius

        // 文字盤: 中心が暗く縁が紫の放射グラデーション、細い白い外周、仰角リング 2 本、白い十字線
        drawCircle(
            Brush.radialGradient(listOf(NavColors.DialCenter, NavColors.DialEdge), center = center, radius = r),
            r, center,
        )
        drawCircle(NavColors.DialRing, elevationRingRadius(30f, layout), center, style = Stroke(1.dp.toPx()))
        drawCircle(NavColors.DialRing, elevationRingRadius(60f, layout), center, style = Stroke(1.dp.toPx()))
        drawLine(NavColors.DialLine, Offset(center.x - r, center.y), Offset(center.x + r, center.y), 1.dp.toPx())
        drawLine(NavColors.DialLine, Offset(center.x, center.y - r), Offset(center.x, center.y + r), 1.dp.toPx())
        drawCircle(NavColors.DialLine, r, center, style = Stroke(1.5.dp.toPx()))

        // 方位は上の「N」だけ（nav-u と同じ。北が上）
        drawCentered(
            measurer, "N", Offset(center.x, center.y - r - 9.dp.toPx()),
            TextStyle(color = NavColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold),
        )

        val markerR = 11.dp.toPx()
        val svidStyle = TextStyle(color = NavColors.OnSphere, fontWeight = FontWeight.Bold)
        // 選択中の球は最後（最前面）に描く
        val ordered = sats.sortedBy { it.key == selectedKey }
        for (sat in ordered) {
            val p = skyPosition(sat.azimuthDegrees, sat.elevationDegrees, layout)
            val c = Offset(p.x, p.y)
            val alpha = if (sat.usedInFix) 1f else 0.5f
            if (sat.key == selectedKey) {
                drawCircle(
                    NavColors.TextPrimary, markerR + 7.dp.toPx(), c,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                )
            }
            drawSphere(c, markerR, levelColor(signalLevelFrom(sat.cn0DbHz)), alpha)
            drawCentered(
                measurer, sat.svid.toString(), c,
                svidStyle.copy(fontSize = if (sat.svid > 99) 8.sp else 10.sp),
            )
            drawBadge(
                measurer, sat.shortLabel, Offset(c.x + markerR * 0.85f, c.y - markerR * 0.85f),
                5.5.dp.toPx(), constellationColor(sat.constellationType), alpha,
            )
        }
    }
}

// ---------------------------------------------------------------- 詳細表示

@Composable
private fun Readout(sat: MergedSatellite?, modifier: Modifier = Modifier) {
    val text = if (sat == null) {
        "衛星の球・バーをタップで詳細"
    } else {
        val bands = sat.signals
            .sortedByDescending { it.carrierFrequencyHz ?: 0f }
            .joinToString(" ") { s ->
                if (s.bandLabel.isEmpty()) {
                    String.format(Locale.US, "%.1f", s.cn0DbHz)
                } else {
                    String.format(Locale.US, "%s:%.1f", s.bandLabel, s.cn0DbHz)
                }
            }
        String.format(
            Locale.US,
            "%s %d 方位%d° 仰角%d° %s %s",
            sat.constellationName, sat.svid, sat.azimuthDegrees.toInt(), sat.elevationDegrees.toInt(),
            bands, if (sat.usedInFix) "使用中" else "未使用",
        )
    }
    Box(
        modifier
            .fillMaxWidth()
            .panelSurface(RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            color = if (sat == null) NavColors.TextSecondary else NavColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
        )
    }
}

// ---------------------------------------------------------------- 受信強度バー

private val BarSlotMin = 26.dp

/**
 * 衛星ごとに 1 本。多周波の衛星は強い方の C/N0 を棒にし、詳細に周波数ごとの値を出す。
 * 衛星が多くて幅に収まらないときは横にスクロールする。
 */
@Composable
internal fun SignalBars(
    sats: List<MergedSatellite>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = 130.dp,
) {
    val measurer = rememberTextMeasurer()
    val currentSats by rememberUpdatedState(sats)
    val currentSelected by rememberUpdatedState(selectedKey)
    val currentOnSelect by rememberUpdatedState(onSelect)

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("受信強度 (C/N0)", color = NavColors.TextSecondary, fontSize = 10.sp)
            Text("dB-Hz ・ 1秒ごとに更新", color = NavColors.TextSecondary, fontSize = 10.sp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val available = maxWidth
            val axisWidth = 24.dp
            val slotDp = maxOf(BarSlotMin, (available - axisWidth) / maxOf(sats.size, 1))
            val contentWidth = if (sats.isEmpty()) available else axisWidth + slotDp * sats.size
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Canvas(
                    Modifier
                        .testTag(TAG_SIGNAL_BARS)
                        .width(maxOf(contentWidth, available))
                        .height(barHeight)
                        .pointerInput(Unit) {
                            detectTapGestures { tap ->
                                val slot = slotDp.toPx()
                                val axis = axisWidth.toPx()
                                val list = currentSats
                                val i = barIndexAt(tap.x - axis, slot, list.size)
                                val key = i?.let { list[it].key }
                                currentOnSelect(if (key == null || key == currentSelected) null else key)
                            }
                        },
                ) {
                    val axis = axisWidth.toPx()
                    val slot = slotDp.toPx()
                    val base = size.height - 30.dp.toPx()
                    val k = (base - 14.dp.toPx()) / MAX_CN0_DB_HZ
                    val tiny = TextStyle(color = NavColors.TextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)

                    for (v in listOf(10, 20, 30, 40)) {
                        val y = base - k * v
                        drawLine(NavColors.GridLine, Offset(axis - 2.dp.toPx(), y), Offset(size.width, y), 1.dp.toPx())
                        val t = measurer.measure(v.toString(), tiny)
                        drawText(t, topLeft = Offset(axis - 5.dp.toPx() - t.size.width, y - t.size.height / 2f))
                    }
                    drawLine(NavColors.TextSecondary, Offset(axis - 2.dp.toPx(), base), Offset(size.width, base), 1.dp.toPx())

                    val barWidth = minOf(slot * 0.6f, 14.dp.toPx())
                    sats.forEachIndexed { i, sat ->
                        val cx = axis + slot * i + slot / 2f
                        val cn0 = sat.cn0DbHz.takeIf { it.isFinite() } ?: 0f
                        val h = maxOf(2.dp.toPx(), (cn0 * k).coerceIn(0f, MAX_CN0_DB_HZ * k))
                        if (sat.key == selectedKey) {
                            drawRect(NavColors.TextPrimary.copy(alpha = 0.12f), Offset(cx - slot / 2f, 2.dp.toPx()), androidx.compose.ui.geometry.Size(slot, size.height - 4.dp.toPx()))
                        }
                        val fillColor = levelColor(signalLevelFrom(cn0))
                        val a = if (sat.usedInFix) 1f else 0.5f
                        // 光沢のある棒: 上が明るく下が濃いグラデーションと、左側のハイライト（未使用は薄く）
                        drawRect(
                            Brush.verticalGradient(
                                listOf(lighten(fillColor, 0.35f), fillColor),
                                startY = base - h, endY = base,
                            ),
                            Offset(cx - barWidth / 2f, base - h), androidx.compose.ui.geometry.Size(barWidth, h),
                            alpha = a,
                        )
                        drawRect(
                            Color.White.copy(alpha = 0.28f),
                            Offset(cx - barWidth / 2f, base - h), androidx.compose.ui.geometry.Size(barWidth * 0.3f, h),
                            alpha = a,
                        )
                        drawCentered(
                            measurer, cn0.toInt().toString(), Offset(cx, base - h - 7.dp.toPx()),
                            TextStyle(color = NavColors.TextPrimary, fontSize = 8.sp, fontFamily = FontFamily.Monospace),
                        )
                        drawCentered(
                            measurer, sat.shortLabel, Offset(cx, base + 8.dp.toPx()),
                            TextStyle(color = constellationColor(sat.constellationType), fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        )
                        drawCentered(
                            measurer, sat.svid.toString(), Offset(cx, base + 20.dp.toPx()),
                            TextStyle(color = NavColors.TextPrimary, fontSize = 8.sp, fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
        }
    }
}

/** 測位系ごとの「使用/捕捉 平均C/N0」。例: 5/6 32。狭い画面では C/N0 を整数にして幅を節約。 */
internal fun constellationStatText(stat: io.github.mashishiii.gnssmonitor.data.ConstellationStat?, large: Boolean): String {
    val used = stat?.used ?: 0
    val cap = stat?.captured ?: 0
    val avg = stat?.avgCn0DbHz?.let { if (large) String.format(Locale.US, "%.1f", it) else String.format(Locale.US, "%.0f", it) } ?: "--"
    return "$used/$cap $avg"
}

/** 凡例と同じ頭文字の丸 + 測位系ごとの貢献（使用/捕捉 と使用中の衛星の平均 C/N0）。 */
@Composable
private fun ConstellationStats(stats: GnssStats, modifier: Modifier = Modifier, large: Boolean = false) {
    val measurer = rememberTextMeasurer()
    val items = listOf(
        Triple("G", GnssStatus.CONSTELLATION_GPS, NavColors.Gps),
        Triple("J", GnssStatus.CONSTELLATION_QZSS, NavColors.Qzss),
        Triple("R", GnssStatus.CONSTELLATION_GLONASS, NavColors.Glonass),
        Triple("E", GnssStatus.CONSTELLATION_GALILEO, NavColors.Galileo),
        Triple("C", GnssStatus.CONSTELLATION_BEIDOU, NavColors.BeiDou),
    )
    Row(
        modifier.testTag(TAG_CONSTELLATION_STATS),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((letter, type, color) in items) {
            val stat = stats.constellations.firstOrNull { it.constellationType == type }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Canvas(Modifier.size(if (large) 14.dp else 12.dp)) {
                    drawBadge(measurer, letter, Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f, color)
                }
                Text(
                    constellationStatText(stat, large),
                    color = if (stat != null && stat.used > 0) NavColors.TextPrimary else NavColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (large) 10.sp else 9.sp,
                    lineHeight = if (large) 12.sp else 11.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 帯域の内訳と精度の履歴

private fun bandChipTexts(stats: GnssStats): List<Pair<String, Boolean>> {
    if (!stats.hasFrequencyInfo) return listOf("帯域 ---" to false)
    val chips = stats.bands.map { b ->
        val name = if (b.family == BandFamily.UNKNOWN) "?" else b.family.label
        "$name ${b.used}/${b.captured}" to false
    }
    return chips + ("デュアル ${stats.dualFreqSatellites}" to stats.dualFreq)
}

/** 帯域ごとの「使用/捕捉」と、デュアル周波数の点灯。点灯中のチップはえんじ色。 */
@Composable
private fun BandChips(stats: GnssStats, modifier: Modifier = Modifier, maxLines: Int = 2) {
    FlowRow(
        modifier.testTag(TAG_BAND_CHIPS),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        maxLines = maxLines,
    ) {
        for ((text, lit) in bandChipTexts(stats)) {
            Text(
                text,
                color = if (lit) NavColors.OnButtonOn else NavColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .then(
                        if (lit) {
                            Modifier.background(
                                Brush.verticalGradient(listOf(NavColors.ButtonOnTop, NavColors.ButtonOnBottom)),
                                RoundedCornerShape(6.dp),
                            )
                        } else {
                            Modifier.panelSurface(RoundedCornerShape(6.dp))
                        },
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** 直近 60 秒の水平精度 (68%) の細い折れ線。値の無い秒は線を切る。 */
@Composable
private fun AccuracySparkline(history: List<Float?>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val maxValue = sparklineMax(history)
    val line = NavColors.TextPrimary
    val grid = NavColors.GridLine
    Canvas(
        modifier
            .testTag(TAG_SPARKLINE)
            .panelSurface(RoundedCornerShape(8.dp)),
    ) {
        val padX = 6.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = 4.dp.toPx()
        val w = size.width - padX * 2
        val h = size.height - top - bottom
        if (w <= 0f || h <= 0f) return@Canvas
        val tiny = TextStyle(color = NavColors.TextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        drawLine(grid, Offset(padX, top + h), Offset(padX + w, top + h), 1.dp.toPx())
        drawLine(grid, Offset(padX, top), Offset(padX + w, top), 1.dp.toPx())
        drawText(measurer.measure("精度(68%) 60秒", tiny), topLeft = Offset(padX, 0f))
        val maxText = measurer.measure(String.format(Locale.US, "%.0fm", maxValue), tiny)
        drawText(maxText, topLeft = Offset(size.width - padX - maxText.size.width, 0f))
        for (seg in sparklineSegments(history, w, h, maxValue)) {
            if (seg.size == 1) {
                drawCircle(line, 1.5.dp.toPx(), Offset(padX + seg[0].x, top + seg[0].y))
            } else {
                val path = Path().apply {
                    seg.forEachIndexed { i, pt ->
                        if (i == 0) moveTo(padX + pt.x, top + pt.y) else lineTo(padX + pt.x, top + pt.y)
                    }
                }
                drawPath(path, line, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/** 縦長画面の下段: 左に帯域チップ、右に精度の履歴、その下に測位系ごとの貢献を 1 行。 */
@Composable
private fun BreakdownRow(stats: GnssStats, history: List<Float?>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BandChips(stats, Modifier.weight(1f).fillMaxHeight())
            AccuracySparkline(history, Modifier.width(132.dp).fillMaxHeight())
        }
        ConstellationStats(stats, Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------- 情報パネル

/**
 * 情報パネルの行（ラベルと値）。nav-u の並び（日付・時刻・緯度・経度・速度・高度・測位）に続けて、
 * このアプリの項目: 衛星数、使用衛星の平均 C/N0、デュアル周波数、水平精度 (68%) と 95% の目安、垂直精度、速度精度。
 * 値が取れないときは nav-u のように「---」。先頭の 7 行と後ろの 7 行で、縦長の 2 列表示の左右になる。
 */
internal fun statusRows(state: GnssUiState, stats: GnssStats = computeGnssStats(state.satellites)): List<Pair<String, String>> {
    val hasFix = state.fixState != FixState.NO_FIX
    val p = state.position.takeIf { hasFix }
    val acc68 = p?.accuracyMeters
    return listOf(
        "日付" to (p?.timeMillis?.let { formatDateNav(it) } ?: "---"),
        "時刻" to (p?.timeMillis?.let { formatTimeNav(it) } ?: "---"),
        "緯度" to (p?.let { formatDmsNav(it.latitude, 'N', 'S') } ?: "---"),
        "経度" to (p?.let { formatDmsNav(it.longitude, 'E', 'W') } ?: "---"),
        "速度" to formatSpeedKmh(p?.speedMps),
        "高度" to (p?.altitudeMeters?.let { String.format(Locale.US, "%.0f m", it) } ?: "---"),
        "測位" to state.fixState.label,
        "衛星" to "${state.usedCount} / ${state.capturedCount}",
        "平均C/N0" to (stats.avgCn0DbHz?.let { String.format(Locale.US, "%.1f dB-Hz", it) } ?: "---"),
        "デュアル周波数" to stats.dualFreqLabel(),
        "水平精度(68%)" to (acc68?.let { String.format(Locale.US, "±%.1f m", it) } ?: "---"),
        "95%目安×" + ACCURACY_95_FACTOR.toString().removeSuffix("0") to
            (horizontalAccuracy95(acc68)?.let { String.format(Locale.US, "±%.1f m", it) } ?: "---"),
        "垂直精度" to formatVerticalAccuracy(p?.verticalAccuracyMeters),
        "速度精度" to formatSpeedAccuracyKmh(p?.speedAccuracyMps),
    )
}

/** 右の情報パネル。大きな白い等幅の文字（液晶風）で、値は右寄せ。縦長では 2 列にして高さを抑える。 */
@Composable
private fun InfoPanel(state: GnssUiState, stats: GnssStats, twoColumns: Boolean, modifier: Modifier = Modifier) {
    val rows = statusRows(state, stats)
    Box(
        modifier
            .testTag(TAG_INFO_PANEL)
            .fillMaxWidth()
            .panelSurface(RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (twoColumns) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    rows.take(7).forEach { (l, v) -> InfoRow(l, v, valueSize = 12.sp, labelSize = 10.sp) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    rows.drop(7).forEach { (l, v) -> InfoRow(l, v, valueSize = 12.sp, labelSize = 10.sp) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                rows.forEach { (l, v) -> InfoRow(l, v, valueSize = 15.sp, labelSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueSize: androidx.compose.ui.unit.TextUnit, labelSize: androidx.compose.ui.unit.TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, color = NavColors.TextPrimary, fontSize = labelSize, lineHeight = labelSize * 1.25f,
            maxLines = 1, softWrap = false, modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            value,
            color = NavColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = valueSize,
            lineHeight = valueSize * 1.2f,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f),
        )
    }
}
