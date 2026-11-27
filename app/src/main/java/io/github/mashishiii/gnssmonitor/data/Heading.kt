package io.github.mashishiii.gnssmonitor.data

import kotlin.math.abs

/** 方位を更新する最低速度（m/s）。これより遅いときは直前の方位を保持する。現在地マーカーの矢印と同じ値（約 3.6 km/h）。 */
const val HEADING_MIN_SPEED_MPS = 1f

/** 地図を回したあと、現在地マーカーを置く位置: 地図領域の下端から、高さのこの割合（中央は 0.5）。 */
const val HEADING_UP_MARKER_FROM_BOTTOM = 0.35f

/** 度を 0 以上 360 未満にそろえる。 */
fun normalizeDegrees(degrees: Float): Float {
    if (!degrees.isFinite()) return 0f
    val r = degrees % 360f
    return if (r < 0f) r + 360f else r
}

/**
 * 進行方向（度、真北から時計回り）の更新。測位が速度 [HEADING_MIN_SPEED_MPS] 以上で、方位があるときだけ
 * その方位に更新し、それ以外（遅い・停車・方位なし・測位なし）は [previous] を保つ。
 * 方位の源は GNSS の対地コース（`Location.bearing`）だけ。電子コンパスは使わない。
 */
fun nextHeading(previous: Float?, position: PositionInfo?): Float? {
    val bearing = position?.bearingDegrees ?: return previous
    val speed = position.speedMps ?: return previous
    if (!bearing.isFinite() || !speed.isFinite() || speed < HEADING_MIN_SPEED_MPS) return previous
    return normalizeDegrees(bearing)
}

/** 地図とスカイプロットの向き。選んだ値は 1 つだけ保存して、両方に使う。 */
enum class MapOrientation(val label: String) {
    NORTH_UP("ノースアップ"),
    HEADING_UP("ヘディングアップ"),
    ;

    fun toggled(): MapOrientation = if (this == NORTH_UP) HEADING_UP else NORTH_UP

    companion object {
        /** 当時のカーナビの常用に合わせて、既定はヘディングアップ。 */
        val Default = HEADING_UP

        fun fromName(name: String?): MapOrientation = entries.firstOrNull { it.name == name } ?: Default
    }
}

/** 地図の動き方。レトロ = 1 秒ごとのジャンプ（補間なし）、スムーズ = 測位のあいだを補間。 */
enum class MotionMode(val label: String) {
    RETRO("レトロ"),
    SMOOTH("スムーズ"),
    ;

    fun next(): MotionMode = if (this == RETRO) SMOOTH else RETRO

    companion object {
        val Default = RETRO

        fun fromName(name: String?): MotionMode = entries.firstOrNull { it.name == name } ?: Default
    }
}

/** ヘディングアップで、方位が取れていて、実際に回っているか（方位が無いときは北が上のまま）。 */
fun headingUpActive(orientation: MapOrientation, heading: Float?): Boolean =
    orientation == MapOrientation.HEADING_UP && heading != null && heading.isFinite()

/** `MapView.setMapOrientation` に渡す角度。ノースアップ・方位なしは 0°、ヘディングアップは「地図の上 = 進行方向」になる `-heading`。 */
fun mapRotationFor(orientation: MapOrientation, heading: Float?): Float =
    if (headingUpActive(orientation, heading)) normalizeDegrees(-(heading ?: 0f)) else 0f

/** スカイプロットの文字盤を回す進行方向（度）。0 = 北が上。ノースアップ・方位なしは 0。 */
fun skyHeadingFor(orientation: MapOrientation, heading: Float?): Float =
    if (headingUpActive(orientation, heading)) normalizeDegrees(heading ?: 0f) else 0f

/**
 * 追従中の地図の向き。追従していないあいだは [current]（外した瞬間の角度）のまま。
 * 追従中は設定どおり（新しい測位が来るたびに合わせる）。
 */
fun mapRotationUpdate(follow: Boolean, orientation: MapOrientation, heading: Float?, current: Float): Float =
    if (follow) mapRotationFor(orientation, heading) else current

/**
 * 地図の中心（= 現在地）を、地図領域の中央から画面の下へずらす量（px。正が下）。
 * ヘディングアップで回っていて、追従中のときだけ。それ以外（ノースアップ・追従オフ・方位なし）は 0。
 * 高さに対する割合なので、折りたたみ・展開で同じ見え方になる。
 */
fun markerCenterOffsetPx(orientation: MapOrientation, heading: Float?, follow: Boolean, viewHeightPx: Int): Int =
    if (follow && headingUpActive(orientation, heading) && viewHeightPx > 0) {
        ((0.5f - HEADING_UP_MARKER_FROM_BOTTOM) * viewHeightPx).toInt()
    } else {
        0
    }

/** 表示用の姿勢: 位置と、地図 / 文字盤を回す進行方向。 */
data class DisplayPose(val latitude: Double, val longitude: Double, val heading: Float?)

/** 2 つの角度（度）の間を、短い回り方で補間する。 */
fun lerpAngle(from: Float, to: Float, t: Float): Float {
    var d = normalizeDegrees(to - from)
    if (d > 180f) d -= 360f
    return normalizeDegrees(from + d * t.coerceIn(0f, 1f))
}

fun lerpPose(from: DisplayPose, to: DisplayPose, t: Float): DisplayPose {
    val k = t.coerceIn(0f, 1f).toDouble()
    val h = when {
        from.heading != null && to.heading != null -> lerpAngle(from.heading, to.heading, t)
        else -> to.heading
    }
    return DisplayPose(
        from.latitude + (to.latitude - from.latitude) * k,
        from.longitude + (to.longitude - from.longitude) * k,
        h,
    )
}

/**
 * いま画面に出す姿勢。[to] は最新の測位、[from] は直前に表示していた姿勢、[t] は最新の測位が来てからの経過割合（0〜1）。
 * レトロは常に [to]（測位のあいだで何も変えない）、スムーズは [from] から [to] へ補間する。
 */
fun displayPose(mode: MotionMode, from: DisplayPose?, to: DisplayPose, t: Float): DisplayPose =
    if (mode == MotionMode.RETRO || from == null) to else lerpPose(from, to, t)

/** [abs] の誤差内で同じ角度か（テスト用）。 */
internal fun sameAngle(a: Float, b: Float, eps: Float = 1e-3f): Boolean {
    val d = abs(normalizeDegrees(a - b))
    return d < eps || 360f - d < eps
}
