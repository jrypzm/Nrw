@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package app.years

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.max

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
    /** Bumps when something outside settings changes (a daily line, coming back to the app). */
    var tick by mutableIntStateOf(0)
        private set

    fun update(refresh: Boolean = true, forceLock: Boolean = false, f: (Settings) -> Settings) {
        s = f(s)
        Prefs.save(ctx, s)
        if (refresh) Background.refreshNow(ctx, forceLock)
    }

    fun bump() { tick++ }
}

/** Which sheet is open. */
sealed interface Sheet {
    data object Settings : Sheet
    data class Milestone(val year: Int) : Sheet
    data class PersonEdit(val index: Int?) : Sheet
    data class EventEdit(val index: Int?) : Sheet
    data class Line(val date: LocalDate) : Sheet
    data class LetterRead(val index: Int) : Sheet
    data object Review : Sheet
    data object Lifespan : Sheet
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
        Spacer(Modifier.height(56.dp))
        Text("yrs.", style = T.h2.copy(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.05).em))
        Spacer(Modifier.height(36.dp))
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
                    Text("Stays on this phone. yrs. has no internet access.", style = T.sub, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(48.dp))
                    DateWheels(date, { date = it })
                } else {
                    Text("How long\nis a life?", style = T.h1)
                    Text("80 is a fair average. You can estimate yours later.", style = T.sub, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$span", style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraLight, fontSize = 112.sp, letterSpacing = (-0.05).em, color = C.Fg, fontFeatureSettings = "tnum"), modifier = Modifier.alignByBaseline())
                        Text("years", style = T.sub.copy(fontSize = 17.sp), modifier = Modifier.padding(start = 10.dp).alignByBaseline())
                        Spacer(Modifier.weight(1f))
                        Stepper(span, { span = it }, 40..120)
                    }
                    Spacer(Modifier.height(24.dp))
                    val age = Life.age(date)
                    PreviewGrid(span, age)
                    Text("${Life.whole(max(0.0, span - age))} years · ${Life.whole(Life.daysLeft(Settings(birth = date, span = span)).toDouble())} days left", style = T.sub, modifier = Modifier.padding(top = 14.dp))
                }
            }
        }
        PrimaryButton(if (step == 0) "Continue" else "Begin", {
            if (step == 0) step = 1 else {
                view.confirm()
                st.update { it.copy(birth = date, span = span, reviewedAge = Life.age(date).toInt()) }
            }
        })
        if (step == 1) QuietButton("Back", { step = 0 }) else Spacer(Modifier.height(48.dp))
        Spacer(Modifier.height(8.dp))
    }
}

// ================================================================== home

const val PAGES = 5

@Composable
fun Home(st: AppState) {
    val s = st.s
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = s.page.coerceIn(0, PAGES - 1)) { PAGES }
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

    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }

    // The ripple plays each time you come back.
    val ripple = remember { Animatable(1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                now = ZonedDateTime.now()
                st.bump()
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

    // Moments that find you: a letter you sealed years ago, or the year you just finished.
    LaunchedEffect(Unit) {
        val b = st.s.birth ?: return@LaunchedEffect
        val lived = Life.age(b).toInt()
        val letter = st.s.letters.indexOfFirst { !it.opened && it.age <= lived }
        val lb = Life.lastBirthday(b, LocalDate.now())
        when {
            letter >= 0 -> sheet = Sheet.LetterRead(letter)
            st.s.reviewedAge in 0 until lived && ChronoUnit.DAYS.between(lb, LocalDate.now()) <= 30 -> sheet = Sheet.Review
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("yrs.", style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.05).em, color = C.Faint))
                Spacer(Modifier.weight(1f))
                RoundButton({ sheet = Sheet.Settings }, size = 32.dp) { MoreDots() }
            }
            Spacer(Modifier.height(26.dp))
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Top, beyondViewportPageCount = 1) { page ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 110.dp)) {
                    when (page) {
                        0, 1, 2 -> {
                            val kind = ViewKind.entries[page]
                            val m = Life.model(s, kind, now)
                            Header(st, m)
                            Spacer(Modifier.height(if (s.style == NumberStyle.HERO) 40.dp else 32.dp))
                            DotGrid(st, m, if (kind == ViewKind.LIFE && s.zoom == Life.ZOOM_YEARS) ripple.value else 1f) { y -> sheet = Sheet.Milestone(y) }
                            if (kind == ViewKind.LIFE) ZoomBar(st)
                            if (kind == ViewKind.YEAR) YearExtras(st, onLine = { sheet = Sheet.Line(it) }, onEvent = { sheet = Sheet.EventEdit(it) })
                        }
                        3 -> PeoplePage(st) { sheet = Sheet.PersonEdit(it) }
                        else -> LedgerPage(st)
                    }
                }
            }
        }

        val showToggles = pager.currentPage < 3
        val toggleAlpha by animateFloatAsState(if (showToggles) 1f else 0f, label = "ta")
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, C.Bg.copy(alpha = .9f), C.Bg)))
                .navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 20.dp, top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).alpha(toggleAlpha)) { if (showToggles) Pill(if (s.lived) "Lived" else "Left") { st.update { it.copy(lived = !it.lived) } } }
            PagerDots(pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
            Box(Modifier.weight(1f).alpha(toggleAlpha), contentAlignment = Alignment.CenterEnd) {
                if (showToggles) RoundButton({ st.update { it.copy(pct = !it.pct) } }, on = s.pct) {
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

    val close = { sheet = null }
    when (val sh = sheet) {
        Sheet.Settings -> SettingsSheet(st, close, { toast = it }, { sheet = Sheet.Lifespan })
        is Sheet.Milestone -> MilestoneSheet(st, sh.year, close)
        is Sheet.PersonEdit -> PersonSheet(st, sh.index, close)
        is Sheet.EventEdit -> EventSheet(st, sh.index, close)
        is Sheet.Line -> LineSheet(st, sh.date, close)
        is Sheet.LetterRead -> LetterSheet(st, sh.index, close)
        Sheet.Review -> ReviewSheet(st, close)
        Sheet.Lifespan -> LifespanSheet(st, close) { toast = it }
        null -> Unit
    }
}

@Composable
fun PagerDots(current: Int, onClick: (Int) -> Unit) {
    Row {
        repeat(PAGES) { i ->
            val c by animateColorAsState(if (i == current) C.Fg else C.Faint, label = "pd")
            Box(Modifier.size(18.dp, 36.dp).clickable(remember { MutableInteractionSource() }, indication = null) { onClick(i) }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(if (i >= 3) 5.dp else 7.dp).clip(CircleShape).background(c))
            }
        }
    }
}

// ================================================================== header

/** Tap to change what you count. Hold to feel your heartbeat while the seconds run. */
@Composable
fun Header(st: AppState, m: GridModel) {
    val s = st.s
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var beating by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    val hl = Life.headline(s, m)
    val birth = s.birth
    val sub = when {
        m.kind == ViewKind.LIFE && birth != null && Life.isBirthday(birth) -> "Happy birthday · you're ${Life.age(birth).toInt()} today"
        m.context != null -> "${m.context} · ${m.sub}"
        else -> m.sub
    }
    val canCycle = m.kind == ViewKind.LIFE && !s.pct && m.unit != "lifetime"
    val key = if (beating) "beat" else "${s.style}|${s.lived}|${s.pct}|${s.unit}|${m.kind}"
    AnimatedContent(
        targetState = key, label = "hdr",
        modifier = Modifier.pointerInput(canCycle) {
            detectTapGestures(
                onTap = {
                    // letting go after a heartbeat is not a tap
                    if (held) held = false
                    else if (canCycle) { view.tick(); st.update(refresh = false) { it.copy(unit = (it.unit + 1) % Life.MEASURES.size) } }
                },
                onPress = {
                    held = false
                    val job = scope.launch {
                        delay(450)
                        held = true
                        beating = true
                        while (true) { Heart.beat(ctx); delay(1000) }
                    }
                    tryAwaitRelease()
                    job.cancel()
                    beating = false
                },
            )
        },
        transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 8 }) togetherWith fadeOut(tween(140)) },
    ) { k ->
        Column(Modifier.fillMaxWidth()) {
            if (k == "beat") {
                Heartbeat(s)
                return@Column
            }
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

@Composable
fun Heartbeat(s: Settings) {
    var t by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { withFrameMillis { }; t = System.currentTimeMillis() } }
    val left = if (t > 0) Life.yearsLeft(s) * 365.2425 * 86400.0 else 0.0
    Text(Life.whole(left), style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 44.sp, letterSpacing = (-0.03).em, color = C.Fg, fontFeatureSettings = "tnum"), maxLines = 1, softWrap = false)
    Text("seconds left · each beat is one of them", style = T.sub, modifier = Modifier.padding(top = 3.dp))
}
