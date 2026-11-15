package app.years

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** How long the opening lasts, in milliseconds. The ripple through your years starts as it ends. */
const val INTRO_MS = 2300

private fun ease(t: Float) = 1f - (1f - t.coerceIn(0f, 1f)).pow(4)
private fun easeInOut(t: Float): Float { val x = t.coerceIn(0f, 1f); return if (x < .5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2 }
private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

/**
 * The opening, every time yrs. starts:
 * the name rises in and its dot fills to how much of your life you've lived; the letters dissolve; the dot flies
 * to the year you're in on your grid; and your life opens outward from it in a widening circle.
 * [target] is where that year's dot sits on screen (or null to open from the middle), [lived] is 0..1.
 */
@Composable
fun Intro(target: () -> Pair<Offset, Float>?, lived: Float, yearFill: Float, frozenAt: Float? = null, onDone: () -> Unit) {
    val t = remember { Animatable(frozenAt ?: 0f) }
    val view = LocalView.current
    LaunchedEffect(Unit) {
        if (frozenAt != null) { t.snapTo(frozenAt); return@LaunchedEffect }
        var ticked = false
        t.animateTo(1f, tween(INTRO_MS, easing = LinearEasing)) {
            // one soft tick as the dot lands
            if (!ticked && value > .82f) { ticked = true; view.tick() }
        }
        onDone()
    }
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 92.sp, letterSpacing = (-0.065).em, color = C.Fg)
    val word = remember { measurer.measure("yrs", style) }

    // touches stop here while the opening plays, so nothing underneath is pressed by accident
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val ms = t.value * INTRO_MS
        val W = size.width; val H = size.height
        // the wordmark, centred
        val tw = word.size.width.toFloat(); val th = word.size.height.toFloat()
        val d = 92.sp.toPx() * .24f
        val total = tw + d * 1.15f
        val origin = Offset((W - total) / 2f, H * .44f - th / 2f)
        val dotHome = Offset(origin.x + tw + d * .15f + d / 2f, origin.y + th - d * .55f - d / 2f - th * .06f)

        val rise = ease(seg(ms, 0f, 650f))              // the name rises in
        val fill = easeInOut(seg(ms, 250f, 1150f))      // its dot fills to your life so far
        val dissolve = ease(seg(ms, 1100f, 1500f))      // the letters fade and drift
        val fly = easeInOut(seg(ms, 1350f, 1900f))      // the dot flies to your year
        val open = easeInOut(seg(ms, 1720f, 2300f))     // the page opens from it

        val tgt = target()
        val dest = tgt?.first ?: Offset(W / 2, H / 2)
        val destR = tgt?.second ?: d / 2
        // a gentle arc rather than a straight line
        val arc = sin(fly * Math.PI).toFloat() * min(W, H) * .08f
        val pos = Offset(dotHome.x + (dest.x - dotHome.x) * fly - arc * .6f, dotHome.y + (dest.y - dotHome.y) * fly - arc)
        val r = (d / 2) * (1f + .35f * ease(seg(ms, 1100f, 1400f)) * (1f - fly)) + (destR - d / 2) * fly

        // black over everything, with a circle opening from where the dot landed
        drawRect(Color.Black)
        if (open > 0f) {
            val reach = hypot(W.toDouble(), H.toDouble()).toFloat()
            drawCircle(Color.Transparent, r + reach * open, pos, blendMode = BlendMode.Clear)
        }
        // the letters
        val a = rise * (1f - dissolve)
        if (a > 0f) translate(origin.x - dissolve * 24f, origin.y + (1f - rise) * 26f - dissolve * 6f) { drawText(word, alpha = a) }
        // the dot: dim, filling white from the bottom, glowing a little in flight
        val dotA = rise * (1f - ease(seg(ms, 2050f, 2300f)) * if (tgt == null) 1f else 0f)
        if (fly > 0f && fly < 1f) drawCircle(Color.White.copy(alpha = .10f * sin(fly * Math.PI).toFloat()), r * 2.6f, pos)
        // it fills to your whole life so far, then, as it lands, to how far you are through this year, like the dot it becomes
        val f = (lived * fill) + ((if (tgt != null) yearFill else lived) - lived * fill) * fly
        drawDot(pos.copy(y = pos.y + (1f - rise) * 26f * (1f - fly)), r, f.coerceIn(0f, 1f), dotA)
    }
}

private fun DrawScope.drawDot(c: Offset, r: Float, f: Float, a: Float) {
    if (a <= 0f) return
    drawCircle(C.Partial, r, c, alpha = a)
    clipPath(Path().apply { addOval(Rect(c, r)) }) {
        drawRect(C.Fg, Offset(c.x - r, c.y + r - 2 * r * f), Size(2 * r, 2 * r * f), alpha = a)
    }
}
