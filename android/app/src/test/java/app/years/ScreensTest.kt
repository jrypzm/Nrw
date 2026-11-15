package app.years

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Real renders of every screen, attached to each release so the UI is checked by eye before shipping. */
class ScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.NoActionBar")

    init { Render.still = true }

    private val me = Settings(
        birth = LocalDate.of(2010, 1, 2), span = 80,
        names = mapOf(12 to "Moved to Dubai"),
        people = listOf(Person("Mum", 1976, Often.DAILY), Person("Grandpa", 1946, Often.SEASONAL)),
        events = listOf(Event("Trip to Japan", LocalDate.now().plusDays(43))),
        tourDone = true,
    )

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = darkColorScheme(background = C.Bg, surface = C.Surface, primary = C.Fg, onPrimary = Color.Black, onSurface = C.Fg)) {
            Box(Modifier.fillMaxSize().background(C.Bg)) { content() }
        }
    }

    private fun onboarding(step: Int) = paparazzi.snapshot {
        Frame { Onboarding(AppState(LocalContext.current, Settings(), persist = false), step, LocalDate.of(2010, 1, 2), animate = false) }
    }

    private fun home(s: Settings, tourAt: Int? = null) = paparazzi.snapshot {
        Frame { Home(AppState(LocalContext.current, s, persist = false), null, tourAt) {} }
    }

    @Test fun onboarding0_welcome() = onboarding(0)
    @Test fun onboarding1_birthday() = onboarding(1)
    @Test fun onboarding2_lifespan() = onboarding(2)
    @Test fun onboarding3_reveal() = onboarding(3)

    @Test fun home0_life() = home(me.copy(page = 0))
    @Test fun home0_life_large() = home(me.copy(page = 0, style = NumberStyle.HERO))
    @Test fun home0_life_live_days() = home(me.copy(page = 0, style = NumberStyle.LIVE, unit = 1))
    @Test fun home0_zoom_history() = home(me.copy(page = 0, zoom = 0))
    @Test fun home0_zoom_weeks() = home(me.copy(page = 0, zoom = 3))
    @Test fun home0_zoom_days() = home(me.copy(page = 0, zoom = 4))
    @Test fun home1_decade() = home(me.copy(page = 1))
    @Test fun home2_year() = home(me.copy(page = 2))
    @Test fun home3_people() = home(me.copy(page = 3))
    @Test fun home4_ledger() = home(me.copy(page = 4))

    @Test fun tour0_welcome() = home(me.copy(page = 0, tourDone = false), 0)
    @Test fun tour1_number() = home(me.copy(page = 0, tourDone = false), 1)
    @Test fun tour3_grid() = home(me.copy(page = 0, tourDone = false), 3)
    @Test fun tour5_zoom() = home(me.copy(page = 0, tourDone = false), 5)
    @Test fun tour6_lived() = home(me.copy(page = 0, tourDone = false), 6)
    @Test fun tour8_pages() = home(me.copy(page = 0, tourDone = false), 8)
    @Test fun tour9_settings() = home(me.copy(page = 0, tourDone = false), 9)
    @Test fun about() = paparazzi.snapshot {
        Frame { Column(Modifier.padding(horizontal = 24.dp).background(C.Surface)) { AboutContent {} } }
    }
    @Test fun guide() = paparazzi.snapshot {
        Frame { Column(Modifier.padding(horizontal = 24.dp).background(C.Surface)) { GuideContent({}, {}) } }
    }

    @Test fun story_history() = paparazzi.snapshot {
        Frame { Column(Modifier.padding(horizontal = 24.dp).background(C.Sheet)) { FactContent(me, 2000) {} } }
    }
    @Test fun story_you() = paparazzi.snapshot {
        Frame { Column(Modifier.padding(horizontal = 24.dp).background(C.Sheet)) { FactContent(me, Life.LIFETIMES - 1) {} } }
    }
    private fun est(step: Int, saved: String? = "DEU;m;smoke=3,move=2,drink=1,diet=0,sleep=1,social=0,diab=0") = paparazzi.snapshot {
        Frame { Column(Modifier.background(C.Sheet).padding(horizontal = 24.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) { EstimateBody(EstState(saved, false, step), me.birth!!) {} } }
    }
    @Test fun estimate0_you() = est(0)
    @Test fun estimate1_question() = est(1)
    @Test fun estimate3_bmi() = est(3)
    @Test fun estimate9_result() = est(9)
    @Test fun home4_ledger_set() = home(me.copy(page = 4, sleep = 7f, work = 50f, commute = 90f))
    @Test fun home0_life_pct() = home(me.copy(page = 0, pct = true, lived = true))
    private fun intro(at: Float) = paparazzi.snapshot {
        Frame {
            Box(Modifier.fillMaxSize()) {
                Home(AppState(LocalContext.current, me.copy(page = 0), persist = false), null, null) {}
                Intro({ androidx.compose.ui.geometry.Offset(560f, 1080f) to 30f }, .2f, .4f, frozenAt = at) {}
            }
        }
    }
    @Test fun intro1_rise() = intro(.18f)
    @Test fun intro2_fill() = intro(.42f)
    @Test fun intro3_dissolve() = intro(.6f)
    @Test fun intro4_fly() = intro(.72f)
    @Test fun intro5_open() = intro(.84f)
    @Test fun intro6_end() = intro(.97f)
    @Test fun widgets() = paparazzi.snapshot {
        Frame {
            val ctx = LocalContext.current
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(Render.widget(ctx, WidgetKind.NUMBER, 150f, 150f, me).asImageBitmap(), null)
                    Image(Render.widget(ctx, WidgetKind.DAYS, 150f, 150f, me).asImageBitmap(), null)
                }
                Image(Render.widget(ctx, WidgetKind.LIFE, 320f, 150f, me).asImageBitmap(), null)
                Image(Render.widget(ctx, WidgetKind.DECADE, 320f, 70f, me).asImageBitmap(), null)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(Render.widget(ctx, WidgetKind.YEAR, 150f, 150f, me).asImageBitmap(), null)
                    Image(Render.widget(ctx, WidgetKind.PEOPLE, 150f, 150f, me).asImageBitmap(), null)
                }
                Image(Render.widget(ctx, WidgetKind.PEOPLE, 320f, 150f, me).asImageBitmap(), null)
                Image(Render.widget(ctx, WidgetKind.TODAY, 320f, 120f, me).asImageBitmap(), null)
            }
        }
    }

    @Test fun wallpapers() = paparazzi.snapshot {
        Frame {
            val ctx = LocalContext.current
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WallStyle.entries.forEach { ws ->
                    Canvas(Modifier.size(96.dp, 208.dp).clipToBounds()) {
                        drawIntoCanvas { Render.wallpaper(ctx, it.nativeCanvas, size.width.toInt(), size.height.toInt(), me, ws) }
                    }
                }
            }
        }
    }
}
