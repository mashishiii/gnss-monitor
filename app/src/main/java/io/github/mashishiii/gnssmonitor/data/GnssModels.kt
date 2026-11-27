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
) {
    val constellationName: String get() = constellationName(constellationType)
    val signalClass: SignalClass get() = classifySignal(usedInFix, cn0DbHz)
}

/** 現在地。値が無い項目は null。 */
data class PositionInfo(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
)

/** 画面に出す 1 秒ごとのスナップショット。 */
data class GnssUiState(
    val gpsEnabled: Boolean = true,
    val position: PositionInfo? = null,
    val satellites: List<SatelliteInfo> = emptyList(),
) {
    val capturedCount: Int get() = satellites.size
    val usedCount: Int get() = satellites.count { it.usedInFix }
    val fixState: FixState get() = fixStateFrom(gpsEnabled, usedCount)
}
