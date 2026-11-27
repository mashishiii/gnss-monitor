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
