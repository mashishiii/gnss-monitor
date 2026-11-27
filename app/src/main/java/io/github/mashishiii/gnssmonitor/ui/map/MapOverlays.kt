package io.github.mashishiii.gnssmonitor.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.MotionEvent
import io.github.mashishiii.gnssmonitor.BuildConfig
import io.github.mashishiii.gnssmonitor.data.LogPoint
import io.github.mashishiii.gnssmonitor.data.PositionInfo
import io.github.mashishiii.gnssmonitor.data.TrackColorMode
import io.github.mashishiii.gnssmonitor.data.TrackQuality
import io.github.mashishiii.gnssmonitor.data.ACCURACY_95_FACTOR
import io.github.mashishiii.gnssmonitor.data.accuracyQuality
import io.github.mashishiii.gnssmonitor.data.isTrackGap
import io.github.mashishiii.gnssmonitor.data.trackQuality
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/**
 * osmdroid の設定。OpenStreetMap のタイル利用規約に沿って、アプリを識別できる User-Agent を送り、
 * 一度表示したタイルはアプリ専用領域にキャッシュする（オフラインでもその範囲は表示できる）。
 * 一括ダウンロード（範囲の先読み）はしない。
 */
object MapConfig {
    const val USER_AGENT_BASE = "GNSSMonitor"
    const val PROJECT_URL = "https://github.com/mashishiii/gnss-monitor"

    fun userAgent(versionName: String): String = "$USER_AGENT_BASE/$versionName (+$PROJECT_URL)"

    @Volatile private var configured = false

    fun ensure(context: Context) {
        if (configured) return
        synchronized(this) {
            if (configured) return
            val app = context.applicationContext
            val conf = Configuration.getInstance()
            conf.load(app, app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            conf.userAgentValue = userAgent(BuildConfig.VERSION_NAME)
            val base = File(app.filesDir, "osmdroid")
            conf.osmdroidBasePath = base
            conf.osmdroidTileCache = File(base, "tiles")
            // キャッシュは最大 300MB、超えたら 250MB まで古いものから削除
            conf.tileFileSystemCacheMaxBytes = 300L * 1024 * 1024
            conf.tileFileSystemCacheTrimBytes = 250L * 1024 * 1024
            // OSM の推奨に合わせて同時ダウンロードは 2 本まで
            conf.tileDownloadThreads = 2
            conf.tileDownloadMaxQueueSize = 40
            configured = true
        }
    }
}

/** 軌跡の色（地図ラフに合わせる）。 */
object TrackColors {
    const val GOOD = 0xFF35C27A.toInt()
    const val MID = 0xFFE6C229.toInt()
    const val LOW = 0xFFF0803C.toInt()

    fun of(q: TrackQuality): Int = when (q) {
        TrackQuality.GOOD -> GOOD
        TrackQuality.MID -> MID
        TrackQuality.LOW -> LOW
    }
}

/** 精度の円の線の色と、その背景になる地図タイルの代表色（コントラスト確認用）。 */
object AccuracyCircleColors {
    const val STROKE = 0xFFD83A34.toInt()
    /** OSM 標準タイルの地の色（昼）。 */
    const val DAY_TILE = 0xFFF2EFE9.toInt()
    /** 夜は色反転フィルタをかけるので、昼の地の色の反転。 */
    const val NIGHT_TILE = 0xFF0D1016.toInt()
}

/** 精度の円の描き方。半径が画面を大きくはみ出すときは、地図を埋め尽くさないよう輪郭だけ・描かない。 */
enum class CircleDrawMode { FILLED, OUTLINE, HIDDEN }

fun circleDrawMode(radiusPx: Float, viewMaxPx: Float, minPx: Float): CircleDrawMode = when {
    !radiusPx.isFinite() || radiusPx <= minPx -> CircleDrawMode.HIDDEN
    viewMaxPx <= 0f || radiusPx <= viewMaxPx -> CircleDrawMode.FILLED
    radiusPx <= viewMaxPx * 8f -> CircleDrawMode.OUTLINE
    else -> CircleDrawMode.HIDDEN
}

/** 軌跡を色分けした線。既定は使用衛星数、切り替えで水平精度 (68%)。間が空いた区間は点線。 */
class TrackOverlay(private val density: Float) : Overlay() {
    var points: List<LogPoint> = emptyList()
    var colorMode: TrackColorMode = TrackColorMode.SATELLITES

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val gapPaint = Paint(paint).apply {
        color = TrackColors.LOW
        strokeWidth = 3.5f * density
        pathEffect = DashPathEffect(floatArrayOf(2f * density, 7f * density), 0f)
    }
    private val a = Point()
    private val b = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        val list = points
        if (list.size < 2) return
        // 同じ色が続く区間をまとめて 1 本のパスで描く
        var path = Path()
        var currentColor = 0
        projection.toPixels(GeoPoint(list[0].latitude, list[0].longitude), a)
        path.moveTo(a.x.toFloat(), a.y.toFloat())
        for (i in 1 until list.size) {
            val prev = list[i - 1]
            val next = list[i]
            projection.toPixels(GeoPoint(next.latitude, next.longitude), b)
            if (isTrackGap(prev, next)) {
                if (currentColor != 0) {
                    paint.color = currentColor
                    canvas.drawPath(path, paint)
                }
                canvas.drawLine(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), gapPaint)
                path = Path().apply { moveTo(b.x.toFloat(), b.y.toFloat()) }
                currentColor = 0
            } else {
                val color = TrackColors.of(
                    when (colorMode) {
                        TrackColorMode.SATELLITES -> trackQuality(next.satellitesUsed)
                        TrackColorMode.ACCURACY -> accuracyQuality(next.accuracyMeters)
                    },
                )
                if (currentColor != 0 && color != currentColor) {
                    paint.color = currentColor
                    canvas.drawPath(path, paint)
                    path = Path().apply { moveTo(a.x.toFloat(), a.y.toFloat()) }
                }
                currentColor = color
                path.lineTo(b.x.toFloat(), b.y.toFloat())
            }
            a.set(b.x, b.y)
        }
        if (currentColor != 0) {
            paint.color = currentColor
            canvas.drawPath(path, paint)
        }
    }
}

/** 現在地（精度の円 2 つ — 68% は塗り + 実線、95% 目安は破線 — と、カーナビ風の赤い矢印。止まっているときは丸）。 */
class PositionOverlay(private val density: Float) : Overlay() {
    var position: PositionInfo? = null

    private val accuracyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33D83A34; style = Paint.Style.FILL }
    private val accuracyStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88D83A34.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f * density
    }
    private val accuracy95Stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AccuracyCircleColors.STROKE; style = Paint.Style.STROKE; strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD83A34.toInt(); style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f * density; strokeJoin = Paint.Join.ROUND
    }
    private val pt = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        val p = position ?: return
        projection.toPixels(GeoPoint(p.latitude, p.longitude), pt)
        val x = pt.x.toFloat()
        val y = pt.y.toFloat()
        p.accuracyMeters?.let { acc ->
            val viewMax = maxOf(canvas.width, canvas.height).toFloat()
            val r = projection.metersToPixels(acc, p.latitude, projection.zoomLevel)
            when (circleDrawMode(r, viewMax, 4f * density)) {
                CircleDrawMode.FILLED -> {
                    canvas.drawCircle(x, y, r, accuracyFill)
                    canvas.drawCircle(x, y, r, accuracyStroke)
                }
                CircleDrawMode.OUTLINE -> canvas.drawCircle(x, y, r, accuracyStroke)
                CircleDrawMode.HIDDEN -> Unit
            }
            // 95% の目安（68% 半径 × ACCURACY_95_FACTOR）は破線の輪郭だけ
            val r95 = r * ACCURACY_95_FACTOR
            if (circleDrawMode(r95, viewMax, 4f * density) != CircleDrawMode.HIDDEN) {
                canvas.drawCircle(x, y, r95, accuracy95Stroke)
            }
        }
        val bearing = p.bearingDegrees
        val moving = (p.speedMps ?: 0f) >= 1f && bearing != null
        if (moving) {
            val s = density
            val path = Path().apply {
                moveTo(0f, -12f * s); lineTo(9f * s, 10f * s); lineTo(0f, 5f * s); lineTo(-9f * s, 10f * s); close()
            }
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(bearing)
            canvas.drawPath(path, fill)
            canvas.drawPath(path, stroke)
            canvas.restore()
        } else {
            canvas.drawCircle(x, y, 8f * density, fill)
            canvas.drawCircle(x, y, 8f * density, stroke)
        }
    }
}

/** 地図を指で動かしたら「現在地に追従」を解除するための検出用。 */
class UserGestureOverlay(private val onUserMove: () -> Unit) : Overlay() {
    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_MOVE) onUserMove()
        return false
    }
}
