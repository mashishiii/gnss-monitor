package io.github.mashishiii.gnssmonitor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import io.github.mashishiii.gnssmonitor.data.DisplayPose
import io.github.mashishiii.gnssmonitor.data.MotionMode
import io.github.mashishiii.gnssmonitor.data.displayPose

/** 補間にかける時間。測位の間隔（1 秒）と同じ。 */
private const val SMOOTH_DURATION_NANOS = 1_000_000_000f

/**
 * 画面に出す姿勢（位置・進行方向）。レトロは [target]（最新の測位）をそのまま返す = 1 秒ごとにジャンプ。
 * スムーズは、新しい測位が来るたびに、直前に表示していた姿勢から [target] まで 1 秒かけて補間する。
 */
@Composable
fun rememberDisplayPose(target: DisplayPose?, motion: MotionMode): DisplayPose? {
    var shown by remember { mutableStateOf<DisplayPose?>(null) }
    LaunchedEffect(target, motion) {
        if (motion != MotionMode.SMOOTH || target == null) {
            shown = target
            return@LaunchedEffect
        }
        val from = shown ?: target
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val t = ((now - start) / SMOOTH_DURATION_NANOS).coerceIn(0f, 1f)
            shown = displayPose(MotionMode.SMOOTH, from, target, t)
            if (t >= 1f) break
        }
    }
    return if (motion == MotionMode.RETRO) target else shown ?: target
}

/** 進行方向だけの [rememberDisplayPose]（スカイプロット用）。 */
@Composable
fun rememberDisplayHeading(heading: Float?, motion: MotionMode): Float? {
    val target = remember(heading) { heading?.let { DisplayPose(0.0, 0.0, it) } }
    return rememberDisplayPose(target, motion)?.heading
}
