package app.years

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * First launch, in four calm steps:
 * welcome → birthday → lifespan → your life fills in.
 * The content scrolls if it has to; the button is pinned to the bottom and is always visible.
 */
@Composable
fun Onboarding(st: AppState, startStep: Int = 0, startDate: LocalDate = LocalDate.of(1995, 6, 15), animate: Boolean = true) {
    var step by remember { mutableIntStateOf(startStep) }
    var date by remember { mutableStateOf(startDate) }
    var span by remember { mutableIntStateOf(80) }
    val view = LocalView.current
    BackHandler(enabled = step > 0) { step-- }

    Column(Modifier.fillMaxSize().background(C.Bg).systemBarsPadding()) {
        // top bar: progress through the three questions
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                Text("‹", style = T.body.copy(fontSize = 30.sp, color = C.Mute), modifier = Modifier.clickable { view.tick(); step-- }.padding(end = 16.dp))
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..3).forEach { i ->
                        val w by animateDpAsState(if (i == step) 22.dp else 6.dp, tween(380), label = "pw")
                        val c by animateColorAsState(if (i <= step) C.Fg else C.Faint, label = "pc")
                        Box(Modifier.size(w, 6.dp).clip(CircleShape).background(c))
                    }
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(30.dp))
            }
        }

        AnimatedContent(
            targetState = step, modifier = Modifier.weight(1f).fillMaxWidth(), label = "ob",
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(460)) { dir * it / 6 } + fadeIn(tween(460))) togetherWith (slideOutHorizontally(tween(280)) { -dir * it / 6 } + fadeOut(tween(220)))
            },
        ) { s ->
            when (s) {
                0 -> Welcome(animate)
                1 -> Scrolling {
                    Text("When were\nyou born?", style = T.h1)
                    Text("Only you will ever see this. yrs. has no internet access.", style = T.sub, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(40.dp))
                    DateWheels(date, { date = it })
                    Spacer(Modifier.height(28.dp))
                    val days = max(0L, Life.dayOfLife(date) - 1)
                    Text(
                        if (days == 0L) "Welcome to the world." else "You've been alive for ${Life.whole(days.toDouble())} days.",
                        style = T.body.copy(color = C.Mute, fontFeatureSettings = "tnum"),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                2 -> Scrolling {
                    Text("How long\nis a life?", style = T.h1)
                    Text("80 years is a fair average. You can refine it later.", style = T.sub, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                            Text("$span", style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraLight, fontSize = 96.sp, letterSpacing = (-0.05).em, color = C.Fg, fontFeatureSettings = "tnum"), modifier = Modifier.alignByBaseline())
                            Text("years", style = T.sub.copy(fontSize = 17.sp), modifier = Modifier.padding(start = 8.dp).alignByBaseline())
                        }
                        Stepper(span, { span = it }, 40..120)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(70, 75, 80, 85, 90).forEach { v ->
                            val on = v == span
                            Box(
                                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) C.Fg else C.Surface).clickable { view.tick(); span = v },
                                contentAlignment = Alignment.Center,
                            ) { Text("$v", style = T.label.copy(color = if (on) Color.Black else C.Fg, fontFeatureSettings = "tnum")) }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    val age = Life.age(date)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box(Modifier.fillMaxWidth(0.82f)) { PreviewGrid(span, age.coerceAtMost(span.toDouble())) } }
                    Text(
                        "${Life.whole(max(0.0, span - age))} years · ${Life.whole(Life.daysLeft(Settings(birth = date, span = span)).toDouble())} days left",
                        style = T.sub.copy(fontFeatureSettings = "tnum"), modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
                else -> Reveal(date, span, animate)
            }
        }

        // pinned action
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 16.dp)) {
            PrimaryButton(
                when (step) { 0 -> "Begin"; 3 -> "Enter yrs."; else -> "Continue" },
                {
                    view.tap()
                    if (step < 3) step++ else {
                        view.confirm()
                        st.update { it.copy(birth = date, span = span, reviewedAge = Life.age(date).toInt()) }
                    }
                },
            )
            Text(
                if (step == 0) "Private by design. Nothing leaves your phone." else " ",
                style = T.small.copy(color = C.Faint), modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Scrolling(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp)) { content() }
}

/** The brand: "yrs" with a period that fills to a life two-thirds lived. */
@Composable
fun Wordmark(size: TextUnit, fill: Float, color: Color = C.Fg, dim: Color = C.Partial) {
    val d = with(LocalDensity.current) { (size * 0.24f).toDp() }
    Row(verticalAlignment = Alignment.Bottom) {
        Text("yrs", style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = size, letterSpacing = (-0.065).em, color = color, lineHeight = size))
        Canvas(Modifier.padding(start = d * 0.15f, bottom = d * 0.55f).size(d)) {
            val r = this.size.minDimension / 2
            val c = Offset(r, r)
            drawCircle(dim, r, c)
            clipPath(Path().apply { addOval(Rect(c, r)) }) {
                drawRect(color, topLeft = Offset(0f, 2 * r * (1 - fill)), size = Size(2 * r, 2 * r * fill))
            }
        }
    }
}

@Composable
private fun Welcome(animate: Boolean) {
    val fill = remember { Animatable(if (animate) 0f else 0.62f) }
    var showText by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) {
        if (animate) {
            kotlinx.coroutines.delay(250)
            fill.animateTo(0.62f, tween(1600, easing = FastOutSlowInEasing))
            showText = true
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Bottom) {
        Wordmark(96.sp, fill.value)
        Spacer(Modifier.height(28.dp))
        AnimatedVisibility(showText, enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 4 }) {
            Column {
                Text("Your life,\nin dots.", style = T.h1)
                Text("Every year you've lived, and every year still ahead. Seen at a glance, so you spend them well.", style = T.sub.copy(lineHeight = 22.sp), modifier = Modifier.padding(top = 12.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Your years light up one by one, with a tick for each, until today. */
@Composable
private fun Reveal(birth: LocalDate, span: Int, animate: Boolean) {
    val view = LocalView.current
    val age = Life.age(birth).coerceAtMost(span.toDouble())
    val progress = remember { Animatable(if (animate) 0f else age.toFloat()) }
    var done by remember { mutableStateOf(!animate) }
    var lastTick by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        kotlinx.coroutines.delay(350)
        progress.animateTo(age.toFloat(), tween((900 + age * 45).toInt().coerceAtMost(3800), easing = FastOutSlowInEasing)) {
            val y = floor(value).toInt()
            if (y != lastTick) { lastTick = y; view.tick() }
        }
        done = true
    }
    Scrolling {
        Text("This is\nyour life.", style = T.h1)
        Text("Each dot is one year.", style = T.sub, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(28.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = with(LocalDensity.current) { maxWidth.toPx() }
            val h = Render.gridHeight(w, 10, span)
            Canvas(Modifier.fillMaxWidth().height(with(LocalDensity.current) { h.toDp() })) {
                val cell = size.width / (10 - 0.4f)
                val inset = cell * 0.2f
                val r = cell * 0.3f
                val p = progress.value
                val whole = floor(p).toInt()
                for (i in 0 until span) {
                    val c = Offset(-inset + (i % 10 + .5f) * cell, -inset + (i / 10 + .5f) * cell)
                    when {
                        i < whole -> drawCircle(C.Fg, r, c)
                        i == whole && p < span -> {
                            drawCircle(C.Partial, r, c)
                            clipPath(Path().apply { addOval(Rect(c, r)) }) {
                                drawRect(C.Fg, topLeft = Offset(c.x - r, c.y + r - 2 * r * (p - whole)), size = Size(2 * r, 2 * r * (p - whole)))
                            }
                        }
                        else -> drawCircle(C.Empty, r, c)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        AnimatedVisibility(done, enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 3 }) {
            Column {
                val lived = age.toInt()
                val left = max(0, floor(span - age).toInt())
                Text("$lived ${if (lived == 1) "year" else "years"} lived. $left to go.", style = T.title)
                Text("Make the next one count.", style = T.sub, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
