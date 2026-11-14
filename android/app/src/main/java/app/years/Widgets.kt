package app.years

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize

/** Every widget is one crisp bitmap drawn at the widget's exact size, so it looks identical to the app. */
abstract class DotsWidget(private val kind: WidgetKind) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val open = Intent(context, MainActivity::class.java).putExtra("open", kind.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        provideContent {
            // Reading the version makes every refresh redraw, even while the widget session is still alive.
            @Suppress("UNUSED_VARIABLE") val version = currentState(Widgets.VERSION)
            val size = LocalSize.current
            val bmp = Render.widget(context, kind, size.width.value, size.height.value)
            Image(
                provider = ImageProvider(bmp),
                contentDescription = Render.describe(context, kind),
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(open)),
            )
        }
    }
}

class LifeWidget : DotsWidget(WidgetKind.LIFE)
class NumberWidget : DotsWidget(WidgetKind.NUMBER)
class DecadeWidget : DotsWidget(WidgetKind.DECADE)
class YearWidget : DotsWidget(WidgetKind.YEAR)
class DaysWidget : DotsWidget(WidgetKind.DAYS)
class TodayWidget : DotsWidget(WidgetKind.TODAY)
class PeopleWidget : DotsWidget(WidgetKind.PEOPLE)

class LifeWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = LifeWidget() }
class NumberWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NumberWidget() }
class DecadeWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DecadeWidget() }
class YearWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = YearWidget() }
class DaysWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DaysWidget() }
class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodayWidget() }
class PeopleWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PeopleWidget() }

object Widgets {
    val VERSION = longPreferencesKey("v")

    suspend fun updateAll(c: Context) {
        val stamp = System.currentTimeMillis()
        val manager = GlanceAppWidgetManager(c)
        listOf(LifeWidget(), NumberWidget(), DecadeWidget(), YearWidget(), DaysWidget(), TodayWidget(), PeopleWidget()).forEach { w ->
            runCatching {
                manager.getGlanceIds(w.javaClass).forEach { id ->
                    updateAppWidgetState(c, id) { it[VERSION] = stamp }
                    w.update(c, id)
                }
            }
        }
    }
}
