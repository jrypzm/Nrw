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
        assertTrue("$gone s passed: ${texts()}", texts().contains("Open in ${12 - gone} s"))
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
