// yrs. music: modified from Lotus by dn0ne (GPL-3.0). An album, a playlist, search, settings, folders.
package com.dn0ne.player.ui

import android.os.Environment
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.domain.track.filterTracks
import com.dn0ne.player.app.presentation.LOVED
import com.dn0ne.player.app.presentation.PlayerScreenEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PageTop(ui: Ui, more: (() -> Unit)? = null, end: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.statusBarsPadding().padding(top = 8.dp, start = 10.dp, end = 10.dp).height(44.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTap(Glyph.Back, 26.dp, "Back") { ui.back() }
        Spacer(Modifier.weight(1f))
        end?.invoke()
        if (more != null) IconTap(Glyph.More, 24.dp, "More", onClick = more)
    }
}

@Composable
private fun Hair() = Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(1.dp).background(Ink.Hair))

// ---------- an album ----------

@Composable
fun AlbumPage(ui: Ui, name: String?, bottom: Dp) {
    val albums by ui.vm.albumPlaylists.collectAsState()
    val state by ui.calm.collectAsState()
    val album = albums.firstOrNull { it.name == name }
    if (album == null) {
        Column(Modifier.fillMaxSize()) {
            PageTop(ui)
            Quiet("This album is gone", "Its songs are no longer on this phone, or are outside the folders yrs. music looks in.")
        }
        return
    }
    val tracks = remember(album) { albumOrder(album.trackList) }
    val list = remember(tracks) { Playlist(albumTitle(name), tracks) }
    val year = tracks.firstNotNullOfOrNull { it.year?.takeIf { y -> y.isNotBlank() && y != "0" } }
    val length = tracks.sumOf { it.duration.toLong() }
    val lossless = tracks.isNotEmpty() && tracks.all { it.lossless }
    val numbered = tracks.all { it.trackNumber?.substringBefore('/')?.toIntOrNull() != null }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom)) {
        item(key = "top") { PageTop(ui, more = { ui.panel = Panel.AlbumMenu(name) }) }
        item(key = "head") {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
                Cover(tracks.firstOrNull(), 180.dp, 12.dp, letterOf = albumTitle(name))
                Spacer(Modifier.height(24.dp))
                Txt(albumTitle(name), Type.page)
                Spacer(Modifier.height(4.dp))
                Txt(albumArtist(album), Type.artist.copy(color = Ink.White))
                Spacer(Modifier.height(10.dp))
                Meta(listOfNotNull("Album", year, if (lossless) "Lossless" else null).joinToString(" · "))
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Act("Play") { ui.vm.playAll(list, false) }
                    Spacer(Modifier.width(24.dp))
                    Act("Shuffle", color = Ink.Mute) { ui.vm.playAll(list, true) }
                }
                Spacer(Modifier.height(4.dp))
            }
            Hair()
            Spacer(Modifier.height(6.dp))
        }
        itemsIndexed(tracks, key = { _, t -> t.uri.toString() }) { i, t ->
            val n = if (numbered) (t.trackNumber!!.substringBefore('/').toInt() % 1000).toString() else "${i + 1}"
            SongRow(
                t, playing = state.currentTrack?.uri == t.uri, number = n,
                onClick = { ui.vm.onEvent(PlayerScreenEvent.OnTrackClick(t, list)) },
                onLong = { ui.panel = Panel.Song(t) },
            )
        }
        item(key = "foot") {
            Txt(
                listOfNotNull(year, "${songs(tracks.size)}, ${spanWords(length)}").joinToString(" · "),
                Type.small.copy(fontSize = 12.5.sp), modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 24.dp),
            )
        }
    }
}

// ---------- a playlist ----------

@Composable
fun ListPage(ui: Ui, name: String, bottom: Dp) {
    val lists by ui.vm.playlists.collectAsState()
    val state by ui.calm.collectAsState()
    val accent = LocalAccent.current
    val found = lists.firstOrNull { it.name == name }
    val p = found ?: Playlist(name, emptyList())
    var editing by rememberSaveable(name) { mutableStateOf(false) }
    val h = rememberHaptics()

    val rows = remember(p.trackList, editing) { mutableStateListOf<Track>().apply { addAll(p.trackList) } }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val a = rows.indexOfFirst { it.uri.toString() == from.key }
        val b = rows.indexOfFirst { it.uri.toString() == to.key }
        if (a >= 0 && b >= 0) { rows.add(b, rows.removeAt(a)); h.tick() }
    }
    fun finish() {
        editing = false
        if (found != null && rows.toList() != p.trackList) ui.vm.onEvent(PlayerScreenEvent.OnPlaylistReorder(rows.toList(), found))
    }
    // leaving mid-edit keeps the new order
    val keep by androidx.compose.runtime.rememberUpdatedState({ if (editing) finish() })
    androidx.compose.runtime.DisposableEffect(name) { onDispose { keep() } }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = bottom)) {
        item(key = "top") { PageTop(ui, more = if (found != null) ({ ui.panel = Panel.ListMenu(found) }) else null) }
        item(key = "head") {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
                if (name == LOVED) {
                    Box(Modifier.width(180.dp).height(180.dp).background(Ink.Tile, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Glyph(Glyph.HeartFull, 54.dp, accent)
                    }
                } else Mosaic(p, 180.dp, 12.dp)
                Spacer(Modifier.height(24.dp))
                Txt(name, Type.page)
                Spacer(Modifier.height(8.dp))
                Meta(if (p.trackList.isEmpty()) "No songs yet" else songs(p.trackList.size) + " · " + span(p.trackList.sumOf { it.duration.toLong() }))
                if (p.trackList.isNotEmpty()) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!editing) {
                            Act("Play") { ui.vm.playAll(p, false) }
                            Spacer(Modifier.width(24.dp))
                            Act("Shuffle", color = Ink.Mute) { ui.vm.playAll(p, true) }
                        }
                        Spacer(Modifier.weight(1f))
                        if (found != null && p.trackList.size > 1) {
                            Act(if (editing) "Done" else "Edit", color = if (editing) accent else Ink.Mute) { if (editing) finish() else editing = true }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Hair()
            Spacer(Modifier.height(6.dp))
        }
        if (p.trackList.isEmpty()) {
            item(key = "empty") {
                Txt(
                    if (name == LOVED) "Tap the heart in the player and the song lands here." else "Hold any song and choose Add to playlist.",
                    Type.body, modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                )
            }
        }
        if (editing) {
            items(rows, key = { it.uri.toString() }) { t ->
                ReorderableItem(reorder, key = t.uri.toString()) { dragging ->
                    val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(0.8f, 600f), label = "lift")
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
                        IconTap(Glyph.Close, 18.dp, "Remove", color = Ink.Dim) {
                            rows.remove(t)
                            if (found != null) ui.vm.onEvent(PlayerScreenEvent.OnRemoveFromPlaylist(listOf(t), found))
                        }
                        Box(
                            Modifier.width(44.dp).height(48.dp).semantics { contentDescription = "Drag to move" }
                                .draggableHandle(onDragStarted = { h.hold() }, onDragStopped = { h.tick() }),
                            contentAlignment = Alignment.Center,
                        ) { Glyph(Glyph.Grip, 20.dp, Ink.Dim) }
                    }
                }
            }
        } else {
            items(p.trackList, key = { it.uri.toString() }) { t ->
                SongRow(
                    t, playing = state.currentTrack?.uri == t.uri,
                    onClick = { ui.vm.onEvent(PlayerScreenEvent.OnTrackClick(t, p)) },
                    onLong = { ui.panel = Panel.Song(t, from = p) },
                )
            }
        }
    }
}

// ---------- search ----------

@Composable
fun SearchPage(ui: Ui, bottom: Dp) {
    val tracks by ui.vm.trackList.collectAsState()
    val albums by ui.vm.albumPlaylists.collectAsState()
    val state by ui.calm.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keys = LocalSoftwareKeyboardController.current
    val accent = LocalAccent.current
    LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus() } }
    var found by remember { mutableStateOf<Pair<List<Track>, List<Playlist>>>(emptyList<Track>() to emptyList()) }
    LaunchedEffect(q, tracks, albums) {
        delay(90)
        val t = q.trim()
        found = if (t.isEmpty()) emptyList<Track>() to emptyList() else withContext(Dispatchers.Default) {
            tracks.filterTracks(t) to albums.filter { albumTitle(it.name).contains(t, true) || albumArtist(it).contains(t, true) }.take(8)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.statusBarsPadding().padding(top = 8.dp, start = 10.dp, end = 10.dp).heightIn(min = 52.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(Glyph.Back, 26.dp, "Back") { ui.back() }
            Spacer(Modifier.width(4.dp))
            BasicTextField(
                q, { q = it },
                textStyle = Type.big,
                cursorBrush = SolidColor(accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keys?.hide() }),
                decorationBox = { inner -> Box { if (q.isEmpty()) Txt("Search", Type.big.copy(color = Ink.Ghost)); inner() } },
                modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Search" },
            )
            if (q.isNotEmpty()) IconTap(Glyph.Close, 20.dp, "Clear", color = Ink.Mute) { q = "" }
        }
        val (songsFound, albumsFound) = found
        LazyColumn(Modifier.weight(1f).imePadding(), contentPadding = PaddingValues(bottom = bottom)) {
            if (q.isBlank()) {
                item { Txt("Find a song, an artist or an album.", Type.body, modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) }
            } else if (songsFound.isEmpty() && albumsFound.isEmpty()) {
                item { Quiet("Nothing found", "No song, artist or album matches “${q.trim()}”.") }
            }
            if (albumsFound.isNotEmpty()) {
                item { Meta("Albums", Modifier.padding(start = 24.dp, top = 18.dp, bottom = 6.dp)) }
                items(albumsFound, key = { "a:" + it.name }) { a ->
                    Row(
                        Modifier.fillMaxWidth().height(72.dp).press { keys?.hide(); ui.go(Page.Album(a.name)) }.padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(a.trackList.firstOrNull(), 50.dp, 7.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(albumTitle(a.name), Type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Txt(albumArtist(a), Type.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (songsFound.isNotEmpty()) {
                item { Meta(songs(songsFound.size), Modifier.padding(start = 24.dp, top = 18.dp, bottom = 6.dp)) }
                val list = Playlist(null, songsFound)
                items(songsFound.take(300), key = { "s:" + it.uri }) { t ->
                    SongRow(
                        t, playing = state.currentTrack?.uri == t.uri,
                        onClick = { keys?.hide(); ui.vm.onEvent(PlayerScreenEvent.OnTrackClick(t, list)) },
                        onLong = { ui.panel = Panel.Song(t) },
                    )
                }
            }
        }
    }
}

// ---------- settings: few, honest, calm ----------

@Composable
private fun Cap(text: String, top: Dp = 30.dp) =
    Txt(text.uppercase(), Type.meta.copy(fontSize = 11.5.sp, letterSpacing = Type.meta.letterSpacing), modifier = Modifier.padding(start = 24.dp, top = top, bottom = 4.dp))

@Composable
fun SetRow(title: String, sub: String? = null, value: String, on: Boolean = true, onClick: () -> Unit) {
    val h = rememberHaptics()
    val c by animateColorAsState(if (on) Ink.White else Ink.Faint, tween(160), label = "v")
    Column(Modifier.fillMaxWidth().press { h.click(); onClick() }.padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(title, Type.setting)
                if (sub != null) { Spacer(Modifier.height(3.dp)); Txt(sub, Type.sub.copy(fontSize = 13.sp)) }
            }
            Spacer(Modifier.width(14.dp))
            Txt(value, Type.setting.copy(fontSize = 16.sp, color = c, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF141416)))
    }
}

@Composable
fun SettingsPage(ui: Ui, bottom: Dp) {
    val s = ui.vm.settings
    val cover by s.useAlbumArtColor.collectAsState()
    val onOpen by s.scanOnAppLaunch.collectAsState()
    val music by s.scanMusicFolder.collectAsState()
    val extra by s.extraScanFolders.collectAsState()
    var restart by remember { mutableStateOf(s.jumpToBeginning) }
    var short by remember { mutableStateOf(s.ignoreShortTracks) }
    var size by remember { mutableStateOf(s.lyricsFontSize.value) }
    val folders = (listOfNotNull(if (music) "Music" else null) + extra.map { it.substringAfterLast('/') }).joinToString(", ").ifEmpty { "None chosen" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item { PageTop(ui) }
        item { Txt("Settings", Type.page, modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 8.dp)) }
        item {
            Cap("Playback", 20.dp)
            SetRow("Previous starts the song again", "After the first three seconds", if (restart) "On" else "Off", restart) {
                restart = !restart; s.jumpToBeginning = restart
            }
            Cap("Library")
            SetRow("Folders", folders, "Edit", false) { ui.go(Page.Folders) }
            SetRow("Skip short files", "Under 30 seconds", if (short) "On" else "Off", short) { short = !short; s.ignoreShortTracks = short; ui.vm.rereadLibrary() }
            SetRow("Look for new music on open", null, if (onOpen) "On" else "Off", onOpen) { s.updateScanOnAppLaunch(!onOpen) }
            SetRow("Import a playlist", "From an M3U file", "Choose", false) { ui.outside.pickPlaylist() }
            Cap("Looks")
            SetRow("Colour from the cover", "One tone from the artwork for the timeline", if (cover) "On" else "Off", cover) { s.updateUseAlbumArtColor(!cover) }
            Cap("Lyrics")
            Column(Modifier.padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt("Text size", Type.setting, modifier = Modifier.weight(1f))
                    listOf("Small" to 22f, "Medium" to 27f, "Large" to 32f).forEach { (label, v) ->
                        val chosen = kotlin.math.abs(size - v) < 2.6f
                        val h = rememberHaptics()
                        Txt(
                            label, Type.setting.copy(fontSize = 16.sp, color = if (chosen) Ink.White else Ink.Faint, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                            modifier = Modifier.press { h.click(); size = v; s.lyricsFontSize = v.sp }.padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF141416)))
            }
            Txt(
                "yrs. music has no internet permission.\nBased on Lotus by dn0ne · GPL-3.0",
                Type.small, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp),
            )
        }
    }
}

// ---------- folders ----------

@Composable
fun FoldersList(ui: Ui, find: suspend () -> Set<String>) {
    val s = ui.vm.settings
    val music by s.scanMusicFolder.collectAsState()
    val extra by s.extraScanFolders.collectAsState()
    var seen by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(Unit) { seen = withContext(Dispatchers.IO) { runCatching { find() }.getOrDefault(emptySet()) } }
    val musicPath = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.path.orEmpty() }
    val all = remember(seen, extra) {
        (seen.orEmpty() + extra).filter { it.isNotBlank() && it != musicPath && !it.startsWith("$musicPath/") }.sortedBy { it.lowercase() }
    }
    fun where(path: String): String {
        val parent = path.substringBeforeLast('/', "")
        val phone = "/storage/emulated/0"
        return when {
            parent == phone -> "Phone"
            parent.startsWith("$phone/") -> "Phone / " + parent.removePrefix("$phone/").replace("/", " / ")
            parent.startsWith("/storage/") -> "Card / " + parent.removePrefix("/storage/").substringAfter('/', "").replace("/", " / ").ifEmpty { "" }.trimEnd(' ', '/')
            else -> parent
        }.trimEnd(' ', '/')
    }
    SetRow("Music", "Phone / Music", if (music) "On" else "Off", music) { s.updateScanMusicFolder(!music); ui.vm.rereadLibrary() }
    all.forEach { path ->
        val on = path in extra
        SetRow(path.substringAfterLast('/'), where(path), if (on) "On" else "Off", on) {
            s.updateExtraScanFolders(if (on) extra - path else extra + path)
            ui.vm.rereadLibrary()
        }
    }
    if (seen == null) Txt("Looking for folders with music…", Type.small, modifier = Modifier.padding(24.dp))
}

@Composable
fun FoldersPage(ui: Ui, bottom: Dp, find: suspend () -> Set<String>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item { PageTop(ui) }
        item {
            Txt("Folders", Type.page, modifier = Modifier.padding(start = 24.dp, top = 14.dp))
            Txt(
                "yrs. music shows the songs inside the folders that are on.",
                Type.body, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
            )
            FoldersList(ui, find)
            Row(Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) {
                Act("Add a folder", color = LocalAccent.current) { ui.outside.pickFolder() }
                Spacer(Modifier.width(24.dp))
                Act("Look again", color = Ink.Mute) { ui.vm.refreshLibrary() }
            }
        }
    }
}
