package io.github.mashishiii.gnssmonitor.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogPointTest {
    private fun sats(used: Int, visible: Int) =
        (1..visible).map { SatelliteInfo(1, it, 30f, 0f, 45f, it <= used) }

    private val pos = PositionInfo(35.0, 139.0, 3f, 10.0, 1_000_000L, 2.5f, 90f)

    @Test
    fun logPointNeedsFixAndTime() {
        assertNull(logPointFrom(GnssUiState(position = null, satellites = sats(8, 10))))
        assertNull(logPointFrom(GnssUiState(position = pos, satellites = sats(2, 10))))
        assertNull(logPointFrom(GnssUiState(position = pos.copy(timeMillis = null), satellites = sats(8, 10))))
        assertNull(logPointFrom(GnssUiState(gpsEnabled = false, position = pos, satellites = sats(8, 10))))
        val p = logPointFrom(GnssUiState(position = pos, satellites = sats(8, 10)))!!
        assertEquals(8, p.satellitesUsed)
        assertEquals(10, p.satellitesVisible)
        assertEquals(2.5f, p.speedMps!!, 0f)
        assertEquals(10.0, p.altitudeMeters!!, 0.0)
    }

    @Test
    fun recordOnlyNewerFixes() {
        val a = LogPoint(1000, 0.0, 0.0)
        assertTrue(shouldRecord(null, a))
        assertFalse(shouldRecord(a, a.copy()))
        assertFalse(shouldRecord(a, a.copy(timeMillis = 999)))
        assertTrue(shouldRecord(a, a.copy(timeMillis = 2000)))
    }

    @Test
    fun trackQualityAndGaps() {
        assertEquals(TrackQuality.GOOD, trackQuality(8))
        assertEquals(TrackQuality.MID, trackQuality(5))
        assertEquals(TrackQuality.MID, trackQuality(7))
        assertEquals(TrackQuality.LOW, trackQuality(4))
        val a = LogPoint(0, 0.0, 0.0)
        assertFalse(isTrackGap(a, a.copy(timeMillis = 5000)))
        assertTrue(isTrackGap(a, a.copy(timeMillis = 5001)))
    }

    @Test
    fun recorderPersistsAndReloads() {
        val dir = Files.createTempDirectory("track").toFile()
        val r = TrackRecorder(dir)
        assertFalse(r.add(LogPoint(1000, 1.0, 2.0)))  // 記録していないときは追加しない
        r.begin(500)
        assertTrue(r.state.value.recording)
        assertTrue(r.add(LogPoint(1000, 1.0, 2.0, satellitesUsed = 9)))
        assertFalse(r.add(LogPoint(1000, 1.0, 2.0)))  // 同じ時刻は捨てる
        assertTrue(r.add(LogPoint(2000, 1.1, 2.1)))
        assertEquals(2, r.state.value.points.size)
        r.end()
        assertFalse(r.state.value.recording)

        val reloaded = TrackRecorder(dir).also { it.loadLast() }
        assertEquals(2, reloaded.state.value.points.size)
        assertEquals(9, reloaded.state.value.points[0].satellitesUsed)
        assertEquals(1000L, reloaded.state.value.startedAtMillis)

        // 新しい記録で上書き
        reloaded.begin(3000)
        assertEquals(0, reloaded.state.value.points.size)
        reloaded.end()
        assertEquals(0, TrackRecorder(dir).also { it.loadLast() }.state.value.points.size)
        dir.deleteRecursively()
    }
}
