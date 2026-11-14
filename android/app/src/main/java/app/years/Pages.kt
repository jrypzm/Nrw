package app.years

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
    val memory = remember(tick, today) {
        (1..30).firstNotNullOfOrNull { y -> Lines.get(ctx, today.minusYears(y.toLong()))?.let { y to it } }
    }

    Caps("Today")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface).clickable { onLine(today) }.padding(18.dp)) {
        Text("Day ${Life.whole(Life.dayOfLife(birth, today).toDouble())}", style = T.label.copy(fontWeight = FontWeight.SemiBold))
        Text(line ?: "Write one line about today. Years from now it will be the only thing left of it.", style = T.body.copy(color = if (line != null) C.Fg else C.Mute, fontSize = 16.sp, lineHeight = 22.sp), modifier = Modifier.padding(top = 6.dp))
    }
    if (memory != null) {
        Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(C.Surface).padding(18.dp)) {
            Text(if (memory.first == 1) "A year ago today" else "${memory.first} years ago today", style = T.small)
            Text("“${memory.second}”", style = T.body.copy(fontSize = 16.sp, lineHeight = 22.sp), modifier = Modifier.padding(top = 6.dp))
        }
    }

    Caps("Ahead")
    val upcoming = s.events.withIndex().filter { !it.value.date.isBefore(today) }.sortedBy { it.value.date }
    Group {
        upcoming.forEachIndexed { k, (i, e) ->
            if (k > 0) Divider()
            val d = ChronoUnit.DAYS.between(today, e.date)
            SettingRow(e.name, "${e.date.dayOfMonth} ${Life.mon(e.date)} ${e.date.year}", onClick = { onEvent(i) }) {
                Text(if (d == 0L) "Today" else "in ${Life.whole(d.toDouble())} ${if (d == 1L) "day" else "days"}", style = T.body.copy(color = C.Mute, fontFeatureSettings = "tnum"))
            }
        }
        if (upcoming.isNotEmpty()) Divider()
        SettingRow("Add a countdown", onClick = { onEvent(null) }) { Chevron() }
    }
}

// ------------------------------------------------------------------ People: the tail end

@Composable
fun PeoplePage(st: AppState, onEdit: (Int?) -> Unit) {
    val s = st.s
    Text("People", style = T.title)
    Text("The time you have left together", style = T.sub, modifier = Modifier.padding(top = 3.dp))
    Spacer(Modifier.height(24.dp))
    if (s.people.isEmpty()) {
        Text(
            "Most of the time you'll ever spend with your parents, you've already spent. Add the people you love and see how many more times you'll be together, at the rate you see them now.",
            style = T.body.copy(color = C.Mute, fontSize = 16.sp, lineHeight = 23.sp),
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Add someone", { onEdit(null) })
        return
    }
    s.people.forEachIndexed { i, p ->
        val t = Life.together(s, p)
        val unit = if (p.often == Often.DAILY) (if (t.visits == 1L) "day" else "days") else (if (t.visits == 1L) "visit" else "visits")
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp)).background(C.Surface).clickable { onEdit(i) }.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(p.name, style = T.label.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp), modifier = Modifier.weight(1f))
                Text(p.often.label, style = T.small)
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                Text(Life.whole(t.visits.toDouble()), style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraLight, fontSize = 52.sp, letterSpacing = (-0.04).em, color = C.Fg, fontFeatureSettings = "tnum"), modifier = Modifier.alignByBaseline())
                Text("more $unit together", style = T.sub, modifier = Modifier.padding(start = 8.dp).alignByBaseline())
            }
            Text("About ${Life.whole(t.years)} summers left together · ${p.name} is ${t.theirAge}", style = T.small, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            VisitDots(min(t.visits, 120L).toInt(), more = t.visits - min(t.visits, 120L))
        }
    }
    Spacer(Modifier.height(4.dp))
    SettingRowCard("Add someone") { onEdit(null) }
}

@Composable
fun SettingRowCard(title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface).clickable { onClick() }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = T.body, modifier = Modifier.weight(1f)); Chevron()
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
    if (more > 0) Text("+ ${Life.whole(more.toDouble())} more", style = T.small, modifier = Modifier.padding(top = 6.dp))
}

// ------------------------------------------------------------------ Ledger: where the years go

@Composable
fun LedgerPage(st: AppState) {
    val ctx = LocalContext.current
    val s = st.s
    var hours by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(st.tick) { hours = ScreenTime.hoursPerDay(ctx) }
    val phone = hours ?: 3.0
    val slices = Life.ledger(s, phone)
    val left = Life.yearsLeft(s)
    val shades = listOf(Color(0xFF2C2C2E), Color(0xFF48484A), Color(0xFF8E8E93), Color(0xFF3A3A3C), C.Fg)

    Text("Your ${Life.whole(left)} years", style = T.title)
    Text("Where the time you have left will go", style = T.sub, modifier = Modifier.padding(top = 3.dp))
    Spacer(Modifier.height(28.dp))

    // One dot per year left, coloured by where it goes.
    val total = left.roundToInt().coerceAtLeast(1)
    val counts = slices.map { (it.years / left.coerceAtLeast(0.01) * total) }.let { raw ->
        val ints = raw.map { it.toInt() }.toMutableList()
        var rest = total - ints.sum()
        raw.withIndex().sortedByDescending { it.value - it.value.toInt() }.forEach { if (rest > 0) { ints[it.index]++; rest-- } }
        ints
    }
    val colours = counts.flatMapIndexed { i, n -> List(n) { shades[i] } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = 10
        val w = with(LocalDensity.current) { maxWidth.toPx() }
        val cell = w / (cols - 0.4f)
        val rows = ceil(colours.size / cols.toFloat()).toInt()
        Canvas(Modifier.fillMaxWidth().height(with(LocalDensity.current) { (rows * cell - cell * 0.4f).toDp() })) {
            colours.forEachIndexed { i, col -> drawCircle(col, cell * 0.3f, Offset(-cell * 0.2f + (i % cols + .5f) * cell, -cell * 0.2f + (i / cols + .5f) * cell)) }
        }
    }
    Spacer(Modifier.height(22.dp))
    slices.forEachIndexed { i, sl ->
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(shades[i]))
            Text(sl.label, style = T.body.copy(fontSize = 16.sp, color = if (i == slices.lastIndex) C.Fg else C.Mute), modifier = Modifier.padding(start = 12.dp).weight(1f))
            Text("${"%.1f".format(sl.years)} yrs", style = T.body.copy(fontSize = 16.sp, fontFeatureSettings = "tnum", fontWeight = if (i == slices.lastIndex) FontWeight.SemiBold else FontWeight.Normal))
        }
    }
    Spacer(Modifier.height(18.dp))
    if (hours == null) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface).clickable { ScreenTime.openSettings(ctx) }.padding(18.dp)) {
            Text("Use your real screen time", style = T.label.copy(fontWeight = FontWeight.SemiBold))
            Text("Right now this assumes 3 hours a day. Allow usage access and yrs. reads Android's own screen-time numbers. They never leave your phone.", style = T.small, modifier = Modifier.padding(top = 4.dp))
        }
    } else {
        Text("Based on your last 7 days: ${"%.1f".format(phone)} hours a day on your phone.", style = T.small)
    }
    Text("Sleep 8 h a day, work 40 h a week until 65, 3 h a day eating, chores and getting around.", style = T.small.copy(color = C.Faint), modifier = Modifier.padding(top = 10.dp))
}
