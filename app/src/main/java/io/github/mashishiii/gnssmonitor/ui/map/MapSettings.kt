package io.github.mashishiii.gnssmonitor.ui.map

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.mashishiii.gnssmonitor.data.MapOrientation
import io.github.mashishiii.gnssmonitor.data.MotionMode

/**
 * 地図とスカイプロットで共通の設定（向き・動き）。端末に保存する（軌跡の色と同じ SharedPreferences）。
 * 向きは 1 つだけ保存し、測位状況の「N」でも地図の方位バッジでも同じ値が切り替わる。
 */
class MapSettings private constructor(
    private val prefs: SharedPreferences?,
    orientation: MapOrientation,
    motion: MotionMode,
) {
    var orientation by mutableStateOf(orientation)
        private set
    var motion by mutableStateOf(motion)
        private set

    fun toggleOrientation() {
        orientation = orientation.toggled()
        prefs?.edit()?.putString(KEY_ORIENTATION, orientation.name)?.apply()
    }

    fun nextMotion() {
        motion = motion.next()
        prefs?.edit()?.putString(KEY_MOTION, motion.name)?.apply()
    }

    companion object {
        fun load(context: Context, orientation: MapOrientation? = null, motion: MotionMode? = null): MapSettings {
            val prefs = context.getSharedPreferences(MAP_PREFS, Context.MODE_PRIVATE)
            return MapSettings(
                prefs,
                orientation ?: MapOrientation.fromName(prefs.getString(KEY_ORIENTATION, null)),
                motion ?: MotionMode.fromName(prefs.getString(KEY_MOTION, null)),
            )
        }

        /** 保存しない設定（プレビューやテスト用）。 */
        fun inMemory(orientation: MapOrientation = MapOrientation.Default, motion: MotionMode = MotionMode.Default) =
            MapSettings(null, orientation, motion)
    }
}

internal const val KEY_ORIENTATION = "orientation"
internal const val KEY_MOTION = "motion"
