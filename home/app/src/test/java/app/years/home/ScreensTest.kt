package app.years.home

import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Process
import android.view.View
import android.widget.EditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Real renders of every screen, at the S24 Ultra's size, and the logic behind search. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreensTest {
    private val names = listOf("Phone", "Messages", "Camera", "WhatsApp", "Spotify", "Maps", "Calculator", "Calendar", "Chrome", "Clock", "Gallery", "Gmail", "Google Maps",
        "Instagram", "Music", "Netflix", "Notes", "Settings", "Signal", "Snapchat", "Speedtest", "Strava", "Uber", "YouTube", "Zoom", "Authenticator", "Amazon", "Bank")
    private fun fake() = names.map { App("p.$it/.Main#0", it, "p.${it.lowercase()}", ComponentName("p.${it.lowercase()}", ".Main"), Process.myUserHandle(), false) }

    private fun shot(v: View, name: String) {
        val w = v.rootView.width; val h = v.rootView.height
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.rootView.draw(android.graphics.Canvas(b))
        File("build/screens").mkdirs(); File("build/screens/$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()

    @Test fun screens() {
        val c = Robolectric.buildActivity(HomeActivity::class.java).setup()
        val a = c.get(); idle()
        Apps.setForTest(fake())
        Prefs.get(a).favorites = listOf("Phone", "Messages", "Camera", "WhatsApp", "Spotify", "Maps").map { "p.$it/.Main#0" }
        Prefs.get(a).favoritesSet = true
        Live.notes = mapOf("p.messages" to Live.Note("Mum", "Are you coming for dinner on Sunday? Bring the photos.", System.currentTimeMillis() - 120000), "p.whatsapp" to Live.Note("Sam", "On my way", System.currentTimeMillis()))
        c.pause().resume(); idle()
        val root = a.window.decorView
        shot(root, "1-home")
        a.openDrawer(); shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800)); idle()
        shot(root, "2-everything")
        val search = (root.findViewsOfType(EditText::class.java)).first()
        search.setText("sp"); idle(); shot(root, "3-search")
        search.setText("12 × 8.5 + 3"); idle(); shot(root, "4-calculator")
        search.setText("5 km"); idle(); shot(root, "5-convert")
        a.closeDrawer(); shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800)); idle()
        // the thumb rail: down on the right edge, slide to S, then toward the middle onto an app
        val w = root.width.toFloat(); val h = root.height.toFloat()
        var t = 1000L; var down = 1000L
        fun ev(act: Int, x: Float, y: Float) { t += 16; if (act == android.view.MotionEvent.ACTION_DOWN) down = t; android.view.MotionEvent.obtain(down, t, act, x, y, 0).also { root.dispatchTouchEvent(it); it.recycle() } }
        ev(android.view.MotionEvent.ACTION_DOWN, w - 10f, h * .3f)
        for (k in 1..12) ev(android.view.MotionEvent.ACTION_MOVE, w - 10f, h * .3f + k * (h * .42f / 12))
        idle(); shot(root, "6a-rail-letter")
        ev(android.view.MotionEvent.ACTION_MOVE, w * .55f, h * .5f); idle(); shot(root, "6b-rail-app")
        ev(android.view.MotionEvent.ACTION_CANCEL, w * .55f, h * .5f); shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
        // a swipe up from the middle opens the list; a pull down from its top closes it
        ev(android.view.MotionEvent.ACTION_DOWN, w * .5f, h * .6f)
        for (k in 1..10) ev(android.view.MotionEvent.ACTION_MOVE, w * .5f, h * .6f - k * h * .05f)
        ev(android.view.MotionEvent.ACTION_UP, w * .5f, h * .1f); shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800)); idle()
        assertTrue("swipe up opens the list", a.isDrawerOpen())
        a.closeDrawer(); shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800)); idle()
        assertTrue("and it closes", !a.isDrawerOpen())
        // hold WhatsApp on home
        val m = HomeActivity::class.java.getDeclaredMethod("showSheet", App::class.java); m.isAccessible = true
        m.invoke(a, Apps.byKey("p.Messages/.Main#0")); shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800)); idle()
        shot(root, "6-hold")
    }

    @Test fun pages() {
        Apps.setForTest(fake())
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(); shot(s.window.decorView, "7-settings")
        Prefs.get(s).countOpen("p.Instagram/.Main#0"); Prefs.get(s).countOpen("p.Instagram/.Main#0")
        val i = android.content.Intent(s, PauseActivity::class.java).putExtra("key", "p.Instagram/.Main#0")
        val p = Robolectric.buildActivity(PauseActivity::class.java, i).setup().get(); shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1500)); shot(p.window.decorView, "8-pause")
        val pick = Robolectric.buildActivity(PickActivity::class.java, android.content.Intent(s, PickActivity::class.java).putExtra("mode", "home")).setup().get(); idle(); shot(pick.window.decorView, "9-pick")
    }

    @Test fun search_logic() {
        Apps.setForTest(fake())
        assertEquals("Spotify", Search.apps("spo").first().app.label)
        assertEquals("Gmail", Search.apps("gm").first().app.label)
        assertTrue(Search.apps("gm").any { it.app.label == "Google Maps" })
        assertEquals("Snapchat", Search.apps("snch").first().app.label)
        assertTrue(Search.apps("zzzz").isEmpty())
        assertEquals("105", Search.answer("12 × 8.5 + 3")!!.value)
        assertEquals("3.11 mi", Search.answer("5 km")!!.value)
        assertEquals("86 °F", Search.answer("30 c")!!.value)
        assertEquals("1,234,567.5", Search.fmt(1234567.5))
        assertEquals(null, Search.answer("hello"))
        assertEquals(null, Calc.eval("2+"))
        assertEquals(8.0, Calc.eval("2^3")!!, 0.0)
        assertEquals(0.5, Calc.eval("50%")!!, 0.0)
        assertNotNull(Search.settings("wifi").firstOrNull())
    }
}

private fun <T : View> View.findViewsOfType(cls: Class<T>): List<T> {
    val out = mutableListOf<T>()
    fun walk(v: View) { if (cls.isInstance(v)) out += cls.cast(v)!!; if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
    walk(this); return out
}
