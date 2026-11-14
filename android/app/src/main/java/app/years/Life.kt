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

    /** Exact age: whole calendar years, plus how far you are between your last birthday and the next. */
    fun age(birth: LocalDate, now: ZonedDateTime = ZonedDateTime.now()): Double {
        val today = now.toLocalDate()
        if (!today.isAfter(birth)) return 0.0
        val years = ChronoUnit.YEARS.between(birth, today)
        val last = birth.plusYears(years).atStartOfDay(now.zone)
        val next = birth.plusYears(years + 1).atStartOfDay(now.zone)
        val f = Duration.between(last, now).toMillis().toDouble() / max(1L, Duration.between(last, next).toMillis())
        return years + f.coerceIn(0.0, 0.999999)
    }

    fun isBirthday(birth: LocalDate, today: LocalDate = LocalDate.now()) =
        birth.month == today.month && birth.dayOfMonth == today.dayOfMonth

    fun lastBirthday(birth: LocalDate, today: LocalDate): LocalDate =
        birth.plusYears(ChronoUnit.YEARS.between(birth, today))

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
                val m = ChronoUnit.MONTHS.between(lb, today).toInt().coerceIn(0, 11)
                val st = lb.plusMonths(m.toLong()).atStartOfDay(now.zone)
                val en = lb.plusMonths(m + 1L).atStartOfDay(now.zone)
                val f = (Duration.between(st, now).toMillis().toDouble() /
                        max(1L, Duration.between(st, en).toMillis())).coerceIn(0.0, 1.0)
                val next = lb.plusYears(1)
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
        val base = if (s.lived) m.done else left
        val measure = if (m.kind == ViewKind.LIFE) MEASURES[s.unit.mod(MEASURES.size)] else Measure(m.unit, m.unit + "s", 1.0)
        // The life grid can be zoomed to months, weeks or days: convert its dots back to years first.
        val dotsPerYear = if (m.kind != ViewKind.LIFE) 1.0 else when (m.unit) { "month" -> 12.0; "week" -> 52.1775; "day" -> 365.2425; else -> 1.0 }
        val v = base / dotsPerYear * measure.perYear
        val word = if (!s.lived) "left" else if (m.kind == ViewKind.YEAR) "in" else "lived"
        val whole = floor(v).toLong()
        return Headline(v, if (whole == 1L) measure.one else measure.many, word, base / m.total * 100.0, measure)
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
        val sub = "Age $lived · day ${INT.format(dayOfLife(birth, now.toLocalDate()))}"
        return when (s.zoom.coerceIn(0, 4)) {
            0 -> GridModel(ViewKind.LIFE, LIFETIMES, (LIFETIMES - 1 + (age / s.span).coerceIn(0.0, 1.0)), 50, "lifetime", null,
                "Yours is the last one, filling now",
                { i -> if (i == LIFETIMES - 1) "Your life" else "About ${INT.format((LIFETIMES - 1 - i) * 80L)} years ago" }, null)
            2 -> GridModel(ViewKind.LIFE, s.span * 12, (age * 12).coerceIn(0.0, s.span * 12.0), 36, "month", null, sub,
                { i -> val d = birth.plusMonths(i.toLong()); "${mon(d)} ${d.year} · age ${i / 12}" }, null)
            3 -> GridModel(ViewKind.LIFE, s.span * 52, (age * 52.1775).coerceIn(0.0, s.span * 52.0), 52, "week", null, sub,
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
    fun daysLeft(s: Settings, today: LocalDate = LocalDate.now()): Long =
        max(0L, ChronoUnit.DAYS.between(today, (s.birth ?: today).plusYears(s.span.toLong())))
    fun weekOfLife(birth: LocalDate, today: LocalDate = LocalDate.now()): Long = ChronoUnit.WEEKS.between(birth, today) + 1
    fun yearsLeft(s: Settings): Double = s.birth?.let { max(0.0, s.span - age(it)) } ?: 0.0

    // ------------------------------------------------------------ people (the tail end)

    /** How many more times you'll see someone, and how many years you'll overlap. */
    class Together(val visits: Long, val years: Double, val theirAge: Int)

    fun together(s: Settings, p: Person, today: LocalDate = LocalDate.now()): Together {
        val theirAge = today.year - p.birthYear
        val theirLeft = max(0.0, s.span - theirAge.toDouble())
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
        val phone = left * phoneHours / 24.0
        val upkeep = left * 3.0 / 24.0 // eating, washing, chores, getting around
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
