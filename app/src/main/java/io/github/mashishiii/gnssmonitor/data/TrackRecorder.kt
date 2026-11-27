package io.github.mashishiii.gnssmonitor.data

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 記録中の軌跡を保持し、1 点ずつアプリ専用領域のファイルへ追記する（アプリが終了しても最後の記録が残る）。
 * 保存先: [dir]/last_track.csv。端末の外へは送らない。メインスレッドから使う。
 */
class TrackRecorder(private val dir: File) {

    data class State(
        val recording: Boolean = false,
        val startedAtMillis: Long? = null,
        val points: List<LogPoint> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val points = ArrayList<LogPoint>()
    private var writer: BufferedWriter? = null

    private val file get() = File(dir, FILE_NAME)

    /** 前回の記録を読み込む（アプリ起動時）。 */
    fun loadLast() {
        if (_state.value.recording) return
        val loaded = runCatching { if (file.exists()) TrackFormats.parseCsv(file.readText()) else emptyList() }
            .getOrDefault(emptyList())
        points.clear()
        points.addAll(loaded)
        _state.value = State(recording = false, startedAtMillis = loaded.firstOrNull()?.timeMillis, points = loaded)
    }

    /** 新しい記録を始める。前回の記録は消える。 */
    fun begin(nowMillis: Long) {
        closeWriter()
        points.clear()
        runCatching {
            dir.mkdirs()
            writer = BufferedWriter(FileWriter(file, false)).also {
                it.write(TrackFormats.CSV_HEADER)
                it.write("\r\n")
                it.flush()
            }
        }
        _state.value = State(recording = true, startedAtMillis = nowMillis, points = emptyList())
    }

    /** 記録中なら 1 点追加する。追加したら true。 */
    fun add(point: LogPoint): Boolean {
        if (!_state.value.recording) return false
        if (!shouldRecord(points.lastOrNull(), point)) return false
        points.add(point)
        runCatching {
            writer?.apply {
                write(TrackFormats.csvLine(point))
                write("\r\n")
                flush()
            }
        }
        _state.value = _state.value.copy(points = points.toList())
        return true
    }

    fun end() {
        closeWriter()
        if (_state.value.recording) _state.value = _state.value.copy(recording = false)
    }

    private fun closeWriter() {
        runCatching { writer?.close() }
        writer = null
    }

    companion object {
        const val FILE_NAME = "last_track.csv"
    }
}
