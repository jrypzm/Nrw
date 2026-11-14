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
    private const val YEAR_MS = 365.2425 * 86_400_000.0

    val MEASURES = listOf(
        Measure("year", "years", 1.0),
        Measure("summer", "summers", 1.0),
        Measure("weekend", "weekends", 52.1775),
        Measure("full moon", "full moons", 365.2425 / 29.530589),
        Measure("sunrise", "sunrises", 365.2425),
    )

    fun age(birth: LocalDate, now: ZonedDateTime = ZonedDateTime.now()): Double {
        val b = birth.atStartOfDay(now.zone).toInstant().toEpochMilli()
        return max(0.0, (now.toInstant().toEpochMilli() - b) / YEAR_MS)
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
                    kind, 12, (m + f).coerceIn(0.0, 12.0), 4, "month",
                    "Age $lived", "Turning ${lived + 1} on ${next.dayOfMonth} ${mon(next)}",
                    { i -> val d = lb.plusMonths(i.toLong()); "${mon(d)} ${d.year}" }, null,
                )
            }
            ViewKind.LIFE -> GridModel(
                kind, s.span, age.coerceIn(0.0, s.span.toDouble()), 10, "year", null,
                "Age $lived · ${s.span} year life",
                { i -> "Age $i · ${by + i}" }, { it },
            )
        }
    }

    /** What the big number says. */
    class Headline(val value: Double, val noun: String, val word: String, val pct: Double, val measure: Measure)

    fun headline(s: Settings, m: GridModel): Headline {
        val left = max(0.0, m.total - m.done)
        val base = if (s.lived) m.done else left
        val measure = if (m.kind == ViewKind.LIFE) MEASURES[s.unit.mod(MEASURES.size)] else Measure(m.unit, m.unit + "s", 1.0)
        val v = base * measure.perYear
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
}
