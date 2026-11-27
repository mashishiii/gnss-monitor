package io.github.mashishiii.gnssmonitor.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mashishiii.gnssmonitor.data.GpsLevel

/*
 * nav-u 風の部品。
 *  - 光沢ボタン: 上半分に明るいハイライトを重ねた縦グラデーションと、上が明るく下が暗い縁（ベベル）
 *  - パネル: 上が明るく下が暗い縦グラデーションに、上端の細いハイライト線
 *  - へこんだ値ボックス: 上端が暗く下端が明るい（液晶の表示枠のような見た目）
 * ロゴや意匠そのものは使わず、質感だけを真似ている。
 */

/** 光沢ボタン／光沢バー用の背景。 */
fun Modifier.glossy(shape: Shape, top: Color, bottom: Color, rim: Boolean = true): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val body = Brush.verticalGradient(listOf(top, bottom))
    val gloss = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f)),
        startY = 0f,
        endY = size.height / 2f,
    )
    val rimBrush = Brush.verticalGradient(listOf(NavColors.RimTop, NavColors.RimBottom))
    val stroke = Stroke(1.dp.toPx())
    onDrawBehind {
        drawOutline(outline, body)
        clipRect(bottom = size.height / 2f) { drawOutline(outline, gloss) }
        if (rim) drawOutline(outline, rimBrush, style = stroke)
    }
}

/** パネル（枠つきの面）。 */
fun Modifier.panelSurface(shape: Shape = RoundedCornerShape(10.dp)): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val body = Brush.verticalGradient(listOf(NavColors.PanelTop, NavColors.PanelBottom))
    val border = NavColors.PanelBorder
    val stroke = Stroke(1.dp.toPx())
    val lit = if (NavColors.isNight) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.9f)
    onDrawBehind {
        drawOutline(outline, body)
        drawOutline(outline, border, style = stroke)
        // 上端の内側のハイライト線
        drawLine(lit, Offset(3.dp.toPx(), 1.5.dp.toPx()), Offset(size.width - 3.dp.toPx(), 1.5.dp.toPx()), 1.dp.toPx())
    }
}

/** へこんだ値ボックス（緯度・経度などの表示枠）。 */
fun Modifier.insetBox(): Modifier = drawWithCache {
    val fill = NavColors.ValueBox
    val dark = if (NavColors.isNight) Color.Black.copy(alpha = 0.45f) else Color(0xFF7890A8).copy(alpha = 0.75f)
    val light = if (NavColors.isNight) Color.White.copy(alpha = 0.18f) else Color.White
    val w = 1.dp.toPx()
    onDrawBehind {
        drawRect(fill)
        drawRect(Color.Black.copy(alpha = if (NavColors.isNight) 0.10f else 0.04f), size = androidx.compose.ui.geometry.Size(size.width, size.height / 2f))
        drawLine(dark, Offset(0f, w / 2f), Offset(size.width, w / 2f), w)
        drawLine(dark, Offset(w / 2f, 0f), Offset(w / 2f, size.height), w)
        drawLine(light, Offset(0f, size.height - w / 2f), Offset(size.width, size.height - w / 2f), w)
        drawLine(light, Offset(size.width - w / 2f, 0f), Offset(size.width - w / 2f, size.height), w)
    }
}

/** 下部ツールバーや記録バーの背景（光沢のある横長の帯）。 */
fun Modifier.toolbarSurface(): Modifier = drawWithCache {
    val body = Brush.verticalGradient(listOf(NavColors.BarTop, NavColors.BarBottom))
    val gloss = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.0f)),
        startY = 0f,
        endY = size.height / 2f,
    )
    val edge = NavColors.PanelBorder
    onDrawBehind {
        drawRect(body)
        clipRect(bottom = size.height / 2f) { drawRect(gloss) }
        drawLine(edge, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx())
    }
}

/**
 * 大きめのタッチボタン（nav-u 風に、控えめな濃いグレーの角丸）。[on] は選択中（えんじ色）。
 * [tint] を渡すとその色（記録停止の赤など）。[enabled] が false なら薄く表示してタップを無視する。
 */
@Composable
fun NavButton(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    fontSize: Int = 13,
    enabled: Boolean = true,
    horizontalPadding: Int = 12,
) {
    val shape = RoundedCornerShape(10.dp)
    val top: Color
    val bottom: Color
    val textColor: Color
    when {
        tint != null -> {
            top = lighten(tint, 0.12f); bottom = tint; textColor = Color.White
        }
        on -> {
            top = NavColors.ButtonOnTop; bottom = NavColors.ButtonOnBottom; textColor = NavColors.OnButtonOn
        }
        else -> {
            top = NavColors.ButtonTop; bottom = NavColors.ButtonBottom; textColor = NavColors.TextPrimary
        }
    }
    Text(
        label,
        color = textColor,
        fontSize = fontSize.sp,
        fontWeight = if (on || tint != null) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 44.dp)
            .glossy(shape, top, bottom)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding.dp, vertical = 11.dp),
    )
}

/** 「← 戻る」ボタン（nav-u の画面右下のボタンに倣う）。 */
@Composable
fun BackButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 44.dp)
            .glossy(shape, NavColors.ButtonTop, NavColors.ButtonBottom)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = NavColors.TextPrimary
        Canvas(Modifier.size(14.dp)) {
            val w = 2.dp.toPx()
            val y = size.height / 2f
            drawLine(c, Offset(size.width, y), Offset(1.dp.toPx(), y), w, StrokeCap.Round)
            drawLine(c, Offset(1.dp.toPx(), y), Offset(size.width * 0.5f, y - size.height * 0.4f), w, StrokeCap.Round)
            drawLine(c, Offset(1.dp.toPx(), y), Offset(size.width * 0.5f, y + size.height * 0.4f), w, StrokeCap.Round)
        }
        Text("戻る", color = NavColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

fun lighten(c: Color, amount: Float): Color = Color(
    red = c.red + (1f - c.red) * amount,
    green = c.green + (1f - c.green) * amount,
    blue = c.blue + (1f - c.blue) * amount,
    alpha = c.alpha,
)

fun lampColor(level: GpsLevel): Color = when (level) {
    GpsLevel.FIX -> NavColors.LampFix
    GpsLevel.ACQUIRING -> NavColors.LampAcquiring
    GpsLevel.NONE -> NavColors.LampNone
}

/** 受信レベルのランプ（光沢のある丸）。 */
@Composable
fun GpsLamp(level: GpsLevel, modifier: Modifier = Modifier, size: Int = 14) {
    val c = lampColor(level)
    Box(
        modifier
            .size(size.dp)
            .drawWithCache {
                val r = this.size.minDimension / 2f
                val body = Brush.radialGradient(
                    listOf(lighten(c, 0.55f), c, c.copy(alpha = 1f).let { Color(it.red * 0.65f, it.green * 0.65f, it.blue * 0.65f) }),
                    center = Offset(r * 0.8f, r * 0.7f),
                    radius = r * 1.4f,
                )
                onDrawBehind {
                    drawCircle(body, r)
                    drawCircle(Color.Black.copy(alpha = 0.45f), r, style = Stroke(1.dp.toPx()))
                }
            },
    )
}

/** 画面上部のタイトル帯（nav-u のように、えんじ色の無地の帯に白い文字）。右に受信レベルのランプ。 */
@Composable
fun TitleBar(title: String, level: GpsLevel, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .drawWithCache {
                val body = Brush.verticalGradient(listOf(NavColors.TitleTop, NavColors.TitleBottom))
                onDrawBehind {
                    drawRect(body)
                    drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
                }
            }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = NavColors.OnTitle, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GpsLamp(level)
            Text(level.label, color = NavColors.OnTitle, fontSize = 12.sp)
        }
    }
}

/** 丸い円盤状の飾り（地図の方位マークなど）。 */
fun Modifier.dial(): Modifier = glossy(CircleShape, NavColors.ButtonTop, NavColors.ButtonBottom)

fun darken(c: Color, amount: Float): Color = Color(
    red = c.red * (1f - amount),
    green = c.green * (1f - amount),
    blue = c.blue * (1f - amount),
    alpha = c.alpha,
)
