package app.years

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun PreviewGrid(total: Int, done: Double) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = with(LocalDensity.current) { maxWidth.toPx() }
        val h = Render.gridHeight(w, 10, total)
        Canvas(Modifier.fillMaxWidth().height(with(LocalDensity.current) { h.toDp() })) {
            drawIntoCanvas { Render.grid(it.nativeCanvas, 0f, 0f, size.width, 10, total, done, android.graphics.Color.WHITE) }
        }
    }
}

/** Years · Months · Weeks · Days. Pinch the grid or tap a word. */
@Composable
fun ZoomBar(st: AppState) {
    val view = LocalView.current
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Life.ZOOMS.forEachIndexed { i, z ->
            Text(
                z, style = T.small.copy(color = if (i == st.s.zoom) C.Fg else C.Faint, fontWeight = if (i == st.s.zoom) FontWeight.SemiBold else FontWeight.Normal),
                modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { view.tick(); st.update(refresh = false) { it.copy(zoom = i) } },
            )
        }
    }
}

@Composable
fun DotGrid(st: AppState, m: GridModel, ripple: Float, onName: (Int) -> Unit) {
    val s = st.s
    val ctx = LocalContext.current
    val view = LocalView.current
    val lit = if (s.sun) Color(Life.litColor(Life.sunLevel())) else C.Fg
    val big = m.total > 200
    val ratio = when { big -> 0.64f; m.cols == 10 -> 0.6f; m.cols == 5 -> 0.4f; else -> 0.5f } // medium dots for decade and year
    val appear = remember(m.kind, m.total) { Animatable(if (big) 1f else 0f) }
    LaunchedEffect(appear) { if (!big) appear.animateTo(1f, tween(700 + m.total * 9, easing = LinearEasing)) }
    val breathe by rememberInfiniteTransition(label = "br").animateFloat(1f, .55f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    var tip by remember(m.kind, m.total) { mutableStateOf<Int?>(null) }
    var sticky by remember(m.kind, m.total) { mutableStateOf(false) }
    LaunchedEffect(tip, sticky) { if (tip != null && sticky) { kotlinx.coroutines.delay(3500); tip = null; sticky = false } }
    val sealed = remember(s.letters) { s.letters.filter { !it.opened }.map { it.age }.toSet() }
    val named = remember(s.names, sealed, m.kind, m.total) {
        if (big) emptySet() else (0 until m.total).filter { i -> m.yearIndex?.let { yi -> yi(i).let { s.names.containsKey(it) || it in sealed } } == true }.toSet()
    }
    val doneInt = floor(m.done).toInt()
    val birth = s.birth ?: LocalDate.now()

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val cell = w / (m.cols - (1 - ratio))
        val inset = cell * (1 - ratio) / 2
        val rows = ceil(m.total / m.cols.toFloat()).toInt()
        val hPx = rows * cell - 2 * inset
        fun center(i: Int) = Offset(-inset + (i % m.cols + .5f) * cell, -inset + (i / m.cols + .5f) * cell)
        fun idxAt(o: Offset): Int? {
            val col = floor((o.x + inset) / cell).toInt()
            val row = floor((o.y + inset) / cell).toInt()
            if (col !in 0 until m.cols || row < 0) return null
            val i = row * m.cols + col
            return if (i in 0 until m.total) i else null
        }
        fun set(i: Int?) { if (i != null && i != tip) { tip = i; view.tick() } }
        // For thousands of dots, draw them as two batches of points.
        val points = remember(big, m.total, m.cols, w, doneInt) {
            if (!big) null else {
                val lived = ArrayList<Offset>(doneInt); val rest = ArrayList<Offset>(max(0, m.total - doneInt))
                for (i in 0 until m.total) { if (i == doneInt) continue; (if (i < doneInt) lived else rest).add(center(i)) }
                lived to rest
            }
        }

        Canvas(
            Modifier.fillMaxWidth().height(with(density) { hPx.toDp() }).pointerInput(m.kind, m.total, cell) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 1 tap, 2 moved (let the pager swipe), 3 two fingers (zoom), null held still (scrub)
                    val outcome = withTimeoutOrNull(170L) {
                        var r = 0
                        while (r == 0) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id }
                            r = when {
                                ev.changes.count { it.pressed } >= 2 -> 3
                                ch == null || !ch.pressed -> 1
                                (ch.position - down.position).getDistance() > viewConfiguration.touchSlop -> 2
                                else -> 0
                            }
                        }
                        r
                    }
                    when (outcome) {
                        null -> {
                            sticky = false
                            set(idxAt(down.position))
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.changes.count { it.pressed } >= 2) break
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                set(idxAt(ch.position))
                            }
                            if (tip != null) sticky = true
                        }
                        1 -> { val i = idxAt(down.position); if (i != null) { tip = i; sticky = true; view.tick() } else tip = null }
                        3 -> if (m.kind == ViewKind.LIFE) {
                            var base = 0f
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ps = ev.changes.filter { it.pressed }
                                if (ps.size < 2) break
                                val dist = (ps[0].position - ps[1].position).getDistance()
                                if (base == 0f) base = dist
                                val k = dist / base
                                val z = st.s.zoom
                                if (k > 1.3f && z < Life.ZOOMS.lastIndex) { tip = null; view.confirm(); st.update(refresh = false) { it.copy(zoom = z + 1) }; base = dist }
                                else if (k < 0.77f && z > 0) { tip = null; view.confirm(); st.update(refresh = false) { it.copy(zoom = z - 1) }; base = dist }
                                ev.changes.forEach { it.consume() }
                            }
                        }
                        else -> Unit
                    }
                }
            },
        ) {
            val total = m.total
            val r0 = cell * ratio / 2
            if (points != null) {
                drawPoints(points.first, PointMode.Points, lit, strokeWidth = r0 * 2, cap = StrokeCap.Round)
                drawPoints(points.second, PointMode.Points, C.Empty, strokeWidth = r0 * 2, cap = StrokeCap.Round)
                if (doneInt < total) {
                    val c = center(doneInt); val r = max(r0 * 1.8f, 3.dp.toPx())
                    drawCircle(C.Partial, r, c, alpha = breathe)
                    clipPath(Path().apply { addOval(Rect(c, r)) }) {
                        drawRect(lit, topLeft = Offset(c.x - r, c.y + r - 2 * r * (m.done - doneInt).toFloat()), size = Size(2 * r, 2 * r * (m.done - doneInt).toFloat()), alpha = breathe)
                    }
                }
                tip?.let { drawCircle(C.Fg, r0 * 2.6f, center(it)) }
                return@Canvas
            }
            val dD = 0.7f + total * 0.009f
            val rD = 0.9f + doneInt * 0.016f
            for (i in 0 until total) {
                val c = center(i)
                val l = ((appear.value * dD - i * 0.009f) / 0.7f).coerceIn(0f, 1f)
                val e = 1f - (1f - l) * (1f - l) * (1f - l)
                var k = 0.4f + 0.6f * e
                if (ripple < 1f && i <= doneInt) {
                    val lr = (ripple * rD - i * 0.016f) / 0.9f
                    if (lr > 0f && lr < 1f) k *= 1f + 0.32f * sin(lr * PI).toFloat()
                }
                if (i == tip) k *= 1.38f
                val r = r0 * k
                when {
                    i < doneInt -> drawCircle(lit, r, c, alpha = e)
                    i == doneInt && m.done < total -> {
                        val f = (m.done - doneInt).toFloat()
                        val al = e * breathe
                        drawCircle(C.Partial, r, c, alpha = al)
                        clipPath(Path().apply { addOval(Rect(c, r)) }) {
                            drawRect(lit, topLeft = Offset(c.x - r, c.y + r - 2 * r * f), size = Size(2 * r, 2 * r * f), alpha = al)
                        }
                    }
                    else -> drawCircle(C.Empty, r, c, alpha = e)
                }
                if (i in named) drawCircle(C.Fg.copy(alpha = .55f), cell * 0.43f, c, alpha = e, style = Stroke(1.5.dp.toPx()))
            }
        }

        val ti = tip
        if (ti != null && ti < m.total) {
            val c = center(ti)
            val half = max(cell * 0.38f, 8.dp.value * density.density)
            val yi = m.yearIndex?.invoke(ti)
            val isNow = ti == doneInt && m.done < m.total
            var name = yi?.let { s.names[it] }
            if (yi != null && name == null && yi in sealed) name = "Sealed letter"
            if (m.unit == "day") name = Lines.get(ctx, birth.plusDays(ti.toLong()))
            val hint = if (sticky && yi != null && s.names[yi] == null) (if (ti <= doneInt) "Tap to name this year" else "Tap to plan this year") else null
            Tip(c.x, c.y - half, c.y + half, w, name, m.label(ti) + if (isNow) " · now" else "", hint,
                if (sticky && yi != null) ({ view.tap(); tip = null; sticky = false; onName(yi) }) else null)
        }
    }
}

@Composable
fun Tip(cx: Float, dotTop: Float, dotBottom: Float, maxW: Float, name: String?, label: String, hint: String?, onClick: (() -> Unit)?) {
    val gray = Color(0xFF6E6E73)
    Column(
        Modifier
            .layout { measurable, _ ->
                val p = measurable.measure(Constraints(maxWidth = (maxW * 0.9f).roundToInt()))
                val gap = 6.dp.roundToPx()
                val x = (cx - p.width / 2f).coerceIn(0f, max(0f, maxW - p.width))
                var y = dotTop - p.height - gap
                if (y < 0) y = dotBottom + gap
                layout(0, 0) { p.place(x.roundToInt(), y.roundToInt()) }
            }
            .clip(RoundedCornerShape(12.dp)).background(C.Fg)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (name != null) {
            Text(name, style = T.label.copy(color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
            Text(label, style = T.small.copy(color = gray, fontSize = 12.sp))
        } else {
            Text(label, style = T.label.copy(color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
        }
        if (hint != null) Text(hint, style = T.small.copy(color = gray, fontSize = 12.sp))
    }
}
