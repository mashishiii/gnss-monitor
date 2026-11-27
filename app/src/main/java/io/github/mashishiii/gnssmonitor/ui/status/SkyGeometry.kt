package io.github.mashishiii.gnssmonitor.ui.status

import io.github.mashishiii.gnssmonitor.data.MarkerShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 描画に依存しない座標（ピクセル）。 */
data class Point(val x: Float, val y: Float)

/** スカイプロットの円の位置と大きさ（ピクセル）。 */
data class SkyLayout(val cx: Float, val cy: Float, val radius: Float)

/** 描画領域から、外周ラベルの余白 [marginPx] を引いた円のレイアウトを求める。 */
fun skyLayout(width: Float, height: Float, marginPx: Float): SkyLayout =
    SkyLayout(width / 2f, height / 2f, (min(width, height) / 2f - marginPx).coerceAtLeast(1f))

/**
 * 方位・仰角（度）をスカイプロット上の座標へ。天頂（仰角 90°）が中心、地平線（0°）が外周。
 * 既定（[headingDegrees] = 0）は北が上、東が右。ヘディングアップでは進行方向 [headingDegrees] を上にするので、
 * 方位から進行方向を引いた角度で置く。範囲外・NaN の値は安全な範囲に丸める。
 */
fun skyPosition(azimuthDegrees: Float, elevationDegrees: Float, layout: SkyLayout, headingDegrees: Float = 0f): Point {
    val el = if (elevationDegrees.isFinite()) elevationDegrees.coerceIn(0f, 90f) else 0f
    val az = if (azimuthDegrees.isFinite()) azimuthDegrees else 0f
    val h = if (headingDegrees.isFinite()) headingDegrees else 0f
    val r = layout.radius * (90f - el) / 90f
    val a = (az - h).toDouble() * PI / 180.0
    return Point(
        x = layout.cx + (r * sin(a)).toFloat(),
        y = layout.cy - (r * cos(a)).toFloat(),
    )
}

/**
 * 外周ラベル（N / E / S / W）の中心。真北から時計回りの角度 [compassDegrees]（N = 0、E = 90、S = 180、W = 270）の
 * 方向で、外周の [gapPx] だけ外側。ヘディングアップでは [headingDegrees] ぶん回る（東向きなら N は左）。
 */
fun skyRimPosition(compassDegrees: Float, layout: SkyLayout, gapPx: Float, headingDegrees: Float = 0f): Point {
    val a = (compassDegrees - headingDegrees).toDouble() * PI / 180.0
    val r = layout.radius + gapPx
    return Point(
        x = layout.cx + (r * sin(a)).toFloat(),
        y = layout.cy - (r * cos(a)).toFloat(),
    )
}

/** スカイプロットのタップの結果。 */
sealed interface SkyTap {
    /** 外周の「N」: 向きの切り替え。 */
    data object ToggleOrientation : SkyTap

    /** 球の選択（同じ球をもう一度 = null で解除）。 */
    data class Select(val key: String?) : SkyTap
}

/**
 * タップを「N の切り替え」か「球の選択」に振り分ける。「N」（中心 [northCenter]、半径 [northHitRadius] 以内）は
 * 球より優先。それ以外は [hitRadius] 内で最も近い球。
 */
fun skyTapAction(
    markers: List<Pair<String, Point>>,
    northCenter: Point,
    x: Float,
    y: Float,
    hitRadius: Float,
    northHitRadius: Float,
    selectedKey: String?,
): SkyTap {
    val dx = northCenter.x - x
    val dy = northCenter.y - y
    if (sqrt(dx * dx + dy * dy) <= northHitRadius) return SkyTap.ToggleOrientation
    val key = hitTest(markers, x, y, hitRadius)
    return SkyTap.Select(if (key == null || key == selectedKey) null else key)
}

/** 仰角リング（60°・30°・0°）の半径。 */
fun elevationRingRadius(elevationDegrees: Float, layout: SkyLayout): Float =
    layout.radius * (90f - elevationDegrees) / 90f

/**
 * タップ位置に最も近いマーカーのキーを返す。[hitRadius] より遠ければ null。
 * 同じ距離なら先に渡されたものが優先。
 */
fun hitTest(markers: List<Pair<String, Point>>, x: Float, y: Float, hitRadius: Float): String? {
    var bestKey: String? = null
    var bestDist = Float.MAX_VALUE
    for ((key, p) in markers) {
        val dx = p.x - x
        val dy = p.y - y
        val d = sqrt(dx * dx + dy * dy)
        if (d <= hitRadius && d < bestDist) {
            bestDist = d
            bestKey = key
        }
    }
    return bestKey
}

/** 棒グラフ: タップ位置のスロット番号（範囲外は null）。 */
fun barIndexAt(x: Float, slotWidth: Float, count: Int): Int? {
    if (slotWidth <= 0f || count <= 0 || !x.isFinite() || x < 0f) return null
    val i = (x / slotWidth).toInt()
    return i.takeIf { it in 0 until count }
}

/** 中心 (0,0) 基準のマーカー頂点。円は null（円として描く）。 */
fun markerVertices(shape: MarkerShape, r: Float): List<Point>? = when (shape) {
    MarkerShape.CIRCLE -> null
    MarkerShape.DIAMOND -> {
        val k = r * 1.25f
        listOf(Point(0f, -k), Point(k, 0f), Point(0f, k), Point(-k, 0f))
    }
    MarkerShape.SQUARE -> {
        val k = r * 0.9f
        listOf(Point(-k, -k), Point(k, -k), Point(k, k), Point(-k, k))
    }
    MarkerShape.TRIANGLE ->
        listOf(Point(0f, -r * 1.2f), Point(r * 1.15f, r * 0.85f), Point(-r * 1.15f, r * 0.85f))
    MarkerShape.HEXAGON -> (0 until 6).map { k ->
        val a = PI / 3.0 * k
        Point((r * 1.1f * cos(a)).toFloat(), (r * 1.1f * sin(a)).toFloat())
    }
}
