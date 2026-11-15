package app.years

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The maths behind every screen, pushed through the awkward cases: born today, 29 February, older than the lifespan. */
class LogicTest {
    private val today = LocalDate.now()
    private val births = listOf(today, today.minusDays(1), LocalDate.of(2000, 2, 29), LocalDate.of(1930, 1, 1), LocalDate.of(1995, 7, 1), today.plusDays(1), today.minusYears(40))
    private val spans = listOf(40, 80, 120)

    private fun each(f: (Settings) -> Unit) {
        for (b in births) for (sp in spans) for (lived in listOf(false, true)) for (pct in listOf(false, true))
            f(Settings(birth = b, span = sp, lived = lived, pct = pct, people = listOf(Person("Gran", 1920, Often.DAILY), Person("Kid", today.year, Often.YEARLY)), names = mapOf(3 to "x")))
    }

    @Test fun grids_and_headlines_never_break() = each { s0 ->
        for (zoom in 0..4) for (unit in 0..5) {
            val s = s0.copy(zoom = zoom, unit = unit)
            for (kind in ViewKind.entries) {
                val m = Life.model(s, kind)
                assertTrue("total > 0", m.total > 0)
                assertTrue("done in range for $kind zoom $zoom: ${m.done}/${m.total} birth ${s.birth} span ${s.span}", m.done >= 0.0 && m.done <= m.total)
                m.label(0); m.label(m.total - 1); m.label(m.total / 2)
                val h = Life.headline(s, m)
                assertTrue("value finite", h.value.isFinite() && h.value >= 0.0)
                assertTrue("pct in range: ${h.pct}", h.pct.isFinite() && h.pct >= -0.001 && h.pct <= 100.001)
                Life.whole(h.value); Life.live(h.value); Life.livePct(h.pct)
            }
        }
    }

    @Test fun facts_exist_for_every_dot_and_year() = each { s ->
        for (i in listOf(0, 1, 1874, 3700, Life.LIFETIMES - 2, Life.LIFETIMES - 1)) {
            val f = Facts.life(s, i)
            assertTrue("life facts for $i", f.isNotEmpty() && f.all { it.isNotBlank() })
            assertTrue(Facts.next(f).isNotBlank())
        }
        for (y in listOf(0, 1, s.span / 2, s.span - 1)) assertTrue(Facts.year(s, y, setOf("2020-01-01", "nonsense")).isNotEmpty())
        assertTrue(Facts.you(s).all { !it.contains("-") || !it.contains("You’ll be -") })
        Facts.eraName(-299000); Facts.yearLabel(-5); Facts.yearLabel(500)
        assertEquals("", Facts.next(emptyList()))
    }

    @Test fun the_ledger_always_adds_up() = each { s0 ->
        for (s in listOf(s0, s0.copy(sleep = 11f, work = 80f, commute = 180f, chores = 8f, phone = 12f), s0.copy(sleep = 4f, work = 0f, commute = 0f, chores = 0f, phone = 0f, retire = 40f))) {
            val sl = Ledger.slices(s, s.phone.toDouble())
            val left = Life.yearsLeft(s)
            assertEquals(6, sl.size)
            assertTrue(sl.all { it.years.isFinite() && it.years >= 0.0 })
            assertEquals(left, sl.sumOf { it.years }, 1e-6)
        }
        for (p in s0.people) { val t = Life.together(s0, p); assertTrue(t.visits >= 0 && t.years.isFinite()) }
    }

    @Test fun the_estimate_is_sane_for_every_answer() {
        for (b in births) for (iso in listOf("WLD", "JPN", "NGA", "ZZZ", "")) for (sex in listOf(null, "f", "m")) {
            val base = Lifespan.estimate(EstAnswers(iso, sex), b)
            assertTrue("base ${base.years}", base.years.isFinite() && base.years in 20.0..125.0)
            for (q in EST_Q) for (i in q.options.indices) {
                val e = Lifespan.estimate(EstAnswers(iso, sex, mapOf(q.key to i)), b)
                assertTrue(e.years.isFinite() && e.years >= e.ageNow - 1e-9 && e.years <= 125.0)
                assertTrue(e.q25 <= e.q50 && e.q50 <= e.q75)
                for (a in listOf(80.0, 90.0, 100.0)) assertTrue(e.chance(a) in 0.0..1.0)
            }
            // every worst answer at once, and every best
            val worst = Lifespan.estimate(EstAnswers(iso, sex, EST_Q.associate { q -> q.key to q.options.indices.maxBy { q.options[it].hr } }), b)
            val best = Lifespan.estimate(EstAnswers(iso, sex, EST_Q.associate { q -> q.key to q.options.indices.minBy { q.options[it].hr } }), b)
            assertTrue(worst.years <= base.years + 1e-6 && base.years <= best.years + 1e-6)
            // a saved answer that no longer exists is ignored, not a crash
            Lifespan.estimate(EstAnswers(iso, sex, mapOf("smoke" to 99, "gone" to 1)), b)
        }
        // typical person, typical country: close to the UN figure at birth
        val newborn = Lifespan.estimate(EstAnswers("JPN", "f"), today)
        assertEquals(Lifespan.baseLE("JPN", "f"), newborn.years, 1.5)
    }

    @Test fun saved_answers_survive_and_bad_ones_are_dropped() {
        val a = EstAnswers("DEU", "m", mapOf("smoke" to 3, "move" to 2))
        assertEquals(a, EstAnswers.decode(a.encode()))
        assertEquals(EstAnswers("WLD", null), EstAnswers.decode(EstAnswers("WLD").encode()))
        for (bad in listOf(null, "", ";", "DEU", "DEU;m;smoke=x", ";;;;", "DEU;m;=,=,")) { val d = EstAnswers.decode(bad); assertTrue(d == null || d.picks.values.all { it >= 0 }) }
        assertEquals(237, Lifespan.COUNTRIES.size)
        assertTrue(Lifespan.guessCountry().length == 3)
    }

    @Test fun dates() {
        val leap = LocalDate.of(2000, 2, 29)
        assertEquals(LocalDate.of(2023, 2, 28), Life.birthdayIn(leap, 2023))
        assertEquals(LocalDate.of(2024, 2, 29), Life.birthdayIn(leap, 2024))
        assertEquals(0.0, Life.age(today.plusDays(5)), 0.0)
        assertTrue(Life.age(today.minusYears(30)) in 30.0..30.01)
        assertEquals(1, Life.dayOfLife(today, today))
        assertEquals("1,234,567", Life.whole(1234567.9))
    }
}
