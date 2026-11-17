// yrs. music: modified from Lotus by dn0ne (GPL-3.0). Menus and sheets: black, one hairline, words.
package com.dn0ne.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dn0ne.player.SleepTimer
import com.dn0ne.player.app.domain.sort.SortOrder
import com.dn0ne.player.app.domain.sort.TrackSort
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.presentation.LOVED
import com.dn0ne.player.app.presentation.PlayerScreenEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun BoxScope.Panels(ui: Ui) {
    val panel = ui.panel
    var last by remember { mutableStateOf<Panel?>(null) }
    if (panel != null) last = panel
    val visible = panel != null

    AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(200))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (last is Panel.Menu) 0.5f else 0.66f))
                .pointerInput(Unit) { detectTapGestures { ui.panel = null } }
        )
    }
    val p = last ?: return
    AnimatedVisibility(
        visible && p is Panel.Menu,
        modifier = Modifier.align(Alignment.TopEnd),
        enter = fadeIn(tween(120)) + scaleIn(spring(0.78f, 650f), initialScale = 0.88f, transformOrigin = TransformOrigin(0.92f, 0f)),
        exit = fadeOut(tween(120)) + scaleOut(tween(150), targetScale = 0.94f, transformOrigin = TransformOrigin(0.92f, 0f)),
    ) { Menu(ui) }
    AnimatedVisibility(
        visible && p !is Panel.Menu,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(spring(0.88f, 420f)) { it },
        exit = slideOutVertically(tween(220)) { it },
    ) { Sheet(ui) { SheetBody(ui, p) } }
}

// ---------- the three dots ----------

@Composable
private fun Menu(ui: Ui) {
    val running by SleepTimer.isRunning.collectAsState()
    val minutes by SleepTimer.minutesLeft.collectAsState()
    val sort by ui.vm.trackSort.collectAsState()
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.statusBarsPadding().padding(top = 50.dp, end = 16.dp).width(236.dp)
            .background(Ink.Black, shape).border(1.dp, Ink.Edge, shape).padding(vertical = 6.dp)
    ) {
        MenuRow("Search") { ui.go(Page.Search) }
        MenuRow("Sort by", sortName(sort)) { ui.panel = Panel.Sort }
        MenuRow("Sleep timer", if (running) "$minutes min" else null, valueAccent = running) { ui.panel = Panel.Sleep }
        MenuRow("Settings") { ui.go(Page.Settings) }
    }
}

@Composable
private fun MenuRow(text: String, value: String? = null, valueAccent: Boolean = false, onClick: () -> Unit) {
    val h = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().height(48.dp).semantics { role = Role.Button }.press { h.click(); onClick() }.padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(text, Type.setting.copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
        if (value != null) Txt(value, Type.sub.copy(color = if (valueAccent) LocalAccent.current else Ink.Mute))
    }
}

fun sortName(s: TrackSort) = when (s) {
    TrackSort.Title -> "Title"
    TrackSort.Album -> "Album"
    TrackSort.Artist -> "Artist"
    TrackSort.Genre -> "Genre"
    TrackSort.Year -> "Year"
    TrackSort.TrackNumber -> "Track number"
    TrackSort.DateModified -> "Date added"
}

// ---------- the sheet ----------

@Composable
private fun Sheet(ui: Ui, content: @Composable ColumnScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val pull = remember { Animatable(0f) }
    val d = LocalDensity.current
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = pull.value }
            .background(Ink.Black, shape)
            .border(1.dp, Ink.Edge, shape)
            .pointerInput(Unit) { detectTapGestures { } }
            .draggable(
                rememberDraggableState { dy -> scope.launch { pull.snapTo((pull.value + dy).coerceAtLeast(0f)) } },
                Orientation.Vertical,
                onDragStopped = { v ->
                    if (pull.value > with(d) { 110.dp.toPx() } || v > 900f) { ui.panel = null; delay(260); pull.snapTo(0f) }
                    else pull.animateTo(0f, spring(0.8f, 500f))
                },
            )
            .navigationBarsPadding()
            .imePadding()
            .padding(top = 10.dp, bottom = 10.dp)
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp).width(36.dp).height(4.dp).background(Ink.Ghost, RoundedCornerShape(2.dp)))
        content()
    }
}

@Composable
fun SheetRow(text: String, value: String? = null, color: Color = Ink.White, valueColor: Color = Ink.Mute, onClick: () -> Unit) {
    val h = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).semantics { role = Role.Button }.press { h.click(); onClick() }.padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(text, Type.setting.copy(color = color), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (value != null) Txt(value, Type.setting.copy(fontSize = 15.sp, color = valueColor))
    }
}

@Composable
private fun Head(title: String, sub: String? = null) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 10.dp)) {
        Txt(title, Type.big, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub != null) { Spacer(Modifier.height(6.dp)); Txt(sub, Type.body) }
    }
}

@Composable
private fun SongHead(t: Track) {
    Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(t, 48.dp, 8.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(t.name, Type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Txt(t.who + " · " + t.record, Type.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(1.dp).background(Ink.Hair))
    Spacer(Modifier.height(4.dp))
}

private fun close(ui: Ui) { ui.panel = null }

private fun openAlbum(ui: Ui, album: String?) {
    if (ui.expand.value > 0f) openAlbumFromPlayer(ui, album) else { ui.panel = null; ui.go(Page.Album(album)) }
}

@Composable
private fun SheetBody(ui: Ui, p: Panel) {
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val lists by ui.vm.playlists.collectAsState()
    val albums by ui.vm.albumPlaylists.collectAsState()
    when (p) {
        Panel.Menu -> {}

        is Panel.Song -> {
            val t = p.track
            val loved = lists.firstOrNull { it.name == LOVED }?.trackList?.any { it.uri == t.uri } == true
            SongHead(t)
            SheetRow("Play next") { close(ui); ui.vm.onEvent(PlayerScreenEvent.OnPlayNextClick(t)); ui.say("Plays next") }
            SheetRow("Add to queue") { close(ui); ui.vm.onEvent(PlayerScreenEvent.OnAddToQueueClick(listOf(t))); ui.say("Added to the queue") }
            SheetRow("Add to playlist") { ui.panel = Panel.AddTo(listOf(t)) }
            SheetRow("Go to album") { openAlbum(ui, t.album) }
            SheetRow(if (loved) "Remove from Loved" else "Love") { close(ui); ui.vm.toggleLoved(t, lists) }
            val from = p.from?.name?.let { n -> lists.firstOrNull { it.name == n } }
            if (from != null) {
                SheetRow("Remove from ${from.name}") {
                    close(ui); ui.vm.onEvent(PlayerScreenEvent.OnRemoveFromPlaylist(listOf(t), from))
                }
            }
        }

        Panel.PlayerMenu -> {
            val state by ui.calm.collectAsState()
            val running by SleepTimer.isRunning.collectAsState()
            val minutes by SleepTimer.minutesLeft.collectAsState()
            val t = state.currentTrack
            if (t != null) SongHead(t)
            if (t != null) SheetRow("Go to album") { openAlbumFromPlayer(ui, t.album) }
            if (t != null) SheetRow("Add to playlist") { ui.panel = Panel.AddTo(listOf(t)) }
            SheetRow("Sleep timer", if (running) "$minutes min" else null, valueColor = accent) { ui.panel = Panel.Sleep }
            SheetRow("Choose a lyrics file") { close(ui); ui.outside.pickLyrics() }
            if (state.lyrics != null && ui.layer == Layer.Lyrics) {
                SheetRow("Forget saved lyrics") { close(ui); ui.vm.forgetLyrics() }
            }
        }

        is Panel.AddTo -> {
            Head("Add to playlist")
            SheetRow("New playlist", color = accent) {
                val tracks = p.tracks
                newPlaylist(ui) { name ->
                    scope.launch {
                        val made = withTimeoutOrNull(3000) { ui.vm.playlists.first { l -> l.any { it.name == name } } }?.first { it.name == name }
                        if (made != null) { ui.vm.onEvent(PlayerScreenEvent.OnAddToPlaylist(tracks, made)); ui.say("Added to $name") }
                    }
                }
            }
            val ordered = lists.sortedWith(compareBy({ it.name != LOVED }, { it.name.orEmpty().lowercase() }))
            ordered.forEach { pl ->
                val fresh = p.tracks.filter { t -> pl.trackList.none { it.uri == t.uri } }
                SheetRow(pl.name.orEmpty(), if (fresh.isEmpty()) "Added" else songs(pl.trackList.size), valueColor = if (fresh.isEmpty()) accent else Ink.Dim) {
                    close(ui)
                    if (fresh.isEmpty()) ui.say("Already in ${pl.name}")
                    else { ui.vm.onEvent(PlayerScreenEvent.OnAddToPlaylist(fresh, pl)); ui.say("Added to ${pl.name}") }
                }
            }
        }

        is Panel.Name -> NameBody(ui, p)

        Panel.Sort -> {
            val sort by ui.vm.trackSort.collectAsState()
            val order by ui.vm.trackSortOrder.collectAsState()
            Head("Sort songs by")
            listOf(TrackSort.Title, TrackSort.Artist, TrackSort.Album, TrackSort.Year, TrackSort.DateModified).forEach { s ->
                SheetRow(sortName(s), color = if (s == sort) accent else Ink.White) {
                    ui.vm.onEvent(PlayerScreenEvent.OnTrackSortChange(sort = s))
                }
            }
            val words = when (sort) {
                TrackSort.Year, TrackSort.DateModified -> "Oldest first" to "Newest first"
                else -> "A to Z" to "Z to A"
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp).height(1.dp).background(Ink.Hair))
            SheetRow("Order", if (order == SortOrder.Ascending) words.first else words.second, valueColor = Ink.White) {
                ui.vm.onEvent(PlayerScreenEvent.OnTrackSortChange(order = if (order == SortOrder.Ascending) SortOrder.Descending else SortOrder.Ascending))
            }
        }

        Panel.Sleep -> {
            val running by SleepTimer.isRunning.collectAsState()
            val minutes by SleepTimer.minutesLeft.collectAsState()
            Head("Sleep timer", if (running) "Music stops in $minutes min." else "Music stops on its own after a while.")
            if (running) SheetRow("Turn off", color = accent) { close(ui); SleepTimer.stop(); ui.say("Sleep timer off") }
            listOf(15, 30, 45, 60, 90).forEach { m ->
                SheetRow(if (m < 60) "$m minutes" else if (m == 60) "1 hour" else "1 hour 30 minutes") {
                    close(ui); SleepTimer.updateMinutesLeft(m); SleepTimer.start(); ui.say("Music stops in ${if (m < 60) "$m minutes" else spanWords(m * 60000L)}")
                }
            }
        }

        is Panel.AlbumMenu -> {
            val a = albums.firstOrNull { it.name == p.name }
            val tracks = a?.let { albumOrder(it.trackList) }.orEmpty()
            val key = Pins.album(p.name)
            val pins by ui.pins.items.collectAsState()
            Head(albumTitle(p.name), a?.let { albumArtist(it) })
            SheetRow("Play") { close(ui); ui.vm.playAll(com.dn0ne.player.app.domain.track.Playlist(albumTitle(p.name), tracks), false) }
            SheetRow("Shuffle") { close(ui); ui.vm.playAll(com.dn0ne.player.app.domain.track.Playlist(albumTitle(p.name), tracks), true) }
            SheetRow("Add to queue") { close(ui); ui.vm.onEvent(PlayerScreenEvent.OnAddToQueueClick(tracks)); ui.say("Added to the queue") }
            SheetRow("Add to playlist") { ui.panel = Panel.AddTo(tracks) }
            SheetRow(if (key in pins) "Unpin" else "Pin to Songs") { close(ui); val was = key in pins; ui.pins.toggle(key); ui.say(if (was) "Unpinned" else "Pinned") }
        }

        is Panel.ListMenu -> {
            val pl = lists.firstOrNull { it.name == p.playlist.name } ?: p.playlist
            val key = Pins.list(pl.name)
            val pins by ui.pins.items.collectAsState()
            Head(pl.name.orEmpty(), summary(pl))
            if (pl.trackList.isNotEmpty()) {
                SheetRow("Play") { close(ui); ui.vm.playAll(pl, false) }
                SheetRow("Add to queue") { close(ui); ui.vm.onEvent(PlayerScreenEvent.OnAddToQueueClick(pl.trackList)); ui.say("Added to the queue") }
            }
            if (pl.name != LOVED) {
                SheetRow(if (key in pins) "Unpin" else "Pin to Songs") { close(ui); val was = key in pins; ui.pins.toggle(key); ui.say(if (was) "Unpinned" else "Pinned") }
                SheetRow("Rename") {
                    ui.panel = Panel.Name("Rename playlist", pl.name.orEmpty()) { new ->
                        val clean = new.trim()
                        if (clean.isEmpty() || clean == pl.name) return@Name
                        if (lists.any { it.name.equals(clean, true) } || clean.equals(LOVED, true)) { ui.say("There is already a playlist called $clean"); return@Name }
                        ui.vm.onEvent(PlayerScreenEvent.OnRenamePlaylistClick(clean, pl))
                        ui.pins.rename(Pins.list(pl.name), Pins.list(clean))
                        val i = ui.stack.indexOf(Page.List(pl.name.orEmpty()))
                        if (i >= 0) ui.stack[i] = Page.List(clean)
                    }
                }
                SheetRow("Delete", color = Color(0xFFFF8A80)) {
                    ui.panel = Panel.Confirm("Delete “${pl.name}”?", "Delete") {
                        ui.vm.onEvent(PlayerScreenEvent.OnDeletePlaylistClick(pl))
                        if (key in ui.pins.items.value) ui.pins.toggle(key)
                        if (ui.page == Page.List(pl.name.orEmpty())) ui.back()
                        ui.say("Deleted ${pl.name}")
                    }
                }
            }
        }

        is Panel.Confirm -> {
            Head(p.title, "The songs stay on your phone.")
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Act("Cancel", color = Ink.Mute) { close(ui) }
                Spacer(Modifier.weight(1f))
                Act(p.action, color = Color(0xFFFF8A80)) { close(ui); p.done() }
            }
        }
    }
}

private fun openAlbumFromPlayer(ui: Ui, album: String?) {
    ui.panel = null
    ui.layer = Layer.None
    ui.closePlayer = true
    ui.go(Page.Album(album))
}

@Composable
private fun NameBody(ui: Ui, p: Panel.Name) {
    val accent = LocalAccent.current
    var text by remember(p) { mutableStateOf(TextFieldValue(p.start, TextRange(p.start.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(p) { delay(120); runCatching { focus.requestFocus() } }
    fun save() { val v = text.text; ui.panel = null; p.done(v) }
    Meta(p.title, Modifier.padding(start = 24.dp, top = 12.dp))
    BasicTextField(
        text, { text = it },
        textStyle = Type.big,
        cursorBrush = SolidColor(accent),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
        decorationBox = { inner ->
            Box {
                if (text.text.isEmpty()) Txt("Name", Type.big.copy(color = Ink.Ghost))
                inner()
            }
        },
        modifier = Modifier.fillMaxWidth().focusRequester(focus).padding(horizontal = 24.dp, vertical = 14.dp),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Act("Cancel", color = Ink.Mute) { ui.panel = null }
        Spacer(Modifier.weight(1f))
        Act("Save", color = if (text.text.isBlank()) Ink.Faint else accent) { if (text.text.isNotBlank()) save() }
    }
}

/** A short line that rises above the strip and leaves on its own. */
@Composable
fun BoxScope.Toast(ui: Ui, bottom: Dp) {
    var text by remember { mutableStateOf("") }
    val now = ui.toast
    LaunchedEffect(now) {
        if (now != null) { text = now; delay(2400); if (ui.toast == now) ui.toast = null }
    }
    AnimatedVisibility(
        now != null,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 14.dp),
        enter = fadeIn(tween(160)) + slideInVertically(spring(0.8f, 500f)) { it / 2 },
        exit = fadeOut(tween(220)),
    ) {
        val shape = RoundedCornerShape(12.dp)
        Txt(
            text, Type.sub.copy(color = Ink.White, fontSize = 14.sp),
            modifier = Modifier.background(Color(0xFF0B0B0C), shape).border(1.dp, Ink.Edge, shape).padding(horizontal = 16.dp, vertical = 11.dp),
        )
    }
}

fun albumOrder(tracks: List<Track>): List<Track> = tracks.sortedWith(
    compareBy<Track>({ (it.trackNumber?.substringBefore('/')?.toIntOrNull() ?: Int.MAX_VALUE) }, { it.name.lowercase() })
)
