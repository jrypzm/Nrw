// yrs. music: modified from Lotus by dn0ne (GPL-3.0).
// The playing strip and the player are one surface. Drag it, tap it, fling it: the cover
// travels from the strip to the middle of the screen and back, on a spring.
package com.dn0ne.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dn0ne.player.app.domain.playback.PlaybackMode
import com.dn0ne.player.app.domain.playback.Repeat
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.presentation.LOVED
import com.dn0ne.player.app.presentation.PlayerScreenEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------- time that moves every frame, but only while someone is looking ----------

@Composable
fun rememberPosition(ui: Ui): State<Long> {
    val out = remember { mutableLongStateOf(0L) }
    LaunchedEffect(ui) {
        var pos = 0L; var at = System.nanoTime()
        launch {
            ui.vm.playbackState.collect { s ->
                pos = s.position; at = System.nanoTime()
                if (!s.isPlaying || ui.expand.value <= 0f) out.longValue = s.position
            }
        }
        combine(
            snapshotFlow { ui.expand.value > 0f },
            ui.calm.map { it.isPlaying }.distinctUntilChanged(),
        ) { open, playing -> open && playing }
            .distinctUntilChanged()
            .collectLatest { run ->
                if (run) while (true) withFrameNanos { now ->
                    val v = pos + (now - at) / 1_000_000
                    // never step backwards by a hair; only real seeks move time back
                    if (v >= out.longValue || out.longValue - v > 450) out.longValue = v
                }
            }
    }
    return out
}

/** The art at a fixed request size, so a growing cover never asks for a new image mid-flight. */
@Composable
fun Art(track: Track, px: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Box(modifier.background(Ink.Tile), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val size = with(LocalDensity.current) { (constraints.maxWidth * 0.42f).toSp() }
            Txt(
                track.record.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "♪",
                Type.word.copy(fontSize = size, color = Color(0xFF3A3A3C)), maxLines = 1,
            )
        }
        val request = remember(track.coverArtUri, px) {
            ImageRequest.Builder(ctx).data(track.coverArtUri).size(px.coerceAtLeast(1)).build()
        }
        AsyncImage(request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

private fun repeatMode(mode: PlaybackMode, shuffle: Boolean?, cycle: Boolean): PlaybackMode {
    val sh = shuffle ?: mode.shuffles
    var rep = mode.repeat
    if (cycle) rep = when (rep) { Repeat.Off -> Repeat.All; Repeat.All -> Repeat.One; Repeat.One -> Repeat.Off }
    if (shuffle == true && rep == Repeat.One) rep = Repeat.All
    return PlaybackMode.of(sh && rep != Repeat.One, rep)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerLayer(ui: Ui, position: State<Long>) {
    val state by ui.calm.collectAsState()
    var last by remember { mutableStateOf<Track?>(null) }
    state.currentTrack?.let { last = it }
    val track = last ?: return
    val present = state.currentTrack != null
    val shown by animateFloatAsState(if (present) 1f else 0f, spring(0.9f, 320f), label = "shown")
    if (!present && shown == 0f) return

    val scope = rememberCoroutineScope()
    val h = rememberHaptics()
    val d = LocalDensity.current
    val accent = LocalAccent.current
    val playlists by ui.vm.playlists.collectAsState()
    // when the queue runs out, the next song starts from the strip again
    LaunchedEffect(present) { if (!present) { ui.layer = Layer.None; ui.expand.snapTo(0f) } }
    val loved = playlists.firstOrNull { it.name == LOVED }?.trackList?.any { it.uri == track.uri } == true

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val hh = constraints.maxHeight.toFloat()
        val top = WindowInsets.statusBars.getTop(d).toFloat()
        val nav = WindowInsets.navigationBars.getBottom(d).toFloat()
        val px = d.density
        val stripH = 72f * px + nav
        val stripTop = hh - stripH
        val reserve = 336f * px + nav
        val avail = hh - top - 60f * px - reserve
        val coverS = min(w - 48f * px, avail).coerceAtLeast(150f * px)
        val coverX = (w - coverS) / 2f
        val coverY = top + 60f * px + max(0f, (avail - coverS) / 2f)
        val artS = 44f * px; val artX = 24f * px; val artY = stripTop + 14f * px
        val range = (stripTop - top).coerceAtLeast(1f)
        val f = { ui.expand.value }
        val sink = { (1f - shown) * stripH }
        val open by remember { derivedStateOf { ui.expand.value > 0f } }

        fun go(target: Float, v: Float = 0f) {
            scope.launch {
                val from = ui.expand.value
                ui.expand.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 300f), initialVelocity = v)
                if (abs(from - target) > 0.2f) h.edge()
            }
        }
        fun settle(v: Float) {
            val target = when {
                v < -650f -> 1f
                v > 650f -> 0f
                else -> if (f() > 0.5f) 1f else 0f
            }
            go(target, -v / range)
        }
        val drag = rememberDraggableState { dy -> scope.launch { ui.expand.snapTo((ui.expand.value - dy / range).coerceIn(0f, 1f)) } }

        // sideways: skip, with the thing you dragged following your finger
        val swipe = remember { Animatable(0f) }
        val skip = Modifier.pointerInput(Unit) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    val far = abs(total) > 72f * px
                    if (far) {
                        h.click()
                        ui.vm.onEvent(if (total < 0) PlayerScreenEvent.OnSeekToNextClick else PlayerScreenEvent.OnSeekToPreviousClick)
                    }
                    scope.launch {
                        if (far) swipe.snapTo(-swipe.value.coerceIn(-60f * px, 60f * px) * 0.6f)
                        swipe.animateTo(0f, spring(0.75f, 420f))
                    }
                },
                onDragCancel = { scope.launch { swipe.animateTo(0f, spring(0.75f, 420f)) } },
            ) { change, dx ->
                change.consume(); total += dx
                scope.launch { swipe.snapTo(total * 0.45f) }
            }
        }

        // 1. the surface: a black sheet that rises from the strip to the whole screen
        Box(
            Modifier
                .fillMaxSize()
                .layout { m, c ->
                    val y = (lerp(stripTop, 0f, f()) + sink()).roundToInt().coerceIn(0, c.maxHeight)
                    val p = m.measure(Constraints.fixed(c.maxWidth, c.maxHeight - y))
                    layout(c.maxWidth, c.maxHeight) { p.place(0, y) }
                }
                .background(Ink.Black)
                .drawBehind {
                    val a = (1f - f() * 4f).coerceIn(0f, 1f)
                    if (a > 0f) {
                        drawRect(Ink.Hair, size = Size(size.width, px), alpha = a)
                        val dur = track.duration.coerceAtLeast(1)
                        val p = (position.value.toFloat() / dur).coerceIn(0f, 1f)
                        drawRect(accent, size = Size(size.width * p, px), alpha = a)
                    }
                }
                .draggable(drag, Orientation.Vertical, onDragStopped = { settle(it) })
        ) {
            // the strip
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .graphicsLayer { alpha = (1f - f() * 3.5f).coerceIn(0f, 1f) }
                    .then(skip)
                    .semantics { contentDescription = "Now playing, ${track.name}. Open player" }
                    .press { go(1f) }
                    .padding(start = 82.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    track, Modifier.weight(1f).graphicsLayer { translationX = swipe.value },
                    transitionSpec = { (fadeIn(tween(220, 60)) togetherWith fadeOut(tween(120))) },
                    contentKey = { it.uri }, label = "strip",
                ) { t ->
                    Column {
                        Txt(t.name, Type.title.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Txt(t.who, Type.sub.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                PlayPause(state.isPlaying, 26.dp, 48.dp) {
                    ui.vm.onEvent(if (state.isPlaying) PlayerScreenEvent.OnPauseClick else PlayerScreenEvent.OnPlayClick)
                }
            }
        }

        // 2. the player: everything but the cover, fading up as the sheet opens
        if (open) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = ((f() - 0.42f) / 0.58f).coerceIn(0f, 1f)
                        translationY = (1f - f()) * 90f * px
                    }
                    .drawWithContent {
                        // only inside the rising sheet, never over the library behind it
                        val edge = lerp(stripTop, 0f, f()) + sink() - (1f - f()) * 90f * px
                        clipRect(top = edge.coerceAtLeast(0f)) { this@drawWithContent.drawContent() }
                    }
                    .draggable(drag, Orientation.Vertical, onDragStopped = { settle(it) })
            ) {
                Row(
                    Modifier.statusBarsPadding().padding(top = 8.dp, start = 10.dp, end = 10.dp).height(44.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTap(Glyph.Down, 26.dp, "Close player") { go(0f) }
                    Txt(
                        track.record.uppercase(), Type.meta.copy(textAlign = TextAlign.Center),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).press { go(0f); ui.go(Page.Album(track.album)) }.padding(vertical = 12.dp),
                    )
                    IconTap(Glyph.More, 24.dp, "More") { ui.panel = Panel.PlayerMenu }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 14.dp),
                ) {
                    Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(
                            track, Modifier.weight(1f), contentKey = { it.uri }, label = "title",
                            transitionSpec = { fadeIn(tween(260, 80)) togetherWith fadeOut(tween(120)) },
                        ) { t ->
                            Column {
                                Txt(t.name, Type.big, maxLines = 1, modifier = Modifier.basicMarquee(iterations = 2, initialDelayMillis = 2400, repeatDelayMillis = 2400))
                                Spacer(Modifier.height(5.dp))
                                Txt(t.who, Type.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Heart(loved) { h.confirm(); ui.vm.toggleLoved(track, playlists) }
                    }
                    Spacer(Modifier.height(26.dp))
                    Timeline(position, track.duration.toLong(), Modifier.padding(horizontal = 24.dp)) {
                        ui.vm.onEvent(PlayerScreenEvent.OnSeekTo(it))
                    }
                    Spacer(Modifier.height(16.dp))
                    Controls(ui, state.isPlaying, big = true)
                    Spacer(Modifier.height(26.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        val mode = state.playbackMode
                        Toggle("Shuffle", mode.shuffles) { ui.vm.setPlaybackMode(repeatMode(mode, !mode.shuffles, cycle = false)) }
                        Toggle("Lyrics", false, plain = true) { ui.layer = Layer.Lyrics }
                        Toggle("Up next", false, plain = true) { ui.layer = Layer.UpNext }
                        Toggle(if (mode.repeat == Repeat.One) "Repeat one" else "Repeat", mode.repeat != Repeat.Off) {
                            ui.vm.setPlaybackMode(repeatMode(mode, null, cycle = true))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    var quality by remember { mutableStateOf("") }
                    LaunchedEffect(track.data) { quality = ""; quality = quality(track) }
                    Txt(
                        quality, Type.caps.copy(textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth().height(16.dp), maxLines = 1,
                    )
                }
            }
        }

        // 3. the cover, the one thing that travels
        val coverPx = coverS.roundToInt()
        val rest by animateFloatAsState(if (state.isPlaying) 1f else 0.9f, spring(0.62f, 240f), label = "rest")
        Box(
            Modifier
                .fillMaxSize()
                .layout { m, c ->
                    val k = f()
                    val s = lerp(artS, coverS, k).roundToInt()
                    val p = m.measure(Constraints.fixed(s, s))
                    layout(c.maxWidth, c.maxHeight) {
                        p.place(lerp(artX, coverX, k).roundToInt(), (lerp(artY, coverY, k) + sink()).roundToInt())
                    }
                }
                .graphicsLayer {
                    val k = f()
                    shape = RoundedCornerShape(lerp(7f, 12f, k) * px)
                    clip = true
                    val sc = lerp(1f, rest, k)
                    scaleX = sc; scaleY = sc
                    translationX = swipe.value * if (k > 0.5f) 1.4f else 0f
                }
                .then(skip)
                .pointerInput(Unit) { detectTapGestures { if (ui.expand.value < 0.5f) go(1f) } }
        ) {
            AnimatedContent(
                track, contentKey = { it.uri }, label = "cover",
                transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(280, 120)) },
            ) { t -> Art(t, coverPx, Modifier.fillMaxSize()) }
        }

        // 4. lyrics and the queue open over the player
        AnimatedVisibility(
            open && ui.layer != Layer.None,
            enter = fadeIn(tween(200)) + slideInVertically(spring(0.9f, 380f)) { it / 10 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 12 },
        ) {
            when (ui.layer) {
                Layer.Lyrics -> LyricsLayer(ui, track, position)
                else -> UpNextLayer(ui)
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, plain: Boolean = false, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val c by animateColorAsState(if (on) accent else if (plain) Ink.Mute else Ink.Faint, tween(180), label = "toggle")
    val h = rememberHaptics()
    Txt(
        label, Type.act.copy(color = c),
        modifier = Modifier
            .semantics { contentDescription = if (plain) label else "$label, ${if (on) "on" else "off"}" }
            .press { h.click(); onClick() }
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun Heart(loved: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val pop = remember { Animatable(1f) }
    var was by remember { mutableStateOf(loved) }
    LaunchedEffect(loved) {
        if (loved && !was) { pop.snapTo(0.72f); pop.animateTo(1f, spring(0.38f, 520f)) }
        was = loved
    }
    Box(
        Modifier.size(48.dp)
            .semantics { contentDescription = if (loved) "Loved. Remove from Loved" else "Love" }
            .press(shrink = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(
            if (loved) Glyph.HeartFull else Glyph.Heart, 26.dp, if (loved) accent else Ink.White,
            Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
    }
}

@Composable
fun PlayPause(playing: Boolean, size: Dp, box: Dp, onClick: () -> Unit) {
    val h = rememberHaptics()
    Box(
        Modifier.size(box)
            .semantics { contentDescription = if (playing) "Pause" else "Play" }
            .press(shrink = true) { h.click(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            playing, label = "pp",
            transitionSpec = {
                (fadeIn(tween(140)) + scaleIn(spring(0.6f, 600f), initialScale = 0.7f)) togetherWith
                    (fadeOut(tween(90)) + scaleOut(tween(120), targetScale = 0.7f))
            },
        ) { p -> Glyph(if (p) Glyph.Pause else Glyph.Play, size, Ink.White) }
    }
}

@Composable
private fun Controls(ui: Ui, playing: Boolean, big: Boolean) {
    val h = rememberHaptics()
    val scope = rememberCoroutineScope()
    val nudgeL = remember { Animatable(0f) }
    val nudgeR = remember { Animatable(0f) }
    fun nudge(a: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, by: Float) = scope.launch {
        a.snapTo(by); a.animateTo(0f, spring(0.42f, 600f))
    }
    val side = if (big) 42.dp else 32.dp
    val mid = if (big) 52.dp else 38.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(side + 22.dp).semantics { contentDescription = "Previous" }
                .press(shrink = true) { h.click(); nudge(nudgeL, -8f); ui.vm.onEvent(PlayerScreenEvent.OnSeekToPreviousClick) },
            contentAlignment = Alignment.Center,
        ) { Glyph(Glyph.Prev, side, Ink.White, Modifier.graphicsLayer { translationX = nudgeL.value * density }) }
        Spacer(Modifier.width(if (big) 36.dp else 34.dp))
        PlayPause(playing, mid, mid + 24.dp) {
            ui.vm.onEvent(if (playing) PlayerScreenEvent.OnPauseClick else PlayerScreenEvent.OnPlayClick)
        }
        Spacer(Modifier.width(if (big) 36.dp else 34.dp))
        Box(
            Modifier.size(side + 22.dp).semantics { contentDescription = "Next" }
                .press(shrink = true) { h.click(); nudge(nudgeR, 8f); ui.vm.onEvent(PlayerScreenEvent.OnSeekToNextClick) },
            contentAlignment = Alignment.Center,
        ) { Glyph(Glyph.Next, side, Ink.White, Modifier.graphicsLayer { translationX = nudgeR.value * density }) }
    }
}

/** A plain line. Touch it and it thickens; drag to choose a moment; let go to jump there. */
@Composable
fun Timeline(position: State<Long>, duration: Long, modifier: Modifier = Modifier, times: Boolean = true, onSeek: (Long) -> Unit) {
    val accent = LocalAccent.current
    val h = rememberHaptics()
    var held by remember { mutableStateOf<Float?>(null) }
    val thick by animateDpAsState(if (held != null) 6.dp else 2.dp, spring(0.7f, 700f), label = "thick")
    val dur = duration.coerceAtLeast(1L)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .semantics { contentDescription = "Timeline" }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> held = (o.x / size.width).coerceIn(0f, 1f); h.tick() },
                        onDragEnd = { held?.let { onSeek((it * dur).toLong()) }; held = null; h.tick() },
                        onDragCancel = { held = null },
                    ) { change, dx ->
                        change.consume()
                        held = ((held ?: 0f) + dx / size.width).coerceIn(0f, 1f)
                    }
                }
                .pointerInput(dur) {
                    detectTapGestures { o -> h.click(); onSeek(((o.x / size.width).coerceIn(0f, 1f) * dur).toLong()) }
                }
                .drawBehind {
                    val t = thick.toPx()
                    val y = size.height / 2f - t / 2f
                    val p = held ?: (position.value.toFloat() / dur).coerceIn(0f, 1f)
                    val r = CornerRadius(t / 2f)
                    drawRoundRect(Ink.Rail, Offset(0f, y), Size(size.width, t), r)
                    drawRoundRect(accent, Offset(0f, y), Size(max(t, size.width * p), t), r)
                }
        )
        if (times) {
            val sec by remember(dur) { derivedStateOf { ((held?.let { (it * dur).toLong() }) ?: position.value) / 1000 } }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Txt(clock(sec * 1000), Type.time.copy(color = Ink.White))
                Spacer(Modifier.weight(1f))
                Txt("−" + clock(max(0L, dur / 1000 - sec) * 1000), Type.time)
            }
        }
    }
}

// ---------- lyrics ----------

@Composable
private fun LyricsLayer(ui: Ui, track: Track, position: State<Long>) {
    val state by ui.calm.collectAsState()
    val size = remember { ui.vm.settings.lyricsFontSize.value.coerceIn(20f, 34f) }
    LaunchedEffect(track.uri) { ui.vm.onEvent(PlayerScreenEvent.OnLyricsClick) }
    Column(Modifier.fillMaxSize().background(Ink.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(top = 8.dp, start = 10.dp, end = 10.dp).height(44.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Glyph.Down, 26.dp, "Close lyrics") { ui.layer = Layer.None }
            Txt(
                "${track.name} · ${track.who}", Type.sub.copy(fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            IconTap(Glyph.More, 24.dp, "More") { ui.panel = Panel.PlayerMenu }
        }
        val lyrics = state.lyrics
        Box(Modifier.weight(1f)) {
            when {
                state.isLoadingLyrics -> {}
                lyrics == null || (lyrics.synced.isNullOrEmpty() && lyrics.plain.isNullOrEmpty()) -> Quiet(
                    "No lyrics",
                    "Choose a .lrc or text file for this song and it stays with it. Lyrics saved inside the song show up on their own.",
                    Modifier.padding(top = 40.dp),
                ) { Act("Choose a file", color = LocalAccent.current) { ui.outside.pickLyrics() } }
                !lyrics.synced.isNullOrEmpty() -> Synced(ui, lyrics.synced!!, size, position)
                else -> Plain(lyrics.plain!!, size)
            }
        }
        Timeline(position, track.duration.toLong(), Modifier.padding(horizontal = 24.dp), times = false) {
            ui.vm.onEvent(PlayerScreenEvent.OnSeekTo(it))
        }
        Controls(ui, state.isPlaying, big = false)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun Synced(ui: Ui, lines: List<Pair<Int, String>>, size: Float, position: State<Long>) {
    val list = rememberLazyListState()
    val h = rememberHaptics()
    val now by remember(lines) {
        derivedStateOf {
            val p = position.value + 250
            lines.indexOfLast { it.first <= p }.coerceAtLeast(0)
        }
    }
    // only a finger pauses the follow; our own scrolling must not
    var lastTouch by remember { mutableLongStateOf(0L) }
    val dragged by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { lastTouch = System.currentTimeMillis() + if (dragged) 600_000 else 0 }
    val lead = with(LocalDensity.current) { 120.dp.roundToPx() }
    LaunchedEffect(now) {
        if (System.currentTimeMillis() - lastTouch > 2500) list.animateScrollToItem(now, -lead)
    }
    LazyColumn(state = list, contentPadding = PaddingValues(top = 120.dp, bottom = 260.dp)) {
        itemsIndexed(lines) { i, (time, text) ->
            val target = when (i) { now -> Ink.White; now + 1 -> Ink.Dim; else -> Ink.Ghost }
            val c by animateColorAsState(target, tween(380), label = "line")
            Txt(
                text.ifBlank { "♪" },
                Type.lyric.copy(fontSize = size.sp, lineHeight = (size * 1.26f).sp),
                color = { c },
                modifier = Modifier.fillMaxWidth()
                    .press { h.click(); ui.vm.onEvent(PlayerScreenEvent.OnSeekTo(time.toLong())) }
                    .padding(horizontal = 24.dp, vertical = 13.dp),
            )
        }
    }
}

@Composable
private fun Plain(lines: List<String>, size: Float) {
    LazyColumn(contentPadding = PaddingValues(top = 40.dp, bottom = 120.dp)) {
        itemsIndexed(lines) { _, text ->
            Txt(
                text, Type.lyric.copy(fontSize = (size * 0.82f).sp, lineHeight = (size * 1.1f).sp, color = Color(0xFFD1D1D6)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            )
        }
    }
}

// ---------- up next ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpNextLayer(ui: Ui) {
    val state by ui.calm.collectAsState()
    val h = rememberHaptics()
    val all = state.playlist?.trackList.orEmpty()
    val player = ui.vm.player
    val here = player?.currentMediaItemIndex?.takeIf { it in all.indices }
        ?: all.indexOfFirst { it.uri == state.currentTrack?.uri }.coerceAtLeast(0)
    val shuffled = state.playbackMode.shuffles
    // The real order: in shuffle, ask the player what comes next.
    val order: List<Int> = remember(all, here, shuffled, state.playbackMode) {
        val tl = player?.currentTimeline
        if (shuffled && tl != null && !tl.isEmpty && tl.windowCount == all.size) {
            buildList {
                var i = tl.getNextWindowIndex(here, androidx.media3.common.Player.REPEAT_MODE_OFF, true)
                while (i != androidx.media3.common.C.INDEX_UNSET && size < all.size) { add(i); i = tl.getNextWindowIndex(i, androidx.media3.common.Player.REPEAT_MODE_OFF, true) }
            }
        } else ((here + 1) until all.size).toList()
    }
    // local copy so a drag feels instant; the player catches up on each step
    val rows = remember(order, all) { mutableStateListOf<Pair<String, Int>>().apply { val seen = HashMap<String, Int>(); order.forEach { i -> val k = all[i].uri.toString(); val n = (seen[k] ?: 0) + 1; seen[k] = n; add("$k#$n" to i) } } }
    val list = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(list) { from, to ->
        val a = rows.indexOfFirst { it.first == from.key }; val b = rows.indexOfFirst { it.first == to.key }
        if (a < 0 || b < 0) return@rememberReorderableLazyListState
        val fromAbs = rows[a].second; val toAbs = rows[b].second
        rows.add(b, rows.removeAt(a))
        h.tick()
        ui.vm.onEvent(PlayerScreenEvent.OnReorderingQueue(fromAbs, toAbs))
    }
    val left = rows.sumOf { all.getOrNull(it.second)?.duration?.toLong() ?: 0L }
    Column(Modifier.fillMaxSize().background(Ink.Black).statusBarsPadding()) {
        Row(Modifier.padding(top = 8.dp, start = 10.dp, end = 24.dp).height(44.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Glyph.Down, 26.dp, "Close up next") { ui.layer = Layer.None }
            Spacer(Modifier.weight(1f))
            if (rows.isNotEmpty() && !shuffled) Act("Clear", color = Ink.Mute) { h.confirm(); ui.vm.clearUpNext() }
        }
        Txt("Up next", Type.page, modifier = Modifier.padding(start = 24.dp, top = 14.dp))
        Meta(
            (if (rows.isEmpty()) "Nothing after this song" else songs(rows.size) + " · " + span(left)) + if (shuffled) " · shuffled" else "",
            Modifier.padding(start = 24.dp, top = 8.dp, bottom = 10.dp),
        )
        LazyColumn(state = list, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 40.dp) + navPad()) {
            itemsIndexed(rows, key = { _, r -> r.first }) { _, r ->
                val t = all.getOrNull(r.second) ?: return@itemsIndexed
                ReorderableItem(reorder, key = r.first) { dragging ->
                    val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(0.8f, 600f), label = "lift")
                    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                        if (v != SwipeToDismissBoxValue.Settled) {
                            h.confirm(); ui.vm.onEvent(PlayerScreenEvent.OnRemoveFromQueueClick(r.second)); true
                        } else false
                    })
                    SwipeToDismissBox(
                        swipe,
                        backgroundContent = {
                            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart) {
                                Txt("Remove", Type.act.copy(color = Ink.Dim))
                            }
                        },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(64.dp)
                                .graphicsLayer { scaleX = 1f + 0.02f * lift; scaleY = 1f + 0.02f * lift }
                                .background(if (dragging) Color(0xFF0E0E10) else Ink.Black)
                                .padding(start = 24.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(t, 44.dp, 7.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Txt(t.name, Type.title.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(3.dp))
                                Txt(t.who, Type.sub.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (!shuffled) {
                                Box(
                                    Modifier.size(48.dp).semantics { contentDescription = "Drag to move" }
                                        .draggableHandle(onDragStarted = { h.hold() }, onDragStopped = { h.tick() }),
                                    contentAlignment = Alignment.Center,
                                ) { Glyph(Glyph.Grip, 20.dp, Ink.Dim) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun navPad(): PaddingValues {
    val d = LocalDensity.current
    return PaddingValues(bottom = with(d) { WindowInsets.navigationBars.getBottom(d).toDp() })
}

operator fun PaddingValues.plus(o: PaddingValues): PaddingValues = PaddingValues(
    start = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + o.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    top = calculateTopPadding() + o.calculateTopPadding(),
    end = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + o.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    bottom = calculateBottomPadding() + o.calculateBottomPadding(),
)
