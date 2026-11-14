package app.years

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize

/** Every widget is one crisp bitmap drawn at the widget's exact size, so it looks identical to the app. */
abstract class DotsWidget(private val kind: WidgetKind) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val description = Render.describe(context, kind)
        provideContent {
            val size = LocalSize.current
            val bmp = Render.widget(context, kind, size.width.value, size.height.value)
            Image(
                provider = ImageProvider(bmp),
                contentDescription = description,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
            )
        }
    }
}

class LifeWidget : DotsWidget(WidgetKind.LIFE)
class NumberWidget : DotsWidget(WidgetKind.NUMBER)
class DecadeWidget : DotsWidget(WidgetKind.DECADE)
class YearWidget : DotsWidget(WidgetKind.YEAR)

class LifeWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = LifeWidget() }
class NumberWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NumberWidget() }
class DecadeWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DecadeWidget() }
class YearWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = YearWidget() }

object Widgets {
    suspend fun updateAll(c: Context) {
        runCatching { LifeWidget().updateAll(c) }
        runCatching { NumberWidget().updateAll(c) }
        runCatching { DecadeWidget().updateAll(c) }
        runCatching { YearWidget().updateAll(c) }
    }
}
