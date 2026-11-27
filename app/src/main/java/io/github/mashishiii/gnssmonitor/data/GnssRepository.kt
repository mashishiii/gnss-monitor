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
import android.os.SystemClock
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
    private var latestPositionAtMs = 0L
    private var latestSatellites: List<SatelliteInfo> = emptyList()
    // 水平精度の履歴（アプリのメモリ上だけ。GNSS を止めたら捨てる）
    private var accuracyHistory: List<Float?> = emptyList()
    // 進行方向（速度が 1 m/s 未満のあいだは直前の値を保持。GNSS を止めたら捨てる）
    private var heading: Float? = null
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
                    altitudeMeters = if (location.hasAltitude()) location.altitude.takeIf { it.isFinite() } else null,
                    timeMillis = location.time.takeIf { it > 0L },
                    speedMps = if (location.hasSpeed()) location.speed.takeIf { it.isFinite() && it >= 0f } else null,
                    bearingDegrees = if (location.hasBearing()) location.bearing.takeIf { it.isFinite() } else null,
                    // 垂直精度・速度精度は Android 8（API 26）以上で、端末が返したときだけ
                    verticalAccuracyMeters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
                        location.verticalAccuracyMeters.takeIf { it.isFinite() && it >= 0f }
                    } else {
                        null
                    },
                    speedAccuracyMps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
                        location.speedAccuracyMetersPerSecond.takeIf { it.isFinite() && it >= 0f }
                    } else {
                        null
                    },
                )
                latestPositionAtMs = SystemClock.elapsedRealtime()
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

    // 画面（UI）と記録サービスの両方から使うので、利用者がいる間だけ GNSS を動かす
    private val owners = mutableSetOf<String>()

    /** [owner] として利用を開始する。最初の利用者で GNSS を開始する。メインスレッドから呼ぶ。 */
    fun acquire(owner: String) {
        owners += owner
        start()
    }

    /** [owner] の利用を終える。利用者がいなくなれば GNSS を止める。 */
    fun release(owner: String) {
        owners -= owner
        if (owners.isEmpty()) stop()
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** 権限があれば更新を開始する。二重呼び出しは無視する。 */
    @SuppressLint("MissingPermission")
    private fun start() {
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

    private fun stop() {
        if (!started) return
        started = false
        tickerJob?.cancel()
        tickerJob = null
        accuracyHistory = emptyList()
        heading = null
        runCatching { locationManager.removeUpdates(locationListener) }
        runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
    }

    private fun publish() {
        val gpsEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }
            .getOrDefault(false)
        // 10 秒以上位置が更新されていなければ「測位なし」として扱う（古い値を出し続けない）
        val position = latestPosition?.takeIf {
            SystemClock.elapsedRealtime() - latestPositionAtMs <= POSITION_STALE_MS
        }
        val snapshot = GnssUiState(gpsEnabled = gpsEnabled, position = position, satellites = latestSatellites)
        // 測位できているときだけ精度を履歴に足す（未測位の秒は null = 線が切れる）
        accuracyHistory = appendAccuracy(
            accuracyHistory,
            position?.accuracyMeters?.takeIf { snapshot.fixState != FixState.NO_FIX },
        )
        heading = nextHeading(heading, position)
        _state.value = snapshot.copy(accuracyHistory = accuracyHistory, heading = heading)
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 1000L
        const val POSITION_STALE_MS = 10_000L
    }
}
