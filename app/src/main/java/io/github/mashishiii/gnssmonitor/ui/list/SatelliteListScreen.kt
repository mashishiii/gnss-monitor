package io.github.mashishiii.gnssmonitor.ui.list

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mashishiii.gnssmonitor.data.CrashLog
import io.github.mashishiii.gnssmonitor.data.FixState
import io.github.mashishiii.gnssmonitor.data.GnssUiState
import io.github.mashishiii.gnssmonitor.data.MAX_CN0_DB_HZ
import io.github.mashishiii.gnssmonitor.data.PositionInfo
import io.github.mashishiii.gnssmonitor.data.SatelliteInfo
import io.github.mashishiii.gnssmonitor.data.BandFamily
import io.github.mashishiii.gnssmonitor.data.GnssStats
import io.github.mashishiii.gnssmonitor.data.computeGnssStats
import io.github.mashishiii.gnssmonitor.data.dualFreqLabel
import io.github.mashishiii.gnssmonitor.data.horizontalAccuracy95
import io.github.mashishiii.gnssmonitor.data.constellationShortLabel
import io.github.mashishiii.gnssmonitor.data.uniqueSatelliteKeys
import io.github.mashishiii.gnssmonitor.ui.theme.NavColors
import io.github.mashishiii.gnssmonitor.ui.theme.glossy
import io.github.mashishiii.gnssmonitor.ui.theme.insetBox
import io.github.mashishiii.gnssmonitor.ui.theme.lighten
import io.github.mashishiii.gnssmonitor.ui.theme.panelSurface
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.alpha
import io.github.mashishiii.gnssmonitor.data.SignalLevel
import io.github.mashishiii.gnssmonitor.data.signalLevelFrom
import java.util.Locale
import kotlin.math.abs

@Composable
fun SatelliteListScreen(state: GnssUiState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val stats = remember(state.satellites) { computeGnssStats(state.satellites) }
        StatusHeader(state, stats)
        if (!state.gpsEnabled) {
            Text(
                "端末の位置情報（GPS）がオフです。設定でオンにしてください",
                color = NavColors.Warning,
                fontSize = 13.sp,
            )
        }
        SatelliteList(state.satellites, Modifier.weight(1f), stats)
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .panelSurface(RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) { content() }
}

@Composable
private fun StatusHeader(state: GnssUiState, stats: GnssStats) {
    val fix = state.fixState
    val fixColor = when (fix) {
        FixState.NO_FIX -> NavColors.Warning
        else -> NavColors.TextPrimary
    }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            HeaderRow("測位状態") {
                Text(fix.label, color = fixColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            HeaderRow("衛星") {
                Text(
                    "使用 ${state.usedCount} / 捕捉 ${state.capturedCount}",
                    color = NavColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                )
            }
            HeaderRow("緯度") { Mono(formatLatitude(state.position)) }
            HeaderRow("経度") { Mono(formatLongitude(state.position)) }
            val p = state.position?.takeIf { fix != FixState.NO_FIX }
            HeaderRow("精度") {
                Mono(formatAccuracy(state.position))
                Text(
                    "  95%目安 " + (horizontalAccuracy95(p?.accuracyMeters)?.let { String.format(Locale.US, "±%.1f m", it) } ?: "--"),
                    color = NavColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                )
            }
            HeaderRow("平均C/N0") {
                Mono(stats.avgCn0DbHz?.let { String.format(Locale.US, "%.1f dB-Hz", it) } ?: "---", 13.sp)
                Text("  デュアル " + stats.dualFreqLabel(), color = NavColors.TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
            HeaderRow("帯域") { Mono(bandSummary(stats), 12.sp) }
            HeaderRow("測位系") { Mono(constellationSummary(stats), 12.sp) }
        }
    }
}

@Composable
private fun HeaderRow(label: String, value: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = NavColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.width(72.dp), maxLines = 1)
        value()
    }
}

@Composable
private fun Mono(text: String, size: androidx.compose.ui.unit.TextUnit = 15.sp) {
    Text(
        text,
        color = NavColors.TextPrimary,
        fontFamily = FontFamily.Monospace,
        fontSize = size,
    )
}

private val ColName = 76.dp
private val ColSvid = 40.dp
private val ColBand = 48.dp
private val ColAngle = 44.dp
private val ColUsed = 36.dp

@Composable
internal fun SatelliteList(satellites: List<SatelliteInfo>, modifier: Modifier = Modifier, stats: GnssStats = remember(satellites) { computeGnssStats(satellites) }) {
    Panel(modifier.fillMaxSize()) {
        Column {
            ListHeader()
            if (satellites.isEmpty()) {
                Text(
                    "衛星を探しています…（屋外で空が開けた場所をお試しください）",
                    color = NavColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                // 測位系 + SVID だけでは重複する（多周波端末で同じ衛星が複数行届く）ため、一意なキーを作る
                val keys = remember(satellites) { uniqueSatelliteKeys(satellites) }
                LazyColumn {
                    itemsIndexed(satellites, key = { index, _ -> keys.getOrElse(index) { "idx-$index" } }) { index, sat ->
                        SatelliteRow(sat, zebra = index % 2 == 1, dual = isDualRow(sat, stats))
                    }
                }
            }
        }
    }
}

@Composable
private fun ListHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .glossy(RoundedCornerShape(4.dp), NavColors.TitleTop, NavColors.TitleBottom, rim = false)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("測位系", Modifier.width(ColName))
        HeaderCell("SVID", Modifier.width(ColSvid))
        HeaderCell("帯域", Modifier.width(ColBand))
        HeaderCell("C/N0 (dB-Hz)", Modifier.weight(1f))
        HeaderCell("方位", Modifier.width(ColAngle), TextAlign.End)
        HeaderCell("仰角", Modifier.width(ColAngle), TextAlign.End)
        HeaderCell("使用", Modifier.width(ColUsed), TextAlign.Center)
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, color = NavColors.OnTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = align, modifier = modifier, maxLines = 1)
}

@Composable
private fun SatelliteRow(sat: SatelliteInfo, zebra: Boolean = false, dual: Boolean = false) {
    val level = signalLevelFrom(sat.cn0DbHz)
    val color = levelColor(level)
    Row(
        Modifier
            .fillMaxWidth()
            // 1 行おきに薄く色を付けて、行を目で追いやすくする（nav-u のリスト風）
            .background(if (zebra) NavColors.GridLine.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(sat.constellationName, color = NavColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.width(ColName), maxLines = 1)
        Text(sat.svid.toString(), color = NavColors.TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.width(ColSvid))
        // 帯域の表示名（E5a / B2a など）。デュアル周波数に数えている衛星の L1 / L5 系の行には小さな印 ◆
        Text(
            sat.bandLabel + if (dual) " ◆" else "",
            color = if (dual) NavColors.TextPrimary else NavColors.TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.width(ColBand),
            maxLines = 1,
            softWrap = false,
        )
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            SignalBar(sat.cn0DbHz, color, Modifier.weight(1f).alpha(if (sat.usedInFix) 1f else 0.55f))
            Text(
                String.format(Locale.US, "%4.1f", sat.cn0DbHz.takeIf { it.isFinite() } ?: 0f),
                color = NavColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp, end = 4.dp),
            )
        }
        Text("${sat.azimuthDegrees.toInt()}°", color = NavColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(ColAngle))
        Text("${sat.elevationDegrees.toInt()}°", color = NavColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(ColAngle))
        Text(
            if (sat.usedInFix) "●" else "－",
            color = if (sat.usedInFix) NavColors.UsedText else NavColors.WeakText,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(ColUsed),
        )
    }
}

@Composable
private fun SignalBar(cn0DbHz: Float, color: Color, modifier: Modifier = Modifier) {
    val raw = cn0DbHz / MAX_CN0_DB_HZ
    val fraction = if (raw.isFinite()) raw.coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .height(10.dp)
            .clip(RoundedCornerShape(3.dp))
            .insetBox(),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(Brush.verticalGradient(listOf(lighten(color, 0.4f), color))),
        )
    }
}

private fun levelColor(level: SignalLevel): Color = when (level) {
    SignalLevel.STRONG -> NavColors.LevelStrong
    SignalLevel.MEDIUM -> NavColors.LevelMedium
    SignalLevel.WEAK -> NavColors.LevelWeak
}

internal fun formatLatitude(position: PositionInfo?): String =
    position?.let {
        String.format(Locale.US, "%s %.6f°", if (it.latitude >= 0) "N" else "S", abs(it.latitude))
    } ?: "--"

internal fun formatLongitude(position: PositionInfo?): String =
    position?.let {
        String.format(Locale.US, "%s %.6f°", if (it.longitude >= 0) "E" else "W", abs(it.longitude))
    } ?: "--"

internal fun formatAccuracy(position: PositionInfo?): String =
    position?.accuracyMeters?.let { String.format(Locale.US, "±%.1f m", it) } ?: "--"

/** デュアル周波数に数えた衛星の、使用中の L1 / L5 族の行か。 */
internal fun isDualRow(sat: SatelliteInfo, stats: GnssStats): Boolean =
    sat.usedInFix &&
        (sat.bandFamily == BandFamily.L1 || sat.bandFamily == BandFamily.L5) &&
        "${sat.constellationType}-${sat.svid}" in stats.dualFreqKeys

/** 例: "L1 8/12  L5 5/7"。周波数が取れない端末では "---"。 */
internal fun bandSummary(stats: GnssStats): String =
    if (!stats.hasFrequencyInfo) {
        "---"
    } else {
        stats.bands.joinToString("  ") { b ->
            (if (b.family == BandFamily.UNKNOWN) "?" else b.family.label) + " ${b.used}/${b.captured}"
        }
    }

/** 例: "G 5/6 32.4  E 4/5 33.0"（1 組の中は改行しない空白で結ぶ。頭文字 使用/捕捉 平均C/N0。頭文字は凡例と同じ）。 */
internal fun constellationSummary(stats: GnssStats): String =
    if (stats.constellations.isEmpty()) {
        "---"
    } else {
        stats.constellations.joinToString("  ") { c ->
            constellationShortLabel(c.constellationType) + "\u00A0${c.used}/${c.captured}\u00A0" +
                (c.avgCn0DbHz?.let { String.format(Locale.US, "%.1f", it) } ?: "--")
        }
    }
