package io.github.mashishiii.gnssmonitor.data

import android.location.GnssStatus

/** C/N0 がこの値（dB-Hz）未満の衛星は「弱い」として灰色にする。 */
const val WEAK_CN0_DB_HZ = 20f

/** C/N0 バーの満点（dB-Hz）。 */
const val MAX_CN0_DB_HZ = 50f

/** GnssStatus.CONSTELLATION_* から測位系の表示名を返す。 */
fun constellationName(type: Int): String = when (type) {
    GnssStatus.CONSTELLATION_GPS -> "GPS"
    GnssStatus.CONSTELLATION_QZSS -> "みちびき"
    GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
    GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
    GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
    GnssStatus.CONSTELLATION_SBAS -> "SBAS"
    GnssStatus.CONSTELLATION_IRNSS -> "NavIC"
    else -> "不明"
}

/** 位置計算に使っている衛星の数から測位状態を決める（3機=2D、4機以上=3D）。 */
fun fixStateFrom(gpsEnabled: Boolean, usedCount: Int): FixState = when {
    !gpsEnabled -> FixState.NO_FIX
    usedCount >= 4 -> FixState.FIX_3D
    usedCount == 3 -> FixState.FIX_2D
    else -> FixState.NO_FIX
}

/** 使用中=USED、使用していなくても十分な強度ならRECEIVED、それ以外はWEAK。 */
fun classifySignal(usedInFix: Boolean, cn0DbHz: Float): SignalClass = when {
    usedInFix -> SignalClass.USED
    cn0DbHz >= WEAK_CN0_DB_HZ -> SignalClass.RECEIVED
    else -> SignalClass.WEAK
}

/** 搬送波周波数（Hz）から帯域の目安を返す。 */
fun bandLabel(carrierFrequencyHz: Float?): String {
    if (carrierFrequencyHz == null || !carrierFrequencyHz.isFinite() || carrierFrequencyHz <= 0f) return ""
    val mhz = carrierFrequencyHz / 1_000_000f
    return when {
        mhz in 1550f..1620f -> "L1"
        mhz in 1160f..1195f -> "L5"
        mhz in 1195f..1270f -> "L2"
        else -> "-"
    }
}

/**
 * 衛星 1 機分の値を安全な範囲に整える。
 * NaN / 無限大 / 範囲外の値があっても、描画側（バーの幅など）で例外にならないようにする。
 */
fun sanitizeSatellite(sat: SatelliteInfo): SatelliteInfo = sat.copy(
    cn0DbHz = sat.cn0DbHz.finiteOr(0f).coerceIn(0f, 99f),
    azimuthDegrees = sat.azimuthDegrees.finiteOr(0f).coerceIn(0f, 360f),
    elevationDegrees = sat.elevationDegrees.finiteOr(0f).coerceIn(-90f, 90f),
    carrierFrequencyHz = sat.carrierFrequencyHz?.takeIf { it.isFinite() && it > 0f },
)

private fun Float.finiteOr(default: Float): Float = if (isFinite()) this else default

/**
 * LazyColumn 用の一意なキーを作る。
 * 測位系 + SVID + 周波数だけでは重複しうる（同一周波数が複数行で届く端末がある）ので、
 * 重複したら出現順の連番を付けて必ず一意にする。
 */
fun uniqueSatelliteKeys(satellites: List<SatelliteInfo>): List<String> {
    val seen = HashMap<String, Int>()
    return satellites.map { sat ->
        val base = "${sat.constellationType}-${sat.svid}-${sat.carrierFrequencyHz?.toLong() ?: 0L}"
        val n = seen.merge(base, 1, Int::plus)!!
        if (n == 1) base else "$base#$n"
    }
}

/** 度（小数）を「35° 39′ 31″ N」形式にする。 */
fun formatDms(degrees: Double, positive: Char, negative: Char): String {
    val totalSeconds = Math.round(Math.abs(degrees) * 3600.0)
    val d = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    val hemi = if (degrees >= 0) positive else negative
    return "${d}° ${m.toString().padStart(2, '0')}′ ${s.toString().padStart(2, '0')}″ $hemi"
}

/** 「2026年11月27日」形式。 */
fun formatDateJa(millis: Long, timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()): String =
    java.text.SimpleDateFormat("yyyy年M月d日", java.util.Locale.JAPAN)
        .apply { this.timeZone = timeZone }
        .format(java.util.Date(millis))

/** 「10時59分08秒」形式。 */
fun formatTimeJa(millis: Long, timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()): String =
    java.text.SimpleDateFormat("HH時mm分ss秒", java.util.Locale.JAPAN)
        .apply { this.timeZone = timeZone }
        .format(java.util.Date(millis))

/**
 * nav-u の自車マークの色に倣った受信レベル。
 * 赤 = 測位できている / 橙 = 衛星は捕捉しているが測位前 / 灰 = 衛星を受信できていない。
 */
enum class GpsLevel(val label: String) {
    FIX("測位中"),
    ACQUIRING("捕捉中"),
    NONE("未受信"),
}

fun gpsLevelFrom(fixState: FixState, capturedCount: Int): GpsLevel = when {
    fixState != FixState.NO_FIX -> GpsLevel.FIX
    capturedCount > 0 -> GpsLevel.ACQUIRING
    else -> GpsLevel.NONE
}
