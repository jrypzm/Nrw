package app.years.home

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Process
import android.view.View
import android.widget.EditText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Every new feature, rendered on a real-size phone, and checked. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class FeaturesTest {
    private val day = 86_400_000L
    private val names = listOf("Phone", "Messages", "Camera", "WhatsApp", "Spotify", "Maps", "Calculator", "Calendar", "Chrome", "Clock", "Gallery", "Gmail",
        "Instagram", "Netflix", "Kindle", "Settings", "Signal", "Snapchat", "Strava", "Telegram", "Uber Eats", "YouTube", "Amazon", "Bank", "Booking", "Fitbit", "Lime", "Shazam", "Tesco")
    private fun app(n: String, installed: Long = System.currentTimeMillis() - 400 * day, profile: Int = 0) =
        App("p.$n/.Main#$profile", n, "p.${n.lowercase().replace(" ", "")}", ComponentName("p.${n.lowercase().replace(" ", "")}", ".Main"), Process.myUserHandle(), profile == 1, installed, profile)
    private fun key(n: String) = "p.$n/.Main#0"

    private fun shot(v: View, name: String) {
        val r = v.rootView; val b = Bitmap.createBitmap(r.width, r.height, Bitmap.Config.ARGB_8888)
        r.draw(android.graphics.Canvas(b))
        File("build/screens").mkdirs(); File("build/screens/$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun idle(ms: Long = 0) { val l = shadowOf(android.os.Looper.getMainLooper()); if (ms > 0) l.idleFor(Duration.ofMillis(ms)); l.idle() }

    @After fun reset() {
        Live.notes = emptyMap(); Live.people = emptyList(); Live.now = emptyList(); Live.hour = emptyList(); Live.sessions = emptyList(); Live.holding = 0
    }

    private fun home(): Pair<org.robolectric.android.controller.ActivityController<HomeActivity>, HomeActivity> {
        val c = Robolectric.buildActivity(HomeActivity::class.java).setup(); val a = c.get(); idle()
        Apps.setForTest(names.map { app(it) })
        val p = Prefs.get(a); p.favorites = listOf("Phone", "Messages", "Camera", "WhatsApp", "Spotify").map { key(it) }; p.favoritesSet = true
        return c to a
    }

    @Test fun glow_people_live_hour() {
        val (c, a) = home()
        val pi = PendingIntent.getActivity(a, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        val reply = Notification.Action.Builder(null, "Reply", pi).addRemoteInput(RemoteInput.Builder("text").setLabel("Reply").build()).build()
        val t = System.currentTimeMillis()
        Live.notes = mapOf("p.messages" to Live.Note("Mum", "Are you coming for dinner?", t - 5 * 60_000, 2), "p.whatsapp" to Live.Note("Sam", "On my way", t - 12 * 60_000, 3))
        Live.people = listOf(
            Live.Person("Mum", "Are you coming for dinner? Bring the photos.", "p.messages", t - 5 * 60_000, 2, pi, reply),
            Live.Person("Sam", "On my way, 10 mins", "p.whatsapp", t - 12 * 60_000, 1, pi, reply),
        )
        Live.now = listOf(
            Live.Now("k1", "p.ubereats", "Uber Eats", "Arriving 7:42", 62, 100, false, t, false, false, pi),
            Live.Now("k2", "p.clock", "Timer", "Pasta", 0, 0, false, t + 252_000, true, true, pi),
        )
        Live.hour = listOf(Live.Held("h1", "p.amazon", "Your order has shipped", "Arriving Tuesday", t - 3_600_000, t - 60_000), Live.Held("h2", "p.instagram", "alex.k liked your photo", "", t - 3_000_000, t - 60_000))
        Live.changed(); c.pause().resume(); idle(600)
        val root = a.window.decorView
        shot(root, "20-glow-people-live")
        // the Hour's digest
        val m = HomeActivity::class.java.getDeclaredMethod("showHour"); m.isAccessible = true; m.invoke(a); idle(800)
        shot(root, "21-the-hour")
        // reply to Mum without opening the app
        val r = HomeActivity::class.java.getDeclaredMethod("replyTo", Live.Person::class.java); r.isAccessible = true
        r.invoke(a, Live.people[0]); idle(800)
        shot(root, "21b-reply")
    }

    /** The bug from a real phone: music playing and seven apps on home pushed the apps off the bottom of the screen. */
    @Test fun home_always_fits() {
        val (c, a) = home()
        val p = Prefs.get(a)
        val root = a.window.decorView
        val ms = android.media.session.MediaSession(a, "t"); val mc = android.media.session.MediaController(a, ms.sessionToken); val sh = shadowOf(mc); sh.setPackageName("p.spotify")
        sh.setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "øneheart - her eyes").putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "dreamscape").putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, 120_000).build())
        sh.setPlaybackState(android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PLAYING, 7_000, 1f, android.os.SystemClock.elapsedRealtime()).build())
        fun check(n: Int, music: Boolean, live: Int, name: String?) {
            p.favorites = names.take(n).map { key(it) }
            Live.meta.clear(); Live.state.clear()
            Live.sessions = if (music) listOf(mc) else emptyList()
            val pi = PendingIntent.getActivity(a, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
            Live.now = (1..live).map { Live.Now("k$it", "p.clock", "Timer $it", "Pasta", 0, 0, false, System.currentTimeMillis() + 60_000, true, true, pi) }
            Live.changed(); Live.mediaChanged(); c.pause().resume(); idle(700); idle(300)
            val words = root.findViewsOfType(GlowText::class.java).filter { it.text.toString() in names.take(n) && it.isShown && it.textSize > 18 * a.resources.displayMetrics.scaledDensity && it.height > 0 }
            assertEquals("$n apps, music=$music, live=$live: all on home", n, words.size)
            val loc = IntArray(2)
            for (w in words) { w.getLocationInWindow(loc); assertTrue("${w.text} ends at ${loc[1] + w.height} of ${root.height} ($n apps, music=$music, live=$live)", loc[1] + w.height - w.paddingBottom <= root.height) }
            // and nothing overlaps what's above
            val first = words.minOf { it.getLocationInWindow(loc); loc[1] + it.paddingTop }
            root.findViewsOfType(MediaCard::class.java).firstOrNull { it.isShown }?.let { it.getLocationInWindow(loc); assertTrue("apps start below the music card", first >= loc[1] + it.height) }
            if (name != null) shot(root, name)
        }
        check(7, true, 0, "34-fits-7-apps-music")
        check(8, true, 2, "35-fits-8-apps-music-timers")
        check(8, false, 0, null)
        check(3, true, 0, "36-roomy-again")
        for (n in 1..8) for (m in listOf(true, false)) for (l in 0..2) check(n, m, l, null)
    }

    @Test fun home_is_calm() {
        val (c, a) = home()
        val root = a.window.decorView
        c.pause().resume(); idle(500)
        fun words() = root.findViewsOfType(GlowText::class.java).filter { it.isShown && it.text.toString() in names }
        val before = words()
        assertEquals(5, before.size)
        // an update that changes nothing on home rebuilds nothing: same views, so no flicker and no lost press
        Live.changed(); Live.mediaChanged(); idle(300)
        assertTrue("nothing rebuilt", before.zip(words()).all { (x, y) -> x === y })
        // while a finger is down, a change waits; it lands when the finger lifts
        val t = android.os.SystemClock.uptimeMillis()
        fun ev(act: Int) = android.view.MotionEvent.obtain(t, t, act, root.width * .5f, root.height * .45f, 0).also { root.dispatchTouchEvent(it); it.recycle() }
        ev(android.view.MotionEvent.ACTION_DOWN)
        Live.notes = mapOf("p.messages" to Live.Note("Mum", "hi", System.currentTimeMillis(), 1)); Live.changed(); idle(300)
        assertTrue("waits for the finger", before.zip(words()).all { (x, y) -> x === y })
        ev(android.view.MotionEvent.ACTION_UP); idle(300)
        assertTrue("then updates", words().any { it.text.toString() == "Messages" && it.glowColor != 0 })
        // leaving home with the list open and a search typed: coming back is a clean home
        a.openDrawer(); idle(800)
        val search = root.findViewsOfType(EditText::class.java).first(); search.setText("spo"); idle()
        assertTrue(a.isDrawerOpen())
        c.pause().stop(); idle(); c.start().resume(); idle(500)
        assertTrue("list closed", !a.isDrawerOpen()); assertEquals("", search.text.toString())
    }

    @Test fun picker_box() {
        Apps.setForTest(names.map { app(it) })
        val ctx = androidx_test_context(); val p = Prefs.get(ctx)
        p.favorites = listOf("Phone", "Messages", "Camera").map { key(it) }
        val act = Robolectric.buildActivity(PickActivity::class.java, Intent(ctx, PickActivity::class.java).putExtra("mode", "home")).setup().get(); idle()
        val root = act.window.decorView
        fun cards() = root.findViewsOfType(Card::class.java)
        fun rows(c: Card) = (0 until c.childCount).map { c.getChildAt(it) }.filterIsInstance<android.widget.LinearLayout>()
        fun names(c: Card) = rows(c).map { r -> r.findViewsOfType(android.widget.TextView::class.java).first { it.textSize > 15 * ctx.resources.displayMetrics.scaledDensity }.text.toString() }
        fun tap(c: Card, n: String) { rows(c)[names(c).indexOf(n)].performClick(); idle(300) }
        fun caps() = root.findViewsOfType(android.widget.TextView::class.java).map { it.text.toString() }
        // the box holds exactly what's on home, in order; the list below holds everything else
        assertEquals(listOf("Phone", "Messages", "Camera"), names(cards()[0]))
        assertTrue("Phone" !in names(cards()[1])); assertTrue("WhatsApp" in names(cards()[1]))
        assertTrue(caps().contains("ON HOME · 3 OF 8"))
        shot(root, "40-picker")
        // tap one below: it moves into the box, at the end, and is saved
        tap(cards()[1], "Spotify")
        assertEquals(listOf("Phone", "Messages", "Camera", "Spotify"), names(cards()[0])); assertTrue("Spotify" !in names(cards()[1]))
        assertEquals(listOf("Phone", "Messages", "Camera", "Spotify").map { key(it) }, p.favorites)
        // tap one in the box: it goes back to the list, in its A to Z place
        tap(cards()[0], "Messages")
        assertEquals(listOf("Phone", "Camera", "Spotify"), names(cards()[0])); assertTrue("Messages" in names(cards()[1]))
        assertEquals(names(cards()[1]).sortedBy { it.lowercase() }, names(cards()[1]))
        // the arrow moves an app up one place
        rows(cards()[0])[2].findViewsOfType(PickActivity.Mark::class.java).first { it.hasOnClickListeners() }.performClick(); idle(300)
        assertEquals(listOf("Phone", "Spotify", "Camera"), names(cards()[0])); assertEquals(listOf("Phone", "Spotify", "Camera").map { key(it) }, p.favorites)
        // eight is full: a ninth isn't added, and the page says why
        for (n in listOf("Maps", "Calculator", "Calendar", "Chrome", "Clock")) tap(cards()[1], n)
        assertEquals(8, names(cards()[0]).size); assertTrue(caps().contains("ON HOME · 8 OF 8"))
        tap(cards()[1], "Gallery")
        assertEquals(8, p.favorites.size); assertTrue(caps().any { it.startsWith("Home holds 8 apps") })
        shot(root, "41-picker-full")
        // emptied: a line says what to do
        for (n in names(cards()[0]).toList()) tap(cards()[0], n)
        assertTrue(p.favorites.isEmpty()); assertTrue(caps().any { it.startsWith("Nothing here yet") })
        // the other pages use the same box
        p.paused = setOf(key("Instagram"))
        val pa = Robolectric.buildActivity(PickActivity::class.java, Intent(ctx, PickActivity::class.java).putExtra("mode", "pause")).setup().get(); idle()
        assertEquals(listOf("Instagram"), pa.window.decorView.findViewsOfType(Card::class.java)[0].let { names(it) })
    }

    @Test fun timeline() {
        val (c, a) = home()
        val root = a.window.decorView
        val ms = android.media.session.MediaSession(a, "t"); val mc = android.media.session.MediaController(a, ms.sessionToken); val sh = shadowOf(mc); sh.setPackageName("p.spotify")
        sh.setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "her eyes").putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "øneheart").putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, 120_000).build())
        fun state(actions: Long) = sh.setPlaybackState(android.media.session.PlaybackState.Builder().setActions(actions).setState(android.media.session.PlaybackState.STATE_PAUSED, 30_000, 0f, android.os.SystemClock.elapsedRealtime()).build())
        state(android.media.session.PlaybackState.ACTION_SEEK_TO)
        fun show(n: Int) { Prefs.get(a).favorites = names.take(n).map { key(it) }; Live.meta.clear(); Live.state.clear(); Live.sessions = listOf(mc); Live.played("p.spotify", mc.playbackState); Live.changed(); Live.mediaChanged(); c.pause().resume(); idle(700); idle(300) }
        for ((n, name) in listOf(3 to "42-timeline-full", 8 to "43-timeline-8-apps")) {
            show(n)
            val card = root.findViewsOfType(MediaCard::class.java).first { it.isShown }
            card.compact = false
            val bar = card.findViewsOfType(MediaCard.Progress::class.java).first()
            // the timeline is there in both sizes of the card, a quarter of the way along, with a handle to drag
            assertTrue("$n apps: timeline shown", bar.isShown && bar.width > 0 && bar.height > 0)
            assertEquals(.25f, bar.f, .01f); assertTrue(bar.seekable)
            // fold the card as a small screen would: the timeline must still be there
            if (n == 8) { card.compact = true; idle(300); assertTrue("folded card keeps its timeline", bar.isShown && bar.width > 0 && bar.height > 0) }
            shot(root, name)
            // drag it to three quarters: the line follows the finger and stays there
            val t = android.os.SystemClock.uptimeMillis(); val y = bar.height / 2f
            fun ev(act: Int, x: Float) = android.view.MotionEvent.obtain(t, t, act, x, y, 0).also { bar.dispatchTouchEvent(it); it.recycle() }
            ev(android.view.MotionEvent.ACTION_DOWN, bar.width * .3f); assertTrue(bar.dragging)
            ev(android.view.MotionEvent.ACTION_MOVE, bar.width * .75f); ev(android.view.MotionEvent.ACTION_UP, bar.width * .75f)
            assertTrue(!bar.dragging); assertEquals(.75f, bar.f, .03f)
            idle(600); assertEquals("stays where you put it", .75f, bar.f, .03f)
            if (n == 3) assertTrue(card.findViewsOfType(android.widget.TextView::class.java).any { it.text.toString() == "1:30" || it.text.toString() == "1:29" || it.text.toString() == "1:31" })
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
        }
        // a player that doesn't allow seeking: the line is there, without a handle, and can't be dragged
        state(0L); show(3)
        val bar = root.findViewsOfType(MediaCard::class.java).first { it.isShown }.findViewsOfType(MediaCard.Progress::class.java).first()
        assertTrue(bar.isShown && !bar.seekable)
    }

    @Test fun smart_search() {
        val (_, a) = home()
        a.openDrawer(); idle(800)
        val root = a.window.decorView
        val search = root.findViewsOfType(EditText::class.java).first()
        for ((q, name) in listOf("7:30" to "22-search-alarm", "10 min" to "23-search-timer", ".yt lofi" to "24-search-inside", "07911 123456" to "25-search-phone", "days to christmas" to "26-search-days")) {
            search.setText(q); idle(); shot(root, name)
        }
    }

    @Test fun new_quiet_work_private() {
        val (_, a) = home()
        val now = System.currentTimeMillis()
        val apps = names.map { n -> when (n) { "Shazam", "Tesco" -> app(n, now - day); else -> app(n) } } + listOf(app("Slack", profile = 1), app("Teams", profile = 1), app("Drive", profile = 1))
        // usage access says these haven't been opened in months
        val used = names.associate { "p.${it.lowercase().replace(" ", "")}" to now - 2 * day } + listOf("Booking", "Fitbit", "Lime", "Kindle").associate { "p.${it.lowercase()}" to now - 60 * day }
        Apps.setForTest(apps, privUser = Process.myUserHandle(), privLocked = true, work = Process.myUserHandle(), used = used)
        a.openDrawer(); idle(800)
        val root = a.window.decorView
        shot(root, "27-list-new")
        val list = root.findViewsOfType(android.widget.ListView::class.java).first()
        list.setSelection(list.count - 1); idle(); shot(root, "28-list-quiet-private")
    }

    @Test fun away() {
        val c = Robolectric.buildActivity(HomeActivity::class.java).setup(); val a = c.get(); idle()
        Apps.setForTest(names.map { app(it) })
        val p = Prefs.get(a); p.favorites = listOf("Phone", "Messages", "Camera").map { key(it) }; p.favoritesSet = true
        for (d in 1..6) p.logAway(java.time.LocalDate.now().minusDays(d.toLong()).toString(), listOf(40, 75, 30, 95, 60, 80)[d - 1])
        // the screen went off 2 h 41 m ago, and you just unlocked
        p.offAt = System.currentTimeMillis() - (2 * 60 + 41) * 60_000L
        Away.unlocked(a)
        assertTrue(Away.pending(a) != null)
        c.pause().resume(); idle(600)
        shot(a.window.decorView, "29-away")
        assertTrue("shown once", Away.pending(a) == null)
    }

    @Test fun light() {
        Torch.resetForTest()
        val cm = androidx_test_context().getSystemService(android.hardware.camera2.CameraManager::class.java)
        val cc = org.robolectric.shadows.ShadowCameraCharacteristics.newCameraCharacteristics()
        shadowOf(cc).set(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE, true)
        shadowOf(cc).set(android.hardware.camera2.CameraCharacteristics.LENS_FACING, android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK)
        shadowOf(cm).addCamera("0", cc)
        val (c, a) = home()
        Prefs.get(a).favorites = listOf("Phone", "Messages", "Camera").map { key(it) } + HomeActivity.LIGHT
        c.pause().resume(); idle()
        assertTrue(Torch.available)
        assertTrue(Torch.toggle()); idle(300)
        assertTrue(Torch.on)
        shot(a.window.decorView, "30-light")
        Torch.set(false); Torch.resetForTest()
    }

    @Test fun pause_grows() {
        Apps.setForTest(names.map { app(it) })
        val ctx = androidx_test_context()
        repeat(3) { Prefs.get(ctx).countOpen(key("Instagram")) }
        val i = Intent(ctx, PauseActivity::class.java).putExtra("key", key("Instagram"))
        // the test clock runs the breathing animation, so time moves during setup: measure it
        val t0 = android.os.SystemClock.uptimeMillis()
        val ctl = Robolectric.buildActivity(PauseActivity::class.java, i).setup(); val p = ctl.get()
        val texts = { p.window.decorView.findViewsOfType(android.widget.TextView::class.java).map { it.text.toString() } }
        val gone = ((android.os.SystemClock.uptimeMillis() - t0) / 1000).toInt()
        // the 4th open today waits 12 seconds, counting down
        // earlier tests may have opened it too: the wait follows the real count
        val wait = PauseActivity.waitFor(Prefs.get(ctx).opensToday(key("Instagram")), false)
        assertTrue("$gone s passed: ${texts()}", texts().contains("Open in ${wait - gone} s"))
        shot(p.window.decorView, "31-pause")
    }

    @Test fun moments() {
        val (c, a) = home()
        val p = Prefs.get(a)
        p.momentsOn = true
        for (m in listOf("morning", "work", "evening", "night")) p.setMoment(m, listOf("Spotify", "Messages", "Netflix", "Kindle").map { key(it) })
        c.pause().resume(); idle()
        shot(a.window.decorView, "32-moments")
        // only the big words on home, not the (closed) list of everything
        val big = 30 * a.resources.displayMetrics.scaledDensity
        val shown = a.window.decorView.findViewsOfType(android.widget.TextView::class.java).filter { it.textSize > big }.map { it.text.toString() }
        // weekends have no "work" part: those hours show your usual home
        val t = java.time.LocalDateTime.now(); val usual = t.hour in 9..16 && t.dayOfWeek.value >= 6
        if (usual) assertTrue(shown.contains("Camera")) else { assertTrue(shown.contains("Netflix")); assertTrue(!shown.contains("Camera")) }
        p.momentsOn = false
    }

    @Test fun settings() {
        Apps.setForTest(names.map { app(it) })
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        val scroll = (s.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)) as android.widget.ScrollView
        scroll.scrollTo(0, s.resources.displayMetrics.heightPixels); idle(); shot(s.window.decorView, "33-settings")
        val texts = s.window.decorView.findViewsOfType(android.widget.TextView::class.java).map { it.text.toString() }
        for (t in listOf("Words that glow", "People waiting", "The live line", "Away time", "New and quiet", "Moments", "THE HOUR")) assertTrue("settings has $t", texts.any { it.equals(t, true) })
    }

    private fun androidx_test_context(): android.content.Context = org.robolectric.RuntimeEnvironment.getApplication()
}

/** Every view of a type under this one. */
fun <T : View> View.findViewsOfType(c: Class<T>): List<T> {
    val out = ArrayList<T>()
    fun walk(v: View) { if (c.isInstance(v)) out += c.cast(v)!!; if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
    walk(this); return out
}
