package app.years

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

enum class NumberStyle { CLASSIC, HERO, LIVE }
enum class WallStyle(val label: String) { GRID("Grid"), DAYLIGHT("Daylight"), HOURGLASS("Hourglass"), STARS("Stars") }
enum class WidgetLook(val label: String) { BLACK("Black"), GLASS("Glass") }

data class Settings(
    val birth: LocalDate? = null,
    val span: Int = 80,
    val lived: Boolean = false,
    val pct: Boolean = false,
    val style: NumberStyle = NumberStyle.CLASSIC,
    val sun: Boolean = false,
    val ripple: Boolean = true,
    val names: Map<Int, String> = emptyMap(),
    val wall: WallStyle = WallStyle.DAYLIGHT,
    val autoLock: Boolean = false,
    val widgetLook: WidgetLook = WidgetLook.BLACK,
    val unit: Int = 0,
    val page: Int = 0,
)

object Prefs {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("years", Context.MODE_PRIVATE)

    fun load(c: Context): Settings {
        val p = sp(c)
        val names = mutableMapOf<Int, String>()
        runCatching {
            val o = JSONObject(p.getString("names", "{}") ?: "{}")
            o.keys().forEach { k -> names[k.toInt()] = o.getString(k) }
        }
        return Settings(
            birth = p.getString("birth", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            span = p.getInt("span", 80),
            lived = p.getBoolean("lived", false),
            pct = p.getBoolean("pct", false),
            style = enumOr(p.getString("style", null), NumberStyle.CLASSIC),
            sun = p.getBoolean("sun", false),
            ripple = p.getBoolean("ripple", true),
            names = names,
            wall = enumOr(p.getString("wall", null), WallStyle.DAYLIGHT),
            autoLock = p.getBoolean("autoLock", false),
            widgetLook = enumOr(p.getString("widgetLook", null), WidgetLook.BLACK),
            unit = p.getInt("unit", 0),
            page = p.getInt("page", 0),
        )
    }

    fun save(c: Context, s: Settings) {
        val o = JSONObject()
        s.names.forEach { (k, v) -> o.put(k.toString(), v) }
        sp(c).edit()
            .putString("birth", s.birth?.toString())
            .putInt("span", s.span)
            .putBoolean("lived", s.lived)
            .putBoolean("pct", s.pct)
            .putString("style", s.style.name)
            .putBoolean("sun", s.sun)
            .putBoolean("ripple", s.ripple)
            .putString("names", o.toString())
            .putString("wall", s.wall.name)
            .putBoolean("autoLock", s.autoLock)
            .putString("widgetLook", s.widgetLook.name)
            .putInt("unit", s.unit)
            .putInt("page", s.page)
            .apply()
    }

    fun getString(c: Context, key: String): String? = sp(c).getString(key, null)
    fun putString(c: Context, key: String, value: String) = sp(c).edit().putString(key, value).apply()

    private inline fun <reified T : Enum<T>> enumOr(v: String?, d: T): T =
        runCatching { enumValueOf<T>(v!!) }.getOrDefault(d)
}
