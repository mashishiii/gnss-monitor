package io.github.mashishiii.gnssmonitor.data

/** この機数以上の「使用中のデュアル周波数衛星」があれば、デュアル周波数を点灯する。 */
const val DUAL_FREQ_MIN_SATS = 2

/** 水平精度 68% の半径から 95% の半径の目安を出す係数（2 次元正規分布の仮定: 2.448 / 1.510 ≒ 1.62）。 */
const val ACCURACY_95_FACTOR = 1.62f

/** 水平精度 (68%) の半径から 95% の目安の半径。値が無ければ null。 */
fun horizontalAccuracy95(radius68Meters: Float?): Float? =
    radius68Meters?.takeIf { it.isFinite() && it >= 0f }?.times(ACCURACY_95_FACTOR)

/** 帯域族ごとの捕捉 / 使用の衛星数（同じ衛星の L1 と L5 は、それぞれの帯域で 1 機と数える）。 */
data class BandCount(val family: BandFamily, val captured: Int, val used: Int)

/** 測位系ごとの捕捉 / 使用の衛星数（周波数では増やさない）と、使用中の衛星の平均 C/N0。 */
data class ConstellationStat(
    val constellationType: Int,
    val captured: Int,
    val used: Int,
    /** 使用中の衛星について、衛星ごとに最も強い信号の C/N0 を平均した値。使用 0 機は null。 */
    val avgCn0DbHz: Float?,
)

/** 衛星リストから作る集計。 */
data class GnssStats(
    /** 周波数が取れた帯域の内訳（族の順）。周波数がまったく取れない端末では空。 */
    val bands: List<BandCount>,
    val constellations: List<ConstellationStat>,
    /** 使用中の衛星の平均 C/N0（衛星ごとに最大の信号）。使用 0 機は null。 */
    val avgCn0DbHz: Float?,
    /** 同じ衛星の L1 族と L5 族の信号がどちらも使用中の衛星の数。 */
    val dualFreqSatellites: Int,
    /** デュアル周波数に数えた衛星のキー（測位系-SVID）。 */
    val dualFreqKeys: Set<String>,
    /** 周波数が 1 つでも取れたか。取れなければ帯域・デュアル周波数は表示しない。 */
    val hasFrequencyInfo: Boolean,
) {
    /** デュアル周波数の点灯条件: 使用中のデュアル周波数衛星が [DUAL_FREQ_MIN_SATS] 機以上。 */
    val dualFreq: Boolean get() = hasFrequencyInfo && dualFreqSatellites >= DUAL_FREQ_MIN_SATS

    fun band(family: BandFamily): BandCount? = bands.firstOrNull { it.family == family }

    /** 記録用: L1 / L5 以外の帯域の使用機数（0 は除く）。 */
    val usedOtherBands: Map<BandFamily, Int>
        get() = bands.filter { it.family != BandFamily.L1 && it.family != BandFamily.L5 && it.family != BandFamily.UNKNOWN && it.used > 0 }
            .associate { it.family to it.used }
}

fun computeGnssStats(satellites: List<SatelliteInfo>): GnssStats {
    val merged = mergeSatellites(satellites)

    // 帯域: 衛星ごとに、信号のある族 / 使用中の信号がある族を集める
    val captured = HashMap<BandFamily, Int>()
    val used = HashMap<BandFamily, Int>()
    var anyKnown = false
    val dualKeys = LinkedHashSet<String>()
    for (m in merged) {
        val families = m.signals.map { it.bandFamily }.toSet()
        val usedFamilies = m.signals.filter { it.usedInFix }.map { it.bandFamily }.toSet()
        for (f in families) {
            captured.merge(f, 1, Int::plus)
            if (f != BandFamily.UNKNOWN) anyKnown = true
        }
        for (f in usedFamilies) used.merge(f, 1, Int::plus)
        if (BandFamily.L1 in usedFamilies && BandFamily.L5 in usedFamilies) dualKeys += m.key
    }
    val bands = if (!anyKnown) {
        emptyList()
    } else {
        BandFamily.entries.mapNotNull { f ->
            val c = captured[f] ?: return@mapNotNull null
            BandCount(f, c, used[f] ?: 0)
        }
    }

    // 測位系: 並びは mergeSatellites の順（GPS → みちびき → GLONASS → Galileo → BeiDou …）
    val constellations = merged.groupBy { it.constellationType }.map { (type, sats) ->
        val usedSats = sats.filter { it.usedInFix }
        ConstellationStat(type, sats.size, usedSats.size, usedSats.avgCn0())
    }
    val allUsed = merged.filter { it.usedInFix }

    return GnssStats(
        bands = bands,
        constellations = constellations,
        avgCn0DbHz = allUsed.avgCn0(),
        dualFreqSatellites = dualKeys.size,
        dualFreqKeys = dualKeys,
        hasFrequencyInfo = anyKnown,
    )
}

private fun List<MergedSatellite>.avgCn0(): Float? {
    if (isEmpty()) return null
    val values = map { it.cn0DbHz }.filter { it.isFinite() }
    return if (values.isEmpty()) null else values.sum() / values.size
}

/** 情報パネルに出すデュアル周波数の表示。周波数が取れない端末は「---」。 */
fun GnssStats.dualFreqLabel(): String = when {
    !hasFrequencyInfo -> "---"
    dualFreq -> "点灯 ${dualFreqSatellites}機"
    else -> "消灯"
}
