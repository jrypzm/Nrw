package app.years.home

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import kotlin.math.sin

/** A plain page: black, scrolling, edge to edge, with room for the system bars. */
abstract class Page : Activity() {
    protected lateinit var col: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Type.load(this)
        window.setDecorFitsSystemWindows(false)
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; setBackgroundColor(K.BG) }
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col)
        scroll.setOnApplyWindowInsetsListener { _, ins -> val b = ins.getInsets(WindowInsets.Type.systemBars()); col.setPadding(dpi(24f), b.top + dpi(28f), dpi(24f), b.bottom + dpi(40f)); ins }
        setContentView(scroll)
    }
    protected fun lp(w: Int = -1, h: Int = -2, top: Float = 0f) = LinearLayout.LayoutParams(w, h).apply { topMargin = dpi(top) }
    protected fun title(t: String, lead: String? = null) {
        col.addView(text(t, 32f, Type.display, K.FG, -0.03f))
        if (lead != null) col.addView(text(lead, 15f, Type.body, K.MUTE).apply { setLineSpacing(0f, 1.35f) }, lp(top = 8f))
    }
    protected fun section(t: String) = col.addView(caption(t).apply { setPadding(dpi(4f), 0, 0, 0) }, lp(top = 30f).apply { bottomMargin = dpi(10f) })
    protected fun group(): Card = Card(this).also { col.addView(it, lp()) }

    /** A row in a card: a title, a quieter line under it, and what it says on the right. */
    protected fun row(g: LinearLayout, t: String, sub: String? = null, value: String? = null, onClick: (() -> Unit)? = null): TextView {
        if (g.childCount > 0) g.addView(View(this).apply { setBackgroundColor(K.HAIR) }, LinearLayout.LayoutParams(-1, dpi(1f)).apply { leftMargin = dpi(20f) })
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(20f), dpi(14f), dpi(20f), dpi(14f)) }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(t, 16f, Type.body, K.FG))
        if (sub != null) texts.addView(text(sub, 13f, Type.body, K.MUTE).apply { setLineSpacing(0f, 1.3f) }, lp(top = 3f))
        r.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val v = text(value ?: if (onClick != null) "›" else "", if (value != null) 14f else 20f, Type.medium, if (value != null) K.MUTE else K.FAINT).apply { setPadding(dpi(12f), 0, 0, 0) }
        r.addView(v)
        if (onClick != null) r.setOnClickListener { it.tap(); onClick() }
        g.addView(r)
        return v
    }
}

/** For the apps you chose: a breath before they open. */
class PauseActivity : Page() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = intent.getStringExtra("key")?.let { Apps.byKey(it) } ?: run { finish(); return }
        val opens = Prefs.get(this).opensToday(app.key)
        col.gravity = Gravity.CENTER_VERTICAL
        col.minimumHeight = resources.displayMetrics.heightPixels
        col.addView(caption("A moment"))
        col.addView(text("Open ${app.label}?", 38f, Type.display, K.FG, -0.035f), lp(top = 12f))
        col.addView(text(if (opens == 0) "You haven't opened it today." else "You've opened it $opens ${if (opens == 1) "time" else "times"} today.", 16f, Type.body, K.MUTE), lp(top = 14f))
        val breath = Breath(this)
        col.addView(breath, lp(dpi(150f), dpi(150f), 36f).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val hint = text("Breathe in…", 13f, Type.medium, K.MUTE).apply { gravity = Gravity.CENTER }
        col.addView(hint, lp(top = 10f))
        val no = text("Not now", 16f, Type.medium, K.FG).apply { gravity = Gravity.CENTER; background = rounded(0, dp(26f), dpi(1f), K.HAIR2); setOnClickListener { it.tap(); finish() } }
        val yes = text("Open in 3", 16f, Type.semi, android.graphics.Color.BLACK).apply { gravity = Gravity.CENTER; background = rounded(K.FG, dp(26f)); alpha = .35f; isEnabled = false }
        col.addView(no, lp(h = dpi(52f), top = 40f)); col.addView(yes, lp(h = dpi(52f), top = 10f))
        var left = 3
        val tick = object : Runnable { override fun run() {
            left--
            if (left > 0) { yes.text = "Open in $left"; yes.postDelayed(this, 1000) }
            else { yes.text = "Open"; yes.isEnabled = true; yes.animate().alpha(1f).setDuration(250).start(); hint.text = "…and out." ; yes.setOnClickListener { it.tap(); HomeActivity.launch(this@PauseActivity, app, it); finish() } }
        } }
        yes.postDelayed(tick, 1000)
    }

    /** A dot that slowly grows and settles: one breath in four seconds. */
    class Breath(c: android.content.Context) : View(c) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = K.HAIR2; strokeWidth = c.dp(1.5f) }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = K.FG }
        private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
        private var t = 0f
        private val anim = ValueAnimator.ofFloat(0f, 1f).apply { duration = 4000; repeatCount = ValueAnimator.INFINITE; addUpdateListener { t = it.animatedValue as Float; invalidate() } }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
        override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
        override fun onDraw(c: Canvas) {
            val r = width / 2f; val k = ((1 - kotlin.math.cos(t * 2 * Math.PI)) / 2).toFloat()
            c.drawCircle(r, r, r - ring.strokeWidth, ring)
            c.drawCircle(r, r, r * (.36f + .36f * k), halo)
            c.drawCircle(r, r, r * (.2f + .12f * k), dot)
            @Suppress("UNUSED_VARIABLE") val unused = sin(0.0)
        }
    }
}

/** Settings: what's on home, what's hidden, what pauses first, how it looks, and what it can see. */
class SettingsActivity : Page() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }
    override fun onResume() { super.onResume(); render() }

    private fun render() {
        col.removeAllViews()
        val p = Prefs.get(this)
        title("yrs. home", "Hold any app to add it to home, rename it, hide it or make it pause first.")
        if (intent.getStringExtra("why") == "lock" && !LockService.on())
            col.addView(text("To lock with a double-tap, switch on yrs. home in Accessibility below. It reads nothing on your screen.", 14f, Type.medium, K.FG).apply { setLineSpacing(0f, 1.35f) }, lp(top = 16f))

        val home = getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
        section("Home")
        group().also { g ->
            row(g, "Home app", if (home) "yrs. home is your home screen. Tap to switch to another" else "Make yrs. home your home screen", if (home) "On" else null) { chooseHome() }
            row(g, "Apps on home", "${p.favorites.size} of up to 8, in the order you pick them") { startActivity(Intent(this, PickActivity::class.java).putExtra("mode", "home")) }
            row(g, "Hidden apps", if (p.hidden.isEmpty()) "None" else "${p.hidden.size} hidden from the list and search") { startActivity(Intent(this, PickActivity::class.java).putExtra("mode", "hidden")) }
            row(g, "Pause first", if (p.paused.isEmpty()) "A breath before the apps you choose" else "${p.paused.size} ${if (p.paused.size == 1) "app" else "apps"} ask first") { startActivity(Intent(this, PickActivity::class.java).putExtra("mode", "pause")) }
        }

        section("Thumb rail")
        group().also { g ->
            row(g, "Side", "Slide down the edge of home to reach any app", if (p.railLeft) "Left" else "Right") { p.railLeft = !p.railLeft; render() }
        }

        section("What it can see")
        group().also { g ->
            row(g, "Notification dots and previews", if (Live.enabled(this)) "On. Read only to show a dot and the latest message when you hold an app" else "Off", if (Live.enabled(this)) "On" else null) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            row(g, "Double-tap to lock", if (LockService.on()) "On" else "Switch on yrs. home in Accessibility, under Installed apps", if (LockService.on()) "On" else null) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            val people = checkSelfPermission(Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED && p.people
            row(g, "People in search", if (people) "On" else "Off", if (people) "On" else null) {
                if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 1) else { p.people = !p.people; render() }
            }
            val cal = checkSelfPermission(Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED && p.calendar
            row(g, "Next event on home", if (cal) "On. Shown under the clock when it's today" else "Off", if (cal) "On" else null) {
                if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 2) else { p.calendar = !p.calendar; render() }
            }
        }

        section("Font")
        group().also { g ->
            val custom = p.fontPath?.let { File(it).name }
            row(g, "Typeface", if (custom != null) "Your own: $custom" else "Inter", null) {}
            row(g, "Use my own font", "Pick a .ttf or .otf file already on your phone") {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")), 9)
            }
            if (custom != null) row(g, "Back to Inter") { File(p.fontPath!!).delete(); p.fontPath = null; Type.load(this); restartHome() }
        }

        section("Performance")
        group().also { g ->
            val mi = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
            row(g, "Memory in use", "What yrs. home takes right now", "${mi.totalPss / 1024} MB")
            row(g, "Apps", "Read and sorted in ${Apps.buildMs} ms", "${Apps.all.size}")
        }

        section("About")
        group().also { g ->
            row(g, "yrs. home", "A one-person, open source project. No internet permission: nothing leaves your phone.", runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull())
            row(g, "Source code") { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jrypzm/yrs"))) } }
        }
    }

    @Deprecated("Activity results, for the font file") override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != 9 || res != RESULT_OK) return
        val uri = data?.data ?: return
        val out = File(filesDir, "font-" + System.currentTimeMillis() + ".ttf")
        val ok = runCatching { contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } }; android.graphics.Typeface.createFromFile(out) }.isSuccess
        if (!ok) { out.delete(); return }
        Prefs.get(this).fontPath?.let { File(it).delete() }
        Prefs.get(this).fontPath = out.absolutePath
        Type.load(this); restartHome()
    }

    @Deprecated("Permissions") override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) { super.onRequestPermissionsResult(r, p, g); render() }

    private fun restartHome() { startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)); Runtime.getRuntime().exit(0) }
}

/** Choosing apps: for home (in order), to hide, or to pause first. */
class PickActivity : Page() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "home"
        val p = Prefs.get(this)
        title(
            when (mode) { "home" -> "Apps on home"; "hidden" -> "Hidden apps"; else -> "Pause first" },
            when (mode) { "home" -> "Tap apps in the order you want them, up to 8."; "hidden" -> "Hidden apps leave the list and search. They stay installed."; else -> "These apps show a breathing moment and today's count before they open." },
        )
        val g = Card(this); col.addView(g, lp(top = 20f))
        val chosen = when (mode) { "home" -> p.favorites.toMutableList(); "hidden" -> p.hidden.toMutableList(); else -> p.paused.toMutableList() }
        fun save() { when (mode) { "home" -> { p.favorites = chosen; p.favoritesSet = true }; "hidden" -> { p.hidden = chosen.toSet(); Apps.refresh(this) }; else -> p.paused = chosen.toSet() } }
        val marks = HashMap<String, TextView>()
        fun paint() { for ((k, v) in marks) { val i = chosen.indexOf(k); v.text = if (i < 0) "" else if (mode == "home") "${i + 1}" else "✓"; v.setTextColor(K.FG) } }
        for (a in Apps.all) {
            val v = row(g, a.label, if (a.work) "Work" else null, "") {
                if (a.key in chosen) chosen.remove(a.key) else if (mode != "home" || chosen.size < 8) chosen += a.key
                save(); paint()
            }
            marks[a.key] = v
        }
        paint()
    }
}
