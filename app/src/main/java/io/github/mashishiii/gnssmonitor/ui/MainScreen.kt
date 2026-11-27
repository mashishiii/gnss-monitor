package io.github.mashishiii.gnssmonitor.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import io.github.mashishiii.gnssmonitor.data.gpsLevelFrom
import io.github.mashishiii.gnssmonitor.ui.theme.DisplayMode
import io.github.mashishiii.gnssmonitor.ui.theme.GnssMonitorTheme
import io.github.mashishiii.gnssmonitor.ui.theme.NavButton
import io.github.mashishiii.gnssmonitor.ui.theme.BackButton
import androidx.activity.compose.BackHandler
import io.github.mashishiii.gnssmonitor.ui.theme.TitleBar
import io.github.mashishiii.gnssmonitor.ui.theme.toolbarSurface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import io.github.mashishiii.gnssmonitor.data.LogPoint
import io.github.mashishiii.gnssmonitor.data.TrackFormats
import io.github.mashishiii.gnssmonitor.data.TrackRecorder
import io.github.mashishiii.gnssmonitor.logging.LoggingService
import io.github.mashishiii.gnssmonitor.ui.map.MapScreen
import io.github.mashishiii.gnssmonitor.ui.map.MapSettings
import io.github.mashishiii.gnssmonitor.data.MapOrientation
import io.github.mashishiii.gnssmonitor.data.MotionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** 下のボタンで切り替える画面。 */
enum class AppScreen(val label: String, val title: String) {
    STATUS("測位状況", "測位情報"),
    LIST("衛星一覧", "衛星情報"),
    MAP("地図", "現在地マップ"),
}

@Composable
fun MainScreen(viewModel: GnssViewModel) {
    var permissionGranted by remember { mutableStateOf(viewModel.hasPermission()) }
    // Android 12 以降は FINE と COARSE を一緒に求める必要がある
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionGranted = viewModel.hasPermission()
        if (permissionGranted) viewModel.start()
    }

    // 画面が見えている間だけ GNSS の更新を受ける（記録サービスは v0.3）
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        permissionGranted = viewModel.hasPermission()
        if (permissionGranted) viewModel.start()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stop() }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val track by viewModel.track.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var lastCrash by remember { mutableStateOf(CrashLog.read(context)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(NavColors.Background),
    ) {
        if (lastCrash != null) {
            CrashReport(
                modifier = Modifier.safeDrawingPadding(),
                text = lastCrash.orEmpty(),
                onDismiss = {
                    CrashLog.clear(context)
                    lastCrash = null
                },
            )
        } else if (permissionGranted) {
            AppContent(state, track)
        } else {
            PermissionRequest(
                modifier = Modifier.safeDrawingPadding(),
                onRequest = {
                    launcher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                },
            )
        }
    }
}

@Composable
internal fun AppContent(
    state: GnssUiState,
    track: TrackRecorder.State,
    initialScreen: AppScreen = AppScreen.STATUS,
    initialMode: DisplayMode? = null,
    initialOrientation: MapOrientation? = null,
    initialMotion: MotionMode? = null,
) {
    val context = LocalContext.current
    // 地図とスカイプロットで共通の向き・動き（端末に保存。既定はヘディングアップ・レトロ）
    val mapSettings = remember { MapSettings.load(context, initialOrientation, initialMotion) }
    // 昼夜の表示モード（自動/昼/夜）。選んだ値は端末に覚えておく
    val prefs = remember { context.getSharedPreferences("display", Context.MODE_PRIVATE) }
    var mode by rememberSaveable {
        mutableStateOf(initialMode ?: DisplayMode.fromName(prefs.getString("mode", null)))
    }
    val night = mode.isNight(isSystemInDarkTheme())
    GnssMonitorTheme(night = night) {
        SystemBarIcons(night)
        AppScaffold(
            state, track, initialScreen, mode, mapSettings,
            onMode = {
                mode = it
                prefs.edit().putString("mode", it.name).apply()
            },
        )
    }
}

/** 昼は暗いアイコン、夜は明るいアイコンにして、ステータスバー/ナビゲーションバーの文字を読めるようにする。 */
@Composable
private fun SystemBarIcons(night: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !night
        controller.isAppearanceLightNavigationBars = !night
    }
}

@Composable
private fun AppScaffold(
    state: GnssUiState,
    track: TrackRecorder.State,
    initialScreen: AppScreen,
    mode: DisplayMode,
    mapSettings: MapSettings,
    onMode: (DisplayMode) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 折りたたみの開閉や回転で Activity が作り直されても、画面と選択中の衛星を保つ
    var screen by rememberSaveable { mutableStateOf(initialScreen) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }

    // 記録開始: Android 13 以降は通知の許可を求めてから（拒否されても記録はする）
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        LoggingService.start(context)
    }
    val startRecording = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            LoggingService.start(context)
        }
    }

    // 書き出し: 保存先はユーザーが選ぶ（Storage Access Framework）
    val fileBase = TrackFormats.defaultFileBase(track.startedAtMillis ?: System.currentTimeMillis())
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { exportTrack(context, uri, track.points, ExportFormat.CSV, fileBase) }
    }
    val gpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) scope.launch { exportTrack(context, uri, track.points, ExportFormat.GPX, fileBase) }
    }

    // 測位状況以外では、戻る操作で測位状況に戻る（測位状況では通常どおりアプリを閉じる）
    BackHandler(enabled = screen != AppScreen.STATUS) { screen = AppScreen.STATUS }

    Scaffold(
        containerColor = NavColors.Background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TitleBar(
                screen.title,
                gpsLevelFrom(state.fixState, state.capturedCount),
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            )
        },
        bottomBar = {
            BottomNav(screen, mode, onSelect = { screen = it }, onMode = onMode, onBack = { screen = AppScreen.STATUS })
        },
    ) { innerPadding ->
        // 下のボタンや画面端（ナビゲーションバー・カメラ穴）と重ならないよう、ここで余白を確保する
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .clipToBounds(),
        ) {
            when (screen) {
                AppScreen.STATUS -> StatusScreen(
                    state = state,
                    selectedKey = selectedKey,
                    onSelect = { selectedKey = it },
                    modifier = Modifier.fillMaxSize(),
                    orientation = mapSettings.orientation,
                    motion = mapSettings.motion,
                    onToggleOrientation = mapSettings::toggleOrientation,
                )
                AppScreen.LIST -> SatelliteListScreen(state, Modifier.fillMaxSize())
                AppScreen.MAP -> MapScreen(
                    state = state,
                    track = track,
                    onStartRecording = startRecording,
                    onStopRecording = { LoggingService.stop(context) },
                    onExportCsv = { csvLauncher.launch("$fileBase.csv") },
                    onExportGpx = { gpxLauncher.launch("$fileBase.gpx") },
                    modifier = Modifier.fillMaxSize(),
                    settings = mapSettings,
                )
            }
        }
    }
}

private enum class ExportFormat { CSV, GPX }

private suspend fun exportTrack(
    context: Context,
    uri: Uri,
    points: List<LogPoint>,
    format: ExportFormat,
    name: String,
) {
    val ok = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val writer = out.bufferedWriter(Charsets.UTF_8)
                when (format) {
                    ExportFormat.CSV -> TrackFormats.writeCsv(points, writer)
                    ExportFormat.GPX -> TrackFormats.writeGpx(points, writer, name)
                }
                writer.flush()
            } ?: error("出力先を開けません")
        }.isSuccess
    }
    Toast.makeText(
        context,
        if (ok) "${points.size} 点を書き出しました" else "書き出しに失敗しました",
        Toast.LENGTH_SHORT,
    ).show()
}

/** nav-u のような下部ツールバー。画面切り替えの大きなボタン、昼夜切り替え（表示）、右端に「← 戻る」。 */
@Composable
private fun BottomNav(
    current: AppScreen,
    mode: DisplayMode,
    onSelect: (AppScreen) -> Unit,
    onMode: (DisplayMode) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toolbarSurface()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in AppScreen.entries) {
            NavButton(
                item.label,
                on = item == current,
                onClick = { onSelect(item) },
                modifier = Modifier.weight(1f).testTag("nav-${item.name}"),
                fontSize = 12,
                horizontalPadding = 4,
            )
        }
        NavButton(
            "表示 ${mode.label}",
            on = false,
            onClick = { onMode(mode.next()) },
            modifier = Modifier.weight(1.05f).testTag("nav-DISPLAY"),
            fontSize = 11,
            horizontalPadding = 4,
        )
        BackButton(
            enabled = current != AppScreen.STATUS,
            onClick = onBack,
            modifier = Modifier.weight(0.85f).testTag("nav-BACK"),
        )
    }
}

/** 前回の起動で異常終了していたら、その内容を表示する（開発用）。 */
@Composable
private fun CrashReport(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
private fun PermissionRequest(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
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
