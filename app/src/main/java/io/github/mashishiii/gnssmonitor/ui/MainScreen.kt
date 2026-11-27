package io.github.mashishiii.gnssmonitor.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.mashishiii.gnssmonitor.data.GnssUiState
import io.github.mashishiii.gnssmonitor.ui.list.GnssViewModel
import io.github.mashishiii.gnssmonitor.ui.list.SatelliteListScreen
import io.github.mashishiii.gnssmonitor.ui.status.StatusScreen
import io.github.mashishiii.gnssmonitor.ui.theme.NavColors

/** 下のボタンで切り替える画面。地図は v0.3 で追加する。 */
enum class AppScreen(val label: String) {
    STATUS("測位状況"),
    LIST("衛星一覧"),
}

@Composable
fun MainScreen(viewModel: GnssViewModel) {
    var permissionGranted by remember { mutableStateOf(viewModel.hasPermission()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (granted) viewModel.start()
    }

    // 画面が見えている間だけ GNSS の更新を受ける（記録サービスは v0.3）
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        permissionGranted = viewModel.hasPermission()
        if (permissionGranted) viewModel.start()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stop() }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var lastCrash by remember { mutableStateOf(CrashLog.read(context)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(NavColors.Background)
            .safeDrawingPadding(),
    ) {
        if (lastCrash != null) {
            CrashReport(
                text = lastCrash.orEmpty(),
                onDismiss = {
                    CrashLog.clear(context)
                    lastCrash = null
                },
            )
        } else if (permissionGranted) {
            AppContent(state)
        } else {
            PermissionRequest(onRequest = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) })
        }
    }
}

@Composable
private fun AppContent(state: GnssUiState) {
    // 折りたたみの開閉や回転で Activity が作り直されても、画面と選択中の衛星を保つ
    var screen by rememberSaveable { mutableStateOf(AppScreen.STATUS) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (screen) {
                AppScreen.STATUS -> StatusScreen(
                    state = state,
                    selectedKey = selectedKey,
                    onSelect = { selectedKey = it },
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.LIST -> SatelliteListScreen(state, Modifier.fillMaxSize())
            }
        }
        BottomNav(screen, onSelect = { screen = it })
    }
}

/** カーナビ風の下部ボタン。 */
@Composable
private fun BottomNav(current: AppScreen, onSelect: (AppScreen) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(NavColors.Bar)
            .border(1.dp, NavColors.PanelBorder)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in AppScreen.entries) {
            val on = item == current
            Text(
                item.label,
                color = NavColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .background(if (on) NavColors.ButtonOn else NavColors.Button, RoundedCornerShape(4.dp))
                    .border(1.dp, NavColors.PanelBorder, RoundedCornerShape(4.dp))
                    .clickable { onSelect(item) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/** 前回の起動で異常終了していたら、その内容を表示する（開発用）。 */
@Composable
private fun CrashReport(text: String, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("前回の起動で異常終了しました", color = NavColors.Warning, fontWeight = FontWeight.Bold)
        Text(
            "下のログを開発者に共有してください（画面のスクリーンショットで構いません）。",
            color = NavColors.TextSecondary,
            fontSize = 13.sp,
        )
        Text(
            text,
            color = NavColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        )
        Button(onClick = onDismiss) { Text("閉じて続ける") }
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "衛星の情報を表示するには位置情報の許可が必要です",
            color = NavColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
            Text("位置情報を許可する")
        }
    }
}
