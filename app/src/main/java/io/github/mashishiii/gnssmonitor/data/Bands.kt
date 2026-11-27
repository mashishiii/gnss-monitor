package io.github.mashishiii.gnssmonitor.data

import android.location.GnssStatus

/**
 * 搬送波周波数から決める「帯域族」。カウントとデュアル周波数の判定に使う。
 * 例: GPS L5 / Galileo E5a / BeiDou B2a（いずれも 1176.45 MHz 付近）は、すべて族 L5。
 */
enum class BandFamily(val label: String) {
    L1("L1"),
    L5("L5"),
    E5B("E5b"),
    L2("L2"),
    G2("G2"),
    B3("B3"),
    E6("E6"),
    UNKNOWN("---"),
}

/** 周波数（Hz）から帯域族を返す。周波数が無い・範囲外は [BandFamily.UNKNOWN]。 */
fun bandFamily(carrierFrequencyHz: Float?): BandFamily {
    if (carrierFrequencyHz == null || !carrierFrequencyHz.isFinite() || carrierFrequencyHz <= 0f) return BandFamily.UNKNOWN
    val mhz = carrierFrequencyHz / 1_000_000f
    return when {
        mhz >= 1550f && mhz <= 1620f -> BandFamily.L1
        mhz >= 1164f && mhz <= 1194f -> BandFamily.L5
        mhz > 1194f && mhz <= 1217f -> BandFamily.E5B
        mhz > 1217f && mhz <= 1237f -> BandFamily.L2
        mhz > 1237f && mhz <= 1258f -> BandFamily.G2
        mhz > 1258f && mhz <= 1272f -> BandFamily.B3
        mhz > 1272f && mhz <= 1290f -> BandFamily.E6
        else -> BandFamily.UNKNOWN
    }
}

/**
 * 画面に出す帯域名。同じ周波数でも測位系で呼び名が違うので、測位系に合わせる。
 * 周波数が無ければ空文字、帯域族の範囲外なら "-"。
 */
fun bandLabelFor(constellationType: Int, carrierFrequencyHz: Float?): String {
    if (carrierFrequencyHz == null || !carrierFrequencyHz.isFinite() || carrierFrequencyHz <= 0f) return ""
    val mhz = carrierFrequencyHz / 1_000_000f
    return when (bandFamily(carrierFrequencyHz)) {
        BandFamily.L1 -> when (constellationType) {
            GnssStatus.CONSTELLATION_GALILEO -> "E1"
            // B1C は 1575.42 MHz、B1I は 1561.098 MHz
            GnssStatus.CONSTELLATION_BEIDOU -> if (mhz >= 1568f) "B1C" else "B1I"
            GnssStatus.CONSTELLATION_GLONASS -> "G1"
            else -> "L1"
        }
        BandFamily.L5 -> when (constellationType) {
            GnssStatus.CONSTELLATION_GALILEO -> "E5a"
            GnssStatus.CONSTELLATION_BEIDOU -> "B2a"
            else -> "L5"
        }
        BandFamily.E5B -> when (constellationType) {
            GnssStatus.CONSTELLATION_GALILEO -> "E5b"
            // 周波数だけでは B2I と B2b を区別できない
            GnssStatus.CONSTELLATION_BEIDOU -> "B2"
            GnssStatus.CONSTELLATION_GLONASS -> "G3"
            else -> "E5b"
        }
        BandFamily.L2 -> "L2"
        BandFamily.G2 -> "G2"
        BandFamily.B3 -> "B3"
        BandFamily.E6 -> if (constellationType == GnssStatus.CONSTELLATION_QZSS) "L6" else "E6"
        BandFamily.UNKNOWN -> "-"
    }
}
