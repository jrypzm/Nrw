package app.years

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

// ------------------------------------------------------------------ This year: today's line and what's ahead

@Composable
fun YearExtras(st: AppState, onLine: (LocalDate) -> Unit, onEvent: (Int?) -> Unit) {
    val ctx = LocalContext.current
    val s = st.s
    val today = LocalDate.now()
    val birth = s.birth ?: return
    val tick = st.tick
    val line = remember(tick, today) { Lines.get(ctx, today) }
    val memory = remember(tick, today) { (1..30).firstNotNullOfOrNull { y -> Lines.get(ctx, today.minusYears(y.toLong()))?.let { y to it } } }

    Column(Modifier.fillMaxWidth().padding(top = 32.dp).card().pressable({ onLine(today) }).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cap("Today", Modifier.weight(1f))
            Text("Day ${Life.whole(Life.dayOfLife(birth, today).toDouble())}", style = T.small.copy(color = C.Mute, fontFeatureSettings = "tnum"))
        }
        Text(
            line ?: "Write one line about today. Years from now it will be the only thing left of it.",
            style = if (line != null) T.fact else T.body.copy(color = C.Mute, fontSize = 17.sp, lineHeight = 24.sp), modifier = Modifier.padding(top = 10.dp),
        )
        if (line == null) Text("Tap to write", style = T.small.copy(color = C.Fg), modifier = Modifier.padding(top = 12.dp))
    }
    if (memory != null) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).card().padding(20.dp)) {
            Cap(if (memory.first == 1) "A year ago today" else "${memory.first} years ago today")
            Text("“${memory.second}”", style = T.body.copy(fontSize = 17.sp, lineHeight = 24.sp), modifier = Modifier.padding(top = 10.dp))
        }
    }

    Cap("Ahead", Modifier.padding(start = 4.dp, top = 30.dp, bottom = 10.dp))
    val upcoming = s.events.withIndex().filter { !it.value.date.isBefore(today) }.sortedBy { it.value.date }
    Column(Modifier.fillMaxWidth().card()) {
        upcoming.forEachIndexed { k, (i, e) ->
            if (k > 0) Divider()
            val d = ChronoUnit.DAYS.between(today, e.date)
            Row(Modifier.fillMaxWidth().pressable({ onEvent(i) }).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = T.body)
                    Text("${e.date.dayOfMonth} ${Life.mon(e.date)} ${e.date.year}", style = T.small)
                }
                Text(
                    buildAnnotatedString {
                        if (d == 0L) append("Today") else { withStyle(SpanStyle(color = C.Fg)) { append(Life.whole(d.toDouble())) }; append(if (d == 1L) " day" else " days") }
                    },
                    style = T.body.copy(color = C.Mute, fontFeatureSettings = "tnum"),
                )
            }
        }
        if (upcoming.isNotEmpty()) Divider()
        Row(Modifier.fillMaxWidth().pressable({ onEvent(null) }).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Add a countdown", style = T.body, modifier = Modifier.weight(1f)); Chevron()
        }
    }
}

// ------------------------------------------------------------------ People: the tail end

@Composable
fun PeoplePage(st: AppState, onEdit: (Int?) -> Unit) {
    val s = st.s
    Cap("People")
    Text(
        buildAnnotatedString { append("The time you have\n"); withStyle(SpanStyle(color = C.Dim)) { append("left together.") } },
        style = T.headline, modifier = Modifier.padding(top = 12.dp),
    )
    if (s.people.isEmpty()) {
        Text(
            "Most of the time you'll ever spend with your parents, you've already spent. Add the people you love and see how many more times you'll be together, at the rate you see them now.",
            style = T.body.copy(color = C.Mute, fontSize = 16.sp, lineHeight = 24.sp), modifier = Modifier.padding(top = 18.dp),
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Add someone", { onEdit(null) })
        return
    }
    Spacer(Modifier.height(26.dp))
    s.people.forEachIndexed { i, p ->
        val t = Life.together(s, p)
        val unit = if (p.often == Often.DAILY) (if (t.visits == 1L) "day" else "days") else (if (t.visits == 1L) "visit" else "visits")
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).card().pressable({ onEdit(i) }).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = T.label.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp), modifier = Modifier.weight(1f))
                Text(p.often.label, style = T.small)
            }
            val v = rolling(t.visits.toDouble(), "${p.name}|${p.often}|${p.birthYear}|${s.span}", from = 0.0)
            Text(Life.whole(v), style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraLight, fontSize = 68.sp, lineHeight = 70.sp, letterSpacing = (-0.05).em, color = C.Fg, fontFeatureSettings = "tnum"), modifier = Modifier.padding(top = 6.dp))
            Text("more $unit together", style = T.label.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            Text("About ${Life.whole(t.years)} summers left together · ${p.name} is ${t.theirAge}", style = T.small, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
            VisitDots(min(t.visits, 120L).toInt(), more = t.visits - min(t.visits, 120L))
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth().card().pressable({ onEdit(null) }).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Add someone", style = T.body, modifier = Modifier.weight(1f)); Chevron()
    }
}

@Composable
fun VisitDots(n: Int, more: Long) {
    if (n <= 0) { Text("No more visits at this rate. Change how often.", style = T.small); return }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = 20
        val w = with(LocalDensity.current) { maxWidth.toPx() }
        val cell = w / cols
        val rows = ceil(n / cols.toFloat()).toInt()
        Canvas(Modifier.fillMaxWidth().height(with(LocalDensity.current) { (rows * cell).toDp() })) {
            for (i in 0 until n) drawCircle(C.Fg, cell * 0.28f, Offset((i % cols + .5f) * cell, (i / cols + .5f) * cell))
        }
    }
    if (more > 0) Text("+ ${Life.whole(more.toDouble())} more", style = T.small, modifier = Modifier.padding(top = 8.dp))
}

// ------------------------------------------------------------------ Time: where the years go

@Composable
fun LedgerPage(st: AppState) {
    val ctx = LocalContext.current
    val s = st.s
    var real by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(st.tick) { real = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ScreenTime.hoursPerDay(ctx) } }
    val phone = real ?: s.phone.toDouble()
    val slices = Ledger.slices(s, phone)
    val left = Life.yearsLeft(s)
    val free = slices.last().years

    Cap("Where your years go")
    val v = rolling(free, "ledger", from = 0.0)
    BigNumber("%.1f".format(Locale.ENGLISH, v), 112.sp, modifier = Modifier.padding(top = 4.dp))
    Text("years truly yours", style = T.word, modifier = Modifier.padding(top = 2.dp))
    Text("of the ${Life.whole(left)} you have left", style = T.sub, modifier = Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(30.dp))

    // One dot per year left, coloured by where it goes.
    val total = left.roundToInt().coerceAtLeast(1)
    val counts = slices.map { it.years / left.coerceAtLeast(0.01) * total }.let { raw ->
        val ints = raw.map { it.toInt() }.toMutableList()
        var rest = total - ints.sum()
        raw.withIndex().sortedByDescending { it.value - it.value.toInt() }.forEach { if (rest > 0) { ints[it.index]++; rest-- } }
        ints
    }
    val colours = counts.flatMapIndexed { i, n -> List(n) { Color(slices[i].shade) } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = 10
        val w = with(LocalDensity.current) { maxWidth.toPx() }
        val cell = w / (cols - 0.4f)
        val rows = ceil(colours.size / cols.toFloat()).toInt()
        Canvas(Modifier.fillMaxWidth().height(with(LocalDensity.current) { (rows * cell - cell * 0.4f).toDp() })) {
            colours.forEachIndexed { i, col -> drawCircle(col, cell * 0.3f, Offset(-cell * 0.2f + (i % cols + .5f) * cell, -cell * 0.2f + (i / cols + .5f) * cell)) }
        }
    }
    Spacer(Modifier.height(24.dp))
    slices.forEachIndexed { i, sl ->
        val mine = i == slices.lastIndex
        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Hair))
        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(sl.shade)))
            Text(sl.label, style = T.body.copy(fontSize = 16.sp, color = if (mine) C.Fg else C.Mute), modifier = Modifier.padding(start = 12.dp).weight(1f))
            Text("${"%.1f".format(Locale.ENGLISH, sl.years)} yrs", style = T.body.copy(fontSize = 16.sp, fontFeatureSettings = "tnum", fontWeight = if (mine) FontWeight.SemiBold else FontWeight.Normal))
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 24.dp).card().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Cap("Your days")
        Text("Move these to match your life. Everything above follows.", style = T.small, modifier = Modifier.padding(top = 6.dp))
        Knob(Ledger.SLEEP, s.sleep) { v -> st.update(refresh = false) { it.copy(sleep = v) } }
        Knob(Ledger.WORK, s.work) { v -> st.update(refresh = false) { it.copy(work = v) } }
        Knob(Ledger.RETIRE, s.retire) { v -> st.update(refresh = false) { it.copy(retire = v) } }
        Knob(Ledger.COMMUTE, s.commute) { v -> st.update(refresh = false) { it.copy(commute = v) } }
        Knob(Ledger.CHORES, s.chores) { v -> st.update(refresh = false) { it.copy(chores = v) } }
        if (real == null) Knob(Ledger.PHONE, s.phone) { v -> st.update(refresh = false) { it.copy(phone = v) } }
        else Text("Phone: ${"%.1f".format(Locale.ENGLISH, phone)} h a day, from your screen time over the last 7 days.", style = T.small.copy(color = C.Fg), modifier = Modifier.padding(top = 16.dp))
        QuietButton("Reset to typical", {
            val d = Settings()
            st.update(refresh = false) { it.copy(sleep = d.sleep, work = d.work, retire = d.retire, commute = d.commute, chores = d.chores, phone = d.phone) }
        }, Modifier.padding(top = 6.dp))
    }
    if (real == null) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).card().pressable({ ScreenTime.openSettings(ctx) }).padding(20.dp)) {
            Text("Use your real screen time", style = T.label.copy(fontWeight = FontWeight.SemiBold))
            Text("Allow usage access and yrs. reads Android's own screen-time numbers instead of the slider. They never leave your phone.", style = T.small, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Knob(k: Ledger.Knob, value: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(k.label, style = T.small.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
        Text(k.fmt(value), style = T.small.copy(fontSize = 14.sp, color = C.Fg, fontFeatureSettings = "tnum"))
    }
    YrsSlider(value, k.min, k.max, k.step, onChange)
}
