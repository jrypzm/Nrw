@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package app.years

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
    data class Fact(val index: Int) : Sheet
    data object Guide : Sheet
    data object About : Sheet
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
val PAGE_NAMES = listOf("Life", "Decade", "Year", "People", "Time")

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
    // The grids only need a fresh "now" every so often; the Live number ticks on its own, inside the header.
    LaunchedEffect(Unit) {
        while (true) { delay(20_000); now = ZonedDateTime.now() }
    }

    var sheet by rememberSaveable(stateSaver = SheetSaver) { mutableStateOf<Sheet?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }

    // The first-run tour: each step lights up one real control and moves on once you've tried it.
    val targets = remember { TourTargets() }
    // the opening plays each time the app starts (not on rotation, not in screenshots or a replayed tour)
    val still = LocalInspectionMode.current || Render.still || tourAt != null
    var intro by rememberSaveable { mutableStateOf(!still && st.s.birth != null) }
    var firstResume by remember { mutableStateOf(true) }
    var tourStep by remember { mutableIntStateOf(tourAt ?: -1) }
    fun tourSnap() = TourSnap(st.s, TourSignal.beats, TourSignal.taps, TourSignal.scrubs, sheet)
    var tourBase by remember { mutableStateOf(tourSnap()) }
    var tourOk by remember { mutableStateOf(false) }
    fun tourGo(n: Int) {
        tourOk = false
        if (n !in TOUR.indices) {
            val finished = n >= TOUR.size
            tourStep = -1; st.update(refresh = false) { it.copy(tourDone = true) }
            if (finished) scope.launch { delay(700); toast = "You're all set. The guide is in Settings." }
            return
        }
        if (n in 1..2 && (st.s.zoom != Life.ZOOM_YEARS || st.s.pct)) st.update(refresh = false) { it.copy(zoom = Life.ZOOM_YEARS, pct = false) }
        if (n == 0 && tourStep < 0) {
            scope.launch { pager.animateScrollToPage(0) }
            st.update(refresh = false) { it.copy(zoom = Life.ZOOM_YEARS, pct = false) }
        }
        tourBase = tourSnap()
        tourStep = n
    }
    LaunchedEffect(Unit) { if (tourAt == null && !st.s.tourDone) { delay(700L + if (intro) INTRO_MS else 0); tourGo(0) } }
    // you did it: the card says so, then moves on
    LaunchedEffect(tourStep, st.s, sheet, TourSignal.beats, TourSignal.taps, TourSignal.scrubs) {
        val step = TOUR.getOrNull(tourStep) ?: return@LaunchedEffect
        if (tourOk || step.done?.invoke(tourBase, tourSnap()) != true) return@LaunchedEffect
        view.confirm()
        if (tourStep == TOUR.size - 1) { tourGo(TOUR.size); return@LaunchedEffect }
        tourOk = true
    }
    // moving on lives in its own effect, so trying the gesture again during the pause can't cancel it
    LaunchedEffect(tourOk, tourStep) {
        if (!tourOk) return@LaunchedEffect
        val n = tourStep
        delay(if ((TOUR.getOrNull(n)?.ok?.length ?: 0) > 30) 1700 else 1200)
        if (tourStep == n && tourOk) tourGo(n + 1)
    }

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
            "TODAY" -> { pager.scrollToPage(2); if (sheet !is Sheet.LetterRead && sheet != Sheet.Review) sheet = Sheet.Line(LocalDate.now()) }
            "PEOPLE" -> pager.scrollToPage(3)
            "ESTIMATE" -> { pager.scrollToPage(0); if (sheet !is Sheet.LetterRead) sheet = Sheet.Lifespan }
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
                    delay(if (firstResume && intro) INTRO_MS - 450L else 500L)
                    firstResume = false
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
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("yrs.", style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.05).em, color = C.Fg))
                Spacer(Modifier.weight(1f))
                Box(Modifier.tourTarget(targets, "settings")) { RoundToggle(false, { sheet = Sheet.Settings }, size = 40.dp) { MoreDots(it) } }
            }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Top, beyondViewportPageCount = 1) { page ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 10.dp, bottom = 150.dp)) {
                    when (page) {
                        0, 1, 2 -> {
                            val kind = ViewKind.entries[page]
                            val m = Life.model(s, kind, now)
                            val life = kind == ViewKind.LIFE
                            Header(st, m, targets.takeIf { life })
                            Spacer(Modifier.height(30.dp))
                            Box(if (life) Modifier.tourTarget(targets, "grid") else Modifier) {
                                DotGrid(st, m, if (life && s.zoom == Life.ZOOM_YEARS) ripple.value else 1f, onLife = { sheet = Sheet.Fact(it) }) { y -> sheet = Sheet.Milestone(y) }
                            }
                            when (kind) {
                                ViewKind.LIFE -> {
                                    Box(Modifier.tourTarget(targets, "zoom")) { ZoomRuler(s.zoom) { z -> st.update(refresh = false) { it.copy(zoom = z) } } }
                                    LifeFoot(st)
                                }
                                ViewKind.DECADE -> DecadeFoot(st)
                                ViewKind.YEAR -> YearExtras(st, onLine = { sheet = Sheet.Line(it) }, onEvent = { sheet = Sheet.EventEdit(it) })
                            }
                        }
                        3 -> PeoplePage(st) { sheet = Sheet.PersonEdit(it) }
                        else -> LedgerPage(st)
                    }
                }
            }
        }

        // the page bar floats over a soft fade, so content scrolls away under it
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, C.Bg.copy(alpha = .85f), C.Bg))),
        )
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp).tourTarget(targets, "pages")) {
            PageBar(pager.currentPage + pager.currentPageOffsetFraction, PAGE_NAMES, { scope.launch { pager.animateScrollToPage(it) } })
        }

        AnimatedVisibility(
            visible = toast != null, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 86.dp),
            enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Text(toast ?: "", style = T.label.copy(fontSize = 14.sp), modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(C.Surface2).padding(horizontal = 16.dp, vertical = 10.dp))
        }

        if (intro) {
            val b = st.s.birth
            val age = b?.let { Life.age(it) } ?: 0.0
            Intro(
                target = {
                    // the dot of the year you're in, when the Life grid is showing years
                    val r = targets.rects["grid"]
                    if (r == null || pager.currentPage != 0 || st.s.zoom != Life.ZOOM_YEARS || age >= st.s.span) null else {
                        val cell = r.width / 9.6f; val inset = cell * .2f; val i = age.toInt()
                        androidx.compose.ui.geometry.Offset(r.left - inset + (i % 10 + .5f) * cell, r.top - inset + (i / 10 + .5f) * cell) to cell * .3f
                    }
                },
                lived = (age / st.s.span).toFloat().coerceIn(0f, 1f),
                yearFill = (age - floor(age)).toFloat(),
            ) { intro = false }
        }
        if (tourStep in TOUR.indices && sheet == null) {
            TourOverlay(st, targets, tourStep, tourOk, onNext = { view.tap(); tourGo(tourStep + 1) }, onBack = { view.tap(); tourGo((tourStep - 1).coerceAtLeast(0)) }, onSkip = { tourGo(-1) })
        }
    }

    val close = { sheet = null }
    when (val sh = sheet) {
        Sheet.Settings -> SettingsSheet(st, close, { toast = it }, { sheet = Sheet.Lifespan }, { sheet = Sheet.Guide }, { sheet = Sheet.About })
        Sheet.Guide -> GuideSheet(close) { sheet = null; tourGo(0) }
        Sheet.About -> AboutSheet(close)
        is Sheet.Milestone -> MilestoneSheet(st, sh.year, close)
        is Sheet.PersonEdit -> PersonSheet(st, sh.index, close)
        is Sheet.EventEdit -> EventSheet(st, sh.index, close)
        is Sheet.Line -> LineSheet(st, sh.date, close)
        is Sheet.LetterRead -> LetterSheet(st, sh.index, close)
        Sheet.Review -> ReviewSheet(st, close)
        Sheet.Lifespan -> EstimateSheet(st, close) { toast = it }
        is Sheet.Fact -> FactSheet(st, sh.index, close)
        null -> Unit
    }
}


/** Keeps the open sheet across rotation and theme changes. */
val SheetSaver = Saver<Sheet?, String>(
    save = { sh ->
        when (sh) {
            null -> ""
            Sheet.Settings -> "settings"; Sheet.Review -> "review"; Sheet.Lifespan -> "lifespan"; Sheet.Guide -> "guide"; Sheet.About -> "about"
            is Sheet.Milestone -> "milestone:${sh.year}"; is Sheet.PersonEdit -> "person:${sh.index ?: ""}"; is Sheet.EventEdit -> "event:${sh.index ?: ""}"
            is Sheet.Line -> "line:${sh.date}"; is Sheet.LetterRead -> "letter:${sh.index}"; is Sheet.Fact -> "fact:${sh.index}"
        }
    },
    restore = { v ->
        val k = v.substringBefore(":"); val a = v.substringAfter(":", "")
        runCatching {
            when (k) {
                "settings" -> Sheet.Settings; "review" -> Sheet.Review; "lifespan" -> Sheet.Lifespan; "guide" -> Sheet.Guide; "about" -> Sheet.About
                "milestone" -> Sheet.Milestone(a.toInt()); "person" -> Sheet.PersonEdit(a.toIntOrNull()); "event" -> Sheet.EventEdit(a.toIntOrNull())
                "line" -> Sheet.Line(LocalDate.parse(a)); "letter" -> Sheet.LetterRead(a.toInt()); "fact" -> Sheet.Fact(a.toInt())
                else -> null
            }
        }.getOrNull()
    },
)
