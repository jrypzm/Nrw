// yrs. music: modified from Lotus by dn0ne (GPL-3.0). The whole app in one place.
package com.dn0ne.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dn0ne.player.app.presentation.PlayerViewModel
import com.dn0ne.player.app.presentation.components.snackbar.SnackbarController
import kotlinx.coroutines.launch

@Composable
fun MusicApp(vm: PlayerViewModel, pins: Pins, outside: Outside, findFolders: suspend () -> Set<String>) {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { 3 }
    val songs = rememberLazyListState()
    val albums = rememberLazyGridState()
    val lists = rememberLazyListState()
    val ui = remember { Ui(vm, pins, outside, pager, songs, albums, lists, scope) }
    val context = LocalContext.current
    val state by ui.calm.collectAsState()
    val useCover by vm.settings.useAlbumArtColor.collectAsState()
    val accent by rememberAccent(state.currentTrack, useCover)
    val position = rememberPosition(ui)
    val h = rememberHaptics()

    LaunchedEffect(Unit) { SnackbarController.events.collect { ui.say(context.getString(it.message)) } }
    LaunchedEffect(ui.closePlayer) {
        if (ui.closePlayer) { ui.closePlayer = false; ui.expand.animateTo(0f, spring(0.9f, 360f)) }
    }

    // back: a sheet, then lyrics or the queue, then the player, then the page, then Songs
    BackHandler(ui.panel != null) { ui.panel = null }
    BackHandler(ui.panel == null && ui.expand.targetValue > 0f) {
        if (ui.layer != Layer.None) ui.layer = Layer.None
        else scope.launch { ui.expand.animateTo(0f, spring(0.9f, 360f)); h.edge() }
    }
    BackHandler(ui.panel == null && ui.expand.targetValue == 0f && (ui.stack.size > 1 || ui.pager.currentPage != 0)) {
        if (!ui.back()) scope.launch { ui.pager.animateScrollToPage(0, animationSpec = spring(0.9f, 380f)) }
    }

    val d = LocalDensity.current
    val nav = with(d) { WindowInsets.navigationBars.getBottom(d).toDp() }
    val bottom: Dp = if (state.currentTrack != null) 72.dp + nav + 8.dp else nav + 16.dp

    CompositionLocalProvider(LocalAccent provides accent) {
        Box(Modifier.fillMaxSize().background(Ink.Black)) {
            AnimatedContent(
                ui.page,
                Modifier.graphicsLayer {
                    // the library steps back a little as the player rises over it
                    val k = ui.expand.value
                    alpha = 1f - 0.55f * k
                    val sc = 1f - 0.03f * k
                    scaleX = sc; scaleY = sc
                },
                transitionSpec = {
                    val dir = if (ui.forward) 1 else -1
                    (slideInHorizontally(spring(0.92f, 420f)) { dir * it / 5 } + fadeIn(tween(220, 40))) togetherWith
                        (slideOutHorizontally(spring(0.92f, 420f)) { -dir * it / 8 } + fadeOut(tween(140)))
                },
                label = "page",
            ) { page ->
                when (page) {
                    Page.Home -> Home(ui, bottom)
                    is Page.Album -> AlbumPage(ui, page.name, bottom)
                    is Page.List -> ListPage(ui, page.name, bottom)
                    Page.Search -> SearchPage(ui, bottom)
                    Page.Settings -> SettingsPage(ui, bottom)
                    Page.Folders -> FoldersPage(ui, bottom, findFolders)
                }
            }
            PlayerLayer(ui, position)
            val up by remember { derivedStateOf { ui.expand.value > 0.5f } }
            Toast(ui, if (up) nav + 120.dp else bottom)
            Panels(ui)
        }
    }
}

/** First open: say what this is, ask for the music, then where it lives. */
@Composable
fun Welcome(ui: WelcomeUi, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(if (ui.granted) 1 else 0) }
    LaunchedEffect(ui.granted) { if (ui.granted && step == 0) step = 1 }
    Box(Modifier.fillMaxSize().background(Ink.Black)) {
        AnimatedContent(step, transitionSpec = {
            (slideInHorizontally(spring(0.92f, 380f)) { it / 5 } + fadeIn(tween(240, 60))) togetherWith
                (slideOutHorizontally(spring(0.92f, 380f)) { -it / 8 } + fadeOut(tween(140)))
        }, label = "welcome") { s ->
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                if (s == 0) {
                    Column(Modifier.weight(1f).padding(horizontal = 28.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                        Logo(64.dp)
                        Spacer(Modifier.height(36.dp))
                        Txt("yrs. music", Type.page.copy(fontSize = 36.sp, lineHeight = 40.sp))
                        Spacer(Modifier.height(12.dp))
                        Txt("The songs on this phone, played beautifully.\nNothing leaves the phone. There is no internet in here.", Type.body.copy(fontSize = 16.sp, lineHeight = 24.sp), modifier = Modifier.widthIn(max = 360.dp))
                    }
                    Column(Modifier.padding(horizontal = 28.dp, vertical = 24.dp)) {
                        Txt("yrs. music needs to see your music files to play them.", Type.small)
                        Spacer(Modifier.height(6.dp))
                        Act("Allow access to music", color = Ink.Accent, style = Type.act.copy(fontSize = 17.sp)) { ui.ask() }
                    }
                } else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(40.dp))
                        Txt("Where is your music?", Type.page, modifier = Modifier.padding(horizontal = 24.dp))
                        Spacer(Modifier.height(10.dp))
                        Txt("Turn on the folders that hold songs. You can change this later in Settings.", Type.body, modifier = Modifier.padding(horizontal = 24.dp))
                        Spacer(Modifier.height(16.dp))
                        ui.folders()
                        Row(Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) {
                            Act("Add a folder", color = Ink.Mute) { ui.pick() }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp)) {
                        Spacer(Modifier.weight(1f))
                        Act("Done", color = Ink.Accent, style = Type.act.copy(fontSize = 17.sp)) { onDone() }
                    }
                }
            }
        }
    }
}

class WelcomeUi(
    val granted: Boolean,
    val ask: () -> Unit,
    val pick: () -> Unit,
    val folders: @Composable () -> Unit,
)
