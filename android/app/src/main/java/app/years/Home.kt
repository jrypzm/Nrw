package app.years

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor

// ================================================================== the header of a page

/**
 * What a page is about, its number, and what the number means. Tap the number to count another way;
 * hold it to feel a heartbeat for every second. Left or Lived and % sit right under it.
 */
@Composable
fun Header(st: AppState, m: GridModel, targets: TourTargets?) {
    val s = st.s
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var beating by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    // Live counts to eight decimals, so only here does time tick fast: 20 times a second, for this text alone.
    var liveNow by remember { mutableStateOf(java.time.ZonedDateTime.now()) }
    LaunchedEffect(s.style) { while (s.style == NumberStyle.LIVE) { delay(50); liveNow = java.time.ZonedDateTime.now() } }
    val hl = Life.headline(s, if (s.style == NumberStyle.LIVE) Life.model(s, m.kind, liveNow) else m)
    val birth = s.birth
    val life = m.kind == ViewKind.LIFE
    val canCycle = life && !s.pct && m.unit == "year"
    val caption = when (m.kind) {
        ViewKind.LIFE -> if (m.unit == "lifetime") "All of history" else "Your life"
        ViewKind.DECADE -> m.context ?: "This decade"
        ViewKind.YEAR -> "This year of your life"
    }
    val sub = when {
        life && birth != null && Life.isBirthday(birth) -> "Happy birthday · you're ${Life.age(birth).toInt()} today"
        m.kind == ViewKind.DECADE -> m.sub
        m.context != null -> "${m.context} · ${m.sub}"
        life && m.unit == "year" && birth != null -> "${m.sub} · ${floor((Life.age(birth) % 1) * 100).toInt()}% through this year"
        else -> m.sub
    }

    Cap(caption)
    Spacer(Modifier.height(4.dp))
    Box(
        (if (targets != null) Modifier.tourTarget(targets, "number") else Modifier).fillMaxWidth().pointerInput(canCycle) {
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
                        held = true; beating = true
                        TourSignal.beats++
                        while (true) { Heart.beat(ctx); delay(1000) }
                    }
                    tryAwaitRelease()
                    job.cancel()
                    beating = false
                },
            )
        },
    ) {
        AnimatedContent(beating, transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(360, easing = Ease)) { it / 10 }) togetherWith fadeOut(tween(140)) }, label = "beat") { b ->
            if (b) Heartbeat(s) else Column {
                val target = if (s.pct) hl.pct else hl.value
                val key = "${s.unit}|${s.lived}|${s.pct}|${m.kind}|${m.unit}|${s.span}|${s.style}"
                val v = rolling(target, key, from = 0.0)
                val word = if (s.pct) hl.word else "${hl.noun} ${hl.word}"
                when (s.style) {
                    NumberStyle.LIVE -> {
                        val t = if (s.pct) Life.livePct(hl.pct) else Life.live(hl.value)
                        val dot = t.indexOf('.').let { if (it < 0) t.length else it }
                        Text(
                            buildAnnotatedString { append(t.substring(0, dot)); withStyle(SpanStyle(color = C.Dim)) { append(t.substring(dot)); if (s.pct) append("%") } },
                            style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraLight, fontSize = if (t.length > 13) 40.sp else 50.sp, letterSpacing = (-0.035).em, color = C.Fg, fontFeatureSettings = "tnum"),
                            maxLines = 1, softWrap = false, modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                    else -> BigNumber(if (s.pct) "${floor(v).toInt()}" else Life.whole(v), if (s.style == NumberStyle.HERO) 150.sp else 104.sp, pct = s.pct)
                }
                Text(
                    if (life && !s.lived && !s.pct && hl.value <= 0.0) "Every year is a gift" else word,
                    style = T.word, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
    Text(sub, style = T.sub, modifier = Modifier.padding(top = 8.dp))
    if (canCycle) Box(Modifier.padding(top = 16.dp)) { UnitChips(s.unit.mod(Life.MEASURES.size)) { u -> st.update(refresh = false) { it.copy(unit = u) } } }
    Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(if (targets != null) Modifier.tourTarget(targets, "lived") else Modifier) { LeftLived(s.lived, { v -> st.update { it.copy(lived = v) } }) }
        Spacer(Modifier.width(10.dp))
        Box(if (targets != null) Modifier.tourTarget(targets, "pct") else Modifier) {
            RoundToggle(s.pct, { st.update { it.copy(pct = !it.pct) } }) { c -> Text("%", style = T.label.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c)) }
        }
    }
}

/** Every second you have left, ticking away while you hold the number. */
@Composable
fun Heartbeat(s: Settings) {
    var t by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(100); t = System.currentTimeMillis() } }
    val left = if (t > 0) Life.yearsLeft(s) * 365.2425 * 86400.0 else 0.0
    Column {
        BigNumber(Life.whole(left), 112.sp)
        Text("seconds left", style = T.word, modifier = Modifier.padding(top = 2.dp))
        Text("Each beat you feel is one of them", style = T.sub, modifier = Modifier.padding(top = 2.dp))
    }
}

// ================================================================== under the grid

@Composable
fun LifeFoot(st: AppState) {
    val s = st.s
    val ctx = LocalContext.current
    val birth = s.birth ?: return
    val today = LocalDate.now()
    val line = remember(st.tick, today) { Lines.get(ctx, today) }
    val lived = Life.age(birth).toInt()
    val next = Life.birthdayIn(birth, Life.lastBirthday(birth, today).year + 1)
    val toNext = ChronoUnit.DAYS.between(today, next)
    Row(Modifier.fillMaxWidth().padding(top = 30.dp)) {
        FootStat("Today", "Day ${Life.whole(Life.dayOfLife(birth, today).toDouble())}", line?.let { "“$it”" }, Modifier.weight(1f).padding(end = 12.dp))
        FootStat("Next birthday", if (Life.isBirthday(birth, today)) "Today" else "in ${Life.whole(toNext.toDouble())} ${if (toNext == 1L) "day" else "days"}", "turning ${if (Life.isBirthday(birth, today)) lived else lived + 1}", Modifier.weight(1f))
    }
    val history = s.zoom == 0
    FactCard(
        if (history) "From history" else "Did you know",
        { if (history) Facts.life(s, (0 until Life.LIFETIMES - 1).random()) else Facts.you(s) },
        modifier = Modifier.padding(top = 26.dp),
    )
}

@Composable
fun DecadeFoot(st: AppState) {
    val s = st.s
    val birth = s.birth ?: return
    val lived = Life.age(birth).toInt()
    val start = lived / 10 * 10
    val togo = 10 - (lived - start)
    val named = s.names.keys.count { it in start until start + 10 }
    Row(Modifier.fillMaxWidth().padding(top = 30.dp)) {
        FootStat(if (start == 0) "Your first decade" else "Your ${start}s", "$togo ${if (togo == 1) "year" else "years"} to go", "ending at ${start + 10}", Modifier.weight(1f).padding(end = 12.dp))
        FootStat("Named years", if (named == 0) "None yet" else "$named", "tap a year to name it", Modifier.weight(1f))
    }
    val lead = remember(start) { (start until start + 10).shuffled().firstNotNullOfOrNull { y -> Facts.year(s, y).firstOrNull { it.startsWith("In ") } } }
    FactCard("In your ${if (start == 0) "first decade" else "${start}s"}", { Facts.year(s, (start until start + 10).random()) }, lead = lead, modifier = Modifier.padding(top = 26.dp))
}
