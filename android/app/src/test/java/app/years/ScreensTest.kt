package app.years

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

    private val me = Settings(
        birth = LocalDate.of(2010, 1, 2), span = 80,
        names = mapOf(12 to "Moved to Dubai"),
        people = listOf(Person("Mum", 1976, Often.DAILY), Person("Grandpa", 1946, Often.SEASONAL)),
        events = listOf(Event("Trip to Japan", LocalDate.now().plusDays(43))),
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

    private fun home(s: Settings) = paparazzi.snapshot {
        Frame { Home(AppState(LocalContext.current, s, persist = false), null) {} }
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
            }
        }
    }

    @Test fun wallpapers() = paparazzi.snapshot {
        Frame {
            val ctx = LocalContext.current
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WallStyle.entries.forEach { ws ->
                    Canvas(Modifier.size(96.dp, 208.dp)) {
                        drawIntoCanvas { Render.wallpaper(ctx, it.nativeCanvas, size.width.toInt(), size.height.toInt(), me, ws) }
                    }
                }
            }
        }
    }
}
