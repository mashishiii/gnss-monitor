package io.github.mashishiii.gnssmonitor.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * LocationManager（GPS プロバイダ）と GnssStatus.Callback から測位情報を集め、
 * 1 秒ごとに [state] へ公開する。コールバックはすべてメインスレッドで呼ばれる。
 */
class GnssRepository(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(GnssUiState())
    val state: StateFlow<GnssUiState> = _state.asStateFlow()

    // コールバックが随時更新する最新値。1 秒ごとに state へ反映する。
    private var latestPosition: PositionInfo? = null
    private var latestSatellites: List<SatelliteInfo> = emptyList()
    private var started = false
    private var tickerJob: Job? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            runCatching {
                val lat = location.latitude
                val lon = location.longitude
                if (!lat.isFinite() || !lon.isFinite()) return
                latestPosition = PositionInfo(
                    latitude = lat,
                    longitude = lon,
                    accuracyMeters = if (location.hasAccuracy()) location.accuracy.takeIf { it.isFinite() } else null,
                )
            }
        }

        @Deprecated("API 29 以降では呼ばれない")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            // 1 機分の読み取りに失敗しても、その衛星だけ捨てて続行する
            val list = ArrayList<SatelliteInfo>(status.satelliteCount)
            for (i in 0 until status.satelliteCount) {
                runCatching { list.add(sanitizeSatellite(readSatellite(status, i))) }
            }
            list.sortWith(
                compareBy<SatelliteInfo>({ it.constellationType }, { it.svid }, { it.carrierFrequencyHz ?: 0f }),
            )
            latestSatellites = list
        }

        override fun onStopped() {
            latestSatellites = emptyList()
        }
    }

    private fun readSatellite(status: GnssStatus, i: Int) = SatelliteInfo(
        constellationType = status.getConstellationType(i),
        svid = status.getSvid(i),
        cn0DbHz = status.getCn0DbHz(i),
        azimuthDegrees = status.getAzimuthDegrees(i),
        elevationDegrees = status.getElevationDegrees(i),
        usedInFix = status.usedInFix(i),
        carrierFrequencyHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && status.hasCarrierFrequencyHz(i)) {
            status.getCarrierFrequencyHz(i)
        } else {
            null
        },
    )

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** 権限があれば更新を開始する。二重呼び出しは無視する。 */
    @SuppressLint("MissingPermission")
    fun start() {
        if (started || !hasPermission()) return
        // 権限の取り消しや GPS プロバイダ無しの端末で例外になっても、アプリは落とさない
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                locationListener,
                Looper.getMainLooper(),
            )
            locationManager.registerGnssStatusCallback(gnssCallback, handler)
        } catch (e: SecurityException) {
            runCatching { locationManager.removeUpdates(locationListener) }
            return
        } catch (e: IllegalArgumentException) {
            runCatching { locationManager.removeUpdates(locationListener) }
            return
        }
        started = true
        tickerJob = scope.launch {
            while (isActive) {
                publish()
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        tickerJob?.cancel()
        tickerJob = null
        runCatching { locationManager.removeUpdates(locationListener) }
        runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
    }

    private fun publish() {
        val gpsEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }
            .getOrDefault(false)
        _state.value = GnssUiState(
            gpsEnabled = gpsEnabled,
            position = latestPosition,
            satellites = latestSatellites,
        )
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 1000L
    }
}
