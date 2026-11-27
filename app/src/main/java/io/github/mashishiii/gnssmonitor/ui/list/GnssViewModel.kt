package io.github.mashishiii.gnssmonitor.ui.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.mashishiii.gnssmonitor.data.GnssRepository
import io.github.mashishiii.gnssmonitor.data.GnssUiState
import kotlinx.coroutines.flow.StateFlow

class GnssViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GnssRepository(application)

    val state: StateFlow<GnssUiState> = repository.state

    fun hasPermission(): Boolean = repository.hasPermission()

    fun start() = repository.start()

    fun stop() = repository.stop()

    override fun onCleared() {
        repository.stop()
    }
}
