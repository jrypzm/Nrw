package com.dn0ne.player

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.dn0ne.player.app.data.LyricsReader
import com.dn0ne.player.app.data.SavedPlayerState
import com.dn0ne.player.app.data.remote.OfflineLyricsProvider
import com.dn0ne.player.app.data.remote.OfflineMetadataProvider
import com.dn0ne.player.app.data.repository.LyricsRepository
import com.dn0ne.player.app.data.repository.PlaylistRepository
import com.dn0ne.player.app.data.repository.TrackRepository
import com.dn0ne.player.app.domain.lyrics.Lyrics
import com.dn0ne.player.app.domain.result.DataError
import com.dn0ne.player.app.domain.result.Result
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.presentation.LOVED
import com.dn0ne.player.app.presentation.PlayerScreenEvent
import com.dn0ne.player.app.presentation.PlayerViewModel
import com.dn0ne.player.core.data.MusicScanner
import com.dn0ne.player.core.data.Settings
import com.dn0ne.player.ui.MusicApp
import com.dn0ne.player.ui.Outside
import com.dn0ne.player.ui.Pins
import com.dn0ne.player.ui.Welcome
import com.dn0ne.player.ui.WelcomeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Real renders of every screen at a tall phone's size, driven through the real view model. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi", application = Application::class)
abstract class Screens {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val albums = listOf(
        Triple("Slow Weather", "Mara Vell", "fog") to listOf("Lantern Hours", "Porch Light", "Slow Weather", "The Long Way", "Paper Moons", "Northbound", "Glass Harbour", "Undertow", "Small Hours", "Kept", "Afterglow"),
        Triple("Bloom", "Juno Park", "bloom") to listOf("Open Window", "Petals", "Sunday Again", "Bloom"),
        Triple("Concrete Gardens", "The Low Hours", "concrete") to listOf("Grey Rooms", "Overpass", "Weeds"),
        Triple("Dunes", "Sol Arden", "dunes") to listOf("Heat Lines", "Mirage", "Caravan"),
        Triple("Embers", "Kite & Coast", "ember") to listOf("Kindling", "Slow Burn", "Ash Road"),
        Triple("Fern Hollow", "Ada Lowe", "fern") to listOf("Underleaf", "Rain on Moss"),
        Triple("Night Swim", "Oceanic", "ocean") to listOf("Tidepool", "Night Swim", "Salt"),
        Triple("Moonrise", "Mara Vell", "moon") to listOf("Moonrise", "Silver"),
        Triple("Snowfield", "Halden", "snow") to listOf("White Out", "Breath"),
        Triple("Haze", "Lumen Drift", "haze") to listOf("Haze", "Soft Focus"),
    )

    protected fun cover(name: String): Uri {
        val f = File.createTempFile(name, ".jpg")
        javaClass.getResourceAsStream("/covers/$name.jpg")!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        return Uri.fromFile(f)
    }

    protected fun tracks(): List<Track> {
        var id = 0
        return albums.flatMap { (a, titles) ->
            val art = cover(a.third)
            titles.mapIndexed { i, t ->
                id++
                val uri = Uri.parse("content://media/external/audio/media/$id")
                Track(
                    uri = uri, mediaItem = MediaItem.Builder().setUri(uri).setMediaId("$id").build(), coverArtUri = art,
                    duration = 150_000 + (id * 37_000) % 170_000, size = 30_000_000L, dateModified = 1_700_000_000L + id,
                    data = "/storage/emulated/0/Music/${a.first}/${i + 1} $t.${if (id % 3 == 0) "mp3" else "flac"}",
                    title = t, artist = a.second, album = a.first, albumArtist = a.second, year = "2024", trackNumber = "${i + 1}",
                )
            }
        }
    }

    protected class Fakes(val all: List<Track>) : TrackRepository, PlaylistRepository, LyricsRepository, LyricsReader {
        val lists = MutableStateFlow(
            listOf(
                Playlist(LOVED, all.filterIndexed { i, _ -> i % 5 == 0 }),
                Playlist("Late drives", all.filterIndexed { i, _ -> i % 3 == 1 }),
                Playlist("Morning", all.filterIndexed { i, _ -> i % 4 == 2 }),
            )
        )
        override fun getTracks() = all
        override fun getFoldersWithAudio() = setOf("/storage/emulated/0/Music", "/storage/emulated/0/Download", "/storage/emulated/0/Recordings")
        override fun getPlaylists() = lists
        override suspend fun insertPlaylist(playlist: Playlist) = lists.update { it + playlist }
        override suspend fun updatePlaylistTrackList(playlist: Playlist, trackList: List<Track>) = lists.update { l -> l.map { if (it.name == playlist.name) it.copy(trackList = trackList) else it } }
        override suspend fun renamePlaylist(playlist: Playlist, name: String) = lists.update { l -> l.map { if (it.name == playlist.name) it.copy(name = name) else it } }
        override suspend fun deletePlaylist(playlist: Playlist) = lists.update { l -> l.filter { it.name != playlist.name } }
        override fun getLyricsByUri(uri: String): Lyrics? = if (uri == all.first().uri.toString()) Lyrics(
            uri, areFromRemote = false, synced = listOf(
                0 to "We left the porch light on", 4000 to "for the hours that never came",
                9000 to "and the lantern kept on burning", 14000 to "like it knew the way.",
                19000 to "Slow weather, slow weather", 24000 to "carry me home",
            ),
        ) else null
        override suspend fun insertLyrics(lyrics: Lyrics) {}
        override suspend fun updateLyrics(lyrics: Lyrics) {}
        override suspend fun deleteLyrics(lyrics: Lyrics) {}
        override fun readFromTag(track: Track): Result<Lyrics?, DataError.Local> = Result.Error(DataError.Local.FailedToRead)
    }

    protected val outside = object : Outside {
        override fun pickFolder() {}
        override fun pickLyrics() {}
        override fun pickPlaylist() {}
        override fun askForMusic() {}
    }

    protected fun model(all: List<Track>): Pair<PlayerViewModel, Fakes> {
        val ctx = rule.activity.applicationContext
        val f = Fakes(all)
        val settings = Settings(ctx)
        val vm = PlayerViewModel(
            savedPlayerState = SavedPlayerState(ctx), trackRepository = f, metadataProvider = OfflineMetadataProvider(),
            lyricsProvider = OfflineLyricsProvider(), lyricsRepository = f, lyricsReader = f, playlistRepository = f,
            unsupportedArtworkEditFormats = emptyList(), settings = settings, musicScanner = MusicScanner(ctx, settings),
            equalizerController = EqualizerController(ctx),
        )
        return vm to f
    }

    protected fun settle(ms: Long = 900) {
        var left = ms
        while (left > 0) {
            rule.mainClock.advanceTimeBy(50)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(8)
            left -= 50
        }
    }

    protected fun shot(name: String, wait: Long = 300) {
        settle(wait)
        val v: View = rule.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(b))
        File("build/screens").mkdirs()
        File("build/screens/$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** The whole app with a library: every page, the player, lyrics, menus and sheets. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi", application = Application::class)
class ScreensTest : Screens() {
    @Test fun screens() {
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { rule.activity.actionBar?.hide() }
        val all = tracks()
        val (vm, _) = model(all)
        val pins = Pins(rule.activity.applicationContext)
        pins.toggle(Pins.album("Bloom")); pins.toggle(Pins.list("Late drives")); pins.toggle(Pins.album("Slow Weather"))
        rule.setContent { MusicApp(vm, pins, outside) { setOf("/storage/emulated/0/Music", "/storage/emulated/0/Download") } }
        settle(2500)
        shot("01-songs")

        rule.runOnUiThread { vm.player = ExoPlayer.Builder(rule.activity).setUsePlatformDiagnostics(false).build() }
        settle(800)
        rule.runOnUiThread { vm.onEvent(PlayerScreenEvent.OnTrackClick(all.first(), Playlist(null, all))) }
        settle(1500)
        shot("02-songs-playing")

        rule.onNodeWithContentDescription("Albums").performClick(); settle(1500); shot("03-albums")
        rule.onNodeWithContentDescription("Playlists").performClick(); settle(1500); shot("04-playlists")
        rule.onNodeWithContentDescription("Songs").performClick(); settle(1200)

        rule.onNodeWithContentDescription("Now playing", substring = true).performClick()
        settle(100); shot("05a-opening", 0)
        settle(1500); shot("05-player")

        rule.onNodeWithText("Lyrics").performClick(); settle(1200)
        rule.runOnUiThread { vm.onEvent(PlayerScreenEvent.OnSeekTo(9500)) }
        settle(1200); shot("06-lyrics")
        rule.onNodeWithContentDescription("Close lyrics").performClick(); settle(900)

        rule.onNodeWithText("Up next").performClick(); settle(1200); shot("07-up-next")
        rule.onNodeWithContentDescription("Close up next").performClick(); settle(900)
        rule.onNodeWithContentDescription("Close player").performClick(); settle(1500)
        shot("08-closed")

        rule.onNodeWithContentDescription("More").performClick(); settle(900); shot("09-menu")
        rule.onNodeWithText("Settings").performClick(); settle(1200); shot("10-settings")
        rule.onNodeWithText("Folders").performClick(); settle(1500); shot("11-folders")
        rule.onNodeWithContentDescription("Back").performClick(); settle(900)
        rule.onNodeWithContentDescription("Back").performClick(); settle(900)

        rule.onAllNodesWithText("Slow Weather").onFirst().performClick(); settle(1500); shot("12-album")
        rule.onNodeWithContentDescription("Back").performClick(); settle(900)
        rule.onNodeWithText("Late drives").performClick(); settle(1500); shot("13-playlist")
        rule.onNodeWithText("Edit").performClick(); settle(900); shot("14-playlist-edit")
        rule.onNodeWithText("Done").performClick(); settle(600)
        rule.onNodeWithContentDescription("Back").performClick(); settle(900)

        rule.onNodeWithContentDescription("More").performClick(); settle(900)
        rule.onNodeWithText("Search").performClick(); settle(900)
        rule.onNodeWithContentDescription("Search").performTextInput("mara"); settle(1200); shot("15-search")
        rule.onNodeWithContentDescription("Back").performClick(); settle(900)

        rule.onAllNodesWithText("Afterglow").onFirst().performTouchInput { longClick() }; settle(1200); shot("16-song-sheet")
        rule.onNodeWithText("Add to playlist").performClick(); settle(900); shot("17-add-to")
        rule.onNodeWithText("New playlist").performClick(); settle(900); shot("18-name")
        assertTrue(File("build/screens/18-name.png").exists())
    }
}

/** No music yet: the calm empty pages. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi", application = Application::class)
class EmptyTest : Screens() {
    @Test fun empty() {
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { rule.activity.actionBar?.hide() }
        val (vm, _) = model(emptyList())
        val pins = Pins(rule.activity.applicationContext)
        rule.setContent { MusicApp(vm, pins, outside) { emptySet() } }
        settle(2600); shot("20-empty")
        rule.onNodeWithContentDescription("Albums").performClick(); settle(1200); shot("21-empty-albums")
        rule.onNodeWithContentDescription("Playlists").performClick(); settle(1200); shot("22-empty-playlists")
    }
}

/** First open. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi", application = Application::class)
class WelcomeTest : Screens() {
    @Test fun welcome() {
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { rule.activity.actionBar?.hide() }
        rule.setContent { Welcome(WelcomeUi(false, {}, {}) {}) {} }
        settle(800); shot("23-welcome")
    }
}
