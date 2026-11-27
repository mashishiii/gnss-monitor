package io.github.mashishiii.gnssmonitor.data

/** 記録する 1 点。 */
data class LogPoint(
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val speedMps: Float? = null,
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    // --- v0.5 で追加（古い記録には無いので null / 既定値） ---
    /** 垂直精度（m）。 */
    val verticalAccuracyMeters: Float? = null,
    /** 速度精度（m/s）。 */
    val speedAccuracyMps: Float? = null,
    /** 使用中の衛星の平均 C/N0（dB-Hz）。 */
    val avgCn0DbHz: Float? = null,
    /** デュアル周波数が点灯していたか。周波数が取れない端末では null。 */
    val dualFreq: Boolean? = null,
    /** 使用中のデュアル周波数衛星の数。周波数が取れない端末では null。 */
    val dualFreqSats: Int? = null,
    /** L1 族の信号を使っている衛星の数。周波数が取れない端末では null。 */
    val satsL1: Int? = null,
    /** L5 族の信号を使っている衛星の数。周波数が取れない端末では null。 */
    val satsL5: Int? = null,
    /** L1 / L5 以外の帯域の使用機数（0 は除く）。 */
    val satsOtherBands: Map<BandFamily, Int> = emptyMap(),
)

/** 測位できていて時刻のある状態だけを記録点にする。 */
fun logPointFrom(state: GnssUiState): LogPoint? {
    val p = state.position ?: return null
    val time = p.timeMillis ?: return null
    if (state.fixState == FixState.NO_FIX) return null
    if (!p.latitude.isFinite() || !p.longitude.isFinite()) return null
    val stats = computeGnssStats(state.satellites)
    val hasFreq = stats.hasFrequencyInfo
    return LogPoint(
        timeMillis = time,
        latitude = p.latitude,
        longitude = p.longitude,
        altitudeMeters = p.altitudeMeters,
        accuracyMeters = p.accuracyMeters,
        speedMps = p.speedMps,
        satellitesUsed = state.usedCount,
        satellitesVisible = state.capturedCount,
        verticalAccuracyMeters = p.verticalAccuracyMeters,
        speedAccuracyMps = p.speedAccuracyMps,
        avgCn0DbHz = stats.avgCn0DbHz,
        dualFreq = if (hasFreq) stats.dualFreq else null,
        dualFreqSats = if (hasFreq) stats.dualFreqSatellites else null,
        satsL1 = if (hasFreq) stats.band(BandFamily.L1)?.used ?: 0 else null,
        satsL5 = if (hasFreq) stats.band(BandFamily.L5)?.used ?: 0 else null,
        satsOtherBands = stats.usedOtherBands,
    )
}

/** 同じ測位（同じ時刻）を二重に記録しない。時刻が戻った点も捨てる。 */
fun shouldRecord(last: LogPoint?, next: LogPoint): Boolean = last == null || next.timeMillis > last.timeMillis

/** 軌跡の色分け（使用衛星数）。 */
enum class TrackQuality { GOOD, MID, LOW }

fun trackQuality(satellitesUsed: Int): TrackQuality = when {
    satellitesUsed >= 8 -> TrackQuality.GOOD
    satellitesUsed >= 5 -> TrackQuality.MID
    else -> TrackQuality.LOW
}

/** この秒数より間が空いた区間は「測位なし」として点線で描く。 */
const val TRACK_GAP_MS = 5_000L

fun isTrackGap(prev: LogPoint, next: LogPoint): Boolean = next.timeMillis - prev.timeMillis > TRACK_GAP_MS

/** 軌跡の色の付け方。 */
enum class TrackColorMode(val label: String) {
    /** 使用衛星数（既定）。 */
    SATELLITES("衛星数"),

    /** 水平精度 (68%)。 */
    ACCURACY("精度"),
    ;

    fun next(): TrackColorMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): TrackColorMode = entries.firstOrNull { it.name == name } ?: SATELLITES
    }
}

/** 水平精度による色分け: 5 m 未満 = GOOD、5〜15 m = MID、15 m 超または値なし = LOW。 */
fun accuracyQuality(accuracyMeters: Float?): TrackQuality = when {
    accuracyMeters == null || !accuracyMeters.isFinite() -> TrackQuality.LOW
    accuracyMeters < 5f -> TrackQuality.GOOD
    accuracyMeters <= 15f -> TrackQuality.MID
    else -> TrackQuality.LOW
}
