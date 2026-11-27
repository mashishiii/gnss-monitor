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
import io.github.mashishiii.gnssmonitor.data.SignalClass
import io.github.mashishiii.gnssmonitor.data.uniqueSatelliteKeys
import io.github.mashishiii.gnssmonitor.ui.theme.NavColors
import io.github.mashishiii.gnssmonitor.ui.theme.glossy
import io.github.mashishiii.gnssmonitor.ui.theme.insetBox
import io.github.mashishiii.gnssmonitor.ui.theme.lighten
import io.github.mashishiii.gnssmonitor.ui.theme.panelSurface
import androidx.compose.ui.graphics.Brush
import java.util.Locale
import kotlin.math.abs

@Composable
fun SatelliteListScreen(state: GnssUiState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusHeader(state)
        if (!state.gpsEnabled) {
            Text(
                "端末の位置情報（GPS）がオフです。設定でオンにしてください",
                color = NavColors.Warning,
                fontSize = 13.sp,
            )
        }
        SatelliteList(state.satellites, Modifier.weight(1f))
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .panelSurface(RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) { content() }
}

@Composable
private fun StatusHeader(state: GnssUiState) {
    val fix = state.fixState
    val fixColor = when (fix) {
        FixState.NO_FIX -> NavColors.Warning
        else -> NavColors.UsedText
    }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            HeaderRow("精度") { Mono(formatAccuracy(state.position)) }
        }
    }
}

@Composable
private fun HeaderRow(label: String, value: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = NavColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.width(72.dp))
        value()
    }
}

@Composable
private fun Mono(text: String) {
    Text(
        text,
        color = NavColors.TextPrimary,
        fontFamily = FontFamily.Monospace,
        fontSize = 15.sp,
        modifier = Modifier.insetBox().padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private val ColName = 76.dp
private val ColSvid = 40.dp
private val ColBand = 32.dp
private val ColAngle = 44.dp
private val ColUsed = 36.dp

@Composable
internal fun SatelliteList(satellites: List<SatelliteInfo>, modifier: Modifier = Modifier) {
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
                        SatelliteRow(sat, zebra = index % 2 == 1)
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
private fun SatelliteRow(sat: SatelliteInfo, zebra: Boolean = false) {
    val color = signalColor(sat.signalClass)
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
        Text(sat.bandLabel, color = NavColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.width(ColBand), maxLines = 1)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            SignalBar(sat.cn0DbHz, color, Modifier.weight(1f))
            Text(
                String.format(Locale.US, "%4.1f", sat.cn0DbHz.takeIf { it.isFinite() } ?: 0f),
                color = signalTextColor(sat.signalClass),
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

private fun signalColor(signalClass: SignalClass): Color = when (signalClass) {
    SignalClass.USED -> NavColors.Used
    SignalClass.RECEIVED -> NavColors.Received
    SignalClass.WEAK -> NavColors.Weak
}

private fun signalTextColor(signalClass: SignalClass): Color = when (signalClass) {
    SignalClass.USED -> NavColors.UsedText
    SignalClass.RECEIVED -> NavColors.ReceivedText
    SignalClass.WEAK -> NavColors.WeakText
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
