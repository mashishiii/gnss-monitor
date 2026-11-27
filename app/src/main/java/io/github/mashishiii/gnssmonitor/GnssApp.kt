package io.github.mashishiii.gnssmonitor

import android.app.Application
import io.github.mashishiii.gnssmonitor.data.CrashLog
import io.github.mashishiii.gnssmonitor.data.GnssRepository
import io.github.mashishiii.gnssmonitor.data.TrackRecorder
import java.io.File

class GnssApp : Application() {

    /** 画面と記録サービスで共有する GNSS の取得元。 */
    val repository: GnssRepository by lazy { GnssRepository(this) }

    /** 軌跡の記録（アプリ専用領域の tracks/ に保存）。 */
    val recorder: TrackRecorder by lazy { TrackRecorder(File(filesDir, "tracks")).also { it.loadLast() } }

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
