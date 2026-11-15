@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.years

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

// ================================================================== a story for every history dot

@Composable
fun FactSheet(st: AppState, start: Int, onDismiss: () -> Unit) {
    var i by remember { mutableIntStateOf(start.coerceIn(0, Life.LIFETIMES - 1)) }
    YrsSheet(onDismiss) { FactContent(st.s, i) { i = it } }
}

@Composable
fun ColumnScope.FactContent(s: Settings, i: Int, onMove: (Int) -> Unit) {
    val birth = s.birth ?: return
    val last = Life.LIFETIMES - 1
    val mine = i == last
    val y0 = Facts.lifeStart(birth.year, i)
    val facts = remember(i) { Facts.life(s, i) }
    val seen = remember(i) { mutableStateOf(setOf<String>()) }
    var fact by remember(i) { mutableStateOf(Facts.next(facts)) }
    LaunchedEffect(fact) { seen.value = seen.value + fact }

    Cap(if (mine) "Your life" else Facts.eraName(y0), Modifier.padding(top = 4.dp))
    Text(if (mine) "You, since ${birth.year}" else "About ${Life.whole((LocalDate.now().year - y0).toDouble())} years ago", style = T.h2.copy(fontSize = 26.sp), modifier = Modifier.padding(top = 8.dp))
    Text(
        if (mine) "Things that are true about the life you’re living" else "${Facts.yearLabel(y0)} to ${Facts.yearLabel(y0 + 80)} · life ${Life.whole(i + 1.0)} of ${Life.whole(Life.LIFETIMES.toDouble())}",
        style = T.sub, modifier = Modifier.padding(top = 4.dp),
    )
    AnimatedContent(fact, transitionSpec = { (fadeIn(tween(420)) + slideInVertically(tween(560, easing = Ease)) { it / 6 }) togetherWith fadeOut(tween(160)) }, label = "story",
        modifier = Modifier.padding(top = 20.dp)) { f ->
        Box(Modifier.fillMaxWidth().heightIn(min = 190.dp).card(RoundedCornerShape(26.dp)).padding(24.dp), contentAlignment = Alignment.CenterStart) {
            Text(f, style = T.fact.copy(fontSize = 23.sp, lineHeight = 31.sp))
        }
    }
    val n = seen.value.size.coerceAtMost(facts.size)
    Text("$n of ${facts.size} things this ${if (mine) "part of the grid" else "life"} can tell you${if (n == facts.size) ". They’ll come round again." else ""}", style = T.small, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
    Spacer(Modifier.height(20.dp))
    PrimaryButton("Tell me another", {
        val fresh = facts.filter { it !in seen.value }
        if (fresh.isEmpty()) seen.value = emptySet()
        fact = Facts.next(fresh.ifEmpty { facts })
    })
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).alpha(if (i > 0) 1f else .3f)) { QuietButton("‹  Older life", { if (i > 0) onMove(i - 1) }) }
        Box(Modifier.weight(1f).alpha(if (!mine) 1f else .3f)) { QuietButton("Newer life  ›", { if (!mine) onMove(i + 1) }) }
    }
}

// ================================================================== estimate my lifespan

private val SEXES = listOf("f" to "Woman", "m" to "Man", "x" to "Rather not say")
private fun sexWord(s: String?) = when (s) { "f" -> "women"; "m" -> "men"; else -> "people" }

/** From Settings: the estimate replaces your lifespan. */
@Composable
fun EstimateSheet(st: AppState, onDismiss: () -> Unit, toast: (String) -> Unit) {
    val birth = st.s.birth ?: return
    EstimateFlow(st.s.est, birth, onSave = { st.update(refresh = false) { s -> s.copy(est = it) } }, onDismiss = onDismiss) { years ->
        val y = years.coerceIn(40, 120)
        st.update { it.copy(span = y) }; toast("Lifespan set to $y"); onDismiss()
    }
}

/**
 * One question at a time, with the number at the top moving as you answer. Opens on your result if you've answered
 * before. [onSave] keeps the answers whenever the sheet closes; [onUse] takes the estimate in whole years.
 */
class EstState(saved: String?, startAtResult: Boolean, startStep: Int? = null) {
    private val first = EstAnswers.decode(saved)
    val total = EST_Q.size + 2
    var ans by mutableStateOf(first ?: EstAnswers(Lifespan.guessCountry()))
    var step by mutableIntStateOf(startStep ?: if (first != null && startAtResult) total - 1 else 0)
    var back by mutableStateOf(false)
    fun go(to: Int) { back = to < step; step = to.coerceIn(0, total - 1) }
}

@Composable
fun EstimateFlow(saved: String?, birth: LocalDate, onSave: (String) -> Unit, onDismiss: () -> Unit, startAtResult: Boolean = true, onUse: (Int) -> Unit) {
    val es = remember { EstState(saved, startAtResult) }
    YrsSheet({ onSave(es.ans.encode()); onDismiss() }) { EstimateBody(es, birth) { onSave(es.ans.encode()); onUse(it) } }
}

@Composable
fun ColumnScope.EstimateBody(es: EstState, birth: LocalDate, onUse: (Int) -> Unit) {
    val est = remember(es.ans, birth) { Lifespan.estimate(es.ans, birth) }
    val total = es.total
    LiveTop(es.step, total, est.years, es.step == total - 1)
    AnimatedContent(es.step, transitionSpec = {
        val d = if (es.back) -1 else 1
        (slideInHorizontally(tween(460, easing = Ease)) { d * it / 7 } + fadeIn(tween(380))) togetherWith (slideOutHorizontally(tween(260)) { -d * it / 7 } + fadeOut(tween(180)))
    }, label = "est") { k ->
        Column(Modifier.fillMaxWidth()) {
            when {
                k == 0 -> AboutYou(es.ans, { es.ans = it }) { es.go(1) }
                k < total - 1 -> Question(EST_Q[k - 1], es.ans, { es.ans = it }, onNext = { if (es.step == k) es.go(k + 1) }, onBack = { if (es.step == k) es.go(k - 1) })
                else -> Result(es.ans, est, onUse = { onUse(est.years.roundToInt()) }, onRedo = { es.go(1) })
            }
        }
    }
}

/** Progress on the left; on the right, the estimate so far, with a small chip for how much the last answer moved it. */
@Composable
private fun LiveTop(step: Int, total: Int, years: Double, done: Boolean) {
    var last by remember { mutableStateOf(years) }
    var delta by remember { mutableStateOf(0.0) }
    LaunchedEffect(years) { if (abs(years - last) >= .05) { delta = years - last; last = years; delay(1400); delta = 0.0 } else last = years }
    val v = rolling(years, years)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 22.dp), verticalAlignment = Alignment.Bottom) {
        Row(Modifier.weight(1f).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(total) { k ->
                val sz by animateFloatAsState(if (k == step) 9f else 6f, label = "ps")
                Box(Modifier.size(sz.dp).clip(CircleShape).background(if (k <= step) C.Fg else C.Surface3))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%.1f".format(Locale.ENGLISH, v), style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraLight, fontSize = 46.sp, lineHeight = 46.sp, letterSpacing = (-0.045).em, color = C.Fg, fontFeatureSettings = "tnum"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(delta != 0.0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Text(
                        (if (delta > 0) "+" else "−") + "%.1f".format(Locale.ENGLISH, abs(delta)),
                        style = T.small.copy(fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp)).background(C.Fg).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                Text(if (done) "years, your estimate" else "years, so far", style = T.small.copy(fontSize = 12.sp))
            }
        }
    }
}

@Composable
private fun AboutYou(ans: EstAnswers, onChange: (EstAnswers) -> Unit, onNext: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val view = LocalView.current
    Text("Let’s start with you.", style = T.question)
    Text("Where you live and your sex set the starting point: the average for people like you, from the UN.", style = T.sub.copy(lineHeight = 21.sp), modifier = Modifier.padding(top = 10.dp))
    Caps("Where you live")
    BasicTextField(
        q, { q = it.take(40) }, singleLine = true, textStyle = T.body, cursorBrush = SolidColor(C.Fg),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Surface2).padding(16.dp),
        decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search countries", style = T.body.copy(color = C.Dim)); inner() } },
    )
    val hits = remember(q, ans.country) {
        val t = q.trim().lowercase()
        if (t.isEmpty()) listOf(Lifespan.country(ans.country))
        else Lifespan.COUNTRIES.filter { it.iso != "WLD" && t in it.name.lowercase() }.sortedBy { it.name.lowercase().indexOf(t) }.take(6) +
            (if ("world average".contains(t)) listOf(Lifespan.WORLD) else emptyList())
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(C.Surface2)) {
        if (hits.isEmpty()) SettingRow("No country by that name") {}
        hits.forEachIndexed { k, c ->
            if (k > 0) Divider()
            SettingRow(if (c.iso == "WLD") "World average" else c.name, onClick = { view.tick(); q = ""; onChange(ans.copy(country = c.iso)) }) {
                if (c.iso == ans.country) Text("✓", style = T.body)
            }
        }
    }
    Caps("Sex")
    Segmented(SEXES.map { it.first }, ans.sex ?: "x", { k -> SEXES.first { it.first == k }.second }) { onChange(ans.copy(sex = if (it == "x") null else it)) }
    Text(
        "Born today, ${sexWord(ans.sex)} in ${Lifespan.placeName(ans.country)} live ${"%.1f".format(Locale.ENGLISH, Lifespan.baseLE(ans.country, ans.sex))} years on average. Women outlive men almost everywhere on Earth.",
        style = T.small, modifier = Modifier.padding(top = 12.dp, start = 4.dp),
    )
    Spacer(Modifier.height(24.dp))
    PrimaryButton("Continue", onNext)
}

@Composable
private fun Question(q: EstQ, ans: EstAnswers, onChange: (EstAnswers) -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    val picked = ans.picks[q.key]
    var advance by remember { mutableIntStateOf(0) }
    LaunchedEffect(advance) { if (advance > 0) { delay(420); onNext() } }
    fun pick(i: Int) { onChange(ans.copy(picks = ans.picks + (q.key to i))) }
    Text(q.title, style = T.question)
    if (q.calc) BmiCalc(q) { i -> if (picked != i) pick(i) }
    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        q.options.forEachIndexed { i, o -> OptionCard(o.label, o.sub, picked == i) { pick(i); advance++ } }
    }
    WhyCard(q.why, q.source)
    Spacer(Modifier.height(22.dp))
    PrimaryButton(if (picked != null) "Continue" else "Skip this one", onNext)
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) { QuietButton("Back", onBack) }
        if (picked != null) Box(Modifier.weight(1f)) { QuietButton("Clear", { onChange(ans.copy(picks = ans.picks - q.key)) }) }
    }
}

/** Height and weight to BMI, in centimetres and kilograms or feet and pounds. */
@Composable
private fun BmiCalc(q: EstQ, onPick: (Int) -> Unit) {
    val imperial = remember { Locale.getDefault().country in setOf("US", "LR", "MM") }
    var imp by remember { mutableStateOf(imperial) }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    val bmi = run {
        val m = if (imp) ((a.toDoubleOrNull() ?: 0.0) * 12 + (b.toDoubleOrNull() ?: 0.0)) * .0254 else (a.toDoubleOrNull() ?: 0.0) / 100
        val kg = if (imp) (c.toDoubleOrNull() ?: 0.0) * .45359237 else c.toDoubleOrNull() ?: 0.0
        if (m < .9 || m > 2.6 || kg < 25 || kg > 350) null else kg / (m * m)
    }
    val idx = bmi?.let { when { it < 18.5 -> 0; it < 25 -> 1; it < 30 -> 2; it < 35 -> 3; it < 40 -> 4; else -> 5 } }
    LaunchedEffect(idx) { if (idx != null) onPick(idx) }
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Segmented(listOf(false, true), imp, { if (it) "ft · lb" else "cm · kg" }) { imp = it; a = ""; b = ""; c = "" }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            @Composable fun f(v: String, set: (String) -> Unit, hint: String, w: Float) {
                BasicTextField(
                    v, { set(it.filter { ch -> ch.isDigit() || ch == '.' }.take(5)) }, singleLine = true, textStyle = T.body, cursorBrush = SolidColor(C.Fg),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(w).clip(RoundedCornerShape(14.dp)).background(C.Surface2).padding(horizontal = 14.dp, vertical = 14.dp),
                    decorationBox = { inner -> Box { if (v.isEmpty()) Text(hint, style = T.body.copy(color = C.Dim)); inner() } },
                )
            }
            if (imp) { f(a, { a = it }, "ft", 1f); f(b, { b = it }, "in", 1f); f(c, { c = it }, "lb", 1.4f) }
            else { f(a, { a = it }, "Height, cm", 1f); f(c, { c = it }, "Weight, kg", 1f) }
        }
        Text(
            if (bmi == null) "Or work it out: enter your height and weight." else buildAnnotatedString {
                append("Your BMI is "); withStyle(SpanStyle(color = C.Fg, fontWeight = FontWeight.SemiBold)) { append("%.1f".format(Locale.ENGLISH, bmi)) }; append(" · ${q.options[idx!!].sub}")
            }.text,
            style = T.small.copy(fontSize = 14.sp), modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        )
    }
}

@Composable
private fun Result(ans: EstAnswers, r: Estimate, onUse: () -> Unit, onRedo: () -> Unit) {
    val yrs = r.years.roundToInt()
    val diff = r.years - r.typical
    val answered = EST_Q.count { ans.picks.containsKey(it.key) }
    fun pc(p: Double) = when { p >= .995 -> "99%+"; p < .01 -> "<1%"; else -> "${(p * 100).roundToInt()}%" }
    Text("You could live to about $yrs.", style = T.question)
    Text(
        "That’s ${if (abs(diff) < .3) "right on the average" else "${"%.1f".format(Locale.ENGLISH, abs(diff))} years ${if (diff > 0) "more" else "less"} than the average"} for ${sexWord(ans.sex)} your age in ${Lifespan.placeName(ans.country)}" +
            if (answered < EST_Q.size) ", from $answered of ${EST_Q.size} answers." else ".",
        style = T.sub.copy(lineHeight = 21.sp), modifier = Modifier.padding(top = 10.dp),
    )

    // Every age to 110 as a dot: white for lived, brighter ahead the likelier you are to reach it.
    Box(Modifier.fillMaxWidth().padding(top = 26.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxWidth(.78f)) {
            val w = with(LocalDensity.current) { maxWidth.toPx() }
            val cell = w / 10
            Canvas(Modifier.fillMaxWidth().height(with(LocalDensity.current) { (cell * 11).toDp() })) {
                val rr = cell * .29f
                val ag = r.ageNow
                for (i in 0 until 110) {
                    val c = Offset((i % 10 + .5f) * cell, (i / 10 + .5f) * cell)
                    when {
                        i < floor(ag) -> drawCircle(C.Fg, rr, c)
                        i == floor(ag).toInt() -> {
                            drawCircle(C.Partial, rr, c)
                            val f = (ag - floor(ag)).toFloat()
                            clipPath(Path().apply { addOval(Rect(c, rr)) }) { drawRect(C.Fg, Offset(c.x - rr, c.y + rr - 2 * rr * f), Size(2 * rr, 2 * rr * f)) }
                        }
                        else -> drawCircle(C.Fg.copy(alpha = (.07 + .55 * r.chance(i + 1.0).pow(1.5)).toFloat()), rr, c)
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(C.Fg)); Text("  Lived     ", style = T.small.copy(fontSize = 12.sp))
        Box(Modifier.size(8.dp).clip(CircleShape).background(C.Mute)); Text("  Brighter: more likely to reach", style = T.small.copy(fontSize = 12.sp))
    }

    Caps("The range")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(r.q25 to "1 in 4 people like you die before this age", r.q50 to "Half live past this", r.q75 to "1 in 4 live past this").forEachIndexed { k, (a, t) ->
            val mid = k == 1
            Column(Modifier.weight(1f).heightIn(min = 112.dp).clip(RoundedCornerShape(18.dp)).background(if (mid) C.Fg else C.Surface2).padding(14.dp)) {
                Text("${a.roundToInt()}", style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 34.sp, letterSpacing = (-0.04).em, color = if (mid) Color.Black else C.Fg))
                Text(t, style = T.small.copy(fontSize = 12.sp, lineHeight = 15.sp, color = if (mid) Color(0xFF6E6E73) else C.Mute), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    Caps("Your chances of reaching")
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); shown = true }
    listOf(80, 90, 100).forEachIndexed { k, a ->
        val p = r.chance(a.toDouble())
        val w by animateFloatAsState(if (shown || Render.still) p.toFloat().coerceAtLeast(.015f) else 0f, tween(1100, delayMillis = k * 90, easing = Ease), label = "ch")
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$a", style = T.body.copy(color = C.Mute, fontSize = 16.sp), modifier = Modifier.width(38.dp))
            Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(C.Surface2)) {
                Box(Modifier.fillMaxWidth(w).height(8.dp).clip(CircleShape).background(C.Fg))
            }
            Text(pc(p), style = T.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), modifier = Modifier.width(62.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }

    if (r.contrib.isNotEmpty()) {
        Caps("What moved it")
        Group {
            r.contrib.forEachIndexed { k, (key, y) ->
                if (k > 0) Divider()
                val q = EST_Q.first { it.key == key }
                SettingRow(Lifespan.NAMES[key] ?: key, q.options[ans.picks[key]!!].label) {
                    Text("${if (y > 0) "+" else "−"}${"%.1f".format(Locale.ENGLISH, abs(y))} yrs", style = T.body.copy(color = if (y > 0) C.Fg else C.Mute, fontFeatureSettings = "tnum"))
                }
            }
        }
    }
    WhyCard("Did you know? In 1900, a newborn could expect to live about 32 years, worldwide. Today it’s about 73. Most of the gain came from children surviving: clean water, vaccines and antibiotics.")
    Spacer(Modifier.height(24.dp))
    PrimaryButton("Use $yrs years", onUse)
    QuietButton("Change answers", onRedo)
    var how by remember { mutableStateOf(false) }
    Text("How this works  ${if (how) "⌃" else "›"}", style = T.label.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 8.dp, start = 4.dp).pressable({ how = !how }).padding(vertical = 8.dp))
    AnimatedVisibility(how) {
        Text(
            "Your starting point is life expectancy at birth for your sex and country (UN World Population Prospects 2024), turned into a mortality curve where the risk of dying roughly doubles every 8 years of age. Each answer scales that yearly risk by a ratio from large studies, re-centred so an average answer changes nothing. Habits cluster in the same people, so the biggest effect counts in full and the rest count less, calibrated so five healthy habits add about 12 to 14 years at 50, as Li et al. found (Circulation 2018). Because you’re ${r.ageNow.toInt()} already, it counts only the years from now.\n\nIt uses today’s death rates. Medicine keeps improving, so the real number is likely a little higher. It’s a population average to reflect on, not a prediction about you, and not medical advice.",
            style = T.small.copy(fontSize = 13.sp, lineHeight = 19.sp), modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}
