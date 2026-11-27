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
import io.github.mashishiii.gnssmonitor.data.MarkerShape
import io.github.mashishiii.gnssmonitor.data.MergedSatellite
import io.github.mashishiii.gnssmonitor.data.PositionInfo
import io.github.mashishiii.gnssmonitor.data.SignalClass
import io.github.mashishiii.gnssmonitor.data.formatDateJa
import io.github.mashishiii.gnssmonitor.data.formatDms
import io.github.mashishiii.gnssmonitor.data.formatTimeJa
import io.github.mashishiii.gnssmonitor.data.mergeSatellites
import io.github.mashishiii.gnssmonitor.ui.theme.NavColors
import io.github.mashishiii.gnssmonitor.ui.theme.constellationColor
import java.util.Locale

/** この幅（dp）以上なら、スカイプロットと受信強度を横並びにする。折りたたみ展開時や横向き用。 */
private val WideThreshold = 600.dp

internal const val TAG_SKY_PLOT = "skyPlot"
internal const val TAG_SIGNAL_BARS = "signalBars"

/** 測位状況画面（既定の画面）。 */
@Composable
fun StatusScreen(
    state: GnssUiState,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sats = remember(state.satellites) { mergeSatellites(state.satellites) }
    val selected = sats.firstOrNull { it.key == selectedKey }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FixStrip(state, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = true)) {
            if (maxWidth >= WideThreshold) {
                WideLayout(state, sats, selected, onSelect, maxWidth, maxHeight)
            } else {
                NarrowLayout(state, sats, selected, onSelect, maxWidth, maxHeight)
            }
        }
    }
}

/**
 * 縦長画面でスカイプロット以外（凡例・詳細・強度バー・情報ボックス）に必要な高さの目安。
 * 残りが [NarrowMinSky] 以上あれば、スクロールなしで全部が見えるようにスカイプロットを縮める。
 */
private val NarrowReservedHeight = 370.dp
private val NarrowMinSky = 200.dp

/** スクロールなしで収める配置にできるか。 */
internal fun narrowFitsWithoutScroll(maxHeight: Dp): Boolean = maxHeight - NarrowReservedHeight >= NarrowMinSky

@Composable
private fun NarrowLayout(
    state: GnssUiState,
    sats: List<MergedSatellite>,
    selected: MergedSatellite?,
    onSelect: (String?) -> Unit,
    maxWidth: Dp,
    maxHeight: Dp,
) {
    if (narrowFitsWithoutScroll(maxHeight)) {
        // スカイプロットが残りの高さを使う（下のボタンと重ならず、凡例・高度まで一画面に収まる）
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                SkyPlot(
                    sats, selected?.key, onSelect,
                    Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).testTag(TAG_SKY_PLOT),
                )
            }
            SkyLegend()
            Readout(selected, Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            SignalBars(sats, selected?.key, onSelect, barHeight = 112.dp)
            InfoRows(state, Modifier.padding(top = 4.dp))
        }
    } else {
        // 高さが足りない（横向きのスマホなど）ときはスクロール
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SkyPlot(sats, selected?.key, onSelect, Modifier.size(minOf(maxWidth, 320.dp)).testTag(TAG_SKY_PLOT))
            SkyLegend()
            Readout(selected, Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            SignalBars(sats, selected?.key, onSelect, barHeight = 112.dp)
            InfoRows(state, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun WideLayout(
    state: GnssUiState,
    sats: List<MergedSatellite>,
    selected: MergedSatellite?,
    onSelect: (String?) -> Unit,
    maxWidth: Dp,
    maxHeight: Dp,
) {
    // 横向きのスマホのように高さが足りないときは、スカイプロットを高さに合わせて小さくする
    val skySize = minOf(maxWidth * 0.46f, maxOf(220.dp, maxHeight - 8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier.width(skySize).verticalScroll(rememberScrollState()),
        ) {
            SkyPlot(sats, selected?.key, onSelect, Modifier.size(skySize).testTag(TAG_SKY_PLOT))
            SkyLegend()
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Readout(selected, Modifier.padding(end = 8.dp, bottom = 6.dp))
            SignalBars(sats, selected?.key, onSelect, Modifier.padding(end = 8.dp))
            InfoRows(state, Modifier.padding(top = 8.dp, end = 8.dp))
        }
    }
}

// ---------------------------------------------------------------- 上部の帯

@Composable
private fun FixStrip(state: GnssUiState, modifier: Modifier = Modifier) {
    val fix = state.fixState
    val hasFix = fix != FixState.NO_FIX
    val accuracy = state.position?.accuracyMeters?.takeIf { hasFix }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StripCell(
            "測位状態",
            if (hasFix) "${fix.label} 測位" else fix.label,
            if (hasFix) NavColors.UsedText else NavColors.Warning,
            Modifier.weight(1f),
        )
        StripCell("使用 / 捕捉", "${state.usedCount} / ${state.capturedCount}", NavColors.TextPrimary, Modifier.weight(1f))
        StripCell(
            "水平精度",
            accuracy?.let { String.format(Locale.US, "±%.1f m", it) } ?: "---",
            NavColors.TextPrimary,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun StripCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(NavColors.Panel, RoundedCornerShape(4.dp))
            .border(1.dp, NavColors.PanelBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, color = NavColors.TextSecondary, fontSize = 10.sp, maxLines = 1)
        Text(value, color = valueColor, fontFamily = FontFamily.Monospace, fontSize = 15.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------- スカイプロット

private fun markerFill(sat: MergedSatellite): Color = when (sat.signalClass) {
    SignalClass.USED -> NavColors.Used
    SignalClass.RECEIVED -> NavColors.Received
    SignalClass.WEAK -> NavColors.Weak
}

private fun DrawScope.drawMarker(
    shape: MarkerShape,
    center: Offset,
    r: Float,
    fill: Color,
    stroke: Color,
    strokeWidth: Float,
) {
    val vertices = markerVertices(shape, r)
    if (vertices == null) {
        drawCircle(fill, r, center)
        drawCircle(stroke, r, center, style = Stroke(strokeWidth))
    } else {
        val path = Path().apply {
            vertices.forEachIndexed { i, v ->
                if (i == 0) moveTo(center.x + v.x, center.y + v.y) else lineTo(center.x + v.x, center.y + v.y)
            }
            close()
        }
        drawPath(path, fill)
        drawPath(path, stroke, style = Stroke(strokeWidth))
    }
}

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
        val labelStyle = TextStyle(color = NavColors.TextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)

        drawCircle(NavColors.Panel, r, center)
        drawCircle(Color(0xFF3B5A78), r, center, style = Stroke(2.dp.toPx()))
        drawCircle(NavColors.GridLine, elevationRingRadius(30f, layout), center, style = Stroke(1.dp.toPx()))
        drawCircle(NavColors.GridLine, elevationRingRadius(60f, layout), center, style = Stroke(1.dp.toPx()))
        drawLine(NavColors.GridLine, Offset(center.x - r, center.y), Offset(center.x + r, center.y), 1.dp.toPx())
        drawLine(NavColors.GridLine, Offset(center.x, center.y - r), Offset(center.x, center.y + r), 1.dp.toPx())
        drawText(
            measurer.measure("60°", labelStyle),
            topLeft = Offset(center.x + 3.dp.toPx(), center.y - elevationRingRadius(60f, layout) - 11.dp.toPx()),
        )
        drawText(
            measurer.measure("30°", labelStyle),
            topLeft = Offset(center.x + 3.dp.toPx(), center.y - elevationRingRadius(30f, layout) - 11.dp.toPx()),
        )

        // 方位ラベル（N が上）
        val dirStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val off = r + 10.dp.toPx()
        drawCentered(measurer, "N", Offset(center.x, center.y - off), dirStyle.copy(color = NavColors.TextPrimary))
        drawCentered(measurer, "E", Offset(center.x + off, center.y), dirStyle.copy(color = NavColors.TextSecondary))
        drawCentered(measurer, "S", Offset(center.x, center.y + off), dirStyle.copy(color = NavColors.TextSecondary))
        drawCentered(measurer, "W", Offset(center.x - off, center.y), dirStyle.copy(color = NavColors.TextSecondary))

        val markerR = 10.dp.toPx()
        val svidStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        // 選択中のマーカーは最後（最前面）に描く
        val ordered = sats.sortedBy { it.key == selectedKey }
        for (sat in ordered) {
            val p = skyPosition(sat.azimuthDegrees, sat.elevationDegrees, layout)
            val c = Offset(p.x, p.y)
            if (sat.key == selectedKey) {
                drawCircle(
                    Color.White, markerR + 8.dp.toPx(), c,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                )
            }
            drawMarker(sat.markerShape, c, markerR, markerFill(sat), constellationColor(sat.constellationType), 2.dp.toPx())
            drawCentered(
                measurer, sat.svid.toString(),
                Offset(c.x, c.y + if (sat.markerShape == MarkerShape.TRIANGLE) 2.dp.toPx() else 0f),
                svidStyle.copy(fontSize = if (sat.svid > 99) 8.sp else 9.5.sp),
            )
        }
    }
}

@Composable
private fun LegendShape(shape: MarkerShape, color: Color) {
    Canvas(Modifier.size(16.dp)) {
        translate(size.width / 2f, size.height / 2f) {
            drawMarker(shape, Offset.Zero, 5.dp.toPx(), Color.Transparent, color, 1.6.dp.toPx())
        }
    }
}

@Composable
private fun SkyLegend() {
    Column(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val shapes = listOf(
                Triple("GPS", MarkerShape.CIRCLE, NavColors.Gps),
                Triple("みちびき", MarkerShape.DIAMOND, NavColors.Qzss),
                Triple("GLONASS", MarkerShape.SQUARE, NavColors.Glonass),
                Triple("Galileo", MarkerShape.TRIANGLE, NavColors.Galileo),
                Triple("BeiDou", MarkerShape.HEXAGON, NavColors.BeiDou),
            )
            for ((name, shape, color) in shapes) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendShape(shape, color)
                    Text(name, color = NavColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(start = 3.dp))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((color, label) in listOf(
                NavColors.Used to "測位に使用中",
                NavColors.Received to "受信中(未使用)",
                NavColors.Weak to "弱い/未受信",
            )) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(color, RoundedCornerShape(2.dp)))
                    Text(label, color = NavColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 詳細表示

@Composable
private fun Readout(sat: MergedSatellite?, modifier: Modifier = Modifier) {
    val text = if (sat == null) {
        "衛星マーカー・バーをタップで詳細"
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
            .background(NavColors.Panel, RoundedCornerShape(4.dp))
            .border(1.dp, NavColors.PanelBorder, RoundedCornerShape(4.dp))
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
                            drawRect(Color.White.copy(alpha = 0.10f), Offset(cx - slot / 2f, 2.dp.toPx()), androidx.compose.ui.geometry.Size(slot, size.height - 4.dp.toPx()))
                        }
                        drawRect(markerFill(sat), Offset(cx - barWidth / 2f, base - h), androidx.compose.ui.geometry.Size(barWidth, h))
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

// ---------------------------------------------------------------- ラベル/値のボックス

@Composable
private fun InfoRows(state: GnssUiState, modifier: Modifier = Modifier) {
    val rows = infoRows(state.position, state.fixState != FixState.NO_FIX)
    Column(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for ((label, value) in rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = NavColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.width(58.dp))
                Text(
                    value,
                    color = NavColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .background(NavColors.ValueBox)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** 緯度・経度・高度・年月日・現在時刻。測位できていないときは nav-u のように "---"。 */
internal fun infoRows(position: PositionInfo?, hasFix: Boolean): List<Pair<String, String>> {
    val p = position.takeIf { hasFix }
    return listOf(
        "緯度" to (p?.let { formatDms(it.latitude, 'N', 'S') } ?: "---"),
        "経度" to (p?.let { formatDms(it.longitude, 'E', 'W') } ?: "---"),
        "高度" to (p?.altitudeMeters?.let { String.format(Locale.US, "%.0f m", it) } ?: "---"),
        "年月日" to (p?.timeMillis?.let { formatDateJa(it) } ?: "---"),
        "現在時刻" to (p?.timeMillis?.let { formatTimeJa(it) } ?: "---"),
    )
}
