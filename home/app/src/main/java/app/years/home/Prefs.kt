package app.years.home

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.time.LocalDate

/** Everything you choose, kept on the phone. Read once, written when it changes. */
class Prefs private constructor(private val sp: SharedPreferences) {
    /** Home apps, in order, as app keys. */
    var favorites: List<String>
        get() = sp.getString("fav", null)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
        set(v) = sp.edit().putString("fav", v.joinToString("\n")).apply()
    var favoritesSet: Boolean
        get() = sp.getBoolean("favSet", false)
        set(v) = sp.edit().putBoolean("favSet", v).apply()
    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()
    var paused: Set<String>
        get() = sp.getStringSet("paused", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("paused", v).apply()
    var renames: Map<String, String>
        get() = runCatching { JSONObject(sp.getString("renames", "{}")!!).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }.getOrDefault(emptyMap())
        set(v) = sp.edit().putString("renames", JSONObject(v).toString()).apply()
    var railLeft: Boolean
        get() = sp.getBoolean("railLeft", false)
        set(v) = sp.edit().putBoolean("railLeft", v).apply()
    var fontPath: String?
        get() = sp.getString("font", null)
        set(v) = sp.edit().putString("font", v).apply()
    var calendar: Boolean
        get() = sp.getBoolean("calendar", true)
        set(v) = sp.edit().putBoolean("calendar", v).apply()
    var people: Boolean
        get() = sp.getBoolean("people", true)
        set(v) = sp.edit().putBoolean("people", v).apply()

    /** How many times each app was opened from here today. Counted by the launcher itself, no permission needed. */
    fun opensToday(key: String): Int {
        val o = runCatching { JSONObject(sp.getString("opens", "{}")!!) }.getOrDefault(JSONObject())
        return if (o.optString("day") == LocalDate.now().toString()) o.optJSONObject("n")?.optInt(key) ?: 0 else 0
    }
    fun countOpen(key: String) {
        val today = LocalDate.now().toString()
        val o = runCatching { JSONObject(sp.getString("opens", "{}")!!) }.getOrDefault(JSONObject())
        val n = if (o.optString("day") == today) o.optJSONObject("n") ?: JSONObject() else JSONObject()
        n.put(key, n.optInt(key) + 1)
        sp.edit().putString("opens", JSONObject().put("day", today).put("n", n).toString()).apply()
    }

    companion object {
        @Volatile private var one: Prefs? = null
        fun get(c: Context): Prefs = one ?: synchronized(this) { one ?: Prefs(c.applicationContext.getSharedPreferences("home", Context.MODE_PRIVATE)).also { one = it } }
    }
}
