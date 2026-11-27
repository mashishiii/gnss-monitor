package io.github.mashishiii.gnssmonitor.logging

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.mashishiii.gnssmonitor.GnssApp
import io.github.mashishiii.gnssmonitor.MainActivity
import io.github.mashishiii.gnssmonitor.R
import io.github.mashishiii.gnssmonitor.data.logPointFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 画面を消しても記録を続けるためのフォアグラウンドサービス（種類: location）。
 * 記録中は通知を出し、通知の「記録停止」からも止められる。
 * 位置情報の権限がある状態で、アプリが前面にあるときに開始する必要がある（Android 14 以降の決まり）。
 */
class LoggingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var active = false

    private val app get() = application as GnssApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopLogging()
            else -> startLogging()
        }
        return START_NOT_STICKY
    }

    private fun startLogging() {
        if (active) return
        if (!app.repository.hasPermission()) {
            stopSelf()
            return
        }
        createChannel()
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(0),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        }.isSuccess
        if (!started) {
            // 背景からの開始や権限不足（SecurityException / ForegroundServiceStartNotAllowedException）
            stopSelf()
            return
        }
        active = true
        _running.value = true
        app.recorder.begin(System.currentTimeMillis())
        app.repository.acquire(OWNER)
        collectJob = scope.launch {
            app.repository.state.collect { state ->
                val point = logPointFrom(state) ?: return@collect
                if (app.recorder.add(point)) {
                    val count = app.recorder.state.value.points.size
                    if (count % NOTIFICATION_UPDATE_EVERY == 0) updateNotification(count)
                }
            }
        }
    }

    private fun stopLogging() {
        if (active) {
            active = false
            collectJob?.cancel()
            collectJob = null
            app.repository.release(OWNER)
            app.recorder.end()
        }
        _running.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (active) {
            active = false
            collectJob?.cancel()
            app.repository.release(OWNER)
            app.recorder.end()
        }
        _running.value = false
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "軌跡の記録", NotificationManager.IMPORTANCE_LOW).apply {
            description = "記録中であることを表示します"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(points: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, LoggingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_satellite)
            .setContentTitle("GNSS 軌跡を記録中")
            .setContentText(if (points == 0) "測位を待っています" else "$points 点を記録")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, "記録停止", stop)
            .build()
    }

    private fun updateNotification(points: Int) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(points))
        }
    }

    companion object {
        private const val ACTION_START = "io.github.mashishiii.gnssmonitor.action.START_LOGGING"
        private const val ACTION_STOP = "io.github.mashishiii.gnssmonitor.action.STOP_LOGGING"
        private const val CHANNEL_ID = "logging"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_UPDATE_EVERY = 10
        private const val OWNER = "logger"

        private val _running = MutableStateFlow(false)

        /** サービスが記録中か。 */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /** アプリが前面にあるとき（ボタン操作）に呼ぶ。 */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LoggingService::class.java).setAction(ACTION_START),
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(Intent(context, LoggingService::class.java).setAction(ACTION_STOP))
            }
        }
    }
}
