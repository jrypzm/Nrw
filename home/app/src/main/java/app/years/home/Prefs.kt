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

    private fun bool(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun setBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    private fun json(k: String) = runCatching { JSONObject(sp.getString(k, "{}")!!) }.getOrDefault(JSONObject())

    /** The Hour: notifications that aren't from people wait for these times (minutes after midnight). */
    var hourOn: Boolean get() = bool("hourOn", false); set(v) = setBool("hourOn", v)
    var hourTimes: List<Int>
        get() = sp.getString("hourTimes", "780,1140")!!.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0 until 1440 }.sorted().ifEmpty { listOf(780, 1140) }
        set(v) = sp.edit().putString("hourTimes", v.joinToString(",")).apply()
    /** Apps (by package) that always get through, Hour or not. */
    var hourAlways: Set<String> get() = sp.getStringSet("hourAlways", emptySet())!!.toSet(); set(v) = sp.edit().putStringSet("hourAlways", v).apply()
    /** What The Hour is holding and what it has handed back, as JSON, so nothing is lost if Android restarts us. */
    var hourHeld: String get() = sp.getString("hourHeld", "[]")!!; set(v) = sp.edit().putString("hourHeld", v).apply()
    var hourBatch: String get() = sp.getString("hourBatch", "[]")!!; set(v) = sp.edit().putString("hourBatch", v).apply()

    var glow: Boolean get() = bool("glow", true); set(v) = setBool("glow", v)
    var peopleHome: Boolean get() = bool("peopleHome", true); set(v) = setBool("peopleHome", v)
    var live: Boolean get() = bool("live", true); set(v) = setBool("live", v)
    var away: Boolean get() = bool("away", true); set(v) = setBool("away", v)
    var tidy: Boolean get() = bool("tidy", true); set(v) = setBool("tidy", v)
    var strict: Boolean get() = bool("strict", false); set(v) = setBool("strict", v)
    var headphones: Boolean get() = bool("headphones", true); set(v) = setBool("headphones", v)

    /** Moments: a home for each part of the day. */
    var momentsOn: Boolean get() = bool("momentsOn", false); set(v) = setBool("momentsOn", v)
    fun moment(name: String): List<String> = sp.getString("m_$name", null)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
    fun setMoment(name: String, v: List<String>) = sp.edit().putString("m_$name", v.joinToString("\n")).apply()

    /** When yrs. home first ran: nothing counts as "quiet" until it has watched for a month. */
    val firstRun: Long get() = sp.getLong("firstRun", 0L).takeIf { it > 0 } ?: System.currentTimeMillis().also { sp.edit().putLong("firstRun", it).apply() }
    /** The last time each app was opened from here. */
    fun lastOpen(key: String): Long = json("lastOpen").optLong(key, 0L)
    fun lastOpens(): Map<String, Long> = json("lastOpen").let { o -> o.keys().asSequence().associateWith { o.optLong(it) } }

    /** When you last looked at each app's news, so its word stops glowing. */
    fun seen(pkg: String): Long = json("seen").optLong(pkg, 0L)
    fun markSeen(pkg: String) { val o = json("seen"); o.put(pkg, System.currentTimeMillis()); sp.edit().putString("seen", o.toString()).apply() }

    /** Away time: when the screen went off, and the longest break each day (minutes). */
    var offAt: Long get() = sp.getLong("offAt", 0L); set(v) = sp.edit().putLong("offAt", v).apply()
    var lastAway: Long get() = sp.getLong("lastAway", 0L); set(v) = sp.edit().putLong("lastAway", v).apply()
    var lastAwayAt: Long get() = sp.getLong("lastAwayAt", 0L); set(v) = sp.edit().putLong("lastAwayAt", v).apply()
    var awayShown: Long get() = sp.getLong("awayShown", 0L); set(v) = sp.edit().putLong("awayShown", v).apply()
    fun awayLog(): Map<String, Int> = json("awayLog").let { o -> o.keys().asSequence().associateWith { o.optInt(it) } }
    fun logAway(day: String, minutes: Int) {
        val o = json("awayLog"); if (minutes > o.optInt(day)) o.put(day, minutes)
        // keep two weeks
        val keep = LocalDate.now().minusDays(14).toString(); o.keys().asSequence().toList().filter { it < keep }.forEach { o.remove(it) }
        sp.edit().putString("awayLog", o.toString()).apply()
    }

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
        val lo = json("lastOpen"); lo.put(key, System.currentTimeMillis()); sp.edit().putString("lastOpen", lo.toString()).apply()
    }

    companion object {
        @Volatile private var one: Prefs? = null
        fun get(c: Context): Prefs = one ?: synchronized(this) { one ?: Prefs(c.applicationContext.getSharedPreferences("home", Context.MODE_PRIVATE)).also { one = it } }
    }
}
