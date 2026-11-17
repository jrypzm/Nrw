// yrs. music: modified from Lotus by dn0ne (GPL-3.0). Where you are, what is open.
package com.dn0ne.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.presentation.PlayerViewModel
import com.dn0ne.player.app.presentation.components.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface Page {
    data object Home : Page
    data class Album(val name: String?) : Page
    data class List(val name: String) : Page
    data object Search : Page
    data object Settings : Page
    data object Folders : Page
}

sealed interface Panel {
    data object Menu : Panel
    data object PlayerMenu : Panel
    data class Song(val track: Track, val from: Playlist? = null) : Panel
    data class AddTo(val tracks: kotlin.collections.List<Track>) : Panel
    data class Name(val title: String, val start: String, val done: (String) -> Unit) : Panel
    data object Sort : Panel
    data object Sleep : Panel
    data class AlbumMenu(val name: String?) : Panel
    data class ListMenu(val playlist: Playlist) : Panel
    data class Confirm(val title: String, val action: String, val done: () -> Unit) : Panel
}

enum class Layer { None, Lyrics, UpNext }

/** Things only the activity can do: system pickers and permission prompts. */
interface Outside {
    fun pickFolder()
    fun pickLyrics()
    fun pickPlaylist()
    fun askForMusic()
}

class Ui(
    val vm: PlayerViewModel,
    val pins: Pins,
    val outside: Outside,
    val pager: PagerState,
    val songs: LazyListState,
    val albums: LazyGridState,
    val lists: LazyListState,
    scope: CoroutineScope,
) {
    /** Playback without the ticking position, so the whole app is not redrawn twenty times a second. */
    val calm: StateFlow<PlaybackState> = vm.playbackState
        .map { it.copy(position = 0L) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, PlaybackState())

    val stack = mutableStateListOf<Page>(Page.Home)
    var forward by mutableStateOf(true)
    var panel by mutableStateOf<Panel?>(null)
    var toast by mutableStateOf<String?>(null)
    var layer by mutableStateOf(Layer.None)
    /** Set when something asks the player to step aside (e.g. Go to album). */
    var closePlayer by mutableStateOf(false)
    val expand = Animatable(0f)

    val page: Page get() = stack.last()

    fun go(p: Page) {
        panel = null
        if (stack.last() == p) return
        forward = true
        stack.add(p)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        forward = false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun home() {
        forward = false
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun say(text: String) { toast = text }
}
