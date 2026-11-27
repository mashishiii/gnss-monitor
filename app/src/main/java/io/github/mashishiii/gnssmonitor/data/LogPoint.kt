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
)

/** 測位できていて時刻のある状態だけを記録点にする。 */
fun logPointFrom(state: GnssUiState): LogPoint? {
    val p = state.position ?: return null
    val time = p.timeMillis ?: return null
    if (state.fixState == FixState.NO_FIX) return null
    if (!p.latitude.isFinite() || !p.longitude.isFinite()) return null
    return LogPoint(
        timeMillis = time,
        latitude = p.latitude,
        longitude = p.longitude,
        altitudeMeters = p.altitudeMeters,
        accuracyMeters = p.accuracyMeters,
        speedMps = p.speedMps,
        satellitesUsed = state.usedCount,
        satellitesVisible = state.capturedCount,
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
