// yrs. music: modified from Lotus by dn0ne (GPL-3.0). Hand-drawn glyphs, one stroke weight.
package com.dn0ne.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun f(v: Float) = "%.3f".format(java.util.Locale.ROOT, v)

/** The rounded triangle used for play, next and previous (same maths as the design). */
private fun tri(x: Float, y: Float, w: Float, h: Float): String {
    val r = 1.6f
    return "M${f(x)} ${f(y + r * 1.3f)}c0 -${f(r)} 1.15 -${f(r * 1.5f)} 2.05 -1.05" +
        "l${f(w - 2.6f)} ${f(h / 2 - 1.3f)}c.85 .55 .85 1.85 0 2.4" +
        "L${f(x + 2.05f)} ${f(y + h + r * .25f)}C${f(x + 1.15f)} ${f(y + h + .75f)} ${f(x)} ${f(y + h)} ${f(x)} ${f(y + h - r * 1.3f)}z"
}

private fun icon(name: String, fills: List<String> = emptyList(), strokes: List<String> = emptyList(), width: Float = 1.6f, mirror: Boolean = false): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    if (mirror) b.addGroup(scaleX = -1f, pivotX = 12f)
    fills.forEach { b.addPath(addPathNodes(it), fill = SolidColor(Color.White)) }
    strokes.forEach {
        b.addPath(
            addPathNodes(it), stroke = SolidColor(Color.White), strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
    if (mirror) b.clearGroup()
    return b.build()
}

private const val HEART = "M12 19s-7-4.2-7-9.3A3.9 3.9 0 0 1 12 7.5a3.9 3.9 0 0 1 7 2.2C19 14.8 12 19 12 19z"

object Glyph {
    val More = icon("more", fills = listOf(
        "M4 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0z",
        "M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0z",
        "M17 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0z",
    ))
    val Down = icon("down", strokes = listOf("M6.5 9.5l5.5 5.5 5.5-5.5"))
    val Back = icon("back", strokes = listOf("M14.5 6l-6 6 6 6"))
    val Heart = icon("heart", strokes = listOf(HEART))
    val HeartFull = icon("heartFull", fills = listOf(HEART), strokes = listOf(HEART))
    val Play = icon("play", fills = listOf(tri(6.4f, 4.3f, 14.2f, 15.4f)))
    val Pause = icon("pause", fills = listOf(
        "M7.4 4.5h1.4a1.2 1.2 0 0 1 1.2 1.2v12.6a1.2 1.2 0 0 1 -1.2 1.2h-1.4a1.2 1.2 0 0 1 -1.2 -1.2v-12.6a1.2 1.2 0 0 1 1.2 -1.2z",
        "M15.2 4.5h1.4a1.2 1.2 0 0 1 1.2 1.2v12.6a1.2 1.2 0 0 1 -1.2 1.2h-1.4a1.2 1.2 0 0 1 -1.2 -1.2v-12.6a1.2 1.2 0 0 1 1.2 -1.2z",
    ))
    val Next = icon("next", fills = listOf(tri(3.2f, 6.3f, 9.4f, 11.4f), tri(11.9f, 6.3f, 9.4f, 11.4f)))
    val Prev = icon("prev", fills = listOf(tri(3.2f, 6.3f, 9.4f, 11.4f), tri(11.9f, 6.3f, 9.4f, 11.4f)), mirror = true)
    val Grip = icon("grip", strokes = listOf("M6 9.5h12M6 14.5h12"))
    val Search = icon("search", strokes = listOf("M10.8 17.1a6.3 6.3 0 1 0 0 -12.6a6.3 6.3 0 1 0 0 12.6z", "M15.4 15.4l4.1 4.1"))
    val Close = icon("close", strokes = listOf("M6.5 6.5l11 11M17.5 6.5l-11 11"))
}

@Composable
fun Glyph(icon: ImageVector, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Image(icon, contentDescription = null, colorFilter = ColorFilter.tint(color), modifier = modifier.size(size))
}
