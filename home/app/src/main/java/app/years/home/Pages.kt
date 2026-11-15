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

/** For the apps you chose: a breath before they open. Each open today waits a little longer, and asks why. */
class PauseActivity : Page() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = intent.getStringExtra("key")?.let { Apps.byKey(it) } ?: run { finish(); return }
        val p = Prefs.get(this)
        val opens = p.opensToday(app.key)
        val strict = p.strict
        val wait = waitFor(opens, strict)
        col.gravity = Gravity.CENTER_VERTICAL
        col.minimumHeight = resources.displayMetrics.heightPixels
        col.addView(caption("A moment"))
        col.addView(text(if (opens == 0) "Open ${app.label}?" else "${app.label},\n${ordinal(opens + 1)} time today.", 40f, Type.display, K.FG, -0.035f).apply { setLineSpacing(0f, 1.02f) }, lp(top = 12f))
        val rest = if (opens > 0) "Each open waits a little longer." else "A few seconds, then it's yours."
        val leadView = text(rest, 16f, Type.body, K.MUTE).apply { setLineSpacing(0f, 1.3f) }
        col.addView(leadView, lp(top = 14f))
        // minutes today, read on a spare thread, added when ready
        Thread {
            val mins = Usage.minutesToday(this, app.pkg)
            if (mins != null && mins > 0) runOnUiThread { if (!isDestroyed) leadView.text = "$mins ${if (mins == 1) "minute" else "minutes"} so far. $rest" }
        }.start()

        // the breath, with the seconds inside it
        val box = FrameLayout(this)
        box.addView(Breath(this), FrameLayout.LayoutParams(-1, -1))
        val count = text("$wait", 40f, Type.display, K.BG, -0.04f).apply { gravity = Gravity.CENTER; fontFeatureSettings = "tnum" }
        box.addView(count, FrameLayout.LayoutParams(-1, -1))
        col.addView(box, lp(dpi(170f), dpi(170f), 34f).apply { gravity = Gravity.CENTER_HORIZONTAL })

        col.addView(caption("Why now?"), lp(top = 30f))
        val reasons = FlowRow(this)
        val hint = text(if (strict) "Pick one to continue." else "", 14f, Type.medium, K.MUTE)
        var chosen: TextView? = null
        val no = text("Not now", 16f, Type.medium, K.FG).apply { gravity = Gravity.CENTER; background = rounded(0, dp(27f), dpi(1f), K.HAIR2); setOnClickListener { it.tap(); finish() } }
        val yes = text("Open in $wait s", 16f, Type.semi, K.DIM).apply { gravity = Gravity.CENTER; background = rounded(0xFF1C1C1E.toInt(), dp(27f)); isEnabled = false }
        var left = wait
        fun ready() = left <= 0 && (!strict || chosen != null)
        fun paint() {
            if (ready()) {
                yes.text = "Open"; yes.setTextColor(android.graphics.Color.BLACK); yes.background = rounded(K.FG, dp(27f)); yes.isEnabled = true
                yes.setOnClickListener { it.tap(); HomeActivity.launch(this@PauseActivity, app, it); finish() }
            } else yes.text = if (left > 0) "Open in $left s" else "Pick a reason"
        }
        for (r in listOf("Reply to someone", "Post something", "Look something up", "Just bored")) reasons.addView(text(r, 13.5f, Type.medium, K.MUTE).apply {
            background = rounded(0, dp(16f), dpi(1f), K.HAIR2); setPadding(dpi(14f), dpi(8f), dpi(14f), dpi(8f))
            setOnClickListener { v ->
                v.tap()
                chosen?.let { it.setTextColor(K.MUTE); it.background = rounded(0, dp(16f), dpi(1f), K.HAIR2) }
                chosen = this; setTextColor(K.BG); background = rounded(K.FG, dp(16f))
                hint.text = when (r) { "Just bored" -> "Fair. Maybe a walk instead?"; "Look something up" -> "Find it, then come back."; else -> "Go do that one thing." }
                // honest answer, gentle nudge: "Not now" gets the spotlight
                if (r == "Just bored") { no.setTextColor(K.BG); no.background = rounded(K.FG, dp(27f)) } else { no.setTextColor(K.FG); no.background = rounded(0, dp(27f), dpi(1f), K.HAIR2) }
                paint()
            }
        })
        col.addView(reasons, lp(top = 12f)); col.addView(hint, lp(top = 6f))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(no, LinearLayout.LayoutParams(0, dpi(54f), 1f)); buttons.addView(yes, LinearLayout.LayoutParams(0, dpi(54f), 1f).apply { leftMargin = dpi(10f) })
        col.addView(buttons, lp(top = 26f))
        val tick = object : Runnable { override fun run() {
            left--; count.text = if (left > 0) "$left" else ""
            paint(); if (left > 0) count.postDelayed(this, 1000)
        } }
        count.postDelayed(tick, 1000)
    }

    companion object {
        /** 3 seconds the first time today, 3 more each time after, up to half a minute. Strict doubles it. */
        fun waitFor(opensToday: Int, strict: Boolean): Int = ((3 + 3 * opensToday).coerceAtMost(30)) * (if (strict) 2 else 1)
        fun ordinal(n: Int): String = "$n" + if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
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
            c.drawCircle(r, r, r * (.6f + .3f * k), halo)
            c.drawCircle(r, r, r * (.44f + .08f * k), dot)
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

        section("On home")
        group().also { g ->
            fun sw(t: String, sub: String, on: Boolean, set: (Boolean) -> Unit) = row(g, t, sub, if (on) "On" else "Off") { set(!on); render() }
            sw("Words that glow", "An app's name lights up when something new arrives", p.glow) { p.glow = it }
            sw("People waiting", "Messages from people, across apps, with reply", p.peopleHome) { p.peopleHome = it }
            sw("The live line", "Timers, calls, deliveries and directions under the clock", p.live) { p.live = it }
            sw("Away time", "How long you were off your phone, when you come back", p.away) { p.away = it }
            sw("Headphones", "Your music app, when headphones connect", p.headphones) { p.headphones = it }
            sw("New and quiet", "New apps at the top of the list; apps you never open move to a quiet shelf", p.tidy) { p.tidy = it }
            if (Torch.also { it.init(this) }.available) {
                val lit = HomeActivity.LIGHT in p.favorites
                sw("Light", "A torch on home, as a word" + if (Torch.dimmable) ". Slide to dim it" else "", lit) { p.favorites = if (it) p.favorites + HomeActivity.LIGHT else p.favorites - HomeActivity.LIGHT }
            }
            sw("Strict pause", "Paused apps wait twice as long and ask why before they open", p.strict) { p.strict = it }
        }

        section("Moments")
        group().also { g ->
            row(g, "Home for each part of the day", "Morning 5–9, work 9–5 on weekdays, evening 5–10, night. Empty ones use your usual home.", if (p.momentsOn) "On" else "Off") { p.momentsOn = !p.momentsOn; render() }
            if (p.momentsOn) for ((m, label) in listOf("morning" to "Morning", "work" to "Work", "evening" to "Evening", "night" to "Night")) {
                val n = p.moment(m).size
                row(g, label, if (n == 0) "Uses your usual home" else "$n ${if (n == 1) "app" else "apps"}") { startActivity(Intent(this, PickActivity::class.java).putExtra("mode", "moment:$m")) }
            }
        }

        section("The Hour")
        group().also { g ->
            val listening = Live.enabled(this)
            row(g, "Notifications on your schedule", if (!listening) "Needs notification access first. People always get through." else "People reach you straight away. Shops, social and news wait and arrive together.", if (p.hourOn && listening) "On" else "Off") {
                if (!listening) startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) else { p.hourOn = !p.hourOn; render() }
            }
            if (p.hourOn) {
                val h24 = android.text.format.DateFormat.is24HourFormat(this)
                val presets = listOf(listOf(780, 1140), listOf(540, 780, 1080), listOf(720), listOf(1080), listOf(480, 720, 1020, 1260))
                val cur = presets.indexOf(p.hourTimes).let { if (it < 0) 0 else it }
                row(g, "Times", "Tap to change", p.hourTimes.joinToString(", ") { Kinds.hourName(it, h24) }) { p.hourTimes = presets[(cur + 1) % presets.size]; render() }
                row(g, "Always let through", if (p.hourAlways.isEmpty()) "Calls, messages, alarms and reminders always do" else "${p.hourAlways.size} more ${if (p.hourAlways.size == 1) "app" else "apps"}") { startActivity(Intent(this, PickActivity::class.java).putExtra("mode", "always")) }
                row(g, "Turning it off", "Anything already waiting still arrives at its time.")
            }
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
            val usage = Usage.allowed(this)
            row(g, "Usage access", if (usage) "On. Shows minutes today when an app pauses, and finds apps you never open" else "Optional. For minutes today and the quiet shelf", if (usage) "On" else null) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
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

/** Choosing apps: for home (in order), a moment of the day, to hide, to pause first, or to get through The Hour. */
class PickActivity : Page() {
    private var waiting: (() -> Unit)? = null
    override fun onDestroy() { waiting?.let { Apps.off(it) }; super.onDestroy() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // opened fresh after Android restarted the app: load the apps, then draw again
        if (Apps.all.isEmpty()) { Apps.onChange { if (!isDestroyed) recreate() }.let { f -> waiting = f }; Apps.start(this) }
        val mode = intent.getStringExtra("mode") ?: "home"
        val moment = mode.removePrefix("moment:").takeIf { mode.startsWith("moment:") }
        val p = Prefs.get(this)
        val ordered = mode == "home" || moment != null
        title(
            when { moment != null -> moment.replaceFirstChar { it.uppercase() }; mode == "home" -> "Apps on home"; mode == "hidden" -> "Hidden apps"; mode == "always" -> "Always let through"; else -> "Pause first" },
            when {
                moment != null -> "Home during this part of the day. Tap in order, up to 8. Leave it empty to use your usual home."
                mode == "home" -> "Tap apps in the order you want them, up to 8."
                mode == "hidden" -> "Hidden apps leave the list and search. They stay installed."
                mode == "always" -> "These apps' notifications never wait for The Hour."
                else -> "These apps show a breathing moment and today's count before they open."
            },
        )
        val g = Card(this); col.addView(g, lp(top = 20f))
        val chosen: MutableList<String> = when { moment != null -> p.moment(moment).toMutableList(); mode == "home" -> p.favorites.toMutableList(); mode == "hidden" -> p.hidden.toMutableList(); mode == "always" -> p.hourAlways.toMutableList(); else -> p.paused.toMutableList() }
        fun save() {
            when {
                moment != null -> p.setMoment(moment, chosen)
                mode == "home" -> { p.favorites = chosen; p.favoritesSet = true }
                mode == "hidden" -> { p.hidden = chosen.toSet(); Apps.refresh(this) }
                mode == "always" -> p.hourAlways = chosen.toSet()
                else -> p.paused = chosen.toSet()
            }
        }
        val marks = HashMap<String, TextView>()
        fun paint() { for ((k, v) in marks) { val i = chosen.indexOf(k); v.text = if (i < 0) "" else if (ordered) "${chosen.filter { it != HomeActivity.LIGHT }.indexOf(k) + 1}" else "✓"; v.setTextColor(K.FG) } }
        // The Hour works by app, so each app appears once there
        val apps = if (mode == "always") Apps.all.distinctBy { it.pkg } else Apps.all
        for (a in apps) {
            val id = if (mode == "always") a.pkg else a.key
            val v = row(g, a.label, if (a.work) "Work" else null, "") {
                if (id in chosen) chosen.remove(id) else if (!ordered || chosen.count { it != HomeActivity.LIGHT } < 8) chosen += id
                save(); paint()
            }
            marks[id] = v
        }
        paint()
    }
}
