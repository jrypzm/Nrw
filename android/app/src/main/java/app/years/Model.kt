package app.years

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ================================================================== estimate my lifespan
// Same model as the web app. A Gompertz–Makeham mortality curve (a small constant risk plus a risk that doubles about
// every 7.7 years of age) is calibrated so a typical person of your sex in your country matches the UN life expectancy.
// Each answer then scales the yearly risk of death by a published hazard ratio, re-centred on how common that answer
// is, so a typical answer changes nothing. Habits cluster in the same people, so the biggest effect counts fully and
// each further one counts less, calibrated so five healthy habits add ~13 years at 50 (Li et al., Circulation 2018).

class EstO(val label: String, val sub: String, val hr: Double, val share: Double)
class EstQ(val key: String, val title: String, val why: String, val source: String, val calc: Boolean, val options: List<EstO>)

/** Your answers. [sex] is "f", "m" or null; [picks] maps a question's key to the chosen option. */
data class EstAnswers(val country: String, val sex: String? = null, val picks: Map<String, Int> = emptyMap()) {
    fun encode(): String = "$country;${sex ?: ""};" + picks.entries.joinToString(",") { "${it.key}=${it.value}" }

    companion object {
        fun decode(s: String?): EstAnswers? = runCatching {
            val p = s!!.split(";")
            EstAnswers(p[0], p[1].ifEmpty { null }, p.getOrElse(2) { "" }.split(",").filter { "=" in it }.associate { it.substringBefore("=") to it.substringAfter("=").toInt() })
        }.getOrNull()
    }
}

class Country(val iso: String, val name: String, val women: Double, val men: Double)

class Estimate(
    val years: Double, val typical: Double, val base: Double, val q25: Double, val q50: Double, val q75: Double,
    val chance: (Double) -> Double, val contrib: List<Pair<String, Double>>, val ageNow: Double,
)

object Lifespan {
    val COUNTRIES: List<Country> by lazy {
        LE_DATA.lines().mapNotNull { l -> l.split("|").takeIf { it.size == 4 }?.let { Country(it[0], it[1], it[2].toDouble(), it[3].toDouble()) } }
    }
    val WORLD: Country get() = COUNTRIES.first { it.iso == "WLD" }
    fun country(iso: String?): Country = COUNTRIES.firstOrNull { it.iso == iso } ?: WORLD
    fun placeName(iso: String?): String = country(iso).let { if (it.iso == "WLD") "the world" else it.name }

    /** Your country from the phone's region, or the world average. */
    fun guessCountry(): String = runCatching {
        val iso3 = Locale("", Locale.getDefault().country).isO3Country
        if (COUNTRIES.any { it.iso == iso3 }) iso3 else "WLD"
    }.getOrDefault("WLD")

    fun baseLE(iso: String?, sex: String?): Double = country(iso).let { if (sex == "f") it.women else if (sex == "m") it.men else (it.women + it.men) / 2 }

    private const val GB = 0.09
    private const val GL = 0.0004
    private val CW = doubleArrayOf(1.0, .35, .25, .18, .14, .12, .1, .1)
    private fun childDeaths(le: Double) = min(.3, exp(-2.254 - 0.1256 * (le - 54)))
    private fun step(a: Double, hr: Double, t: Double, h: Double) = exp(-hr * (GL + a * exp(GB * (t + h / 2))) * h)

    /** Survival from [age], in quarter years, to 125. */
    private fun curve(a: Double, hr: Double, age: Double): DoubleArray {
        val out = ArrayList<Double>(512); out.add(1.0)
        var s = 1.0; var t = age
        while (t < 125 && s > 1e-6) { s *= step(a, hr, t, .25); out.add(s); t += .25 }
        return out.toDoubleArray()
    }
    private fun restOf(c: DoubleArray): Double { var e = 0.0; for (i in 1 until c.size) e += (c[i] + c[i - 1]) / 2 * .25; return e }

    private val cal = HashMap<Double, Double>()
    private fun calibrate(le0: Double): Double = cal.getOrPut(le0) {
        val q5 = childDeaths(le0); var lo = 1e-7; var hi = 1e-2
        repeat(50) { val mid = kotlin.math.sqrt(lo * hi); val e = q5 * .5 + (1 - q5) * (5 + restOf(curve(mid, 1.0, 5.0))); if (e > le0) lo = mid else hi = mid }
        kotlin.math.sqrt(lo * hi)
    }

    private fun relHR(q: EstQ, i: Int): Double = q.options[i].hr / q.options.sumOf { it.hr * it.share }
    private fun combine(ls: List<Double>): Double = exp(ls.sortedByDescending { abs(it) }.mapIndexed { i, l -> l * (CW.getOrElse(i) { .1 }) }.sum())

    fun estimate(ans: EstAnswers, birth: LocalDate): Estimate {
        val ageNow = max(0.0, Life.age(birth))
        val le0 = baseLE(ans.country, ans.sex)
        val a = calibrate((le0 * 10).roundToInt() / 10.0)
        val logs = EST_Q.mapNotNull { q -> ans.picks[q.key]?.takeIf { it in q.options.indices }?.let { q.key to ln(relHR(q, it)) } }
        fun run(ls: List<Double>): Pair<DoubleArray, Double> { val c = curve(a, combine(ls), ageNow); return c to ageNow + restOf(c) }
        val (c, e) = run(logs.map { it.second })
        fun at(p: Double): Double { val i = c.indexOfFirst { it <= p }; return if (i < 0) 125.0 else ageNow + i * .25 }
        val chance = { y: Double -> if (y <= ageNow) 1.0 else c.getOrElse(((y - ageNow) * 4).roundToInt()) { 0.0 } }
        val contrib = logs.map { (k, _) -> k to (e - run(logs.filter { it.first != k }.map { it.second }).second) }.filter { abs(it.second) >= .1 }.sortedByDescending { it.second }
        return Estimate(e, run(emptyList()).second, le0, at(.75), at(.5), at(.25), chance, contrib, ageNow)
    }

    val NAMES = mapOf("smoke" to "Smoking", "move" to "Activity", "bmi" to "Body weight", "drink" to "Alcohol", "diet" to "Diet", "sleep" to "Sleep", "social" to "People around you", "diab" to "Diabetes")
}

// ================================================================== facts
// Every history dot has its own things to say, and says a different one each time you open it.

object Facts {
    private fun n(v: Double): String = Life.whole(v)
    fun yearLabel(y: Int) = if (y < 0) "${n(-y.toDouble())} BCE" else if (y < 1000) "$y CE" else "$y"

    fun eraName(y0: Int) = when {
        y0 < -115000 -> "The Middle Stone Age"; y0 < -26000 -> "The last ice age"; y0 < -19000 -> "The coldest part of the ice age"
        y0 < -11700 -> "The ice age ends"; y0 < -3200 -> "The first farmers"; y0 < -1200 -> "The Bronze Age"; y0 < 500 -> "The ancient world"
        y0 < 1450 -> "The Middle Ages"; y0 < 1800 -> "The early modern world"; else -> "The modern world"
    }

    /** The calendar year a history dot begins: lives of 80 years, end to end, ending with yours. */
    fun lifeStart(birthYear: Int, i: Int) = birthYear - (Life.LIFETIMES - 1 - i) * 80

    fun life(s: Settings, i: Int): List<String> {
        val birth = s.birth ?: return emptyList()
        if (i == Life.LIFETIMES - 1) return you(s)
        val now = LocalDate.now().year
        val y0 = lifeStart(birth.year, i); val y1 = y0 + 80; val ago = (now - y0).toDouble()
        val between = Life.LIFETIMES - 1 - i
        val out = ERA.filter { y0 <= it.to && y1 >= it.from }.map { it.text }.toMutableList()
        out += "This life began about ${n(ago)} years ago, around ${yearLabel(y0)}."
        out += "That’s about ${n(ago / 25)} generations back. One of your direct ancestors was alive in this lifetime, or you wouldn’t be here."
        out += "${n(between.toDouble())} whole lifetimes, end to end, separate this one from yours."
        out += "If each lifetime took one second, you’d travel back to this one in ${if (between < 60) "$between seconds" else "${between / 60} minutes ${between % 60} seconds"}."
        out += "Since then, Earth has circled the Sun about ${n(ago)} times, and spun about ${n(ago * 365.2425)} times."
        out += "The Moon drifts away from Earth by about 3.8 cm a year, so it was about ${n(ago * .038)} metres closer then."
        val far = when { ago > 100000 -> ", farther than the whole width of the Milky Way"; ago > 26000 -> ", past the centre of our galaxy"; ago > 4000 -> ", farther than most stars you can see with your eyes"; else -> "" }
        out += "Light that left Earth when this life began is now ${n(ago)} light-years away$far."
        if (y1 < -10000) out += "This was about ${n((-10000 - y0).toDouble())} years before anyone, anywhere, planted a crop."
        if (y1 < -3200) out += "It was about ${n((-3200 - y0).toDouble())} years before the first writing."
        out += "By the start of this life, ${"%.1f".format(Locale.ENGLISH, i * 100.0 / Life.LIFETIMES)}% of the time since our species appeared had already passed."
        return out
    }

    private val POP = listOf(1900 to 1.65, 1927 to 2.0, 1950 to 2.5, 1960 to 3.0, 1974 to 4.0, 1987 to 5.0, 1999 to 6.0, 2011 to 7.0, 2022 to 8.0, 2030 to 8.5, 2050 to 9.7, 2084 to 10.3, 2100 to 10.2)
    private fun popIn(y: Int): Double {
        if (y <= POP[0].first) return POP[0].second
        for (k in 1 until POP.size) if (y <= POP[k].first) { val (a, p) = POP[k - 1]; val (b, q) = POP[k]; return p + (q - p) * (y - a) / (b - a) }
        return POP.last().second
    }

    /** What a year of your life can say: the world that year, and you in it. The event, if any, comes first. */
    fun year(s: Settings, yi: Int, lines: Set<String> = emptySet()): List<String> {
        val birth = s.birth ?: return emptyList()
        val y = birth.year + yi; val today = LocalDate.now(); val lived = Life.age(birth).toInt()
        val out = mutableListOf<String>()
        EVENTS[y]?.let { out += "In $y: $it" }
        EVENTS[y + 1]?.let { if (y + 1 <= today.year) out += "In ${y + 1}, as you turned ${yi + 1}: $it" }
        out += if (yi <= lived) "The world had about ${"%.1f".format(Locale.ENGLISH, popIn(y))} billion people in $y." else "The world is projected to have about ${"%.1f".format(Locale.ENGLISH, popIn(y))} billion people in $y."
        val from = Life.birthdayIn(birth, y); val to = Life.birthdayIn(birth, y + 1)
        if (!from.isAfter(today)) {
            val ago = today.year - y
            out += "That was ${when (ago) { 0 -> "this year"; 1 -> "last year"; else -> "$ago years ago" }}: about ${n(ChronoUnit.DAYS.between(from, today).toDouble())} days back."
        } else out += "It starts in about ${n(ChronoUnit.DAYS.between(today, from).toDouble())} days. You’ll be $yi on your birthday that year."
        val days = ChronoUnit.DAYS.between(from, to)
        out += "This year of your life ${if (yi <= lived) "had" else "will have"} $days days${if (days == 366L) ", thanks to a 29 February" else ""}."
        val written = lines.count { k -> runCatching { LocalDate.parse(k) }.getOrNull()?.let { !it.isBefore(from) && it.isBefore(to) } == true }
        if (written > 0) out += "You wrote $written ${if (written == 1) "line" else "lines"} in this year of your life."
        out += "It’s year ${yi + 1} of the ${s.span} on your grid${if (yi < lived) "" else if (yi == lived) ", and it’s happening now" else ""}."
        return out
    }

    /** Fun facts about you, worked out from your birthday. All approximate, all averages. */
    fun you(s: Settings): List<String> {
        val b = s.birth ?: return emptyList()
        val today = LocalDate.now()
        val days = Life.daysLived(b); val yrs = days / 365.2425; val by = b.year
        val olympics = (1896..today.year step 4).filter { it !in setOf(1916, 1940, 1944) }.map { if (it == 2020) 2021 else it }
            .count { it > by || (it == by && b.monthValue < 8) }
        val cups = (1930..today.year step 4).filter { it !in setOf(1942, 1946) }.count { it > by }
        var leaps = 0
        for (y in by..today.year) if (java.time.Year.isLeap(y.toLong())) { val d = LocalDate.of(y, 2, 29); if (d.isAfter(b) && !d.isAfter(today)) leaps++ }
        val next = listOf(10000, 15000, 20000, 25000, 30000).firstOrNull { it > days + 1 }
        val weekday = b.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
        return listOfNotNull(
            "Your heart has beaten roughly ${n(days * 24 * 60 * 75 / 1e6)} million times, at about 75 beats a minute.",
            "You’ve taken roughly ${n(days * 24 * 60 * 15 / 1e6)} million breaths.",
            "You’ve spent about ${"%.1f".format(Locale.ENGLISH, yrs / 3)} years asleep.",
            "You’ve seen about ${n(days / 29.5306)} full moons.",
            "You were born on a $weekday.",
            "You’ve lived through $leaps leap ${if (leaps == 1) "day" else "days"}.",
            "$olympics Summer Olympics have been held in your lifetime.",
            "$cups FIFA World Cups have been played since you were born.",
            "Riding on Earth, you’ve travelled about ${n(yrs * .94)} billion kilometres around the Sun.",
            "With the whole solar system, you’ve moved about ${n(yrs * 7.26)} billion kilometres around the centre of the galaxy.",
            next?.let { val d = b.plusDays(it - 1L); "Your ${n(it.toDouble())}th day of life is on ${d.dayOfMonth} ${Life.mon(d)} ${d.year}." } ?: "You’ve passed your 30,000th day of life.",
            "On Mars you’d be ${"%.1f".format(Locale.ENGLISH, days / 686.98)} years old. On Jupiter, ${"%.1f".format(Locale.ENGLISH, days / 4332.6)}.",
            if (by < 1986) "Halley’s Comet came by in 1986, during your lifetime. Its next visit is in 2061." else "Halley’s Comet returns in 2061. You’ll be ${2061 - by}.",
            "Your scalp hair grows about 15 cm a year: around ${n(yrs * .15)} metres so far, laid end to end.",
            "Your fingernails grow about 3.5 mm a month: roughly ${"%.1f".format(Locale.ENGLISH, yrs * 12 * .35 / 100)} metres per nail so far.",
            "People speak about 16,000 words a day on average: something like ${n(max(0.0, days - 730) * 16000 / 1e6)} million words for you so far.",
            if (2050 - by > 0) "You’ll be ${2050 - by} when 2050 begins." else null,
        )
    }

    private var last = ""
    /** A fact from [list], never the one shown just before. */
    fun next(list: List<String>): String {
        if (list.isEmpty()) return ""
        if (list.size == 1) return list[0].also { last = it }
        var f: String
        do f = list.random() while (f == last)
        last = f
        return f
    }
}

// ================================================================== where your years go, adjustable

class Slice(val label: String, val years: Double, val shade: Long)

object Ledger {
    class Knob(val label: String, val min: Float, val max: Float, val step: Float, val fmt: (Float) -> String)

    val SLEEP = Knob("Sleep", 4f, 11f, .25f) { "${num(it)} h a night" }
    val WORK = Knob("Work", 0f, 80f, 1f) { if (it == 0f) "Not working" else "${num(it)} h a week" }
    val RETIRE = Knob("Retire at", 40f, 85f, 1f) { "age ${num(it)}" }
    val COMMUTE = Knob("Commute", 0f, 180f, 5f) { if (it == 0f) "None" else "${num(it)} min a day" }
    val CHORES = Knob("Eating, chores, getting ready", 0f, 8f, .25f) { "${num(it)} h a day" }
    val PHONE = Knob("Phone", 0f, 12f, .25f) { "${num(it)} h a day" }

    fun num(v: Float): String = if (v == floor(v)) "${v.toInt()}" else "%.2f".format(Locale.ENGLISH, v).trimEnd('0').trimEnd('.')

    /** Where the years you have left will go. Work counts 48 weeks a year until you retire; commuting, five days a week. */
    fun slices(s: Settings, phoneHours: Double): List<Slice> {
        val left = Life.yearsLeft(s)
        val age = s.birth?.let { Life.age(it) } ?: 30.0
        val wy = max(0.0, min(left, s.retire - age)); val wk = 48 / 52.1775
        val raw = mutableListOf(
            Slice("Asleep", left * s.sleep / 24, 0xFF2C2C2E), Slice("Working", wy * s.work / 168 * wk, 0xFF48484A),
            Slice("Commuting", wy * s.commute / 60 * 5 / 168 * wk, 0xFF5A5A5E), Slice("Eating and chores", left * s.chores / 24, 0xFF3A3A3C),
            Slice("On your phone", left * phoneHours / 24, 0xFF8E8E93),
        )
        val used = raw.sumOf { it.years }
        val k = if (used > left && used > 0) left / used else 1.0
        val out = raw.map { Slice(it.label, it.years * k, it.shade) }
        return out + Slice("Truly yours", max(0.0, left - out.sumOf { it.years }), 0xFFFFFFFF)
    }
}
