package io.github.mashishiii.gnssmonitor

import android.app.Application
import io.github.mashishiii.gnssmonitor.data.CrashLog

class GnssApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
