package io.github.mashishiii.gnssmonitor.ui.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.mashishiii.gnssmonitor.GnssApp
import io.github.mashishiii.gnssmonitor.data.GnssUiState
import io.github.mashishiii.gnssmonitor.data.TrackRecorder
import kotlinx.coroutines.flow.StateFlow

class GnssViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GnssApp
    private val repository = app.repository

    val state: StateFlow<GnssUiState> = repository.state
    val track: StateFlow<TrackRecorder.State> = app.recorder.state

    fun hasPermission(): Boolean = repository.hasPermission()

    /** 画面が見えている間だけ GNSS を使う（記録中はサービスが別に使い続ける）。 */
    fun start() = repository.acquire(OWNER)

    fun stop() = repository.release(OWNER)

    override fun onCleared() {
        repository.release(OWNER)
    }

    private companion object {
        const val OWNER = "ui"
    }
}
