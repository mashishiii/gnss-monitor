package io.github.mashishiii.gnssmonitor.data

/** 受信状態の分類。UI の色分け（緑=使用 / 青=捕捉 / 灰=弱い）に使う。 */
enum class SignalClass { USED, RECEIVED, WEAK }

/** 測位状態。 */
enum class FixState(val label: String) {
    NO_FIX("未測位"),
    FIX_2D("2D"),
    FIX_3D("3D"),
}

/** 衛星 1 機分の情報。 */
data class SatelliteInfo(
    val constellationType: Int,
    val svid: Int,
    val cn0DbHz: Float,
    val azimuthDegrees: Float,
    val elevationDegrees: Float,
    val usedInFix: Boolean,
    /** 搬送波周波数（Hz）。取得できない端末・API では null。 */
    val carrierFrequencyHz: Float? = null,
) {
    val bandLabel: String get() = bandLabel(carrierFrequencyHz)
    val constellationName: String get() = constellationName(constellationType)
    val signalClass: SignalClass get() = classifySignal(usedInFix, cn0DbHz)
}

/** 現在地。値が無い項目は null。 */
data class PositionInfo(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitudeMeters: Double? = null,
    /** 測位時刻（Unix ミリ秒、GNSS の時刻）。 */
    val timeMillis: Long? = null,
    /** 対地速度（m/s）。 */
    val speedMps: Float? = null,
    /** 進行方向（度、北=0）。 */
    val bearingDegrees: Float? = null,
)

/** 画面に出す 1 秒ごとのスナップショット。 */
data class GnssUiState(
    val gpsEnabled: Boolean = true,
    val position: PositionInfo? = null,
    val satellites: List<SatelliteInfo> = emptyList(),
) {
    // 多周波（L1 + L5 など）の端末では同じ衛星が周波数ごとに複数行で届くので、衛星単位で数える
    val capturedCount: Int get() = satellites.distinctBy { it.constellationType to it.svid }.size
    val usedCount: Int
        get() = satellites.filter { it.usedInFix }.distinctBy { it.constellationType to it.svid }.size
    val fixState: FixState get() = fixStateFrom(gpsEnabled, usedCount)
}
