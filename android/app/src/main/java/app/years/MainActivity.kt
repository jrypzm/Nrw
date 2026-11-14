@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package app.years

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        Background.schedule(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = C.Bg, surface = C.Surface, primary = C.Fg, onPrimary = Color.Black, onSurface = C.Fg)) {
                App()
            }
        }
    }
}

class AppState(private val ctx: Context) {
    var s by mutableStateOf(Prefs.load(ctx))
        private set

    fun update(refresh: Boolean = true, forceLock: Boolean = false, f: (Settings) -> Settings) {
        s = f(s)
        Prefs.save(ctx, s)
        if (refresh) Background.refreshNow(ctx, forceLock)
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val st = remember { AppState(ctx.applicationContext) }
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        AnimatedContent(
            targetState = st.s.birth == null,
            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(250)) }, label = "root",
        ) { onboarding -> if (onboarding) Onboarding(st) else Home(st) }
    }
}

// ================================================================== onboarding

@Composable
fun Onboarding(st: AppState) {
    var step by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf(LocalDate.of(1995, 6, 15)) }
    var span by remember { mutableIntStateOf(80) }
    val view = LocalView.current
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(64.dp))
        AnimatedContent(
            targetState = step, modifier = Modifier.weight(1f), label = "ob",
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(420)) { dir * it / 5 } + fadeIn(tween(420))) togetherWith (slideOutHorizontally(tween(260)) { -dir * it / 5 } + fadeOut(tween(200)))
            },
        ) { s ->
            Column(Modifier.fillMaxWidth()) {
                if (s == 0) {
                    Text("When were\nyou born?", style = T.h1)
                    Text("Stays on this phone. Years has no internet access.", style = T.sub, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(56.dp))
                    DateWheels(date) { date = it }
                } else {
                    Text("How long\nis a life?", style = T.h1)
                    Text("80 is a fair average. Change it anytime.", style = T.sub, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(28.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$span", style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraLight, fontSize = 112.sp, letterSpacing = (-0.05).em, color = C.Fg, fontFeatureSettings = "tnum"), modifier = Modifier.alignByBaseline())
                        Text("years", style = T.sub.copy(fontSize = 17.sp), modifier = Modifier.padding(start = 10.dp).alignByBaseline())
                        Spacer(Modifier.weight(1f))
                        Stepper(span, { span = it }, 40..120)
                    }
                    Spacer(Modifier.height(28.dp))
                    val age = Life.age(date)
                    PreviewGrid(span, age)
                    Text("${max(0, floor(span - age).toInt())} years left", style = T.sub, modifier = Modifier.padding(top = 14.dp))
                }
            }
        }
        PrimaryButton(if (step == 0) "Continue" else "Begin", {
            if (step == 0) step = 1 else { view.confirm(); st.update { it.copy(birth = date, span = span) } }
        })
        if (step == 1) QuietButton("Back", { step = 0 }) else Spacer(Modifier.height(48.dp))
        Spacer(Modifier.height(8.dp))
    }
}

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

// ================================================================== home

@Composable
fun Home(st: AppState) {
    val s = st.s
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = s.page.coerceIn(0, 2)) { 3 }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { p -> if (p != st.s.page) { view.tick(); st.update(refresh = false) { it.copy(page = p) } } }
    }

    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(s.style) {
        while (true) {
            if (s.style == NumberStyle.LIVE) withFrameMillis { } else delay(20_000)
            now = ZonedDateTime.now()
        }
    }

    // The unlock ripple: plays every time you come back to the app.
    val ripple = remember { Animatable(1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                now = ZonedDateTime.now()
                if (st.s.ripple) scope.launch {
                    delay(500)
                    val lived = st.s.birth?.let { Life.age(it).toInt() } ?: 0
                    ripple.snapTo(0f)
                    ripple.animateTo(1f, tween(900 + lived * 16, easing = LinearEasing))
                }
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    var showSettings by remember { mutableStateOf(false) }
    var milestone by remember { mutableStateOf<Int?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp), horizontalArrangement = Arrangement.End) {
                RoundButton({ showSettings = true }, size = 32.dp) { MoreDots() }
            }
            Spacer(Modifier.height(30.dp))
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Top, beyondViewportPageCount = 1) { page ->
                Page(st, ViewKind.entries[page], now, ripple.value) { milestone = it }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { Pill(if (s.lived) "Lived" else "Left") { st.update { it.copy(lived = !it.lived) } } }
            PagerDots(pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                RoundButton({ st.update { it.copy(pct = !it.pct) } }, on = s.pct) {
                    Text("%", style = T.label.copy(fontWeight = FontWeight.SemiBold, color = if (s.pct) Color.Black else C.Fg))
                }
            }
        }

        AnimatedVisibility(
            visible = toast != null, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp),
            enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Text(toast ?: "", style = T.label.copy(fontSize = 14.sp), modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(C.Surface2).padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }

    if (showSettings) SettingsSheet(st, onDismiss = { showSettings = false }, toast = { toast = it })
    milestone?.let { y -> MilestoneSheet(st, y) { milestone = null } }
}

@Composable
fun PagerDots(current: Int, onClick: (Int) -> Unit) {
    Row {
        repeat(3) { i ->
            val c by animateColorAsState(if (i == current) C.Fg else C.Faint, label = "pd")
            Box(Modifier.size(22.dp, 36.dp).clickable(remember { MutableInteractionSource() }, indication = null) { onClick(i) }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(c))
            }
        }
    }
}

@Composable
fun Page(st: AppState, kind: ViewKind, now: ZonedDateTime, ripple: Float, onName: (Int) -> Unit) {
    val s = st.s
    val m = Life.model(s, kind, now)
    val view = LocalView.current
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Header(s, m, if (kind == ViewKind.LIFE && !s.pct) ({ view.tick(); st.update(refresh = false) { it.copy(unit = (it.unit + 1) % Life.MEASURES.size) } }) else null)
        Spacer(Modifier.height(if (s.style == NumberStyle.HERO) 40.dp else 32.dp))
        DotGrid(s, m, if (kind == ViewKind.LIFE) ripple else 1f, onName)
    }
}

@Composable
fun Header(s: Settings, m: GridModel, onTap: (() -> Unit)?) {
    val hl = Life.headline(s, m)
    val birth = s.birth
    val sub = when {
        m.kind == ViewKind.LIFE && birth != null && Life.isBirthday(birth) -> "Happy birthday · year ${Life.age(birth).toInt() + 1} begins"
        m.context != null -> "${m.context} · ${m.sub}"
        else -> m.sub
    }
    val key = "${s.style}|${s.lived}|${s.pct}|${s.unit}|${m.kind}"
    val tapMod = if (onTap != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { onTap() } else Modifier
    AnimatedContent(
        targetState = key, modifier = tapMod, label = "hdr",
        transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 8 }) togetherWith fadeOut(tween(140)) },
    ) { _ ->
        Column(Modifier.fillMaxWidth()) {
            when (s.style) {
                NumberStyle.CLASSIC -> {
                    val title = when {
                        s.pct -> "${floor(hl.pct).toInt()}% ${hl.word}"
                        m.kind == ViewKind.LIFE && !s.lived && hl.value <= 0.0 -> "Every year is a gift"
                        !s.lived && floor(hl.value) == 0.0 -> "Less than a ${hl.measure.one} left"
                        else -> "${Life.whole(hl.value)} ${hl.noun} ${hl.word}"
                    }
                    Text(title, style = T.title)
                }
                NumberStyle.HERO -> {
                    val num = if (s.pct) "${floor(hl.pct).toInt()}" else Life.whole(hl.value)
                    val fs = when { num.length <= 3 -> 104.sp; num.length <= 5 -> 78.sp; else -> 60.sp }
                    Row {
                        Text(
                            buildAnnotatedString {
                                append(num)
                                if (s.pct) withStyle(SpanStyle(fontSize = fs * 0.42f, fontWeight = FontWeight.Light)) { append("%") }
                            },
                            style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraLight, fontSize = fs, lineHeight = fs, letterSpacing = (-0.045).em, color = C.Fg, fontFeatureSettings = "tnum"),
                            modifier = Modifier.alignByBaseline(),
                        )
                        Text(if (s.pct) hl.word else "${hl.noun} ${hl.word}", style = T.sub.copy(fontSize = 17.sp), modifier = Modifier.padding(start = 10.dp).alignByBaseline())
                    }
                }
                NumberStyle.LIVE -> {
                    val v = if (s.pct) Life.livePct(hl.pct) else Life.live(hl.value)
                    val dot = v.indexOf('.').let { if (it < 0) v.length else it }
                    Text(
                        buildAnnotatedString {
                            append(v.substring(0, dot))
                            withStyle(SpanStyle(color = C.Mute)) { append(v.substring(dot)); if (s.pct) append("%") }
                        },
                        style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = if (v.length > 13) 34.sp else 44.sp, letterSpacing = (-0.03).em, color = C.Fg, fontFeatureSettings = "tnum"),
                        maxLines = 1, softWrap = false,
                    )
                }
            }
            val prefix = if (s.style == NumberStyle.LIVE) (if (s.pct) "${hl.word} · " else "${hl.measure.many} ${hl.word} · ") else ""
            Text(prefix + sub, style = T.sub, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

// ================================================================== the grid

@Composable
fun DotGrid(s: Settings, m: GridModel, ripple: Float, onName: (Int) -> Unit) {
    val view = LocalView.current
    val lit = if (s.sun) Color(Life.litColor(Life.sunLevel())) else C.Fg
    val ratio = if (m.cols == 10) 0.6f else 0.56f
    val appear = remember(m.kind, m.total) { Animatable(0f) }
    LaunchedEffect(appear) { appear.animateTo(1f, tween(700 + m.total * 9, easing = LinearEasing)) }
    val breathe by rememberInfiniteTransition(label = "br").animateFloat(1f, .55f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    var tip by remember(m.kind) { mutableStateOf<Int?>(null) }
    var sticky by remember(m.kind) { mutableStateOf(false) }
    LaunchedEffect(tip, sticky) { if (tip != null && sticky) { delay(3500); tip = null; sticky = false } }
    val named = remember(s.names, m.kind, m.total) { (0 until m.total).filter { i -> m.yearIndex?.let { s.names.containsKey(it(i)) } == true }.toSet() }
    val doneInt = floor(m.done).toInt()

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val cell = w / (m.cols - (1 - ratio))
        val inset = cell * (1 - ratio) / 2
        val rows = ceil(m.total / m.cols.toFloat()).toInt()
        val hPx = rows * cell - 2 * inset
        fun idxAt(o: Offset): Int? {
            val col = floor((o.x + inset) / cell).toInt()
            val row = floor((o.y + inset) / cell).toInt()
            if (col !in 0 until m.cols || row < 0) return null
            val i = row * m.cols + col
            return if (i in 0 until m.total) i else null
        }
        fun set(i: Int?) { if (i != null && i != tip) { tip = i; view.tick() } }

        Canvas(
            Modifier.fillMaxWidth().height(with(density) { hPx.toDp() }).pointerInput(m.kind, m.total, cell) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Hold briefly to scrub through time; move sideways right away to swipe views; lift to tap.
                    val outcome = withTimeoutOrNull(170L) {
                        var r = 0
                        while (r == 0) {
                            val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            r = when {
                                ch == null -> 2
                                !ch.pressed -> 1
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
                                val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                set(idxAt(ch.position))
                            }
                            if (tip != null) sticky = true
                        }
                        1 -> { val i = idxAt(down.position); if (i != null) { tip = i; sticky = true; view.tick() } else tip = null }
                        else -> Unit
                    }
                }
            },
        ) {
            val total = m.total
            val dD = 0.7f + total * 0.009f
            val rD = 0.9f + doneInt * 0.016f
            for (i in 0 until total) {
                val cx = -inset + (i % m.cols + .5f) * cell
                val cy = -inset + (i / m.cols + .5f) * cell
                val l = ((appear.value * dD - i * 0.009f) / 0.7f).coerceIn(0f, 1f)
                val e = 1f - (1f - l) * (1f - l) * (1f - l)
                var k = 0.4f + 0.6f * e
                if (ripple < 1f && i <= doneInt) {
                    val lr = (ripple * rD - i * 0.016f) / 0.9f
                    if (lr > 0f && lr < 1f) k *= 1f + 0.32f * sin(lr * PI).toFloat()
                }
                if (i == tip) k *= 1.38f
                val r = cell * ratio / 2 * k
                val c = Offset(cx, cy)
                when {
                    i < doneInt -> drawCircle(lit, r, c, alpha = e)
                    i == doneInt && m.done < total -> {
                        val f = (m.done - doneInt).toFloat()
                        val al = e * breathe
                        drawCircle(C.Partial, r, c, alpha = al)
                        clipPath(Path().apply { addOval(Rect(c, r)) }) {
                            drawRect(lit, topLeft = Offset(cx - r, cy + r - 2 * r * f), size = Size(2 * r, 2 * r * f), alpha = al)
                        }
                    }
                    else -> drawCircle(C.Empty, r, c, alpha = e)
                }
                if (i in named) drawCircle(C.Fg.copy(alpha = .55f), cell * 0.43f, c, alpha = e, style = Stroke(1.5.dp.toPx()))
            }
        }

        val ti = tip
        if (ti != null && ti < m.total) {
            val cx = -inset + (ti % m.cols + .5f) * cell
            val dotTop = -inset + (ti / m.cols) * cell + cell * 0.12f
            val dotBottom = dotTop + cell * 0.76f
            val yi = m.yearIndex?.invoke(ti)
            val name = yi?.let { s.names[it] }
            val isNow = ti == doneInt && m.done < m.total
            val hint = if (sticky && yi != null && name == null) (if (ti <= doneInt) "Tap to name this year" else "Tap to plan this year") else null
            Tip(cx, dotTop, dotBottom, w, name, m.label(ti) + if (isNow) " · now" else "", hint,
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
                val p = measurable.measure(Constraints())
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

// ================================================================== sheets

@Composable
fun SheetHandle() {
    Box(Modifier.padding(top = 10.dp, bottom = 8.dp).size(36.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(C.Faint))
}

@Composable
fun Caps(t: String) { Text(t.uppercase(), style = T.caps, modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp)) }

@Composable
fun Chevron() { Text("›", style = T.body.copy(color = C.Faint, fontSize = 22.sp)) }

@Composable
fun SettingsSheet(st: AppState, onDismiss: () -> Unit, toast: (String) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val s = st.s
    var editBirth by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = C.Surface, scrimColor = Color.Black.copy(alpha = .6f), dragHandle = { SheetHandle() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Settings", style = T.h2, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))

            Caps("You")
            Group {
                val b = s.birth ?: LocalDate.now()
                SettingRow("Birthday", onClick = { editBirth = !editBirth }) {
                    Text("${b.dayOfMonth} ${Life.mon(b)} ${b.year}", style = T.body.copy(color = if (editBirth) C.Fg else C.Mute))
                }
                AnimatedVisibility(editBirth) {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) { DateWheels(b) { d -> st.update { it.copy(birth = d) } } }
                }
                Divider()
                SettingRow("Lifespan") { Stepper(s.span, { v -> st.update { it.copy(span = v) } }, 40..120) }
            }

            Caps("Number")
            Segmented(NumberStyle.entries, s.style, { when (it) { NumberStyle.CLASSIC -> "Classic"; NumberStyle.HERO -> "Large"; NumberStyle.LIVE -> "Live" } }) { v -> st.update(refresh = false) { it.copy(style = v) } }

            Caps("Feel")
            Group {
                SettingRow("Daylight", "Dots glow by day and dim at night") { Toggle(s.sun) { v -> st.update { it.copy(sun = v) } } }
                Divider()
                SettingRow("Ripple", "A wave runs through your years each time you come back") { Toggle(s.ripple) { v -> st.update(refresh = false) { it.copy(ripple = v) } } }
            }

            Caps("Lock screen")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WallStyle.entries.forEach { ws ->
                    val sel = ws == s.wall
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(
                            Modifier.fillMaxWidth().aspectRatio(9f / 19.5f).clip(RoundedCornerShape(14.dp))
                                .border(if (sel) 2.dp else 1.dp, if (sel) C.Fg else C.Sep, RoundedCornerShape(14.dp))
                                .pressable({ st.update(forceLock = true) { it.copy(wall = ws) } }),
                        ) {
                            drawIntoCanvas { Render.wallpaper(ctx, it.nativeCanvas, size.width.toInt(), size.height.toInt(), s, ws, caption = false) }
                        }
                        Text(ws.label, style = T.small.copy(color = if (sel) C.Fg else C.Mute), modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Group {
                SettingRow("Set live wallpaper", "Ripples when you wake your phone, rests otherwise", onClick = { setLiveWallpaper(ctx) }) { Chevron() }
                Divider()
                SettingRow("Auto-update lock screen", "Repaints your lock screen every hour, on any phone") {
                    Toggle(s.autoLock) { v -> st.update(forceLock = v) { it.copy(autoLock = v) }; if (v) toast("Lock screen updating") }
                }
                Divider()
                SettingRow("Save as image", onClick = { if (saveWallpaperImage(ctx, s) != null) toast("Saved to Pictures/Years") else toast("Couldn't save the image") }) { Chevron() }
            }

            Caps("Widgets")
            Segmented(WidgetLook.entries, s.widgetLook, { it.label }) { v -> st.update { it.copy(widgetLook = v) } }
            Spacer(Modifier.height(14.dp))
            Group {
                val list = listOf(
                    "Life" to LifeWidgetReceiver::class.java, "Number" to NumberWidgetReceiver::class.java,
                    "Decade" to DecadeWidgetReceiver::class.java, "This year" to YearWidgetReceiver::class.java,
                )
                list.forEachIndexed { i, (n, cls) ->
                    if (i > 0) Divider()
                    SettingRow("Add $n widget", onClick = { if (!pinWidget(ctx, cls)) toast("Long-press your home screen, then Widgets") }) { Chevron() }
                }
            }

            Caps("Share")
            Group { SettingRow("Share your grid", onClick = { shareGrid(ctx, s) }) { Chevron() } }

            Text(
                "Each dot is one year. Swipe for your decade and this year of life. Hold a dot and drag to travel through time. Tap a year to name it, past or future. Tap the number to count in summers, weekends, full moons or sunrises.\n\nYears has no internet permission. Everything stays on this phone.",
                style = T.small, modifier = Modifier.padding(top = 20.dp, start = 4.dp, end = 4.dp),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Done", { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } })
        }
    }
}

@Composable
fun MilestoneSheet(st: AppState, year: Int, onDismiss: () -> Unit) {
    val s = st.s
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(s.names[year] ?: "") }
    val focus = remember { FocusRequester() }
    val view = LocalView.current
    val past = s.birth?.let { year <= Life.age(it).toInt() } ?: true
    fun save() {
        val v = text.trim().take(40)
        st.update { it.copy(names = if (v.isEmpty()) it.names - year else it.names + (year to v)) }
        view.confirm(); onDismiss()
    }
    LaunchedEffect(Unit) { delay(350); runCatching { focus.requestFocus() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = C.Surface, scrimColor = Color.Black.copy(alpha = .6f), dragHandle = { SheetHandle() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding()) {
            Text("Age $year · ${(s.birth?.year ?: 0) + year}", style = T.h2, modifier = Modifier.padding(top = 6.dp))
            Text(if (past) "What happened this year?" else "What do you want this year to hold?", style = T.sub, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            BasicTextField(
                value = text, onValueChange = { text = it.take(40) }, singleLine = true,
                textStyle = T.body, cursorBrush = SolidColor(C.Fg),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).clip(RoundedCornerShape(12.dp)).background(C.Surface2).padding(16.dp),
                decorationBox = { inner -> Box { if (text.isEmpty()) Text(if (past) "Moved to a new city" else "See the northern lights", style = T.body.copy(color = C.Faint)); inner() } },
            )
            Text("Named years get a thin ring.", style = T.small, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Save", { save() })
            if (s.names.containsKey(year)) QuietButton("Remove name", { text = ""; save() })
        }
    }
}

// ================================================================== system actions

fun setLiveWallpaper(ctx: Context) {
    val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(ctx, YearsWallpaperService::class.java))
    runCatching { ctx.startActivity(i) }.onFailure { runCatching { ctx.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) } }
}

fun pinWidget(ctx: Context, cls: Class<*>): Boolean {
    val m = AppWidgetManager.getInstance(ctx)
    return if (m.isRequestPinAppWidgetSupported) m.requestPinAppWidget(ComponentName(ctx, cls), null, null) else false
}

fun saveImage(ctx: Context, bmp: Bitmap, name: String): Uri? = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Years")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    uri
}.getOrNull()

fun saveWallpaperImage(ctx: Context, s: Settings): Uri? {
    val (w, h) = Background.screen(ctx)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Render.wallpaper(ctx, ACanvas(bmp), w, h, s, s.wall)
    return saveImage(ctx, bmp, "years-wallpaper")
}

fun shareGrid(ctx: Context, s: Settings) {
    val uri = saveImage(ctx, Render.shareCard(ctx, s), "years") ?: return
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { ctx.startActivity(Intent.createChooser(send, "Share your years")) }
}
