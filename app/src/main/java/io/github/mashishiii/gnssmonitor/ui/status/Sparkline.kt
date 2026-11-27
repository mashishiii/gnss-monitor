package io.github.mashishiii.gnssmonitor.ui.status

import io.github.mashishiii.gnssmonitor.data.ACCURACY_HISTORY_SIZE
import kotlin.math.ceil

/** 描画に依存しない折れ線の点（ピクセル）。 */
data class SparkPoint(val x: Float, val y: Float)

/** 縦軸の最大値。データの最大を切り上げた値で、小さくなりすぎないよう [minMax] を下限にする。 */
fun sparklineMax(values: List<Float?>, minMax: Float = 5f): Float {
    val m = values.filterNotNull().filter { it.isFinite() }.maxOrNull() ?: return minMax
    return maxOf(minMax, ceil(m))
}

/**
 * 履歴を折れ線の区間に変える。新しい値が右端。値が無い（null）ところで線を切り、区間ごとのリストにする。
 * 点が 1 つだけの区間も返す（点として描く）。縦軸は 0（下）〜[maxValue]（上）。
 */
fun sparklineSegments(
    values: List<Float?>,
    width: Float,
    height: Float,
    maxValue: Float,
    slots: Int = ACCURACY_HISTORY_SIZE,
): List<List<SparkPoint>> {
    if (values.isEmpty() || width <= 0f || height <= 0f || maxValue <= 0f || slots < 2) return emptyList()
    val shown = values.takeLast(slots)
    val offset = slots - shown.size
    val segments = ArrayList<List<SparkPoint>>()
    var current = ArrayList<SparkPoint>()
    shown.forEachIndexed { i, v ->
        if (v == null || !v.isFinite()) {
            if (current.isNotEmpty()) segments.add(current)
            current = ArrayList()
        } else {
            val x = width * (offset + i) / (slots - 1)
            val y = height - (v / maxValue).coerceIn(0f, 1f) * height
            current.add(SparkPoint(x, y))
        }
    }
    if (current.isNotEmpty()) segments.add(current)
    return segments
}
