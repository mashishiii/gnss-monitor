package io.github.mashishiii.gnssmonitor.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mashishiii.gnssmonitor.data.FixState
import io.github.mashishiii.gnssmonitor.data.GnssUiState
import io.github.mashishiii.gnssmonitor.data.TrackRecorder
import io.github.mashishiii.gnssmonitor.ui.theme.NavColors
import java.util.Locale
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay

internal const val TAG_MAP = "map"
internal const val TAG_RECORD_BUTTON = "recordButton"

/** 地図の初期位置（測位前）: 東京駅付近。 */
private val DefaultCenter = GeoPoint(35.681236, 139.767125)

@Composable
fun MapScreen(
    state: GnssUiState,
    track: TrackRecorder.State,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onExportCsv: () -> Unit,
    onExportGpx: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var follow by rememberSaveable { mutableStateOf(true) }
    var night by rememberSaveable { mutableStateOf(true) }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            OsmMap(
                state = state,
                track = track,
                follow = follow,
                night = night,
                onUserMove = { follow = false },
                modifier = Modifier.fillMaxSize().testTag(TAG_MAP),
            )
            MapStatusBox(state, Modifier.align(Alignment.TopStart).padding(8.dp))
            TrackLegend(Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 44.dp))
            MapSideButtons(
                follow = follow,
                night = night,
                onFollow = { follow = true },
                onNight = { night = !night },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
            SpeedBox(state, Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 24.dp))
            Text(
                "© OpenStreetMap contributors",
                color = NavColors.TextPrimary,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(NavColors.Background.copy(alpha = 0.7f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        RecordBar(track, onStartRecording, onStopRecording, onExportCsv, onExportGpx)
    }
}

// ---------------------------------------------------------------- osmdroid

@Composable
private fun OsmMap(
    state: GnssUiState,
    track: TrackRecorder.State,
    follow: Boolean,
    night: Boolean,
    onUserMove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val trackOverlay = remember { TrackOverlay(density) }
    val positionOverlay = remember { PositionOverlay(density) }
    val mapView = remember {
        MapConfig.ensure(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            controller.setZoom(16.0)
            controller.setCenter(DefaultCenter)
            overlays.add(trackOverlay)
            overlays.add(positionOverlay)
            overlays.add(UserGestureOverlay { onUserMove() })
        }
    }

    // MapView のライフサイクル（タイル取得の一時停止・再開）
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.overlayManager.tilesOverlay.setColorFilter(if (night) TilesOverlay.INVERT_COLORS else null)
            // 夜モードは色反転フィルタが読み込み中の格子にもかかるので、反転後に暗くなる色を指定する
            view.overlayManager.tilesOverlay.loadingBackgroundColor = if (night) 0xFFF0E8DF.toInt() else 0xFFE6E6E6.toInt()
            view.overlayManager.tilesOverlay.loadingLineColor = if (night) 0xFFE3D7CA.toInt() else 0xFFCCCCCC.toInt()
            trackOverlay.points = track.points
            positionOverlay.position = state.position
            val p = state.position
            if (follow && p != null) {
                view.controller.animateTo(GeoPoint(p.latitude, p.longitude))
            } else if (p == null && follow && track.points.isNotEmpty()) {
                val last = track.points.last()
                view.controller.setCenter(GeoPoint(last.latitude, last.longitude))
            }
            view.invalidate()
        },
    )
}

// ---------------------------------------------------------------- 重ね表示

private val OverlayBg = Color(0xD90A1017)

@Composable
private fun OverlayBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(OverlayBg, RoundedCornerShape(4.dp))
            .border(1.dp, NavColors.PanelBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) { content() }
}

@Composable
private fun MapStatusBox(state: GnssUiState, modifier: Modifier = Modifier) {
    val fix = state.fixState
    val acc = state.position?.accuracyMeters?.takeIf { fix != FixState.NO_FIX }
    OverlayBox(modifier) {
        Row {
            Text(
                fix.label,
                color = if (fix == FixState.NO_FIX) NavColors.Warning else NavColors.UsedText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            Text(
                " ${state.usedCount}/${state.capturedCount}衛星" + (acc?.let { String.format(Locale.US, " ±%.1fm", it) } ?: ""),
                color = NavColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun TrackLegend(modifier: Modifier = Modifier) {
    OverlayBox(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            LegendLine(Color(TrackColors.GOOD), "━", "8衛星以上")
            LegendLine(Color(TrackColors.MID), "━", "5〜7衛星")
            LegendLine(Color(TrackColors.LOW), "━", "4衛星以下")
            LegendLine(Color(TrackColors.LOW), "┅", "測位なし（5秒以上）")
        }
    }
}

@Composable
private fun LegendLine(color: Color, mark: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(mark, color = color, fontSize = 10.sp)
        Text(" $label", color = NavColors.TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun MapSideButtons(
    follow: Boolean,
    night: Boolean,
    onFollow: () -> Unit,
    onNight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
        // 地図は常に北が上
        Box(
            Modifier.size(36.dp).background(Color(0xFFE6C229), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("N", color = Color(0xFF1B1B1B), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        NavButton(if (follow) "追従中" else "現在地", on = follow, onClick = onFollow)
        NavButton(if (night) "夜" else "昼", on = false, onClick = onNight)
    }
}

@Composable
internal fun NavButton(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color? = null,
) {
    Text(
        label,
        color = NavColors.TextPrimary,
        fontSize = 12.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = modifier
            .heightIn(min = 40.dp)
            .background(background ?: if (on) NavColors.ButtonOn else NavColors.Button, RoundedCornerShape(4.dp))
            .border(1.dp, NavColors.PanelBorder, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    )
}

@Composable
private fun SpeedBox(state: GnssUiState, modifier: Modifier = Modifier) {
    val p = state.position?.takeIf { state.fixState != FixState.NO_FIX }
    val kmh = p?.speedMps?.let { it * 3.6f }
    OverlayBox(modifier) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    kmh?.let { String.format(Locale.US, "%.0f", it) } ?: "---",
                    color = NavColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                )
                Text(" km/h", color = NavColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(
                "高度 " + (p?.altitudeMeters?.let { String.format(Locale.US, "%.0f m", it) } ?: "---"),
                color = NavColors.TextSecondary,
                fontSize = 10.sp,
            )
        }
    }
}

// ---------------------------------------------------------------- 記録ボタン

/** 記録時間「mm:ss」（1 時間以上は「h:mm:ss」）。 */
internal fun formatElapsed(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

@Composable
private fun RecordBar(
    track: TrackRecorder.State,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onExportCsv: () -> Unit,
    onExportGpx: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(track.recording) {
        while (track.recording) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val hasPoints = track.points.isNotEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .background(NavColors.Background)
            .border(1.dp, NavColors.PanelBorder)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (track.recording) {
                val elapsed = now - (track.startedAtMillis ?: now)
                NavButton(
                    "■ 記録停止  ${formatElapsed(elapsed)}",
                    on = true,
                    onClick = onStop,
                    background = Color(0xFFD83A34),
                    modifier = Modifier.weight(1f).testTag(TAG_RECORD_BUTTON),
                )
            } else {
                NavButton("● 記録開始", on = false, onClick = onStart, modifier = Modifier.weight(1f).testTag(TAG_RECORD_BUTTON))
            }
            NavButton("CSV", on = false, onClick = { if (hasPoints) onExportCsv() })
            NavButton("GPX", on = false, onClick = { if (hasPoints) onExportGpx() })
        }
        Text(
            when {
                track.recording -> "画面を消しても通知を出したまま記録を続けます（${track.points.size} 点）"
                hasPoints -> "前回の記録 ${track.points.size} 点。CSV / GPX で書き出せます（次の記録開始で上書き）"
                else -> "記録するとログを CSV / GPX で書き出せます"
            },
            color = NavColors.TextSecondary,
            fontSize = 10.sp,
        )
    }
}
