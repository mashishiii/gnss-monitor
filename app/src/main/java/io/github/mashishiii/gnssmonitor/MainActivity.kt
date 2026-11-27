package io.github.mashishiii.gnssmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import io.github.mashishiii.gnssmonitor.ui.MainScreen
import io.github.mashishiii.gnssmonitor.ui.list.GnssViewModel
import io.github.mashishiii.gnssmonitor.ui.theme.GnssMonitorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GnssViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GnssMonitorTheme(night = isSystemInDarkTheme()) {
                MainScreen(viewModel)
            }
        }
    }
}
