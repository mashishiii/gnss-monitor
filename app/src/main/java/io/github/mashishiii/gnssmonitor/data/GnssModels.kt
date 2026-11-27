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
    /** 帯域名（測位系に合わせた呼び名。例: Galileo の 1176 MHz は E5a）。 */
    val bandLabel: String get() = bandLabelFor(constellationType, carrierFrequencyHz)
    val bandFamily: BandFamily get() = bandFamily(carrierFrequencyHz)
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
    /** 垂直精度（m、約 68%）。Android 8（API 26）以上で端末が返したときだけ。 */
    val verticalAccuracyMeters: Float? = null,
    /** 速度精度（m/s、約 68%）。Android 8（API 26）以上で端末が返したときだけ。 */
    val speedAccuracyMps: Float? = null,
)

/** 画面に出す 1 秒ごとのスナップショット。 */
data class GnssUiState(
    val gpsEnabled: Boolean = true,
    val position: PositionInfo? = null,
    val satellites: List<SatelliteInfo> = emptyList(),
    /** 直近の水平精度（m、約 68%）の履歴。古い順で 1 秒 1 点、最大 [ACCURACY_HISTORY_SIZE] 点。値が無い秒は null。 */
    val accuracyHistory: List<Float?> = emptyList(),
    /** 進行方向（度、真北から時計回り）。GNSS の対地コースを速度 1 m/s 以上で更新し、遅いときは保持。一度も取れていなければ null。 */
    val heading: Float? = null,
) {
    // 多周波（L1 + L5 など）の端末では同じ衛星が周波数ごとに複数行で届くので、衛星単位で数える
    val capturedCount: Int get() = satellites.distinctBy { it.constellationType to it.svid }.size
    val usedCount: Int
        get() = satellites.filter { it.usedInFix }.distinctBy { it.constellationType to it.svid }.size
    val fixState: FixState get() = fixStateFrom(gpsEnabled, usedCount)
}

/** 精度の履歴に残す点数（1 秒 1 点で 60 秒分）。 */
const val ACCURACY_HISTORY_SIZE = 60

/** 履歴の末尾に 1 点足し、古いものから捨てて [max] 点に収める。 */
fun appendAccuracy(history: List<Float?>, value: Float?, max: Int = ACCURACY_HISTORY_SIZE): List<Float?> {
    val v = value?.takeIf { it.isFinite() && it >= 0f }
    return (history + v).takeLast(max)
}
