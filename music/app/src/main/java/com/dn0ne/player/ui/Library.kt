// yrs. music: modified from Lotus by dn0ne (GPL-3.0). Songs, Albums, Playlists.
package com.dn0ne.player.ui

import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.presentation.LOVED
import com.dn0ne.player.app.presentation.PlayerScreenEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

// ---------- shared rows and words ----------

@Composable
fun IconTap(icon: ImageVector, size: Dp, label: String, modifier: Modifier = Modifier, color: Color = Ink.White, box: Dp = 44.dp, onClick: () -> Unit) {
    val h = rememberHaptics()
    Box(
        modifier
            .size(box)
            .semantics { contentDescription = label; role = Role.Button }
            .press(shrink = true) { h.click(); onClick() },
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, size, color) }
}

@Composable
fun Act(text: String, modifier: Modifier = Modifier, color: Color = Ink.White, style: TextStyle = Type.act, onClick: () -> Unit) {
    val h = rememberHaptics()
    Txt(
        text, style.copy(color = color),
        modifier = modifier
            .semantics { role = Role.Button }
            .press { h.click(); onClick() }
            .padding(vertical = 10.dp),
    )
}

@Composable
fun Meta(text: String, modifier: Modifier = Modifier) {
    Txt(text.uppercase(), Type.meta, modifier = modifier, maxLines = 1)
}

@Composable
fun SongRow(
    track: Track,
    playing: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit,
    modifier: Modifier = Modifier,
    number: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = LocalAccent.current
    val h = rememberHaptics()
    Row(
        modifier
            .fillMaxWidth()
            .height(if (number != null) 60.dp else 72.dp)
            .press(onLongClick = { h.hold(); onLong() }) { onClick() }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Txt(number, Type.time.copy(fontSize = 15.sp, color = if (playing) accent else Ink.Dim, textAlign = TextAlign.Start), modifier = Modifier.width(30.dp))
        } else {
            Cover(track, 50.dp, 7.dp)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Txt(track.name, Type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (number == null) {
                Spacer(Modifier.height(4.dp))
                Txt(track.who, Type.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing() else {
            Spacer(Modifier.width(12.dp))
            Txt(clock(track.duration.toLong()), Type.time.copy(fontSize = 13.sp, color = if (playing) accent else Ink.Dim))
        }
    }
}

/** A calm empty page: a headline, one line of help, and at most two things to do. */
@Composable
fun Quiet(title: String, body: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp)) {
        Txt(title, Type.big)
        Spacer(Modifier.height(10.dp))
        Txt(body, Type.body, modifier = Modifier.widthIn(max = 340.dp))
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { actions() }
    }
}

// ---------- the home: logo, three words, three pages ----------

@Composable
fun Home(ui: Ui, bottom: Dp) {
    val scope = rememberCoroutineScope()
    val h = rememberHaptics()
    LaunchedEffect(ui.pager) { snapshotFlow { ui.pager.settledPage }.drop(1).collect { h.tick() } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, start = 24.dp, end = 10.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Logo(30.dp)
            Spacer(Modifier.width(10.dp))
            Txt("yrs.", Type.brand)
            Spacer(Modifier.weight(1f))
            IconTap(Glyph.More, 24.dp, "More") { ui.panel = Panel.Menu }
        }
        Spacer(Modifier.height(26.dp))
        Words(ui) { i -> scope.launch { ui.pager.animateScrollToPage(i, animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f)) } }
        Spacer(Modifier.height(8.dp))
        HorizontalPager(ui.pager, Modifier.weight(1f), beyondViewportPageCount = 1, key = { it }, verticalAlignment = Alignment.Top) { p ->
            when (p) {
                0 -> SongsPage(ui, bottom)
                1 -> AlbumsPage(ui, bottom)
                else -> ListsPage(ui, bottom)
            }
        }
    }
}

@Composable
private fun Words(ui: Ui, onPick: (Int) -> Unit) {
    val labels = listOf("Songs", "Albums", "Playlists")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        val gap = 26.dp
        val scale = remember(maxWidth, density) {
            val full = labels.sumOf { measurer.measure(it, Type.word).size.width } + with(density) { (gap * 2).toPx() }
            min(1f, with(density) { maxWidth.toPx() } / full)
        }
        val h = rememberHaptics()
        Row(horizontalArrangement = Arrangement.spacedBy(gap * scale)) {
            labels.forEachIndexed { i, label ->
                Txt(
                    label,
                    Type.word.copy(fontSize = Type.word.fontSize * scale),
                    color = {
                        val d = abs(ui.pager.currentPage + ui.pager.currentPageOffsetFraction - i).coerceAtMost(1f)
                        lerp(Ink.White, Ink.Ghost, d)
                    },
                    modifier = Modifier
                        .semantics { role = Role.Tab; contentDescription = label }
                        .press { h.click(); onPick(i) }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

// ---------- Songs ----------

@Composable
private fun SongsPage(ui: Ui, bottom: Dp) {
    val tracks by ui.vm.trackList.collectAsState()
    val state by ui.calm.collectAsState()
    val pins by ui.pins.items.collectAsState()
    val albums by ui.vm.albumPlaylists.collectAsState()
    val lists by ui.vm.playlists.collectAsState()
    val everything = remember(tracks) { Playlist(name = null, trackList = tracks) }

    if (tracks.isEmpty()) {
        EmptyLibrary(ui)
        return
    }
    val pinned = remember(pins, albums, lists) {
        pins.mapNotNull { key ->
            when {
                key.startsWith("a:") -> albums.firstOrNull { Pins.album(it.name) == key }?.let { key to it }
                else -> lists.firstOrNull { Pins.list(it.name) == key }?.let { key to it }
            }
        }
    }
    LazyColumn(state = ui.songs, contentPadding = PaddingValues(bottom = bottom), modifier = Modifier.fillMaxSize()) {
        if (pinned.isNotEmpty()) {
            item(key = "pins", contentType = "pins") {
                Column(Modifier.padding(top = 18.dp)) {
                    Meta("Pinned", Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(12.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(pinned, key = { it.first }) { (key, list) ->
                            val isAlbum = key.startsWith("a:")
                            val h = rememberHaptics()
                            Column(
                                Modifier.width(60.dp).press(onLongClick = { h.hold(); ui.pins.toggle(key); ui.say("Unpinned") }) {
                                    ui.go(if (isAlbum) Page.Album(list.name) else Page.List(list.name.orEmpty()))
                                }
                            ) {
                                if (isAlbum) Cover(list.trackList.firstOrNull(), 60.dp, 9.dp) else Mosaic(list, 60.dp, 9.dp)
                                Spacer(Modifier.height(7.dp))
                                Txt(
                                    if (isAlbum) albumTitle(list.name) else list.name.orEmpty(),
                                    Type.sub.copy(fontSize = 11.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        item(key = "head", contentType = "head") {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Meta(songs(tracks.size))
                Spacer(Modifier.weight(1f))
                Act("Play") { ui.vm.playAll(everything, shuffle = false) }
                Spacer(Modifier.width(22.dp))
                Act("Shuffle", color = Ink.Mute) { ui.vm.playAll(everything, shuffle = true) }
            }
        }
        items(tracks, key = { it.uri.toString() }, contentType = { "song" }) { t ->
            SongRow(
                t, playing = state.currentTrack?.uri == t.uri,
                onClick = { ui.vm.onEvent(PlayerScreenEvent.OnTrackClick(t, everything)) },
                onLong = { ui.panel = Panel.Song(t) },
            )
        }
    }
}

@Composable
private fun EmptyLibrary(ui: Ui) {
    // Give the first read of the library a moment before saying it is empty.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1600); settled = true }
    if (!settled) return
    Quiet(
        "No music yet",
        "yrs. music plays the songs saved on this phone. Put some in your Music folder, or choose the folders it should look in.",
    ) {
        Act("Choose folders", color = LocalAccent.current) { ui.go(Page.Folders) }
        Act("Look again", color = Ink.Mute) { ui.vm.refreshLibrary() }
    }
}

// ---------- Albums ----------

@Composable
private fun AlbumsPage(ui: Ui, bottom: Dp) {
    val raw by ui.vm.albumPlaylists.collectAsState()
    val albums = remember(raw) { raw.sortedBy { albumTitle(it.name).lowercase() } }
    if (albums.isEmpty()) {
        Quiet("No albums yet", "Albums appear here once your songs are found.")
        return
    }
    LazyVerticalGrid(
        GridCells.Fixed(2), state = ui.albums,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = bottom + 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(2) }, key = "head", contentType = "head") {
            Meta(if (albums.size == 1) "1 album" else "${count(albums.size)} albums", Modifier.padding(top = 18.dp, bottom = 0.dp))
        }
        items(albums, key = { "a:" + it.name }, contentType = { "album" }) { a ->
            val h = rememberHaptics()
            Column(Modifier.press(onLongClick = { h.hold(); ui.panel = Panel.AlbumMenu(a.name) }) { ui.go(Page.Album(a.name)) }) {
                CoverBox(a.trackList.firstOrNull()?.coverArtUri, albumTitle(a.name), 12.dp, Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(10.dp))
                Txt(albumTitle(a.name), Type.title.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Txt(albumArtist(a), Type.sub.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

fun albumArtist(a: Playlist): String {
    a.trackList.firstNotNullOfOrNull { it.albumArtist?.takeIf { s -> s.isNotBlank() } }?.let { return it }
    val who = a.trackList.map { it.who }.distinct()
    return if (who.size > 1) "Various artists" else who.firstOrNull() ?: "Unknown artist"
}

// ---------- Playlists ----------

@Composable
private fun ListsPage(ui: Ui, bottom: Dp) {
    val all by ui.vm.playlists.collectAsState()
    val loved = all.firstOrNull { it.name == LOVED }
    val others = remember(all) { all.filter { it.name != LOVED }.sortedBy { it.name.orEmpty().lowercase() } }
    val accent = LocalAccent.current
    LazyColumn(state = ui.lists, contentPadding = PaddingValues(bottom = bottom), modifier = Modifier.fillMaxSize()) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Meta(if (others.size == 1) "1 playlist" else "${count(others.size)} playlists")
                Spacer(Modifier.weight(1f))
                Act("New playlist") { newPlaylist(ui) }
            }
        }
        item(key = "loved") {
            ListRow(
                title = "Loved",
                sub = loved?.let { summary(it) } ?: "Songs you love",
                art = {
                    Box(
                        Modifier.size(56.dp).border(1.dp, Ink.Ghost, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(Glyph.HeartFull, 22.dp, accent) }
                },
                onClick = { ui.go(Page.List(LOVED)) },
                onLong = {},
            )
        }
        items(others, key = { "p:" + it.name }) { p ->
            ListRow(
                title = p.name.orEmpty(), sub = summary(p),
                art = { Mosaic(p, 56.dp, 10.dp) },
                onClick = { ui.go(Page.List(p.name.orEmpty())) },
                onLong = { ui.panel = Panel.ListMenu(p) },
            )
        }
        if (others.isEmpty()) {
            item(key = "hint") {
                Txt(
                    "Make one for a mood, a drive or a person. Hold any song to add it.",
                    Type.small, modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                )
            }
        }
    }
}

fun summary(p: Playlist): String =
    if (p.trackList.isEmpty()) "Empty" else songs(p.trackList.size) + " · " + span(p.trackList.sumOf { it.duration.toLong() })

@Composable
private fun ListRow(title: String, sub: String, art: @Composable () -> Unit, onClick: () -> Unit, onLong: () -> Unit) {
    val h = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().height(80.dp).press(onLongClick = { h.hold(); onLong() }, onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, Type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Txt(sub, Type.sub, maxLines = 1)
        }
    }
}

fun newPlaylist(ui: Ui, then: (String) -> Unit = {}) {
    ui.panel = Panel.Name("New playlist", "") { name ->
        val clean = name.trim()
        val taken = ui.vm.playlists.value.any { it.name.equals(clean, ignoreCase = true) } || clean.equals(LOVED, true)
        if (clean.isEmpty()) return@Name
        if (taken) { ui.say("There is already a playlist called $clean"); return@Name }
        ui.vm.onEvent(PlayerScreenEvent.OnCreatePlaylistClick(clean))
        then(clean)
    }
}
