@file:OptIn(ExperimentalFoundationApi::class)

package app.years

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.abs

// ------------------------------------------------------------------ tokens

object C {
    val Bg = Color.Black
    val Fg = Color.White
    val Mute = Color(0xFF8E8E93)
    val Faint = Color(0xFF48484A)
    val Empty = Color(0xFF232323)
    val Partial = Color(0xFF3A3A3C)
    val Surface = Color(0xFF1C1C1E)
    val Surface2 = Color(0xFF2C2C2E)
    val Surface3 = Color(0xFF3A3A3C)
    val Sep = Color(0xFF38383A)
}

val Display = FontFamily(
    Font(R.font.interdisplay_extralight, FontWeight.ExtraLight),
    Font(R.font.interdisplay_light, FontWeight.Light),
    Font(R.font.interdisplay_semibold, FontWeight.SemiBold),
    Font(R.font.interdisplay_bold, FontWeight.Bold),
)
val Body = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

object T {
    val title = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.02).em, color = C.Fg, fontFeatureSettings = "tnum")
    val sub = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = (-0.01).em, color = C.Mute, fontFeatureSettings = "tnum")
    val body = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 17.sp, letterSpacing = (-0.01).em, color = C.Fg)
    val label = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = C.Fg)
    val small = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = C.Mute, lineHeight = 18.sp)
    val caps = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = C.Mute, letterSpacing = 0.02.em)
    val h1 = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 37.sp, letterSpacing = (-0.025).em, color = C.Fg)
    val h2 = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.015).em, color = C.Fg)
}

// ------------------------------------------------------------------ haptics

fun View.tick() { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
fun View.tap() { performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
fun View.confirm() { performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS) }

// ------------------------------------------------------------------ pressable

/** A tap with a haptic tick. No scale layer: on some phones a layered control was left unpainted. */
@Composable
fun Modifier.pressable(onClick: () -> Unit, haptic: Boolean = true): Modifier {
    val view = LocalView.current
    return this.clickable(remember { MutableInteractionSource() }, indication = null) { if (haptic) view.tap(); onClick() }
}

@Composable
fun RoundButton(onClick: () -> Unit, size: Dp = 36.dp, on: Boolean = false, content: @Composable () -> Unit) {
    val bg by animateColorAsState(if (on) C.Fg else C.Surface, label = "rb")
    Box(Modifier.size(size).background(bg, CircleShape).pressable(onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun MoreDots(color: Color = C.Fg) {
    Canvas(Modifier.size(16.dp, 4.dp)) {
        val r = size.height / 2.5f
        listOf(0.125f, 0.5f, 0.875f).forEach { drawCircle(color, r, Offset(size.width * it, size.height / 2)) }
    }
}

@Composable
fun Pill(text: String, onClick: () -> Unit) {
    Row(
        Modifier.height(36.dp).background(C.Surface, RoundedCornerShape(18.dp)).pressable(onClick).padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = T.label)
        Canvas(Modifier.size(8.dp, 12.dp)) {
            val w = size.width; val h = size.height; val c = C.Fg.copy(alpha = .55f); val s = 1.3.dp.toPx()
            drawLine(c, Offset(w * .19f, h * .33f), Offset(w * .5f, h * .12f), s); drawLine(c, Offset(w * .5f, h * .12f), Offset(w * .81f, h * .33f), s)
            drawLine(c, Offset(w * .19f, h * .67f), Offset(w * .5f, h * .88f), s); drawLine(c, Offset(w * .5f, h * .88f), Offset(w * .81f, h * .67f), s)
        }
    }
}

/** The main white button. Drawn without any extra layer so it can never be left unpainted. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val view = LocalView.current
    val bg = when { !enabled -> C.Fg.copy(alpha = .22f); pressed -> Color(0xFFD1D1D6); else -> C.Fg }
    Box(
        modifier.fillMaxWidth().height(54.dp).background(bg, RoundedCornerShape(27.dp))
            .clickable(src, indication = null, enabled = enabled) { view.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.label.copy(color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(48.dp).pressable(onClick), contentAlignment = Alignment.Center) {
        Text(text, style = T.label.copy(color = C.Mute, fontSize = 16.sp))
    }
}

// ------------------------------------------------------------------ settings parts

@Composable
fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Surface2)) { content() }
}

@Composable
fun Divider() { Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(C.Sep)) }

@Composable
fun SettingRow(title: String, sub: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = if (sub != null) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.body)
            if (sub != null) Text(sub, style = T.small, modifier = Modifier.padding(top = 1.dp))
        }
        trailing()
    }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (on) 20.dp else 0.dp, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), label = "tg")
    val track by animateColorAsState(if (on) C.Fg else C.Surface3, label = "tr")
    val knob by animateColorAsState(if (on) Color.Black else C.Fg, label = "kn")
    val view = LocalView.current
    Box(Modifier.size(51.dp, 31.dp).clip(RoundedCornerShape(16.dp)).background(track).clickable { view.tap(); onChange(!on) }) {
        Box(Modifier.padding(2.dp).offset(x = x).size(27.dp).clip(CircleShape).background(knob))
    }
}

@Composable
fun <E> Segmented(options: List<E>, selected: E, label: (E) -> String, onSelect: (E) -> Unit) {
    val idx = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(C.Surface2).padding(2.dp)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * idx, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "seg")
        Box(Modifier.offset(x = x).width(w).height(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF5A5A5E)))
        Row(Modifier.fillMaxWidth()) {
            val view = LocalView.current
            options.forEachIndexed { i, o ->
                Box(Modifier.weight(1f).height(32.dp).clickable { view.tick(); onSelect(o) }, contentAlignment = Alignment.Center) {
                    Text(label(o), style = T.label.copy(fontSize = 14.sp, color = if (i == idx) C.Fg else C.Mute))
                }
            }
        }
    }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, range: IntRange) {
    val view = LocalView.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(C.Surface3).pressable({ if (value > range.first) { view.tick(); onChange(value - 1) } }, haptic = false), contentAlignment = Alignment.Center) { Text("−", style = T.body.copy(fontSize = 20.sp)) }
        Text("$value", style = T.body.copy(color = C.Mute, fontFeatureSettings = "tnum"), modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
        Box(Modifier.size(32.dp).clip(CircleShape).background(C.Surface3).pressable({ if (value < range.last) { view.tick(); onChange(value + 1) } }, haptic = false), contentAlignment = Alignment.Center) { Text("+", style = T.body.copy(fontSize = 20.sp)) }
    }
}

// ------------------------------------------------------------------ wheel date picker

@Composable
fun Wheel(items: List<String>, index: Int, onIndex: (Int) -> Unit, width: Dp, align: TextAlign = TextAlign.Center) {
    val itemH = 44.dp
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index.coerceIn(0, items.lastIndex))
    val fling = rememberSnapFlingBehavior(lazyListState = state, snapPosition = SnapPosition.Start)
    val view = LocalView.current
    val itemPx = with(LocalDensity.current) { itemH.toPx() }
    val center by remember(items.size) {
        derivedStateOf { (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0).coerceIn(0, items.lastIndex) }
    }
    LaunchedEffect(center) { if (center != index) { view.tick(); onIndex(center) } }
    LaunchedEffect(index, items.size) { if (index != center && !state.isScrollInProgress) state.scrollToItem(index.coerceIn(0, items.lastIndex)) }
    // if a date was clamped mid-fling, settle the wheel on the value that was actually saved
    LaunchedEffect(state.isScrollInProgress) { if (!state.isScrollInProgress && index != center) state.animateScrollToItem(index.coerceIn(0, items.lastIndex)) }
    LazyColumn(state = state, flingBehavior = fling, contentPadding = PaddingValues(vertical = itemH * 2), modifier = Modifier.width(width).height(itemH * 5)) {
        itemsIndexed(items) { i, t ->
            val d = abs(i - center)
            Box(Modifier.fillMaxWidth().height(itemH), contentAlignment = Alignment.Center) {
                Text(
                    t, textAlign = align, modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(
                        fontFamily = Display, fontWeight = if (d == 0) FontWeight.SemiBold else FontWeight.Light,
                        fontSize = if (d == 0) 23.sp else 21.sp, fontFeatureSettings = "tnum",
                        color = C.Fg.copy(alpha = when (d) { 0 -> 1f; 1 -> .42f; else -> .18f }),
                    ),
                )
            }
        }
    }
}

@Composable
fun DateWheels(value: LocalDate, onChange: (LocalDate) -> Unit, fromYear: Int = 1920, toYear: Int = LocalDate.now().year, latest: LocalDate? = LocalDate.now(), earliest: LocalDate? = null) {
    val years = remember(fromYear, toYear) { (fromYear..toYear).toList() }
    val months = remember { Month.values().map { it.getDisplayName(JTextStyle.FULL, Locale.ENGLISH) } }
    val dim = value.withDayOfMonth(1).lengthOfMonth()
    val days = (1..dim).map { "$it" }
    fun set(y: Int, m: Int, d: Int) {
        val maxD = LocalDate.of(y, m, 1).lengthOfMonth()
        var date = LocalDate.of(y, m, d.coerceAtMost(maxD))
        if (latest != null && date.isAfter(latest)) date = latest
        if (earliest != null && date.isBefore(earliest)) date = earliest
        onChange(date)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(C.Surface))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Wheel(days, value.dayOfMonth - 1, { set(value.year, value.monthValue, it + 1) }, 56.dp)
            Wheel(months, value.monthValue - 1, { set(value.year, it + 1, value.dayOfMonth) }, 150.dp)
            Wheel(years.map { "$it" }, years.indexOf(value.year).coerceAtLeast(0), { set(years[it], value.monthValue, value.dayOfMonth) }, 84.dp)
        }
    }
}
