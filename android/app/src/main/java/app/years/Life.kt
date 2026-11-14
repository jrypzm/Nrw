package app.years

import android.graphics.Color
import java.text.DecimalFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ViewKind { LIFE, DECADE, YEAR }

/** One screen's worth of dots: how many there are, how many are done (fractional) and how to talk about them. */
class GridModel(
    val kind: ViewKind,
    val total: Int,
    val done: Double,
    val cols: Int,
    val unit: String,
    val context: String?,
    val sub: String,
    val label: (Int) -> String,
    val yearIndex: ((Int) -> Int)?,
)

/** Ways to count what's left: years, or things you'll see again. */
class Measure(val one: String, val many: String, val perYear: Double)

object Life {

    val MEASURES = listOf(
        Measure("year", "years", 1.0),
        Measure("day", "days", 365.2425),
        Measure("week", "weeks", 52.1775),
        Measure("summer", "summers", 1.0),
        Measure("weekend", "weekends", 52.1775),
        Measure("full moon", "full moons", 365.2425 / 29.530589),
    )

    /** Your birthday in a given year. 29 February falls on 28 February in other years. */
    fun birthdayIn(birth: LocalDate, year: Int): LocalDate = birth.withYear(year)

    /** Whole years you've completed by [today]. */
    fun wholeYears(birth: LocalDate, today: LocalDate): Int {
        var y = today.year - birth.year
        if (birthdayIn(birth, today.year).isAfter(today)) y--
        return max(0, y)
    }

    /** Exact age: whole calendar years, plus how far you are between your last birthday and the next. */
    fun age(birth: LocalDate, now: ZonedDateTime = ZonedDateTime.now()): Double {
        val today = now.toLocalDate()
        if (!today.isAfter(birth)) return 0.0
        val years = wholeYears(birth, today)
        val last = birthdayIn(birth, birth.year + years).atStartOfDay(now.zone)
        val next = birthdayIn(birth, birth.year + years + 1).atStartOfDay(now.zone)
        val f = Duration.between(last, now).toMillis().toDouble() / max(1L, Duration.between(last, next).toMillis())
        return years + f.coerceIn(0.0, 0.999999)
    }

    fun isBirthday(birth: LocalDate, today: LocalDate = LocalDate.now()) =
        today.isAfter(birth) && birthdayIn(birth, today.year) == today

    fun lastBirthday(birth: LocalDate, today: LocalDate): LocalDate =
        birthdayIn(birth, birth.year + wholeYears(birth, today))

    /** The start of the k-th month after your last birthday (clamped at short month ends, never drifting). */
    private fun monthStart(lb: LocalDate, k: Int): LocalDate = lb.plusMonths(k.toLong())

    fun mon(d: LocalDate): String = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    fun model(s: Settings, kind: ViewKind, now: ZonedDateTime = ZonedDateTime.now()): GridModel {
        val birth = s.birth ?: LocalDate.now().minusYears(30)
        val age = age(birth, now)
        val lived = floor(age).toInt()
        val by = birth.year
        return when (kind) {
            ViewKind.DECADE -> {
                val start = (lived / 10) * 10
                GridModel(
                    kind, 10, (age - start).coerceIn(0.0, 10.0), 5, "year",
                    if (start == 0) "Your first decade" else "Your ${start}s",
                    "Ages $start–${start + 9}",
                    { i -> "Age ${start + i} · ${by + start + i}" }, { i -> start + i },
                )
            }
            ViewKind.YEAR -> {
                val today = now.toLocalDate()
                val lb = lastBirthday(birth, today)
                var m = 0
                while (m < 11 && !monthStart(lb, m + 1).isAfter(today)) m++
                val st = monthStart(lb, m).atStartOfDay(now.zone)
                val en = (if (m == 11) birthdayIn(birth, lb.year + 1) else monthStart(lb, m + 1)).atStartOfDay(now.zone)
                val f = (Duration.between(st, now).toMillis().toDouble() /
                        max(1L, Duration.between(st, en).toMillis())).coerceIn(0.0, 1.0)
                val next = birthdayIn(birth, lb.year + 1)
                GridModel(
                    kind, 12, (m + f).coerceIn(0.0, 12.0), 6, "month",
                    "Age $lived", "Turning ${lived + 1} on ${next.dayOfMonth} ${mon(next)}",
                    { i -> val d = lb.plusMonths(i.toLong()); "${mon(d)} ${d.year}" }, null,
                )
            }
            ViewKind.LIFE -> zoomModel(s, birth, age, now)
        }
    }

    /** What the big number says. */
    class Headline(val value: Double, val noun: String, val word: String, val pct: Double, val measure: Measure)

    fun headline(s: Settings, m: GridModel): Headline {
        if (m.unit == "lifetime") {
            val f = (m.done - (m.total - 1)) * 100.0
            return Headline(m.total.toDouble(), "lives", "of human history", if (s.lived) f else 100 - f, Measure("life", "lives", 1.0))
        }
        val left = max(0.0, m.total - m.done)
        // The number always counts in the unit you're looking at: months on Months, weeks on Weeks, days on Days.
        // On Years you can tap to count in days, weeks, summers and more.
        val measure = when {
            m.kind != ViewKind.LIFE -> Measure(m.unit, m.unit + "s", 1.0)
            m.unit == "month" -> Measure("month", "months", 12.0)
            m.unit == "week" -> Measure("week", "weeks", 52.1775)
            m.unit == "day" -> Measure("day", "days", 365.2425)
            else -> MEASURES[s.unit.mod(MEASURES.size)]
        }
        // The life grid can be zoomed to months, weeks or days, so count in years from your exact age instead of dots.
        val base = if (m.kind == ViewKind.LIFE) {
            val lived = s.birth?.let { age(it) }?.coerceAtMost(s.span.toDouble()) ?: 0.0
            if (s.lived) lived else max(0.0, s.span - lived)
        } else if (s.lived) m.done else left
        // Days are counted on the real calendar, so every screen and widget shows the same number.
        val v = if (m.kind == ViewKind.LIFE && measure.one == "day" && s.birth != null) {
            if (s.lived) daysLived(s.birth) else daysLeftExact(s)
        } else base * measure.perYear
        val word = if (!s.lived) "left" else if (m.kind == ViewKind.YEAR) "in" else "lived"
        val whole = floor(v).toLong()
        val pct = if (m.kind == ViewKind.LIFE) base / s.span * 100.0 else base / m.total * 100.0
        return Headline(v, if (whole == 1L) measure.one else measure.many, word, pct, measure)
    }

    private val INT = DecimalFormat("#,##0")
    private val LIVE = DecimalFormat("#,##0.00000000")
    private val LIVE_PCT = DecimalFormat("0.0000000")
    fun whole(v: Double): String = INT.format(floor(v))
    fun live(v: Double): String = LIVE.format(v)
    fun livePct(v: Double): String = LIVE_PCT.format(v)

    /** 0 at night, 1 at midday. */
    fun sunLevel(now: LocalTime = LocalTime.now()): Float {
        val h = now.hour + now.minute / 60f
        return max(0f, sin(((h - 6f) / 12f * PI).toFloat()))
    }

    fun litColor(sun: Float): Int {
        val v = (110 + 145 * sun).roundToInt().coerceIn(0, 255)
        return Color.rgb(v, v, v)
    }

    // ------------------------------------------------------------ zoom through time

    val ZOOMS = listOf("History", "Years", "Months", "Weeks", "Days")
    const val ZOOM_YEARS = 1
    /** About 300,000 years of modern humans, as 80-year lives laid end to end. */
    const val LIFETIMES = 3750

    private fun zoomModel(s: Settings, birth: LocalDate, age: Double, now: ZonedDateTime): GridModel {
        val lived = floor(age).toInt()
        val by = birth.year
        val sub = "Age $lived · ${INT.format(max(0L, dayOfLife(birth, now.toLocalDate()) - 1))} days lived"
        return when (s.zoom.coerceIn(0, 4)) {
            0 -> GridModel(ViewKind.LIFE, LIFETIMES, (LIFETIMES - 1 + (age / s.span).coerceIn(0.0, 1.0)), 50, "lifetime", null,
                "Yours is the last one, filling now",
                { i -> if (i == LIFETIMES - 1) "Your life" else "About ${INT.format((LIFETIMES - 1 - i) * 80L)} years ago" }, null)
            2 -> GridModel(ViewKind.LIFE, s.span * 12, (age * 12).coerceIn(0.0, s.span * 12.0), 36, "month", null, sub,
                { i -> val d = birth.plusMonths(i.toLong()); "${mon(d)} ${d.year} · age ${i / 12}" }, null)
            3 -> GridModel(ViewKind.LIFE, (s.span * 52.1775).roundToInt(), (age * 52.1775).coerceIn(0.0, s.span * 52.1775), 52, "week", null, sub,
                { i -> val d = birth.plusWeeks(i.toLong()); "Week of ${d.dayOfMonth} ${mon(d)} ${d.year}" }, null)
            4 -> {
                val total = ChronoUnit.DAYS.between(birth, birth.plusYears(s.span.toLong())).toInt()
                val today = now.toLocalDate()
                val dayFrac = (now.toLocalTime().toSecondOfDay() / 86400.0)
                val done = (ChronoUnit.DAYS.between(birth, today) + dayFrac).coerceIn(0.0, total.toDouble())
                GridModel(ViewKind.LIFE, total, done, 183, "day", null, sub,
                    { i -> val d = birth.plusDays(i.toLong()); "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth} ${mon(d)} ${d.year}" }, null)
            }
            else -> GridModel(ViewKind.LIFE, s.span, age.coerceIn(0.0, s.span.toDouble()), 10, "year", null, sub,
                { i -> "Age $i · ${by + i}" }, { it })
        }
    }

    fun dayOfLife(birth: LocalDate, today: LocalDate = LocalDate.now()): Long = ChronoUnit.DAYS.between(birth, today) + 1
    /** Days from now until the end of your lifespan, with today's remaining hours as a fraction. */
    fun daysLeftExact(s: Settings, now: ZonedDateTime = ZonedDateTime.now()): Double {
        val end = (s.birth ?: now.toLocalDate()).plusYears(s.span.toLong()).atStartOfDay(now.zone)
        return max(0.0, Duration.between(now, end).toMillis() / 86_400_000.0)
    }
    fun daysLived(birth: LocalDate, now: ZonedDateTime = ZonedDateTime.now()): Double =
        max(0.0, Duration.between(birth.atStartOfDay(now.zone), now).toMillis() / 86_400_000.0)
    /** Whole days left. Matches the number on every screen and widget. */
    fun daysLeft(s: Settings, today: LocalDate = LocalDate.now()): Long =
        if (today == LocalDate.now()) floor(daysLeftExact(s)).toLong()
        else max(0L, ChronoUnit.DAYS.between(today, (s.birth ?: today).plusYears(s.span.toLong())))
    fun weekOfLife(birth: LocalDate, today: LocalDate = LocalDate.now()): Long = ChronoUnit.WEEKS.between(birth, today) + 1
    fun yearsLeft(s: Settings): Double = s.birth?.let { max(0.0, s.span - age(it)) } ?: 0.0

    // ------------------------------------------------------------ people (the tail end)

    /** How many more times you'll see someone, and how many years you'll overlap. */
    class Together(val visits: Long, val years: Double, val theirAge: Int)

    fun together(s: Settings, p: Person, today: LocalDate = LocalDate.now()): Together {
        val theirAge = today.year - p.birthYear
        // Someone who has already passed your lifespan still has years ahead of them: allow at least a few.
        val theirLeft = max(s.span - theirAge.toDouble(), if (theirAge < 105) max(2.0, (100.0 - theirAge) / 3.0) else 1.0)
        val shared = minOf(yearsLeft(s), theirLeft)
        return Together(floor(shared * p.often.perYear).toLong(), shared, theirAge)
    }

    // ------------------------------------------------------------ the ledger

    class Slice(val label: String, val years: Double)

    /** Where the years you have left will go, given your phone time per day. */
    fun ledger(s: Settings, phoneHours: Double): List<Slice> {
        val left = yearsLeft(s)
        val age = s.birth?.let { age(it) } ?: 30.0
        val sleep = left * 8.0 / 24.0
        val workYears = max(0.0, minOf(left, 65.0 - age))
        val work = workYears * (40.0 * 48.0) / 8766.0
        val upkeep = left * 3.0 / 24.0 // eating, washing, chores, getting around
        val phone = minOf(left * phoneHours / 24.0, max(0.0, left - sleep - work - upkeep))
        val free = max(0.0, left - sleep - work - phone - upkeep)
        return listOf(Slice("Asleep", sleep), Slice("Working", work), Slice("On your phone", phone), Slice("Eating, chores, commuting", upkeep), Slice("Truly yours", free))
    }

    // ------------------------------------------------------------ lifespan check

    /** Rough, population-level adjustments. An estimate to reflect on, not medical advice. */
    val HABITS = listOf(
        Triple("smoke", "I smoke", -10),
        Triple("exercise", "I exercise most weeks", 3),
        Triple("sleep", "I usually sleep 7 to 8 hours", 1),
        Triple("drink", "I drink heavily", -5),
        Triple("diet", "I mostly eat whole foods", 2),
    )

    fun estimate(habits: Set<String>, base: Int = 80): Int = (base + HABITS.filter { it.first in habits }.sumOf { it.third }).coerceIn(40, 120)

    /** A short, changing line for the lock screen. */
    fun lineOfDay(c: android.content.Context, s: Settings, today: LocalDate = LocalDate.now()): String? {
        val birth = s.birth ?: return null
        val options = mutableListOf("Day ${INT.format(dayOfLife(birth, today))}", "${INT.format(daysLeft(s, today))} days left")
        s.people.forEach { p -> val t = together(s, p, today); if (t.visits > 0) options += "${INT.format(t.visits)} more ${if (p.often == Often.DAILY) "days" else "visits"} with ${p.name}" }
        val lived = age(birth).toInt()
        s.names.forEach { (year, n) -> if (year <= lived) options += "$n · ${birth.year + year}" }
        s.events.filter { !it.date.isBefore(today) }.minByOrNull { it.date }?.let { e ->
            val d = ChronoUnit.DAYS.between(today, e.date); options += if (d == 0L) "${e.name} · today" else "${e.name} · in $d ${if (d == 1L) "day" else "days"}"
        }
        return options[(today.dayOfYear + today.year) % options.size]
    }
}
