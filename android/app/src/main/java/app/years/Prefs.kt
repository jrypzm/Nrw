package app.years

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class NumberStyle { CLASSIC, HERO, LIVE }
enum class WallStyle(val label: String) { GRID("Grid"), DAYLIGHT("Daylight"), HOURGLASS("Hourglass"), STARS("Stars") }
enum class WidgetLook(val label: String) { BLACK("Black"), GLASS("Glass") }

/** How often you see someone, per year. */
enum class Often(val label: String, val perYear: Double) {
    DAILY("We live together", 365.0), WEEKLY("Every week", 52.0), MONTHLY("Every month", 12.0),
    SEASONAL("A few times a year", 4.0), YEARLY("Once a year", 1.0),
}

data class Person(val name: String, val birthYear: Int, val often: Often)
data class Event(val name: String, val date: LocalDate)
data class Letter(val age: Int, val text: String, val written: LocalDate, val opened: Boolean = false)
data class LastTime(val text: String, val added: LocalDate)

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
    val zoom: Int = 1,
    val people: List<Person> = emptyList(),
    val events: List<Event> = emptyList(),
    val letters: List<Letter> = emptyList(),
    val nudge: Boolean = false,
    val reviewedAge: Int = -1,
    val habits: Set<String> = emptySet(),
    val photos: Set<Int> = emptySet(),
    val voices: Set<Int> = emptySet(),
    val lasts: List<LastTime> = emptyList(),
    val guard: Set<String> = emptySet(),
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
            zoom = p.getInt("zoom2", 1),
            people = arr(p.getString("people", null)) { o -> Person(o.getString("n"), o.getInt("y"), enumOr(o.optString("o"), Often.SEASONAL)) },
            events = arr(p.getString("events", null)) { o -> Event(o.getString("n"), LocalDate.parse(o.getString("d"))) },
            letters = arr(p.getString("letters", null)) { o -> Letter(o.getInt("a"), o.getString("t"), LocalDate.parse(o.getString("w")), o.optBoolean("o", false)) },
            nudge = p.getBoolean("nudge", false),
            reviewedAge = p.getInt("reviewedAge", -1),
            habits = p.getStringSet("habits", emptySet())?.toSet() ?: emptySet(),
            photos = p.getStringSet("photos", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
            voices = p.getStringSet("voices", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
            lasts = arr(p.getString("lasts", null)) { o -> LastTime(o.getString("t"), LocalDate.parse(o.getString("d"))) },
            guard = p.getStringSet("guard", emptySet())?.toSet() ?: emptySet(),
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
            .putInt("zoom2", s.zoom)
            .putString("people", JSONArray(s.people.map { JSONObject().put("n", it.name).put("y", it.birthYear).put("o", it.often.name) }).toString())
            .putString("events", JSONArray(s.events.map { JSONObject().put("n", it.name).put("d", it.date.toString()) }).toString())
            .putString("letters", JSONArray(s.letters.map { JSONObject().put("a", it.age).put("t", it.text).put("w", it.written.toString()).put("o", it.opened) }).toString())
            .putBoolean("nudge", s.nudge)
            .putInt("reviewedAge", s.reviewedAge)
            .putStringSet("habits", s.habits)
            .putStringSet("photos", s.photos.map { it.toString() }.toSet())
            .putStringSet("voices", s.voices.map { it.toString() }.toSet())
            .putString("lasts", JSONArray(s.lasts.map { JSONObject().put("t", it.text).put("d", it.added.toString()) }).toString())
            .putStringSet("guard", s.guard)
            .apply()
    }

    fun getString(c: Context, key: String): String? = sp(c).getString(key, null)
    fun putString(c: Context, key: String, value: String) = sp(c).edit().putString(key, value).apply()

    private fun <T> arr(json: String?, f: (JSONObject) -> T): List<T> = runCatching {
        val a = JSONArray(json ?: "[]")
        (0 until a.length()).mapNotNull { i -> runCatching { f(a.getJSONObject(i)) }.getOrNull() }
    }.getOrDefault(emptyList())

    private inline fun <reified T : Enum<T>> enumOr(v: String?, d: T): T =
        runCatching { enumValueOf<T>(v!!) }.getOrDefault(d)
}

/** One line a day, keyed by ISO date. Kept apart from settings because it grows. */
object Lines {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("years-lines", Context.MODE_PRIVATE)
    fun all(c: Context): Map<String, String> = runCatching { sp(c).all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap() }.getOrDefault(emptyMap())
    fun get(c: Context, d: LocalDate): String? = runCatching { sp(c).getString(d.toString(), null) }.getOrNull()
    fun put(c: Context, d: LocalDate, text: String) {
        val e = sp(c).edit()
        if (text.isBlank()) e.remove(d.toString()) else e.putString(d.toString(), text.trim().take(140))
        e.apply()
    }
}
