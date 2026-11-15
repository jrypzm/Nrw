package app.years

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.sin

// ================================================================== targets

/** Where each control sits on screen, so the tour can light it up. */
class TourTargets {
    val rects = mutableStateMapOf<String, Rect>()
}

fun Modifier.tourTarget(t: TourTargets, key: String): Modifier =
    this.onGloballyPositioned { c -> t.rects[key] = c.boundsInRoot() }

// ================================================================== steps

/** Gestures the tour listens for that don't change a setting: holding the number, tapping and dragging the grid. */
object TourSignal {
    var beats by mutableIntStateOf(0)
    var taps by mutableIntStateOf(0)
    var scrubs by mutableIntStateOf(0)
}

class TourSnap(val s: Settings, val beats: Int, val taps: Int, val scrubs: Int, val sheet: Sheet?)

enum class Hint { NONE, TAP, HOLD, DRAG, PINCH, SWIPE }

/**
 * One lesson. [body] uses your own numbers; *stars* mark the words to bold. [done] compares what you've done
 * since the step began, so a step moves on by itself the moment you try it; Next and Back always work too.
 */
class TourStep(
    val target: String?,
    val title: String,
    val body: (Settings) -> String,
    val hint: Hint = Hint.NONE,
    val tryIt: String = "",
    val ok: String = "",
    val done: ((before: TourSnap, now: TourSnap) -> Boolean)? = null,
)

private fun line(s: Settings): String = runCatching {
    val h = Life.headline(s, Life.model(s, ViewKind.LIFE)); "${Life.whole(h.value)} ${h.noun} ${h.word}"
}.getOrDefault("your years")

private fun livedYears(s: Settings): Int = s.birth?.let { Life.age(it).toInt() } ?: 0

val TOUR = listOf(
    TourStep(null, "Welcome to yrs.", { "A quick tour, under a minute. Try each gesture as we go: *the tour moves on when you do*. You can replay it any time from Settings." }),
    TourStep("number", "This is your number", { "Right now it says *${line(it)}*. Tap it to count another way: days, weeks, summers, weekends or full moons." },
        Hint.TAP, "Tap the number", "That's the same time, counted another way.") { a, b -> a.s.unit != b.s.unit },
    TourStep("number", "Feel the seconds", { "Press and hold the number. Every heartbeat you feel is one second, out of about *${Life.whole(Life.yearsLeft(it) * 365.2425 * 86400)}* you have left." },
        Hint.HOLD, "Hold the number", "Felt that? Let go whenever you like.") { a, b -> b.beats > a.beats },
    TourStep("grid", "One dot, one year", { val l = livedYears(it); "You've lived *$l ${if (l == 1) "dot" else "dots"}*. The one you're in fills a little every day. Tap any dot to see which year it is." },
        Hint.TAP, "Tap a dot", "Tap its label any time to name that year or seal a letter to it.") { a, b -> b.taps > a.taps },
    TourStep("grid", "Travel through time", { "Hold a dot, then drag across the grid without letting go. Each year shows its age and date as you pass it." },
        Hint.DRAG, "Hold and drag", "Your whole life, under one finger.") { a, b -> b.scrubs > a.scrubs },
    TourStep("zoom", "From history to today", { "Pinch the grid, or tap a word here. In weeks you have *${Life.whole(Life.yearsLeft(it) * 52.1775)}* left; in days, *${Life.whole(Life.daysLeftExact(it))}*." },
        Hint.PINCH, "Pinch or tap Weeks", "The number always counts what you see.") { a, b -> a.s.zoom != b.s.zoom },
    TourStep("lived", "Left or lived", { "Switch every number between the time ahead of you and the time you've already lived." },
        Hint.TAP, "Tap Left", "Tap it again whenever you want the other view.") { a, b -> a.s.lived != b.s.lived },
    TourStep("pct", "As a percentage", { s -> "Show it as a share of the whole. You've lived about *${(livedYears(s) * 100 / s.span.coerceAtLeast(1)).coerceIn(0, 100)}%* of ${s.span} years." },
        Hint.TAP, "Tap %", "Tap again to go back to the number.") { a, b -> a.s.pct != b.s.pct },
    TourStep("pages", "Four more pages", { "Swipe sideways: this decade, this year with a line a day and countdowns, the people you love, and where your years go." },
        Hint.SWIPE, "Swipe left", "Swipe back any time. The dots show where you are.") { a, b -> a.s.page != b.s.page },
    TourStep("settings", "Everything else", { "Your birthday, lifespan, number style, lock screen and widgets live here, with a guide to every setting. Tap it to finish." },
        Hint.TAP, "Tap ⋯") { _, b -> b.sheet == Sheet.Settings },
)

/** "*bold*" markup to styled text. */
private fun rich(t: String) = buildAnnotatedString {
    t.split('*').forEachIndexed { i, part -> if (i % 2 == 1) withStyle(SpanStyle(color = C.Fg, fontWeight = FontWeight.SemiBold)) { append(part) } else append(part) }
}

// ================================================================== overlay

/**
 * The dim layer and its spotlight are only drawn, never touched: every tap and gesture falls straight
 * through to the real controls underneath, so you learn by doing. Only the card takes touches.
 */
@Composable
fun TourOverlay(st: AppState, targets: TourTargets, step: Int, success: Boolean, onNext: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit) {
    val s = TOUR[step]
    val d = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val W = constraints.maxWidth.toFloat()
        val H = constraints.maxHeight.toFloat()
        val pad = with(d) { 10.dp.toPx() }
        val raw = s.target?.let { targets.rects[it] }
        val hole = raw?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) } ?: Rect(W / 2, H * .42f, W / 2, H * .42f)
        val r by animateRectAsState(hole, spring(dampingRatio = .82f, stiffness = 220f), label = "hole")

        val inf = rememberInfiniteTransition(label = "tour")
        val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "p")

        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color.Black.copy(alpha = .74f))
            if (r.width > 1f) {
                val cr = CornerRadius(minOf(r.height / 2f, 22.dp.toPx()))
                drawRoundRect(Color.Transparent, r.topLeft, r.size, cr, blendMode = BlendMode.Clear)
                // a soft ring that breathes around whatever you should touch
                val grow = 3.dp.toPx() + 4.dp.toPx() * (.5f + .5f * sin(p * 6.283f))
                drawRoundRect(
                    Color.White.copy(alpha = .28f), Offset(r.left - grow, r.top - grow), Size(r.width + 2 * grow, r.height + 2 * grow),
                    CornerRadius(cr.x + grow), style = Stroke(1.2.dp.toPx()),
                )
                // the ghost fingertip; you pinch the grid itself, not the labels under it
                if (!success) {
                    val at = if (s.hint == Hint.PINCH) targets.rects["grid"] ?: r else r
                    drawHint(s.hint, at, p)
                }
            }
        }

        // below the spotlight, else above it; a spotlight too tall for either (the grid) keeps its top free and the card docks low
        var cardH by remember { mutableIntStateOf(0) }
        val gap = with(d) { 16.dp.toPx() }
        val side = with(d) { 16.dp.toPx() }
        val edge = with(d) { 40.dp.toPx() }
        val y = when {
            raw == null -> (H - cardH) / 2f
            r.bottom + gap + cardH < H - edge -> r.bottom + gap
            r.top - gap - cardH >= edge -> r.top - gap - cardH
            else -> H - cardH - edge
        }
        Column(
            Modifier.offset { IntOffset(side.roundToInt(), y.roundToInt()) }
                .width(with(d) { (W - 2 * side).toDp() })
                .onSizeChanged { cardH = it.height }
                .background(C.Surface2, RoundedCornerShape(24.dp))
                .pressable({}, haptic = false)
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
        ) {
            // progress
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TOUR.indices.forEach { i ->
                    val f by animateFloatAsState(if (i <= step) 1f else 0f, tween(500), label = "seg")
                    Box(Modifier.weight(1f).height(3.dp).background(C.Faint, RoundedCornerShape(2.dp))) {
                        Box(Modifier.fillMaxWidth(f).height(3.dp).background(C.Fg, RoundedCornerShape(2.dp)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${step + 1} OF ${TOUR.size}", style = T.caps.copy(fontSize = 12.sp, letterSpacing = 0.04.em))
                Spacer(Modifier.weight(1f))
                if (step < TOUR.size - 1) Text("Skip tour", style = T.label.copy(color = C.Mute, fontSize = 14.sp), modifier = Modifier.pressable(onSkip).padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
            }
            AnimatedContent(step, transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 10 }) togetherWith fadeOut(tween(120)) }, label = "card") { i ->
                Column {
                    Text(TOUR[i].title, style = T.h2.copy(fontSize = 21.sp), modifier = Modifier.padding(top = 8.dp))
                    Text(rich(TOUR[i].body(st.s)), style = T.body.copy(fontSize = 15.sp, color = Color(0xFFAEAEB2), lineHeight = 21.sp), modifier = Modifier.padding(top = 6.dp))
                }
            }
            // you did it: a tick and a word, then the next step
            AnimatedVisibility(success && s.ok.isNotEmpty(), enter = fadeIn(tween(250)) + expandVertically(tween(350)), exit = fadeOut(tween(150)) + shrinkVertically(tween(200))) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(22.dp)) {
                        drawCircle(Color.White)
                        val w = size.width
                        val path = androidx.compose.ui.graphics.Path().apply { moveTo(w * .28f, w * .52f); lineTo(w * .44f, w * .68f); lineTo(w * .73f, w * .36f) }
                        drawPath(path, Color.Black, style = Stroke(2.2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    }
                    Text(s.ok, style = T.label.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp), modifier = Modifier.padding(start = 10.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step > 0) Box(Modifier.height(36.dp).background(C.Surface3, RoundedCornerShape(18.dp)).pressable(onBack).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text("Back", style = T.label)
                }
                Spacer(Modifier.weight(1f))
                if (s.tryIt.isNotEmpty() && !success) Row(verticalAlignment = Alignment.CenterVertically) {
                    val a by inf.animateFloat(.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "try")
                    Box(Modifier.size(6.dp).graphicsLayer(alpha = a).background(C.Fg, androidx.compose.foundation.shape.CircleShape))
                    Text(s.tryIt, style = T.small, modifier = Modifier.padding(start = 6.dp))
                }
                Box(Modifier.height(36.dp).background(C.Fg, RoundedCornerShape(18.dp)).pressable(onNext).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(
                        when (step) { 0 -> "Start"; TOUR.size - 1 -> "Finish"; else -> "Next" },
                        style = T.label.copy(color = Color.Black, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

/** A ghost fingertip that shows the gesture: a tap, a hold, a drag, a pinch or a swipe. */
private fun DrawScope.drawHint(h: Hint, r: Rect, p: Float) {
    if (h == Hint.NONE) return
    val c = r.center
    val dot = 13.dp.toPx()
    val white = Color.White
    fun finger(at: Offset, a: Float) {
        drawCircle(white.copy(alpha = .22f * a), dot * 1.7f, at)
        drawCircle(white.copy(alpha = .9f * a), dot * .75f, at)
    }
    when (h) {
        Hint.TAP -> {
            val k = (p * 1.6f) % 1f
            drawCircle(white.copy(alpha = .5f * (1 - k)), dot + dot * 2.2f * k, c, style = Stroke(1.5.dp.toPx()))
            finger(c, 1f)
        }
        Hint.HOLD -> {
            finger(c, 1f)
            drawArc(white.copy(alpha = .85f), -90f, 360f * minOf(1f, p * 1.4f), false, Offset(c.x - dot * 2f, c.y - dot * 2f), Size(dot * 4f, dot * 4f), style = Stroke(2.dp.toPx()))
        }
        Hint.DRAG -> {
            val span = minOf(r.width * .32f, 110.dp.toPx())
            val k = (p * 1.2f).coerceAtMost(1f)
            val x = c.x - span + 2 * span * k
            drawLine(white.copy(alpha = .25f), Offset(c.x - span, c.y), Offset(x, c.y), 2.dp.toPx())
            finger(Offset(x, c.y), if (k >= 1f) 0f else 1f)
        }
        Hint.PINCH -> {
            val k = (p * 1.3f).coerceAtMost(1f)
            val o = 16.dp.toPx() + 46.dp.toPx() * k
            val a = if (k >= 1f) 0f else 1f
            finger(Offset(c.x - o * .7f, c.y + o * .7f), a)
            finger(Offset(c.x + o * .7f, c.y - o * .7f), a)
        }
        Hint.SWIPE -> {
            val k = (p * 1.3f).coerceAtMost(1f)
            val y = r.top - 150.dp.toPx()
            val x = c.x + 90.dp.toPx() - 180.dp.toPx() * k
            finger(Offset(x, y), if (k >= 1f) 0f else 1f)
        }
        Hint.NONE -> Unit
    }
}

// ================================================================== the guide

private class G(val title: String, val body: String)

private val GUIDE: List<Pair<String, List<G>>> = listOf(
    "Gestures" to listOf(
        G("Swipe sideways", "Move between your five pages: Life, This decade, This year, People and Where your years go. The dots at the bottom show where you are; tap one to jump."),
        G("Tap the number", "On the Life page in Years, count in years, days, weeks, summers, weekends or full moons."),
        G("Hold the number", "Feel a heartbeat for every second of your life that passes, with the seconds you have left."),
        G("Tap a dot", "Shows that year, month, week or day. Tap the label again to name a year or seal a letter to it."),
        G("Hold and drag", "Hold any dot, then slide across the grid to travel through your life. Let go to keep the label."),
        G("Pinch", "Pinch the Life grid in or out to zoom between History, Years, Months, Weeks and Days. You can also tap the names under the grid."),
        G("Left or Lived", "The button at bottom left switches every number between the time ahead of you and the time behind you."),
        G("%", "The button at bottom right shows any page as a percentage of the whole."),
    ),
    "Your pages" to listOf(
        G("Life", "Every year of your life as one dot. White years are lived, dark years are ahead, and the year you're in fills from the bottom as it passes. Named years and sealed letters get a thin ring."),
        G("History", "Zoom all the way out: about 3,750 lifetimes since the first cities, end to end. Yours is the last dot, still filling."),
        G("This decade", "The ten years of the decade you're in, so the next few feel close."),
        G("This year", "Twelve dots from your last birthday to your next. Below: today's line, what you wrote on this day in past years, and your countdowns."),
        G("A line a day", "Tap Today on the This year page and write one sentence. Years later, on this day brings it back. In Days zoom, hold a day to read its line."),
        G("Countdowns", "On This year, tap Add a countdown for a trip, a wedding or a birthday. Your next one can also appear as the line on your lock screen."),
        G("People", "Add someone, the year they were born and how often you see them. yrs. shows how many more visits you'll likely have, as dots. Tap a person to edit or remove them."),
        G("Where your years go", "Your remaining years, minus sleep, work, eating and chores, and time on your phone. What's left is truly yours."),
    ),
    "Moments" to listOf(
        G("Name a year", "Tap a year's label to give it a name: the year you moved, or the one you'll travel. Named years get a ring."),
        G("Letters to your future self", "When you name a year ahead, you can also write a letter to it. It stays sealed until that birthday, then opens on its own."),
        G("Your birthday review", "In the month after each birthday, yrs. shows the year you just finished: days lived, lines written, and your first and last line."),
        G("Happy birthday", "On your birthday, the Life page says so."),
    ),
    "Settings · You" to listOf(
        G("Birthday", "Tap to change it on the wheels. Everything recalculates at once."),
        G("Lifespan", "How many years your grid holds. 80 is a fair average; use − and + to change it from 40 to 120."),
        G("Estimate my lifespan", "Five questions about habits with the clearest evidence, starting from 80. A rough estimate to reflect on, not medical advice."),
    ),
    "Settings · Number" to listOf(
        G("Classic", "A clear headline, like 48 years left."),
        G("Large", "One big, thin number on its own."),
        G("Live", "Counts down to eight decimal places, so you can watch it move."),
    ),
    "Settings · Feel" to listOf(
        G("Daylight", "Your dots glow bright by day and dim at night, following the sun. It also changes the Grid lock screen."),
        G("Ripple", "A wave runs through your years each time you open yrs. Turn it off for a still grid."),
        G("Monday note", "One quiet notification on Mondays: You're in week 1,630 of your life. Nothing else, ever."),
    ),
    "Settings · Lock screen" to listOf(
        G("Grid, Daylight, Hourglass, Stars", "Tap a style to use it. Grid is your years as dots. Daylight dims with the sun. Hourglass drops one grain each birthday. Stars drift slowly through the day."),
        G("Set live wallpaper", "Makes yrs. your live wallpaper. It ripples through your years each time you wake your phone, and rests otherwise."),
        G("Auto-update lock screen", "Repaints your lock screen with yrs. every hour, on any phone, so it's always true. It also shows a line from your life."),
        G("Save as image", "Saves the lock screen to Pictures/yrs, to set by hand or share."),
    ),
    "Settings · Widgets" to listOf(
        G("Black or Glass", "Choose solid black widgets, or Glass to let your wallpaper show through."),
        G("Add a widget", "Tap any widget to add it straight to your home screen: Life, Years left, Days left, Today, People, This decade and This year of life. Or long-press your home screen and choose Widgets. Tap a widget to open its page."),
    ),
    "Settings · Privacy" to listOf(
        G("Screen time access", "Lets the Ledger use your real phone time from the last week. It's read on your phone and never leaves it. Without it, the Ledger assumes 3 hours a day."),
        G("Share your grid", "Makes an image of your years to share anywhere you like."),
        G("Nothing leaves your phone", "yrs. has no internet permission at all. Your birthday, names, letters and lines stay on this device."),
    ),
    "Settings · About" to listOf(
        G("About yrs.", "Who made it, the MIT license that lets you use and change the code freely, and links to the website and the source."),
    ),
)

@Composable
fun GuideSheet(onDismiss: () -> Unit, onTour: () -> Unit) {
    YrsSheet(onDismiss) { GuideContent(onTour, onDismiss) }
}

@Composable
fun ColumnScope.GuideContent(onTour: () -> Unit, onDone: () -> Unit) {
    Text("How to use yrs.", style = T.h1.copy(fontSize = 30.sp, lineHeight = 33.sp), modifier = Modifier.padding(top = 6.dp))
    Text("Every gesture, page and setting, in one place.", style = T.sub, modifier = Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(18.dp))
    PrimaryButton("Replay the tour", onTour)
    GUIDE.forEach { (section, items) ->
        Caps(section)
        Group {
            items.forEachIndexed { i, g ->
                if (i > 0) Divider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
                    Text(g.title, style = T.body.copy(fontWeight = FontWeight.Medium))
                    Text(g.body, style = T.small.copy(fontSize = 14.sp, lineHeight = 20.sp), modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    PrimaryButton("Done", onDone)
}
