package app.years

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

// ================================================================== the design language
// Black, white and greys. Thin, very large numbers. Cards that are barely there: near-black with a hairline.
// Every control answers a touch with a haptic tick, and every number rolls to its new value.

val Ease = CubicBezierEasing(.22f, 1f, .36f, 1f)
val CardShape = RoundedCornerShape(24.dp)

/** A small uppercase label above a section. */
@Composable
fun Cap(text: String, modifier: Modifier = Modifier, color: Color = C.Dim) {
    Text(text.uppercase(), style = T.cap.copy(color = color), modifier = modifier)
}

/** The quiet card: near black, a hairline edge, generous corners. */
fun Modifier.card(shape: Shape = CardShape, color: Color = C.Card): Modifier = this.clip(shape).background(color).border(1.dp, C.Hair, shape)

/** A number that rolls from its old value to its new one, like a mechanical counter settling. */
@Composable
fun rolling(target: Double, key: Any?, from: Double? = null): Double {
    val still = LocalInspectionMode.current || Render.still
    var start by remember { mutableStateOf(from ?: target) }
    var end by remember { mutableStateOf(target) }
    val p = remember { Animatable(if (still || from == null) 1f else 0f) }
    val latest by rememberUpdatedState(target)
    LaunchedEffect(key) {
        if (still) return@LaunchedEffect
        start = if (p.value >= 1f) end else start + (end - start) * p.value
        end = latest
        if (abs(end - start) < 1e-9) { p.snapTo(1f); return@LaunchedEffect }
        p.snapTo(0f); p.animateTo(1f, tween(900, easing = Ease))
    }
    // once settled, follow the live value (seconds tick on, days change at midnight)
    return if (p.value >= 1f) target else start + (target - start) * p.value
}

/** Picks the largest size that fits [text] in the width, up to [max]. Inter Display digits are about 0.56 em wide. */
@Composable
fun fitSize(text: String, maxWidth: Dp, max: TextUnit, perChar: Float = .58f): TextUnit {
    val w = maxWidth.value / (text.length.coerceAtLeast(1) * perChar)
    return minOf(max.value, w).coerceAtLeast(28f).sp
}

/** The big thin number. [pct] adds a smaller percent sign. */
@Composable
fun BigNumber(text: String, max: TextUnit, pct: Boolean = false, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fs = fitSize(text + if (pct) "%" else "", maxWidth, max)
        Text(
            buildAnnotatedString {
                append(text)
                if (pct) withStyle(SpanStyle(fontSize = fs * .42f, fontWeight = FontWeight.Light, color = C.Mute)) { append("%") }
            },
            style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraLight, fontSize = fs, lineHeight = fs * .98f, letterSpacing = (-0.055).em, color = C.Fg, fontFeatureSettings = "tnum"),
            maxLines = 1, softWrap = false,
        )
    }
}

/** Left | Lived: a capsule with a sliding thumb. */
@Composable
fun LeftLived(lived: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val x by animateDpAsState(if (lived) 74.dp else 0.dp, spring(dampingRatio = .72f, stiffness = Spring.StiffnessMediumLow), label = "ll")
    Box(modifier.size(156.dp, 42.dp).clip(CircleShape).background(C.Glass).border(1.dp, C.Hair, CircleShape).padding(4.dp)) {
        Box(Modifier.offset(x = x).size(74.dp, 34.dp).clip(CircleShape).background(C.Surface2))
        Row(Modifier.fillMaxWidth()) {
            listOf(false to "Left", true to "Lived").forEach { (v, label) ->
                val c by animateColorAsState(if (v == lived) C.Fg else C.Mute, label = "llc")
                Box(Modifier.width(74.dp).height(34.dp).clickable(remember { MutableInteractionSource() }, indication = null) { if (v != lived) { view.tick(); onChange(v) } }, contentAlignment = Alignment.Center) {
                    Text(label, style = T.label.copy(fontSize = with(LocalDensity.current) { 15.dp.toSp() }, color = c), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** A round button that turns white when it's on. */
@Composable
fun RoundToggle(on: Boolean, onClick: () -> Unit, size: Dp = 42.dp, content: @Composable (Color) -> Unit) {
    val bg by animateColorAsState(if (on) C.Fg else C.Glass, label = "rt")
    val fg = if (on) Color.Black else C.Fg
    Box(Modifier.size(size).clip(CircleShape).background(bg).border(1.dp, if (on) Color.Transparent else C.Hair, CircleShape).pressable(onClick), contentAlignment = Alignment.Center) { content(fg) }
}

/** Ways to count your years, as words you can tap. */
@Composable
fun UnitChips(selected: Int, onSelect: (Int) -> Unit) {
    val view = LocalView.current
    Row(Modifier.fillMaxWidth().horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Life.MEASURES.forEachIndexed { i, m ->
            val on = i == selected
            val c by animateColorAsState(if (on) C.Fg else C.Dim, label = "uc")
            Text(
                m.many.replaceFirstChar { it.uppercase() }, style = T.label.copy(fontSize = 15.sp, color = c, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { if (!on) { view.tick(); onSelect(i) } }.padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier = this.horizontalScroll(rememberScrollState())

/** History · Years · Months · Weeks · Days, over a track with a thumb that glides to the one you pick. */
@Composable
fun ZoomRuler(zoom: Int, onZoom: (Int) -> Unit) {
    val view = LocalView.current
    val n = Life.ZOOMS.size
    val pos by animateFloatAsState(zoom.toFloat(), spring(dampingRatio = .7f, stiffness = Spring.StiffnessLow), label = "zr")
    Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Life.ZOOMS.forEachIndexed { i, z ->
                val on = i == zoom
                val c by animateColorAsState(if (on) C.Fg else C.Dim, label = "zc")
                Box(Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, indication = null) { if (!on) { view.tick(); onZoom(i) } }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(z, style = T.small.copy(fontSize = 13.sp, color = c, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal))
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val y = size.height / 2
            val cell = size.width / n
            drawLine(C.Hair2, Offset(cell / 2, y), Offset(size.width - cell / 2, y), 1.5.dp.toPx())
            for (i in 0 until n) drawCircle(C.Hair2, 2.dp.toPx(), Offset(cell * (i + .5f), y))
            drawCircle(C.Fg, 5.dp.toPx(), Offset(cell * (pos + .5f), y))
        }
    }
}

/** The five pages, as a floating bar of words with a pill that slides to the one you're on. */
@Composable
fun PageBar(current: Float, labels: List<String>, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    BoxWithConstraints(modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(C.Glass.copy(alpha = .94f)).border(1.dp, C.Hair, CircleShape).padding(5.dp)) {
        val w = maxWidth / labels.size
        Box(Modifier.offset(x = w * current).width(w).fillMaxHeight().clip(CircleShape).background(C.Surface2))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                val on = abs(current - i) < .5f
                val c by animateColorAsState(if (on) C.Fg else C.Mute, label = "pb")
                Box(Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, indication = null) { view.tick(); onPick(i) }, contentAlignment = Alignment.Center) {
                    // sized in dp, not sp: five words share one bar, so a large system font must not push them out
                    val fs = with(LocalDensity.current) { 14.dp.toSp() }
                    Text(l, style = T.label.copy(fontSize = fs, color = c, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** A slider that never fights the page: it only takes a sideways drag, and a tap jumps to that spot. */
@Composable
fun YrsSlider(value: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit) {
    val view = LocalView.current
    val latest by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val density = LocalDensity.current
    val thumb = 26.dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(40.dp)) {
        val track = with(density) { (maxWidth - thumb).toPx() }
        val half = with(density) { (thumb / 2).toPx() }
        fun valueAt(x: Float): Float {
            val f = ((x - half) / track).coerceIn(0f, 1f)
            val v = min + (max - min) * f
            return ((v / step).roundToInt() * step).coerceIn(min, max)
        }
        fun set(x: Float) { val v = valueAt(x); if (v != latest) { change(v); if (((v - min) / step).roundToInt() % 4 == 0) view.tick() } }
        val f = ((value - min) / (max - min)).coerceIn(0f, 1f)
        Canvas(
            Modifier.fillMaxWidth().height(40.dp)
                .pointerInput(min, max, step) { detectTapGestures { set(it.x) } }
                .pointerInput(min, max, step) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                        if (start != null) { set(start.position.x); horizontalDrag(start.id) { c -> set(c.position.x); c.consume() } }
                    }
                },
        ) {
            val y = size.height / 2
            val h = 4.dp.toPx()
            drawRoundRect(C.Surface3, Offset(half, y - h / 2), Size(track, h), CornerRadius(h / 2))
            drawRoundRect(C.Fg.copy(alpha = .55f), Offset(half, y - h / 2), Size(track * f, h), CornerRadius(h / 2))
            drawCircle(Color.Black.copy(alpha = .35f), half + 2.dp.toPx(), Offset(half + track * f, y + 1.5.dp.toPx()))
            drawCircle(C.Fg, half, Offset(half + track * f, y))
        }
    }
}

/** One fact at a time. Tap for another; the new one rises in as the old fades. */
@Composable
fun FactCard(cap: String, facts: () -> List<String>, lead: String? = null, modifier: Modifier = Modifier, big: Boolean = false) {
    var fact by remember(cap) { mutableStateOf(lead ?: Facts.next(facts())) }
    Column(modifier.fillMaxWidth().card().pressable({ fact = Facts.next(facts()) }).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Cap(cap)
        AnimatedContent(fact, transitionSpec = { (fadeIn(tween(420)) + slideInVertically(tween(520, easing = Ease)) { it / 5 }) togetherWith fadeOut(tween(160)) }, label = "fact") { f ->
            Text(f, style = if (big) T.fact else T.body.copy(fontSize = 17.sp, lineHeight = 24.sp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
        Text("Tap for another", style = T.small, modifier = Modifier.padding(top = 10.dp))
    }
}

/** A choice you can tap: white when chosen, with a filled radio. */
@Composable
fun OptionCard(label: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) C.Fg else C.Surface2, tween(220), label = "ob")
    val fg = if (selected) Color.Black else C.Fg
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(18.dp)).background(bg).pressable(onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = T.body.copy(color = fg))
            if (!sub.isNullOrEmpty()) Text(sub, style = T.small.copy(color = if (selected) Color(0xFF6E6E73) else C.Mute), modifier = Modifier.padding(top = 1.dp))
        }
        val ring by animateDpAsState(if (selected) 7.dp else 1.5.dp, spring(dampingRatio = .6f), label = "or")
        Box(Modifier.size(22.dp).border(ring, if (selected) Color.Black else C.Dim, CircleShape))
    }
}

/** A short line of explanation, with where it comes from. */
@Composable
fun WhyCard(text: String, source: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(16.dp)) {
        Text(text, style = T.small.copy(fontSize = 14.sp, lineHeight = 20.sp))
        if (source != null) Text(source, style = T.small.copy(fontSize = 12.sp, color = C.Dim), modifier = Modifier.padding(top = 8.dp))
    }
}

/** Two numbers side by side under a caption, for the foot of a page. */
@Composable
fun FootStat(cap: String, value: String, rest: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Cap(cap)
        Text(
            buildAnnotatedString { append(value); if (rest != null) withStyle(SpanStyle(color = C.Mute)) { append(" · $rest") } },
            style = T.label.copy(fontSize = 15.sp, lineHeight = 20.sp), modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@Composable
fun Spacer(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun CenterText(text: String, style: TextStyle, modifier: Modifier = Modifier) = Text(text, style = style, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth())
