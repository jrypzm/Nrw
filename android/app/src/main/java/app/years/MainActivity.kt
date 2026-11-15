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
    /** Which screen a widget asked for ("TODAY", "PEOPLE"...). */
    private val open = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        open.value = intent.getStringExtra("open")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) open.value = intent?.getStringExtra("open")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        Background.schedule(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = C.Bg, surface = C.Surface, primary = C.Fg, onPrimary = Color.Black, onSurface = C.Fg)) {
                App(open.value) { open.value = null }
            }
        }
    }
}

/** [persist] is false only in screenshot tests, where nothing is saved or scheduled. */
class AppState(private val ctx: Context, initial: Settings? = null, private val persist: Boolean = true) {
    var s by mutableStateOf(initial ?: Prefs.load(ctx))
        private set
    /** Bumps when something outside settings changes (a daily line, coming back to the app). */
    var tick by mutableIntStateOf(0)
        private set

    fun update(refresh: Boolean = true, forceLock: Boolean = false, f: (Settings) -> Settings) {
        s = f(s)
        if (!persist) return
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
    data object Guide : Sheet
}

@Composable
fun App(open: String?, consumed: () -> Unit) {
    val ctx = LocalContext.current
    val st = remember { AppState(ctx.applicationContext) }
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        AnimatedContent(
            targetState = st.s.birth == null,
            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(250)) }, label = "root",
        ) { onboarding -> if (onboarding) Onboarding(st) else Home(st, open, consumed = consumed) }
    }
}

// ================================================================== home

const val PAGES = 5

@Composable
fun Home(st: AppState, open: String?, tourAt: Int? = null, consumed: () -> Unit) {
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
            delay(if (s.style == NumberStyle.LIVE) 50 else 20_000)
            now = ZonedDateTime.now()
        }
    }

    var sheet by remember { mutableStateOf<Sheet?>(null) }

    // The first-run tour: each step lights up one real control and moves on once you've tried it.
    val targets = remember { TourTargets() }
    var tourStep by remember { mutableIntStateOf(tourAt ?: if (st.s.tourDone) -1 else 0) }
    var tourBase by remember { mutableStateOf(st.s) }
    fun tourGo(n: Int) {
        if (n !in TOUR.indices) { tourStep = -1; st.update(refresh = false) { it.copy(tourDone = true) }; return }
        if (n == 0) {
            scope.launch { pager.animateScrollToPage(0) }
            st.update(refresh = false) { it.copy(zoom = Life.ZOOM_YEARS, pct = false) }
        }
        tourBase = st.s
        tourStep = n
    }
    LaunchedEffect(Unit) { if (tourAt == null && tourStep == 0) { delay(700); tourGo(0) } }
    LaunchedEffect(tourStep, st.s, sheet) {
        val step = TOUR.getOrNull(tourStep) ?: return@LaunchedEffect
        if (step.done(tourBase, st.s, sheet)) { delay(if (tourStep == TOUR.size - 1) 0 else 850); view.confirm(); tourGo(tourStep + 1) }
    }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }

    // Moments that find you: a letter you sealed years ago, or the year you just finished.
    fun checkMoments() {
        if (sheet != null) return
        val b = st.s.birth ?: return
        val lived = Life.age(b).toInt()
        val letter = st.s.letters.indexOfFirst { !it.opened && it.age <= lived }
        val lb = Life.lastBirthday(b, LocalDate.now())
        when {
            letter >= 0 -> sheet = Sheet.LetterRead(letter)
            st.s.reviewedAge in 0 until lived && ChronoUnit.DAYS.between(lb, LocalDate.now()) <= 30 -> sheet = Sheet.Review
        }
    }

    // A widget tap opens the matching screen.
    LaunchedEffect(open) {
        when (open) {
            null -> return@LaunchedEffect
            "TODAY" -> { pager.scrollToPage(2); sheet = Sheet.Line(LocalDate.now()) }
            "PEOPLE" -> pager.scrollToPage(3)
            "DECADE" -> pager.scrollToPage(1)
            "YEAR" -> pager.scrollToPage(2)
            else -> pager.scrollToPage(0)
        }
        consumed()
    }

    // The ripple plays each time you come back.
    val ripple = remember { Animatable(1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                now = ZonedDateTime.now()
                st.bump()
                checkMoments()
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


    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("yrs.", style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.05).em, color = C.Faint))
                Spacer(Modifier.weight(1f))
                Box(Modifier.tourTarget(targets, "settings")) { RoundButton({ sheet = Sheet.Settings }, size = 32.dp) { MoreDots() } }
            }
            Spacer(Modifier.height(26.dp))
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Top, beyondViewportPageCount = 1) { page ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 110.dp)) {
                    when (page) {
                        0, 1, 2 -> {
                            val kind = ViewKind.entries[page]
                            val m = Life.model(s, kind, now)
                            val life = kind == ViewKind.LIFE
                            Box(if (life) Modifier.tourTarget(targets, "number") else Modifier) { Header(st, m) }
                            Spacer(Modifier.height(if (s.style == NumberStyle.HERO) 40.dp else 32.dp))
                            Box(if (life) Modifier.tourTarget(targets, "grid") else Modifier) {
                                DotGrid(st, m, if (life && s.zoom == Life.ZOOM_YEARS) ripple.value else 1f) { y -> sheet = Sheet.Milestone(y) }
                            }
                            if (life) Box(Modifier.tourTarget(targets, "zoom")) { ZoomBar(st) }
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
            Box(Modifier.weight(1f).alpha(toggleAlpha)) {
                if (showToggles) Box(Modifier.tourTarget(targets, "lived")) { Pill(if (s.lived) "Lived" else "Left") { st.update { it.copy(lived = !it.lived) } } }
            }
            Box(Modifier.tourTarget(targets, "pages")) { PagerDots(pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } } }
            Box(Modifier.weight(1f).alpha(toggleAlpha), contentAlignment = Alignment.CenterEnd) {
                if (showToggles) Box(Modifier.tourTarget(targets, "pct")) {
                    RoundButton({ st.update { it.copy(pct = !it.pct) } }, on = s.pct) {
                        Text("%", style = T.label.copy(fontWeight = FontWeight.SemiBold, color = if (s.pct) Color.Black else C.Fg))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = toast != null, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp),
            enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Text(toast ?: "", style = T.label.copy(fontSize = 14.sp), modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(C.Surface2).padding(horizontal = 16.dp, vertical = 10.dp))
        }

        if (tourStep in TOUR.indices && sheet == null) {
            TourOverlay(targets, tourStep, onNext = { view.tap(); tourGo(tourStep + 1) }, onSkip = { tourGo(-1) })
        }
    }

    val close = { sheet = null }
    when (val sh = sheet) {
        Sheet.Settings -> SettingsSheet(st, close, { toast = it }, { sheet = Sheet.Lifespan }, { sheet = Sheet.Guide })
        Sheet.Guide -> GuideSheet(close) { sheet = null; tourGo(0) }
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
    val canCycle = m.kind == ViewKind.LIFE && !s.pct && m.unit == "year"
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
    LaunchedEffect(Unit) { while (true) { delay(50); t = System.currentTimeMillis() } }
    val left = if (t > 0) Life.yearsLeft(s) * 365.2425 * 86400.0 else 0.0
    Text(Life.whole(left), style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 44.sp, letterSpacing = (-0.03).em, color = C.Fg, fontFeatureSettings = "tnum"), maxLines = 1, softWrap = false)
    Text("seconds left · each beat is one of them", style = T.sub, modifier = Modifier.padding(top = 3.dp))
}
