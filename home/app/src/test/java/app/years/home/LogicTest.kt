package app.years.home

import android.app.Notification
import android.content.ComponentName
import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The thinking behind the features, case by case. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogicTest {
    private val now = LocalDateTime.of(2026, 11, 15, 11, 4)

    @Test fun alarms() {
        // at 11:04, the next 7:30 is this evening, so 7:30 pm leads and 7:30 am is offered too
        val a = Smart.alarms("7:30", now, false)
        assertEquals(2, a.size)
        assertEquals("7:30 pm", a[0].value); assertTrue(a[0].big); assertEquals("Today · in 8 h 26 min", a[0].sub)
        assertEquals("7:30 am", a[1].value); assertFalse(a[1].big)
        // after 7:30 pm, "7:30" is tomorrow morning first
        val late = Smart.alarms("7:30", LocalDateTime.of(2026, 11, 15, 21, 0), false)
        assertEquals("7:30 am", late[0].value); assertTrue(late[0].sub.startsWith("Tomorrow"))
        assertEquals("6:45 am", Smart.alarms("6:45am", now, false).single().value)
        assertEquals("7:00 pm", Smart.alarms("7pm", now, false).single().value)
        assertEquals("19:00", Smart.alarms("19:00", now, true).single().value)
        assertEquals(mapOf("h" to 19, "m" to 0), Smart.alarms("19:00", now, false).single().data)
        assertEquals(2, Smart.alarms("alarm 6", now, false).size)
        assertTrue(Smart.alarms("wake me at 6:15", now, false).isNotEmpty())
        // numbers and nonsense are not alarms
        assertTrue(Smart.alarms("7", now, false).isEmpty())
        assertTrue(Smart.alarms("25:00", now, false).isEmpty())
        assertTrue(Smart.alarms("7:75", now, false).isEmpty())
        assertTrue(Smart.alarms("13pm", now, false).isEmpty())
    }

    @Test fun timers() {
        assertEquals(600, Smart.timer("10 min")!!.data["s"])
        assertEquals("10 min", Smart.timer("10 min")!!.value)
        assertEquals(5400, Smart.timer("1h 30m")!!.data["s"])
        assertEquals(90, Smart.timer("90 sec")!!.data["s"])
        assertEquals(300, Smart.timer("timer 5")!!.data["s"])
        assertEquals(3600, Smart.timer("1 hour")!!.data["s"])
        // "5 m" is five metres, not a timer; plain words aren't timers
        assertNull(Smart.timer("5 m")); assertNull(Smart.timer("maps")); assertNull(Smart.timer("0 min"))
        assertNotNull(Search.answer("5 m"))
    }

    @Test fun phones() {
        assertEquals(listOf(Smart.CALL, Smart.TEXT, Smart.SAVE), Smart.phone("07911 123456").map { it.kind })
        assertEquals("+447911123456", Smart.phone("+44 7911 123456")[0].data["n"])
        assertTrue(Smart.phone("12345").isEmpty())
        assertTrue(Smart.phone("12 × 8").isEmpty())
    }

    @Test fun inside() {
        val a = Smart.inside(".yt lofi beats")!!
        assertEquals("youtube", a.data["app"]); assertEquals("lofi beats", a.data["q"]); assertTrue(a.big)
        assertEquals("spotify", Smart.inside(".sp")!!.data["app"])
        assertFalse(Smart.inside(".sp")!!.big)
        assertEquals("web", Smart.inside(".g weather")!!.data["app"])
        assertEquals("netflix", Smart.inside(".netflix dune")!!.data["app"])
        assertNull(Smart.inside("yt lofi")); assertNull(Smart.inside("3.5"))
        // in a full search, ".yt" is the only thing offered
        assertEquals(1, Smart.actions(".yt 7:30", now).size)
    }

    @Test fun dates() {
        val today = LocalDate.of(2026, 11, 15)
        assertEquals("40 days", Smart.date("days to 25 dec", today)!!.value)
        assertEquals("40 days", Smart.date("christmas", today)!!.value)
        assertEquals("Friday 25 December", Smart.date("days until christmas", today)!!.sub)
        assertEquals("47 days", Smart.date("how long until new year", today)!!.value)
        assertEquals("1 day", Smart.date("days to tomorrow", today)!!.value)
        assertEquals("Sunday 6 December", Smart.date("in 3 weeks", today)!!.value)
        assertEquals("Thursday 5 November", Smart.date("10 days ago", today)!!.value)
        assertEquals("Sunday 6 December", Smart.date("3 weeks from today", today)!!.value)
        assertEquals(LocalDate.of(2027, 3, 1), Smart.parseDay("1 march", today))
        assertEquals(LocalDate.of(2026, 12, 31), Smart.parseDay("dec 31", today))
        assertEquals(LocalDate.of(2027, 1, 2), Smart.parseDay("2/1", today))
        assertEquals(LocalDate.of(2026, 11, 20), Smart.parseDay("friday", today))
        assertNull(Smart.date("3 weeks", today)); assertNull(Smart.date("days to nowhere", today)); assertNull(Smart.date("hello", today))
    }

    @Test fun light() {
        assertEquals(Smart.LIGHT, Smart.actions("torch", now).single().kind)
        assertEquals(Smart.LIGHT, Smart.actions("ligh", now).single().kind)
        assertTrue(Smart.actions("li", now).isEmpty())
    }

    @Test fun kinds() {
        val k = Kinds::kind
        // a shop's "your order shipped" waits for The Hour; a message never does
        assertEquals(Kinds.Kind.QUIET, k(null, false, false, false, false, false, false, false, false))
        assertEquals(Kinds.Kind.QUIET, k(Notification.CATEGORY_PROMO, false, false, false, false, false, false, false, false))
        assertEquals(Kinds.Kind.QUIET, k(Notification.CATEGORY_SOCIAL, false, false, false, false, false, false, false, false))
        assertEquals(Kinds.Kind.PERSON, k(null, false, false, true, false, false, false, false, false))
        assertEquals(Kinds.Kind.PERSON, k(null, false, false, false, true, false, false, false, false))
        assertEquals(Kinds.Kind.PERSON, k("missed_call", false, false, false, false, false, false, false, false))
        assertEquals(Kinds.Kind.PASS, k(Notification.CATEGORY_ALARM, false, false, false, false, false, false, false, false))
        assertEquals(Kinds.Kind.PASS, k(Notification.CATEGORY_REMINDER, false, false, false, false, false, false, false, false))
        assertEquals(Kinds.Kind.PASS, k(null, false, false, false, false, false, false, false, true))
        // live: a running timer, a call, a delivery with progress
        assertEquals(Kinds.Kind.NOW, k(Notification.CATEGORY_STOPWATCH, true, false, false, false, false, true, false, false))
        assertEquals(Kinds.Kind.NOW, k(Notification.CATEGORY_CALL, true, false, false, false, false, true, false, false))
        assertEquals(Kinds.Kind.NOW, k(null, true, false, false, false, true, false, false, false))
        assertEquals(Kinds.Kind.NOW, k(null, true, false, false, false, false, false, true, false))
        // "USB debugging connected" is ongoing but not news, and music has its own card
        assertEquals(Kinds.Kind.PASS, k(Notification.CATEGORY_SYSTEM, true, false, false, false, false, false, false, false))
        assertEquals(Kinds.Kind.MEDIA, k(Notification.CATEGORY_TRANSPORT, true, true, false, false, false, false, false, false))
    }

    @Test fun hours() {
        val z = ZoneId.of("Europe/London")
        fun at(h: Int, m: Int, d: Int = 15) = LocalDateTime.of(2026, 11, d, h, m).atZone(z).toInstant().toEpochMilli()
        val times = listOf(780, 1140) // 1 pm and 7 pm
        assertEquals(at(13, 0), Kinds.nextHour(at(11, 4), times, z))
        assertEquals(at(19, 0), Kinds.nextHour(at(13, 0), times, z))
        // within 30 seconds of an Hour, the next one is the one after
        assertEquals(at(19, 0), Kinds.nextHour(at(13, 0) - 10_000, times, z))
        assertEquals(at(13, 0, 16), Kinds.nextHour(at(20, 0), times, z))
        assertEquals("1 pm", Kinds.hourName(780, false)); assertEquals("7:30 pm", Kinds.hourName(1170, false)); assertEquals("12 pm", Kinds.hourName(720, false))
        assertEquals("12 am", Kinds.hourName(0, false)); assertEquals("13:00", Kinds.hourName(780, true))
    }

    @Test fun newAndQuiet() {
        val day = 86_400_000L; val now = 100 * day
        fun app(k: String, installed: Long) = App(k, k, "p.$k", ComponentName("p.$k", ".M"), Process.myUserHandle(), false, installed)
        val fresh = app("Fresh", now - day)
        assertTrue(Apps.isNew(fresh, now, emptyMap()))
        assertFalse("opened once, it's not new any more", Apps.isNew(fresh, now, mapOf(fresh.key to now)))
        assertFalse(Apps.isNew(app("Old", now - 10 * day), now, emptyMap()))
        val old = app("Old", now - 90 * day)
        // yrs. home has only watched for a week: nothing is quiet yet
        assertFalse(Apps.isQuiet(old, now, emptyMap(), emptyMap(), now - 7 * day))
        // after a month of watching, never opened is quiet
        assertTrue(Apps.isQuiet(old, now, emptyMap(), emptyMap(), now - 40 * day))
        assertFalse(Apps.isQuiet(old, now, mapOf(old.key to now - day), emptyMap(), now - 40 * day))
        // usage access knows better, from day one
        assertTrue(Apps.isQuiet(old, now, emptyMap(), mapOf(old.pkg to now - 45 * day), now))
        assertFalse(Apps.isQuiet(old, now, emptyMap(), mapOf(old.pkg to now - 2 * day), now))
        assertFalse("just installed is never quiet", Apps.isQuiet(fresh, now, emptyMap(), mapOf("x" to 1L), now - 40 * day))
    }

    @Test fun pause() {
        assertEquals(3, PauseActivity.waitFor(0, false)); assertEquals(12, PauseActivity.waitFor(3, false))
        assertEquals(30, PauseActivity.waitFor(20, false)); assertEquals(24, PauseActivity.waitFor(3, true))
        assertEquals("4th", PauseActivity.ordinal(4)); assertEquals("2nd", PauseActivity.ordinal(2)); assertEquals("11th", PauseActivity.ordinal(11)); assertEquals("21st", PauseActivity.ordinal(21))
    }

    @Test fun away() {
        assertEquals("2 h 41 m", Away.words((2 * 60 + 41) * 60_000L)); assertEquals("45 m", Away.words(45 * 60_000L))
    }
}
