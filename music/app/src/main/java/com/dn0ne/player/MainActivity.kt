// yrs. music: modified from Lotus by dn0ne (GPL-3.0).
package com.dn0ne.player

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.dn0ne.player.app.data.repository.TrackRepository
import com.dn0ne.player.app.presentation.PlayerViewModel
import com.dn0ne.player.core.data.MusicScanner
import com.dn0ne.player.setup.data.SetupState
import com.dn0ne.player.ui.FoldersList
import com.dn0ne.player.ui.MusicApp
import com.dn0ne.player.ui.Outside
import com.dn0ne.player.ui.Pins
import com.dn0ne.player.ui.Welcome
import com.dn0ne.player.ui.WelcomeUi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.getViewModel

class MainActivity : ComponentActivity() {
    private var controller: ListenableFuture<MediaController>? = null
    private var granted by mutableStateOf(false)
    private var ready by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        preferFastestRefresh()

        SingletonImageLoader.setSafe {
            ImageLoader.Builder(applicationContext)
                .crossfade(220)
                .memoryCache { MemoryCache.Builder().maxSizePercent(applicationContext, 0.25).build() }
                .build()
        }

        val vm = getViewModel<PlayerViewModel>()
        if (vm.player == null) {
            val token = SessionToken(application, ComponentName(application, PlaybackService::class.java))
            val future = MediaController.Builder(application, token).buildAsync()
            controller = future
            future.addListener({ runCatching { vm.player = future.get() } }, MoreExecutors.directExecutor())
        }
        val setup = get<SetupState>()
        val pins = Pins(applicationContext)
        val scanner = get<MusicScanner>()
        granted = hasMusicAccess()
        ready = setup.isComplete && granted

        val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok || hasMusicAccess()
            if (granted) vm.rereadLibrary()
            if (!granted) {
                Toast.makeText(this, getString(R.string.grant_permission_in_settings), Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@registerForActivityResult
            val path = pathOf(uri)
            vm.onFolderPicked(path)
            lifecycleScope.launch { scanner.scanFolder(path); kotlinx.coroutines.delay(1500); vm.rereadLibrary() }
        }
        val pickLyrics = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }
                .getOrNull()?.let { vm.importLyrics(it) }
        }
        val pickList = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            val name = Uri.decode(uri.toString()).substringAfterLast('/').substringBeforeLast('.')
            runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }
                .getOrNull()?.let { vm.parseM3U(name, it) }
        }
        val musicPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

        val outside = object : Outside {
            override fun pickFolder() { runCatching { pickFolder.launch(null) } }
            override fun pickLyrics() { runCatching { pickLyrics.launch(arrayOf("text/plain", "application/lrc", "text/*", "application/octet-stream")) } }
            override fun pickPlaylist() { runCatching { pickList.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "application/x-mpegurl")) } }
            override fun askForMusic() { if (hasMusicAccess()) granted = true else ask.launch(musicPermission) }
        }
        val find: suspend () -> Set<String> = { get<TrackRepository>().getFoldersWithAudio() }

        if (ready && vm.settings.scanOnAppLaunch.value) lifecycleScope.launch { scanner.refreshMedia(showMessages = false) }

        setContent {
            if (ready) {
                MusicApp(vm, pins, outside, find)
            } else {
                val welcome = remember(granted) {
                    WelcomeUi(granted, ask = { outside.askForMusic() }, pick = { outside.pickFolder() }) {
                        val ui = remember { com.dn0ne.player.ui.Ui(vm, pins, outside, androidx.compose.foundation.pager.PagerState { 3 }, androidx.compose.foundation.lazy.LazyListState(), androidx.compose.foundation.lazy.grid.LazyGridState(), androidx.compose.foundation.lazy.LazyListState(), lifecycleScope) }
                        FoldersList(ui, find)
                    }
                }
                Welcome(welcome) {
                    setup.isComplete = true
                    ready = true
                    vm.rereadLibrary()
                    lifecycleScope.launch { scanner.refreshMedia(showMessages = false); kotlinx.coroutines.delay(1500); vm.rereadLibrary() }
                }
            }
        }
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        if (!granted && hasMusicAccess()) granted = true
    }

    override fun onDestroy() {
        // keep the connection across rotation; let it go only when the app is really closing
        if (isFinishing) controller?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val vm = getViewModel<PlayerViewModel>()
        MediaScannerConnection.scanFile(this, arrayOf(uri.path), arrayOf("audio/*")) { _, _ -> vm.playTrackFromUri(uri) }
    }

    private fun hasMusicAccess(): Boolean = checkSelfPermission(
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED

    /** Ask for the screen's highest refresh rate at the current resolution, so motion is never stepped. */
    @Suppress("DEPRECATION")
    private fun preferFastestRefresh() {
        runCatching {
            val display = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
            display ?: return
            val now = display.mode
            val best = display.supportedModes
                .filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
        }
    }

    private fun pathOf(uri: Uri): String {
        val decoded = Uri.decode(uri.toString())
        val volume = decoded.substringAfter("tree/").substringBefore(':').takeIf { it != "primary" } ?: "emulated/0"
        val path = decoded.substringAfterLast(':')
        return "/storage/$volume/$path".trimEnd('/')
    }
}
