package app.years.home

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Search that reads what you mean. A time offers an alarm, a length a timer, a number a call, ".yt lofi" a search
 * inside YouTube, "days to 25 dec" a count. Pure functions of the text and the clock, so every case is tested.
 */
object Smart {
    const val ALARM = "alarm"; const val TIMER = "timer"; const val CALL = "call"; const val TEXT = "text"; const val SAVE = "save"
    const val INSIDE = "inside"; const val DATE = "date"; const val LIGHT = "light"

    /** Never throws: whatever you type, search carries on. */
    fun actions(q: String, now: LocalDateTime = LocalDateTime.now(), h24: Boolean = false): List<Hit.Action> = runCatching { read(q, now, h24) }.getOrDefault(emptyList())

    private fun read(q: String, now: LocalDateTime, h24: Boolean): List<Hit.Action> {
        val t = q.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return emptyList()
        val out = ArrayList<Hit.Action>()
        inside(q.trim())?.let { return listOf(it) }
        out += alarms(t, now, h24)
        timer(t)?.let { out += it }
        out += phone(q.trim())
        date(t, now.toLocalDate())?.let { out += it }
        if (t.length >= 3 && listOf("light", "torch", "flashlight", "flash").any { it.startsWith(t) }) out += Hit.Action(LIGHT, "Light", "Light", "Turn the torch on or off", false)
        return out
    }

    // ------------------------------------------------------------------ alarms: "7:30", "7pm", "alarm 6", "wake me at 6:45"

    private val timeRx = Regex("^(?:(alarm|wake(?: me)?(?: up)?)(?: at| for)? )?(\\d{1,2})(?:[:.](\\d{2}))? ?(am|pm|a|p)?$")

    fun alarms(t: String, now: LocalDateTime, h24: Boolean): List<Hit.Action> {
        val m = timeRx.matchEntire(t) ?: return emptyList()
        val asked = m.groupValues[1].isNotEmpty(); val hs = m.groupValues[2]; val mins = m.groupValues[3]; val ap = m.groupValues[4]
        // a bare number is a sum, not a time, unless you said "alarm"
        if (!asked && mins.isEmpty() && ap.isEmpty()) return emptyList()
        // "5.50" is a price and "3.14" is pi: a dot only makes a time with am/pm or "alarm"
        if ('.' in t && !asked && ap.isEmpty()) return emptyList()
        val h = hs.toInt(); val mi = mins.ifEmpty { "0" }.toInt()
        if (mi > 59) return emptyList()
        val candidates: List<LocalTime> = when {
            ap.isNotEmpty() -> { if (h !in 1..12) return emptyList(); listOf(LocalTime.of((h % 12) + if (ap.startsWith("p")) 12 else 0, mi)) }
            h > 23 -> return emptyList()
            h == 0 || h > 12 || hs.length == 2 && hs.startsWith("0") || h24 -> listOf(LocalTime.of(h, mi))
            else -> listOf(LocalTime.of(h % 12, mi), LocalTime.of(h % 12 + 12, mi)).sortedBy { next(it, now) }
        }
        return candidates.mapIndexed { i, lt ->
            val at = next(lt, now)
            val label = clock(lt, h24)
            // rounded up: an alarm 30 seconds away is "in 1 min", not "in 0 min"
            val mins = (ChronoUnit.SECONDS.between(now, at) + 59) / 60
            if (i == 0) Hit.Action(ALARM, "Alarm", label, "${dayWord(at, now)} · in ${inWords(mins)}", true, mapOf("h" to lt.hour, "m" to lt.minute))
            else Hit.Action(ALARM, "Alarm", label, "Set it for $label instead", false, mapOf("h" to lt.hour, "m" to lt.minute))
        }
    }

    private fun next(t: LocalTime, now: LocalDateTime): LocalDateTime { val d = now.toLocalDate().atTime(t); return if (d.isAfter(now)) d else d.plusDays(1) }
    fun clock(t: LocalTime, h24: Boolean): String = if (h24) t.format(DateTimeFormatter.ofPattern("H:mm")) else
        "${if (t.hour % 12 == 0) 12 else t.hour % 12}:${"%02d".format(t.minute)} ${if (t.hour < 12) "am" else "pm"}"
    private fun dayWord(at: LocalDateTime, now: LocalDateTime) = if (at.toLocalDate() == now.toLocalDate()) "Today" else "Tomorrow"
    fun inWords(min: Long): String { val h = min / 60; val m = min % 60; return when { h == 0L -> "$m min"; m == 0L -> "$h h"; else -> "$h h $m min" } }

    // ------------------------------------------------------------------ timers: "10 min", "1h 30m", "90 sec", "timer 5"

    private val timerRx = Regex("^(?:timer(?: for)? )?(?:(\\d+) ?(?:h|hr|hrs|hour|hours))? ?(?:(\\d+) ?(min|mins|minute|minutes|m))? ?(?:(\\d+) ?(?:s|sec|secs|second|seconds))?$")

    fun timer(t: String): Hit.Action? {
        Regex("^timer(?: for)? (\\d{1,6})$").matchEntire(t)?.let { return timerOf(it.groupValues[1].toLong() * 60) }
        if (Regex("\\d{7,}").containsMatchIn(t)) return null
        val m = timerRx.matchEntire(t) ?: return null
        val h = m.groupValues[1]; val mi = m.groupValues[2]; val unit = m.groupValues[3]; val s = m.groupValues[4]
        if (h.isEmpty() && mi.isEmpty() && s.isEmpty()) return null
        // "5 m" alone is five metres; a lone "m" counts as minutes only after hours ("1h 30m")
        if (unit == "m" && h.isEmpty()) return null
        val secs = (h.ifEmpty { "0" }.toLong() * 3600) + (mi.ifEmpty { "0" }.toLong() * 60) + s.ifEmpty { "0" }.toLong()
        return timerOf(secs)
    }
    private fun timerOf(secs: Long): Hit.Action? {
        if (secs <= 0 || secs > 24 * 3600) return null
        val h = secs / 3600; val m = secs % 3600 / 60; val s = secs % 60
        val v = listOfNotNull(if (h > 0) "$h h" else null, if (m > 0) "$m min" else null, if (s > 0) "$s sec" else null).joinToString(" ")
        return Hit.Action(TIMER, "Timer", v, "Starts as soon as you tap", true, mapOf("s" to secs.toInt()))
    }

    // ------------------------------------------------------------------ phone numbers

    fun phone(q: String): List<Hit.Action> {
        if (!Regex("^\\+?[(]?[0-9][0-9 ()\\-]{5,}[0-9]$").matches(q)) return emptyList()
        // 2026-11-15 is a date
        if (Regex("^\\d{4}-\\d{1,2}-\\d{1,2}$").matches(q)) return emptyList()
        val digits = q.count { it.isDigit() }; if (digits < 7 || digits > 15) return emptyList()
        val n = q.filter { it.isDigit() || it == '+' }
        return listOf(Hit.Action(CALL, "Call", q, "Call this number", false, mapOf("n" to n)), Hit.Action(TEXT, "Text", q, "Send a message", false, mapOf("n" to n)),
            Hit.Action(SAVE, "Save", q, "Add to contacts", false, mapOf("n" to n)))
    }

    // ------------------------------------------------------------------ ".yt lofi": search inside an app

    /** Short names for the apps people search inside most. Anything else matches an app by name. */
    val ALIASES = mapOf("yt" to "youtube", "y" to "youtube", "m" to "maps", "map" to "maps", "sp" to "spotify", "s" to "spotify", "p" to "play store", "play" to "play store",
        "w" to "wikipedia", "wiki" to "wikipedia", "a" to "amazon", "g" to "web", "web" to "web", "ig" to "instagram", "x" to "x", "r" to "reddit", "n" to "netflix")

    fun inside(q: String): Hit.Action? {
        val m = Regex("^\\.([a-zA-Z]\\S*)(?: (.*))?$").matchEntire(q) ?: return null
        val alias = m.groupValues[1].lowercase(Locale.ROOT); val what = m.groupValues[2].trim()
        val name = ALIASES[alias] ?: alias
        if (name == "web") return Hit.Action(INSIDE, "Web", what.ifEmpty { "…" }, if (what.isEmpty()) "Type what to search the web for" else "Search the web", what.isNotEmpty(), mapOf("app" to "web", "q" to what))
        return Hit.Action(INSIDE, name, what.ifEmpty { "…" }, "", what.isNotEmpty(), mapOf("app" to name, "q" to what))
    }

    // ------------------------------------------------------------------ dates: "days to 25 dec", "christmas", "in 3 weeks", "10 days ago"

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val monthNames = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    /** "dec", "december" or "sept"; never "junk" or "mayday". */
    private fun month(s: String): Int? = (monthNames.indexOfFirst { it == s || (s.length >= 3 && it.startsWith(s)) }).takeIf { it >= 0 }?.plus(1)

    fun parseDay(s: String, today: LocalDate): LocalDate? {
        val t = s.trim().removePrefix("the ").trim()
        when (t) {
            "christmas", "xmas" -> return upcoming(MonthDay.of(12, 25), today)
            "new year", "new years", "new year's", "new years day" -> return upcoming(MonthDay.of(1, 1), today)
            "halloween" -> return upcoming(MonthDay.of(10, 31), today)
            "valentines", "valentine's", "valentines day" -> return upcoming(MonthDay.of(2, 14), today)
            "tomorrow" -> return today.plusDays(1)
        }
        DayOfWeek.entries.firstOrNull { t == it.name.lowercase() || t == it.name.lowercase().take(3) }?.let { d -> var x = today.plusDays(1); while (x.dayOfWeek != d) x = x.plusDays(1); return x }
        Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").matchEntire(t)?.let { return runCatching { LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }.getOrNull() }
        Regex("^(\\d{1,2})[/.](\\d{1,2})(?:[/.](\\d{2,4}))?$").matchEntire(t)?.let { m ->
            val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt(); val y = m.groupValues[3]
            return runCatching { if (y.isEmpty()) upcoming(MonthDay.of(mo, d), today) else LocalDate.of(y.toInt().let { if (it < 100) 2000 + it else it }, mo, d) }.getOrNull()
        }
        Regex("^(\\d{1,2})(?:st|nd|rd|th)? ([a-z]+)(?: (\\d{4}))?$").matchEntire(t)?.let { m ->
            val mo = month(m.groupValues[2]) ?: return null; val d = m.groupValues[1].toInt(); val y = m.groupValues[3]
            return runCatching { if (y.isEmpty()) upcoming(MonthDay.of(mo, d), today) else LocalDate.of(y.toInt(), mo, d) }.getOrNull()
        }
        Regex("^([a-z]+) (\\d{1,2})(?:st|nd|rd|th)?(?:,? (\\d{4}))?$").matchEntire(t)?.let { m ->
            val mo = month(m.groupValues[1]) ?: return null; val d = m.groupValues[2].toInt(); val y = m.groupValues[3]
            return runCatching { if (y.isEmpty()) upcoming(MonthDay.of(mo, d), today) else LocalDate.of(y.toInt(), mo, d) }.getOrNull()
        }
        return null
    }
    /** The next time this day comes round; 29 February waits for a leap year rather than becoming the 28th. */
    private fun upcoming(md: MonthDay, today: LocalDate): LocalDate {
        var y = today.year
        while (true) { if (md.isValidYear(y)) { val d = md.atYear(y); if (!d.isBefore(today)) return d }; y++ }
    }
    private fun dayName(d: LocalDate, today: LocalDate) = d.format(DateTimeFormatter.ofPattern("EEEE d MMMM" + if (d.year != today.year) " yyyy" else "", Locale.ENGLISH))

    fun date(t: String, today: LocalDate): Hit.Action? {
        // "in 3 weeks", "3 weeks from today", "10 days ago"
        Regex("^(?:in )?(\\d+) (day|days|week|weeks|month|months|year|years)(?: (from (?:today|now)|ago))?$").matchEntire(t)?.let { m ->
            val tail = m.groupValues[3]
            if (!t.startsWith("in ") && tail.isEmpty()) return null
            val n = m.groupValues[1].toLongOrNull()?.takeIf { it <= 100_000 } ?: return null; val u = m.groupValues[2]
            val signed = if (tail == "ago") -n else n
            val d = when { u.startsWith("day") -> today.plusDays(signed); u.startsWith("week") -> today.plusWeeks(signed); u.startsWith("month") -> today.plusMonths(signed); else -> today.plusYears(signed) }
            return Hit.Action(DATE, "Date", dayName(d, today), "${if (signed < 0) "Was" else "Will be"} ${count(ChronoUnit.DAYS.between(today, d))}", true)
        }
        val m = Regex("^(?:how (?:long|many days) (?:until|till|to) |days? (?:until|till|to|before) |countdown (?:to )?)(.+)$").matchEntire(t)
        val target = if (m != null) parseDay(m.groupValues[1], today) else if (t in setOf("christmas", "xmas", "new year", "halloween")) parseDay(t, today) else null
        target ?: return null
        val days = ChronoUnit.DAYS.between(today, target)
        return Hit.Action(DATE, "Days", if (days == 0L) "Today" else count(days), dayName(target, today), true)
    }
    private fun count(d: Long): String { val a = kotlin.math.abs(d); return if (a == 1L) "1 day" else "$a days" }
}
