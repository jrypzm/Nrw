package app.years.home

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * A layout audit: every screen, on small and large phones and with large text, filled with awkward content.
 * Anything drawn off the screen, on top of something else, or squeezed to nothing fails.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AuditTest {
    private val day = 86_400_000L
    private val names = listOf("Phone", "Messages", "Google Play Services for AR", "WhatsApp Business", "Spotify", "Maps", "Calculator", "Samsung Internet Browser", "Chrome", "Clock", "Gallery", "Gmail",
        "Instagram", "Netflix", "Kindle", "Settings", "Signal", "Snapchat", "Strava", "Telegram", "Uber Eats", "YouTube", "Amazon", "Bank")
    private fun app(n: String, profile: Int = 0) = App("p.$n/.Main#$profile", n, "p.${n.lowercase().replace(" ", "")}", ComponentName("p.${n.lowercase().replace(" ", "")}", ".Main"), Process.myUserHandle(), profile == 1, System.currentTimeMillis() - 400 * day, profile)
    private fun key(n: String) = "p.$n/.Main#0"
    private fun idle(ms: Long = 0) { val l = shadowOf(android.os.Looper.getMainLooper()); if (ms > 0) l.idleFor(Duration.ofMillis(ms)); l.idle() }
    private val problems = ArrayList<String>()
    private var tag = ""

    @After fun reset() { Live.notes = emptyMap(); Live.people = emptyList(); Live.now = emptyList(); Live.hour = emptyList(); Live.sessions = emptyList(); Live.holding = 0; Live.meta.clear(); Live.state.clear(); RuntimeEnvironment.setFontScale(1f) }

    private fun shot(v: View, name: String) {
        val r = v.rootView; val b = Bitmap.createBitmap(r.width, r.height, Bitmap.Config.ARGB_8888); r.draw(android.graphics.Canvas(b))
        File("build/audit").mkdirs(); File("build/audit/$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun rect(v: View): Rect { val l = IntArray(2); v.getLocationInWindow(l); return Rect(l[0], l[1], l[0] + v.width, l[1] + v.height) }
    /** The part of a view that holds its content (glow padding and touch padding don't count). */
    private fun inner(v: View): Rect { val r = rect(v); return Rect(r.left + v.paddingLeft, r.top + v.paddingTop, r.right - v.paddingRight, r.bottom - v.paddingBottom) }
    private fun scrolls(v: View): Boolean { var p = v.parent; while (p is View) { if (p is ScrollView || p is ListView) return true; p = p.parent }; return false }
    /** Not really there: invisible, faded out, or inside something laid out at no size. */
    private fun faded(v: View): Boolean { var x: View? = v; while (x != null) { if (x.alpha < .05f || x.visibility != View.VISIBLE || x.width == 0 || x.height == 0) return true; x = x.parent as? View }; return false }

    /** Checks one layer of the screen: [scope] is the part that's in front. */
    private fun audit(root: View, scope: View, name: String) {
        shot(root, "$tag-$name")
        val screen = Rect(0, 0, root.width, root.height)
        val texts = scope.findViewsOfType(TextView::class.java).filter { it !is EditText && it.isShown && !faded(it) && it.text.isNotEmpty() && it.width > 0 && it.height > 0 }
        for (t in texts) {
            val r = inner(t)
            if (!scrolls(t) && !screen.contains(r)) problems += "$tag $name: “${t.text.toString().take(30)}” runs off the screen ($r of $screen)"
            // a line of text needs its own height
            val fm = t.paint.fontMetrics; val need = (fm.descent - fm.ascent) * 0.9f
            if (r.height() < need) problems += "$tag $name: “${t.text.toString().take(30)}” is squeezed (${r.height()} px for a ${need.toInt()} px line)"
            if (r.width() < t.paint.measureText(t.text.toString().take(1)) * .9f) problems += "$tag $name: “${t.text.toString().take(30)}” has no width (${r.width()} px)"
        }
        // no two pieces of text on top of each other
        val fixed = texts.filter { !scrolls(it) }
        for (i in fixed.indices) for (j in i + 1 until fixed.size) {
            val a = inner(fixed[i]); val b = inner(fixed[j])
            if (Rect.intersects(a, b)) { val x = Rect(a); x.intersect(b); if (x.width() > 4 && x.height() > 4) problems += "$tag $name: “${fixed[i].text.toString().take(24)}” overlaps “${fixed[j].text.toString().take(24)}”" }
        }
    }

    private fun run(t: String, fontScale: Float = 1f) {
        tag = t
        RuntimeEnvironment.setFontScale(fontScale)
        val pi = PendingIntent.getActivity(RuntimeEnvironment.getApplication(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        val reply = Notification.Action.Builder(null, "Reply", pi).addRemoteInput(RemoteInput.Builder("text").build()).build()
        val now = System.currentTimeMillis()

        // ---------------- home, as full as it gets
        val c = Robolectric.buildActivity(HomeActivity::class.java).setup(); val a = c.get(); idle()
        Apps.setForTest(names.map { app(it) } + app("Slack", 1), privUser = Process.myUserHandle(), privLocked = true, work = Process.myUserHandle())
        val p = Prefs.get(a); p.favoritesSet = true
        val root = a.window.decorView
        val home = (root.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup).getChildAt(0)
        val ms = android.media.session.MediaSession(a, "t"); val mc = android.media.session.MediaController(a, ms.sessionToken); shadowOf(mc).setPackageName("p.spotify")
        shadowOf(mc).setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "A very long song title that goes on and on (Remastered 2011 Deluxe Edition)")
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "An Artist With A Long Name feat. Another Artist & Friends").putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, 4_000_000).build())
        shadowOf(mc).setPlaybackState(android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PLAYING, 3_700_000, 1f, android.os.SystemClock.elapsedRealtime()).build())
        val people = listOf(
            Live.Person("Alexandra Montgomery-Fitzgerald", "A long message that should be cut off politely at the end of the line and never wrap strangely", "p.messages", now - 60_000, 12, pi, reply),
            Live.Person("Sam", "ok", "p.whatsappbusiness", now - 120_000, 1, pi, reply), Live.Person("Mum", "", "p.phone", now - 180_000, 1, pi, null, true))
        val live = listOf(Live.Now("k1", "p.ubereats", "Uber Eats with a long restaurant name", "Your order is arriving in about twelve minutes", 62, 100, false, now, false, false, pi),
            Live.Now("k2", "p.clock", "Timer", "Pasta", 0, 0, false, now + 3_700_000, true, true, pi))
        val hour = listOf(Live.Held("h1", "p.amazon", "Your order has shipped and will arrive on Tuesday between 9 and 5", "Track it", now - 3_600_000, now - 60_000))
        fun set(favs: Int, music: Boolean, lv: Int, pp: Int, hr: Boolean, name: String) {
            p.favorites = names.take(favs).map { key(it) }
            Live.sessions = if (music) listOf(mc) else emptyList(); Live.meta.clear(); Live.state.clear()
            Live.now = live.take(lv); Live.people = people.take(pp); Live.hour = if (hr) hour else emptyList()
            Live.notes = if (pp > 0) mapOf("p.messages" to Live.Note("Alexandra Montgomery-Fitzgerald", "hi", now, 12)) else emptyMap()
            Live.changed(); Live.mediaChanged(); c.pause().resume(); idle(700); idle(400)
            audit(root, home, name)
            // every app you put on home must be there, whole
            val shown = home.findViewsOfType(GlowText::class.java).filter { it.isShown && it.text.toString() in names.take(favs) }.map { it.text.toString() }.toSet()
            if (shown.size != favs && tag !in setOf("tiny", "smallhuge")) problems += "$tag $name: ${favs - shown.size} of $favs home apps missing"
        }
        set(8, true, 2, 3, true, "home-everything")
        set(8, false, 0, 0, false, "home-8")
        set(5, true, 0, 3, false, "home-5-music-people")
        set(3, false, 2, 3, true, "home-3-live-people")
        set(1, false, 0, 0, false, "home-1")
        set(6, true, 1, 1, true, "home-6-mixed")

        // ---------------- sheets
        fun call(m: String, vararg args: Any) { val f = HomeActivity::class.java.declaredMethods.first { it.name == m && it.parameterCount == args.size }; f.isAccessible = true; f.invoke(a, *args); idle(800) }
        fun sheet(): View { val f = HomeActivity::class.java.getDeclaredField("sheet"); f.isAccessible = true; return f.get(a) as View }
        fun close() { val f = HomeActivity::class.java.getDeclaredMethod("closeSheet"); f.isAccessible = true; f.invoke(a); idle(400) }
        Live.notes = mapOf("p.googleplayservicesforar" to Live.Note("A sender with a long name", "A long notification body. ".repeat(12), now - 60_000, 3))
        call("showSheet", Apps.byKey(key("Google Play Services for AR"))!!); audit(root, sheet(), "sheet-hold"); close()
        Live.hour = (1..9).map { Live.Held("h$it", "p.${names[it].lowercase().replace(" ", "")}", "Notification number $it with a fairly long title", "And some more text underneath it", now - it * 60_000, now - 60_000) }
        call("showHour"); audit(root, sheet(), "sheet-hour"); close()
        call("replyTo", people[0]); audit(root, sheet(), "sheet-reply"); close()
        call("rename", Apps.byKey(key("Samsung Internet Browser"))!!); audit(root, sheet(), "sheet-rename"); close()

        // ---------------- the list and search
        p.favorites = names.take(5).map { key(it) }
        a.openDrawer(); idle(800)
        val drawer = (root.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup).let { r -> (0 until r.childCount).map { r.getChildAt(it) }.first { v -> v.findViewsOfType(ListView::class.java).isNotEmpty() } }
        audit(root, drawer, "list")
        val list = drawer.findViewsOfType(ListView::class.java).first(); list.setSelection(list.count - 1); idle(); audit(root, drawer, "list-end")
        val search = drawer.findViewsOfType(EditText::class.java).first(); val t0 = a.resources.displayMetrics.scaledDensity
        for ((q, n) in listOf("7:30" to "alarm", "1h 30m 20s" to "timer", ".yt a very long search inside youtube that keeps going" to "inside", "+44 (0) 7911 123 456" to "phone", "days to christmas" to "days",
            "123456789*987654321" to "calc-big", "1,000,000 km to mi" to "convert", "s" to "apps", "google play services" to "long-app", "zzzzqqq" to "nothing", "in 3 weeks" to "date")) {
            search.setText(q); idle(); list.setSelection(0); idle(); audit(root, drawer, "search-$n")
            // a big answer must show whole, never end in "…"
            for (t in list.findViewsOfType(TextView::class.java).filter { it.isShown && it.typeface == Type.thin && it.textSize > 17 * t0 && it.layout != null }) if (n != "inside" && (0 until t.layout.lineCount).any { t.layout.getEllipsisCount(it) > 0 }) problems += "$tag search-$n: “${t.text.toString().take(24)}” is cut off"
            // single-line rows in the list must not be clipped vertically
            for (t in list.findViewsOfType(TextView::class.java).filter { it.isShown && it.text.isNotEmpty() }) if (t.height - t.paddingTop - t.paddingBottom < t.lineHeight * .8f) problems += "$tag search-$n: “${t.text.toString().take(24)}” is squeezed in the list"
        }
        search.setText(""); a.closeDrawer(); idle(800)

        // ---------------- the rail
        var tm = 1000L; var down = 1000L
        fun ev(act: Int, x: Float, y: Float) { tm += 16; if (act == android.view.MotionEvent.ACTION_DOWN) down = tm; android.view.MotionEvent.obtain(down, tm, act, x, y, 0).also { root.dispatchTouchEvent(it); it.recycle() } }
        val w = root.width.toFloat(); val h = root.height.toFloat()
        ev(android.view.MotionEvent.ACTION_DOWN, w - 8f, h * .5f); for (k in 1..6) ev(android.view.MotionEvent.ACTION_MOVE, w - 8f, h * .5f + k * 4f)
        ev(android.view.MotionEvent.ACTION_MOVE, w * .5f, h * .5f); idle(300); shot(root, "$tag-rail")
        ev(android.view.MotionEvent.ACTION_CANCEL, w * .5f, h * .5f); idle(400)

        // ---------------- pages
        fun page(v: View) = v.findViewById<ViewGroup>(android.R.id.content)
        p.hourOn = true; p.momentsOn = true
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(); audit(s.window.decorView, page(s.window.decorView), "settings")
        (page(s.window.decorView).getChildAt(0) as ScrollView).let { it.scrollTo(0, 100000); idle() }; audit(s.window.decorView, page(s.window.decorView), "settings-end")
        p.hourOn = false; p.momentsOn = false
        repeat(3) { p.countOpen(key("Google Play Services for AR")) }
        val pa = Robolectric.buildActivity(PauseActivity::class.java, Intent(a, PauseActivity::class.java).putExtra("key", key("Google Play Services for AR"))).setup().get(); idle(300)
        audit(pa.window.decorView, page(pa.window.decorView), "pause")
        val pk = Robolectric.buildActivity(PickActivity::class.java, Intent(a, PickActivity::class.java).putExtra("mode", "home")).setup().get(); idle(); audit(pk.window.decorView, page(pk.window.decorView), "pick")
        val we = Robolectric.buildActivity(WelcomeActivity::class.java).setup().get(); idle(); audit(we.window.decorView, page(we.window.decorView), "welcome")
        c.pause().stop().destroy()
    }

    private fun finish() = assertTrue(problems.size.toString() + " layout problems:\n" + problems.distinct().joinToString("\n"), problems.isEmpty())

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi") fun big_phone() { run("big"); finish() }
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun small_phone() { run("small"); finish() }
    @Test @Config(qualifiers = "w320dp-h568dp-xhdpi") fun tiny_phone() { run("tiny"); finish() }
    @Test @Config(qualifiers = "w384dp-h832dp-xxhdpi") fun large_text() { run("largetext", 1.3f); finish() }
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun small_phone_huge_text() { run("smallhuge", 1.5f); finish() }
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi") fun tablet() { run("tablet"); finish() }
}
