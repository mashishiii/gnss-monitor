package io.github.mashishiii.gnssmonitor.data

import android.location.GnssStatus

/** 同じ衛星の複数周波数（L1 / L5 など）の行を 1 機にまとめたもの。 */
data class MergedSatellite(
    val constellationType: Int,
    val svid: Int,
    val signals: List<SatelliteInfo>,
) {
    /** 測位系 + SVID で一意。 */
    val key: String get() = "$constellationType-$svid"

    private val best: SatelliteInfo get() = signals.maxByOrNull { it.cn0DbHz } ?: signals.first()

    /** 最も強い周波数の C/N0。 */
    val cn0DbHz: Float get() = best.cn0DbHz
    val azimuthDegrees: Float get() = best.azimuthDegrees
    val elevationDegrees: Float get() = best.elevationDegrees
    val usedInFix: Boolean get() = signals.any { it.usedInFix }
    val signalClass: SignalClass get() = classifySignal(usedInFix, cn0DbHz)
    val constellationName: String get() = constellationName(constellationType)
    val shortLabel: String get() = constellationShortLabel(constellationType)
    val markerShape: MarkerShape get() = markerShapeFor(constellationType)

    /** 例: "L1/L5"。周波数が不明なら空文字。 */
    val bands: String
        get() = signals.sortedByDescending { it.carrierFrequencyHz ?: 0f }
            .map { it.bandLabel }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("/")
}

/** 地図記号の形。測位系ごとに固定。 */
enum class MarkerShape { CIRCLE, DIAMOND, SQUARE, TRIANGLE, HEXAGON }

fun markerShapeFor(type: Int): MarkerShape = when (type) {
    GnssStatus.CONSTELLATION_QZSS -> MarkerShape.DIAMOND
    GnssStatus.CONSTELLATION_GLONASS -> MarkerShape.SQUARE
    GnssStatus.CONSTELLATION_GALILEO -> MarkerShape.TRIANGLE
    GnssStatus.CONSTELLATION_BEIDOU -> MarkerShape.HEXAGON
    else -> MarkerShape.CIRCLE
}

/** 受信強度バーの下に出す 1 文字ラベル。 */
fun constellationShortLabel(type: Int): String = when (type) {
    GnssStatus.CONSTELLATION_GPS -> "G"
    GnssStatus.CONSTELLATION_QZSS -> "J"
    GnssStatus.CONSTELLATION_GLONASS -> "R"
    GnssStatus.CONSTELLATION_GALILEO -> "E"
    GnssStatus.CONSTELLATION_BEIDOU -> "C"
    GnssStatus.CONSTELLATION_SBAS -> "S"
    GnssStatus.CONSTELLATION_IRNSS -> "I"
    else -> "?"
}

private fun constellationOrder(type: Int): Int = when (type) {
    GnssStatus.CONSTELLATION_GPS -> 0
    GnssStatus.CONSTELLATION_QZSS -> 1
    GnssStatus.CONSTELLATION_GLONASS -> 2
    GnssStatus.CONSTELLATION_GALILEO -> 3
    GnssStatus.CONSTELLATION_BEIDOU -> 4
    GnssStatus.CONSTELLATION_SBAS -> 5
    GnssStatus.CONSTELLATION_IRNSS -> 6
    else -> 7
}

/** 測位系 + SVID が同じ行を 1 機にまとめ、GPS→みちびき→GLONASS→Galileo→BeiDou の順・SVID 順に並べる。 */
fun mergeSatellites(satellites: List<SatelliteInfo>): List<MergedSatellite> =
    satellites
        .groupBy { it.constellationType to it.svid }
        .map { (id, rows) -> MergedSatellite(id.first, id.second, rows) }
        .sortedWith(compareBy({ constellationOrder(it.constellationType) }, { it.constellationType }, { it.svid }))
