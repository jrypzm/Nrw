package app.years.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.media.session.PlaybackState
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

/**
 * The home screen. Black, the time and date, one quiet line when something matters, and the apps you chose as words.
 * Swipe up for everything; slide the edge for the thumb rail; swipe down for notifications; double-tap to lock.
 */
class HomeActivity : Activity() {
    private lateinit var root: Root
    private lateinit var home: FrameLayout
    private lateinit var date: TextView
    private lateinit var clock: TextView
    private lateinit var smart: LinearLayout
    private lateinit var favs: LinearLayout
    private lateinit var peopleCol: FitColumn
    private lateinit var flashView: TextView
    private lateinit var nudge: TextView
    private lateinit var rail: RailView
    private lateinit var drawer: FrameLayout
    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var strip: AlphaStrip
    private val adapter = RowAdapter()
    private var sheet: View? = null
    private var insetTop = 0; private var insetBottom = 0; private var imeBottom = 0
    private val bg = Executors.newSingleThreadExecutor()
    private var battery = -1; private var charging = false

    // ================================================================== building

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Type.load(this)
        window.setDecorFitsSystemWindows(false)

        root = Root(this)
        home = FrameLayout(this)
        date = caption("")
        clock = text("", 112f, Type.thin, K.FG, -0.055f).apply { setLineSpacing(0f, .92f); fontFeatureSettings = "tnum" }
        smart = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(date); addView(clock, lp(-2, -2).apply { topMargin = dpi(8f); leftMargin = -dpi(5f) }); addView(smart, lp(-1, -2).apply { topMargin = dpi(16f) })
        }
        // top: the time and what's happening; middle: people, only as many as fit; bottom: your apps
        peopleCol = FitColumn(this)
        favs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(top, lp(-1, -2)); column.addView(peopleCol, LinearLayout.LayoutParams(-1, 0, 1f)); column.addView(favs, lp(-1, -2))
        nudge = text("Make yrs. home your home screen  ›", 14f, Type.medium, K.MUTE).apply { visibility = View.GONE; setPadding(0, dpi(12f), 0, dpi(12f)); setOnClickListener { tap(); askToBeHome() } }
        home.addView(column, FrameLayout.LayoutParams(-1, -1))
        home.addView(nudge, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
        root.addView(home, FrameLayout.LayoutParams(-1, -1))
        Torch.init(this); Feel.init(this)
        Torch.onChange { runOnUiThread { renderFavs() } }

        // while the rail is out, home steps back so the apps beside your thumb stand alone
        rail = RailView(this, { on -> home.animate().alpha(if (on) .12f else 1f).setDuration(if (on) 140L else 220L).setInterpolator(K.EASE).start() }) { app, v -> open(app, v) }.apply { left = Prefs.get(context).railLeft }
        root.addView(rail, FrameLayout.LayoutParams(-1, -1))

        buildDrawer()
        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
        flashView = text("", 14f, Type.medium, K.BG).apply { background = rounded(K.FG, dp(20f)); setPadding(dpi(18f), dpi(10f), dpi(18f), dpi(10f)); alpha = 0f; visibility = View.GONE }
        root.addView(flashView, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        setContentView(root)
        // white status bar icons on black; only possible once the window has its content
        window.insetsController?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)

        root.setOnApplyWindowInsetsListener { _, ins ->
            val b = ins.getInsets(WindowInsets.Type.systemBars()); val ime = ins.getInsets(WindowInsets.Type.ime())
            insetTop = b.top; insetBottom = b.bottom
            val side = dpi(32f)
            top.setPadding(side, b.top + dpi(64f), side, 0)
            peopleCol.setPadding(side, dpi(18f), side, dpi(12f))
            favs.setPadding(side, 0, side + dpi(30f), b.bottom + dpi(64f))
            (flashView.layoutParams as FrameLayout.LayoutParams).bottomMargin = b.bottom + dpi(28f)
            (nudge.layoutParams as FrameLayout.LayoutParams).apply { leftMargin = side; bottomMargin = b.bottom + dpi(16f) }
            rail.insetTop = b.top; rail.insetBottom = b.bottom
            drawer.setPadding(0, b.top, 0, max(b.bottom, ime.bottom))
            imeBottom = ime.bottom; sheet?.setPadding(0, 0, 0, max(0, ime.bottom - b.bottom))
            ins
        }

        Apps.onChange { onApps() }
        Live.onChange { renderFavs(); renderSmart(); renderPeople(); if (isDrawerOpen()) adapter.notifyDataSetChanged() }
        Apps.start(this)
        // away time: the screen going off and the next unlock. Registered for as long as home lives.
        registerReceiver(screen, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_ON) }, Context.RECEIVER_NOT_EXPORTED)
        drawer.post { drawer.translationY = drawer.height.toFloat(); drawer.visibility = View.INVISIBLE }
    }

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { Away.screenOff(c); if (sheet != null) closeSheet(); if (isDrawerOpen()) closeDrawer() }
                Intent.ACTION_USER_PRESENT -> Away.unlocked(c)
                // no lock screen at all: on is the same as unlocked
                Intent.ACTION_SCREEN_ON -> if (getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == false) Away.unlocked(c)
            }
        }
    }

    override fun onDestroy() { runCatching { unregisterReceiver(screen) }; super.onDestroy() }

    /** A short white note at the bottom of the screen: "Alarm set for 7:30 am". */
    fun flash(t: String) {
        flashView.text = t; flashView.visibility = View.VISIBLE; flashView.animate().cancel()
        flashView.translationY = dp(12f)
        flashView.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(220).setInterpolator(K.EASE).withEndAction {
            flashView.animate().alpha(0f).setStartDelay(2200).setDuration(260).withEndAction { flashView.visibility = View.GONE }.start()
        }.start()
    }

    private fun onApps() {
        val p = Prefs.get(this)
        if (!p.favoritesSet && Apps.all.isNotEmpty()) { p.favorites = Apps.defaultFavorites(); p.favoritesSet = true }
        renderFavs(); refreshList()
    }

    // ================================================================== the home screen

    private fun renderClock() {
        val now = LocalDateTime.now()
        val pattern = if (DateFormat.is24HourFormat(this)) "H:mm" else "h:mm"
        clock.text = now.format(DateTimeFormatter.ofPattern(pattern))
        date.text = now.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.getDefault())).uppercase()
    }

    /** The part of the day, when Moments is on: its own home apps, if you've chosen some. */
    private fun moment(): String? {
        if (!Prefs.get(this).momentsOn) return null
        val t = LocalDateTime.now(); val h = t.hour; val weekend = t.dayOfWeek.value >= 6
        return when (h) { in 5..8 -> "morning"; in 9..16 -> if (weekend) null else "work"; in 17..21 -> "evening"; else -> "night" }
    }
    /** The home apps showing right now, and where they're kept. */
    private fun homeList(): Pair<String?, List<String>> {
        val p = Prefs.get(this); val m = moment()
        if (m != null) { val l = p.moment(m); if (l.isNotEmpty()) return m to l }
        return null to p.favorites
    }
    private fun saveHomeList(l: List<String>) { val (m, _) = homeList(); if (m != null) Prefs.get(this).setMoment(m, l) else Prefs.get(this).favorites = l }

    private fun renderFavs() {
        favs.removeAllViews()
        val p = Prefs.get(this)
        val notes = Live.notes
        val (m, keys) = homeList()
        if (m != null) favs.addView(caption(m).apply { setPadding(0, 0, 0, dpi(6f)); setOnClickListener { it.tap(); startActivity(Intent(this@HomeActivity, PickActivity::class.java).putExtra("mode", "moment:$m")) } })
        // something new lights its word up; the rest step back a little
        fun fresh(pkg: String) = p.glow && notes[pkg]?.let { it.time > p.seen(pkg) } == true
        val apps = keys.mapNotNull { k -> if (k == LIGHT) null else Apps.byKey(k)?.takeIf { it.profile != 2 } }
        val anyNew = apps.any { fresh(it.pkg) } || (LIGHT in keys && Torch.on)
        for (key in keys) {
            if (key == LIGHT) { if (Torch.available) favs.addView(lightRow(anyNew)); continue }
            val app = Apps.byKey(key)?.takeIf { it.profile != 2 } ?: continue
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val lit = fresh(app.pkg)
            val t = text(app.label, 34f, Type.display, if (lit || !anyNew) K.FG else 0xFF9A9AA0.toInt(), -0.03f).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
            row.addView(t, lp(0, -2).apply { weight = 1f; width = -2 })
            if (lit) glow(t, 14f, 0xA6FFFFFF.toInt())
            if (lit) notes[app.pkg]?.let { n ->
                val who = if (n.count > 1) "${n.count}" else n.title.take(18)
                if (who.isNotEmpty()) row.addView(text(who, 15f, Type.medium, K.MUTE), lp(-2, -2).apply { leftMargin = dpi(12f); topMargin = dpi(4f) })
            } else if (!p.glow && notes.containsKey(app.pkg)) row.addView(Dot(this), lp(dpi(7f), dpi(7f)).apply { leftMargin = dpi(10f); bottomMargin = dpi(14f) })
            row.setPadding(0, dpi(10f), 0, dpi(10f))
            row.setOnClickListener { open(app, t) }
            row.setOnLongClickListener { it.confirm(); showSheet(app); true }
            favs.addView(row, lp(-2, -2))
        }
        if (favs.childCount == (if (m != null) 1 else 0)) favs.addView(text(if (Apps.all.isEmpty()) "Finding your apps…" else "Swipe up for all your apps.\nHold one to put it here.", 16f, Type.medium, K.DIM).apply { setLineSpacing(0f, 1.3f) })
    }

    /**
     * A soft light around a word. The light needs room to fade, so the word gets padding all round and the same
     * amount back as negative margin: it stays exactly where it was, and the glow never meets a hard edge.
     */
    private fun glow(t: TextView, radius: Float, color: Int) {
        val pad = dpi(radius * 1.6f)
        (t as? GlowText)?.let { it.glowRadius = dp(radius); it.glowColor = color }
        t.setPadding(pad, pad, pad, pad)
        (t.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(-pad, -pad, -pad, -pad)
        (t.parent as? ViewGroup)?.let { it.clipChildren = false; it.clipToPadding = false }
    }

    /** "Light": a torch that's a word. Tap to switch, slide to dim (where the phone can). */
    private fun lightRow(anyNew: Boolean): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dpi(10f), 0, dpi(10f)); clipChildren = false; clipToPadding = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false }
        val on = Torch.on
        val t = text("Light", if (on) 44f else 34f, Type.display, if (on || !anyNew) K.FG else 0xFF9A9AA0.toInt(), -0.03f)
        row.addView(t)
        if (on) glow(t, 20f, 0xD9FFFFFF.toInt())
        if (on && Torch.dimmable) row.addView(text("${(Torch.level * 100).toInt()}%", 15f, Type.medium, K.FG), lp(-2, -2).apply { leftMargin = dpi(14f); topMargin = dpi(6f) })
        col.addView(row)
        if (on && Torch.dimmable) {
            col.addView(Slider(this) { f -> Torch.setLevel(f); ((row.getChildAt(1)) as? TextView)?.text = "${(f * 100).toInt()}%" }.apply { f = Torch.level }, lp(-1, dpi(44f)).apply { topMargin = dpi(12f) })
            col.addView(caption("Slide for brightness · tap Light to turn off"), lp(-2, -2).apply { topMargin = dpi(10f) })
        }
        t.setOnClickListener { it.confirm(); if (!Torch.toggle()) flash("The torch is busy. Close the camera and try again") }
        t.setOnLongClickListener { it.confirm(); saveHomeList(homeList().second - LIGHT); renderFavs(); flash("Light removed from home"); true }
        return col
    }

    /** One line under the clock, only when it's worth a glance: music, an alarm soon, the next event, charging. */
    private var card: MediaCard? = null
    private var awayView: View? = null
    private val nowRows = HashMap<String, NowRow>()
    private val labels = HashMap<String, String>()
    private fun label(pkg: String) = labels.getOrPut(pkg) { Apps.all.firstOrNull { it.pkg == pkg }?.label ?: runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault("") }

    /**
     * Under the clock, in order of what matters now: coming back from a break, The Hour, live things (a timer, a call,
     * a delivery), the music, then one quiet line (headphones in, an alarm soon, the next event, charging).
     */
    private fun renderSmart() {
        val p = Prefs.get(this)
        val want = ArrayList<View>()
        awayView?.let { want += it }
        // The Hour: what it handed back, waiting to be read
        val hour = Live.hour
        if (hour.isNotEmpty()) {
            val h24 = DateFormat.is24HourFormat(this)
            val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(hour.maxOf { it.until }), ZoneId.systemDefault())
            val name = Kinds.hourName(at.hour * 60 + at.minute, h24)
            want += text(bold("The $name Hour", "${hour.size} ${if (hour.size == 1) "thing" else "things"} waited for you"), 15f, Type.medium, K.MUTE).apply {
                isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dpi(4f), 0, dpi(4f)); setOnClickListener { it.tap(); showHour() }
            }
        }
        // live things
        if (p.live) {
            val now = Live.now.take(2)
            nowRows.keys.retainAll(now.map { it.key }.toSet())
            for (n in now) want += nowRows.getOrPut(n.key) { NowRow(this, label(n.pkg)) }.also { it.bind(n) }
        }
        // music
        val m = Live.media()
        if (m != null) {
            val c = card ?: MediaCard(this) { mc ->
                runCatching { mc.sessionActivity!!.send() }.onFailure { runCatching { startActivity(packageManager.getLaunchIntentForPackage(mc.packageName)) } }
            }.also { card = it }
            c.bind(m); want += c
        }
        // one quiet line
        if (m == null) quietLine()?.let { want += it }
        // apply, keeping views that stay so nothing flickers
        val same = want.size == smart.childCount && want.indices.all { smart.getChildAt(it) === want[it] }
        if (!same) {
            val had = (0 until smart.childCount).map { smart.getChildAt(it) }.toSet()
            smart.removeAllViews()
            for ((i, v) in want.withIndex()) {
                (v.parent as? ViewGroup)?.removeView(v)
                smart.addView(v, lp(-1, -2).apply { if (i > 0) topMargin = dpi(if (v is MediaCard || want[i - 1] is MediaCard) 16f else 8f) })
                if (v !in had && (v is MediaCard || v is NowRow || v === awayView)) { v.alpha = 0f; v.translationY = dp(10f); v.animate().alpha(1f).translationY(0f).setDuration(420).setInterpolator(K.EASE).start() }
            }
        }
    }

    private fun bold(a: String, b: String) = android.text.SpannableStringBuilder().apply { append(a, android.text.style.ForegroundColorSpan(K.FG), 0); append(" · $b") }

    private fun quietLine(): View? {
        val p = Prefs.get(this)
        if (p.headphones && Ears.connected) {
            val music = musicApp()
            if (music != null) return text(bold("Headphones in", "${music.label} is ready"), 15f, Type.medium, K.MUTE).apply {
                isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dpi(4f), 0, dpi(4f)); setOnClickListener { open(music, it) }
            }
        }
        val line = nextThing()
        if (line != null) return text(line, 15f, Type.medium, K.MUTE).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
        if (p.hourOn && Live.holding > 0) {
            val next = LocalDateTime.ofInstant(Instant.ofEpochMilli(Kinds.nextHour(System.currentTimeMillis(), p.hourTimes)), ZoneId.systemDefault())
            return text("${Live.holding} waiting for ${Kinds.hourName(next.hour * 60 + next.minute, DateFormat.is24HourFormat(this))}", 15f, Type.medium, K.DIM)
        }
        return null
    }

    /** The app you listen with: the last one that played, else one that sounds like music. */
    private fun musicApp(): App? {
        Live.sessions.firstOrNull()?.packageName?.let { pkg -> Apps.visible.firstOrNull { it.pkg == pkg }?.let { return it } }
        val names = listOf("spotify", "youtube music", "apple music", "tidal", "deezer", "soundcloud", "music", "podcasts", "pocket casts", "audible")
        for (n in names) Apps.visible.firstOrNull { it.norm == n || it.norm.startsWith("$n ") }?.let { return it }
        return null
    }

    /** People waiting for you, as many as fit between the clock and your apps. */
    private fun renderPeople() {
        val p = Prefs.get(this)
        peopleCol.removeAllViews()
        if (!p.peopleHome) return
        val people = Live.people.filter { it.time > seenPeople[it.name] ?: 0L }.take(4)
        if (people.isEmpty()) return
        peopleCol.addView(caption("Waiting for you"))
        for (person in people) peopleCol.addView(PersonCard(this, person, label(person.pkg), { seen -> seen(seen) }) { replyTo(it) })
    }

    private fun seen(p: Live.Person) { seenPeople[p.name] = System.currentTimeMillis(); Prefs.get(this).markSeen(p.pkg); renderPeople(); renderFavs() }

    /** Reply without opening the app: their message, your answer, sent through the app's own reply. */
    private fun replyTo(person: Live.Person) {
        lateinit var field: EditText
        fun send() {
            val msg = field.text.toString().trim(); if (msg.isEmpty()) return
            val ok = PersonCard.send(this, person, msg)
            hideKeyboardOf(field); closeSheet()
            flash(if (ok) "Sent to ${person.name.substringBefore(' ')}" else "Couldn't send. Open the chat instead")
            if (ok) seen(person)
        }
        panel {
            addView(caption("${label(person.pkg)} · ${PersonCard.ago(person.time)}"))
            addView(text(person.name, 26f, Type.display, K.FG, -0.025f), lp(-1, -2).apply { topMargin = dpi(6f) })
            addView(text(person.text, 16f, Type.body, 0xFFD1D1D6.toInt()).apply { maxLines = 6; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.35f) }, lp(-1, -2).apply { topMargin = dpi(8f) })
            val row = LinearLayout(this@HomeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = rounded(K.GLASS, dp(24f), dpi(1f), K.HAIR); setPadding(dpi(18f), 0, dpi(6f), 0) }
            field = EditText(this@HomeActivity).apply {
                hint = "Reply to ${person.name.substringBefore(' ')}"; setHintTextColor(K.DIM); setTextColor(K.FG); typeface = Type.body; textSize = 16f; background = null; setPadding(0, 0, 0, 0)
                imeOptions = EditorInfo.IME_ACTION_SEND; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                textCursorDrawable = rounded(K.FG, 0f).apply { setSize(dpi(2f), dpi(20f)) }
                setOnEditorActionListener { _, _, _ -> send(); true }
            }
            row.addView(field, LinearLayout.LayoutParams(0, dpi(48f), 1f))
            row.addView(text("↑", 17f, Type.semi, K.BG).apply { gravity = Gravity.CENTER; background = rounded(K.FG, dp(18f)); setOnClickListener { it.confirm(); send() } }, LinearLayout.LayoutParams(dpi(36f), dpi(36f)))
            addView(row, lp(-1, -2).apply { topMargin = dpi(20f) })
            addView(text("Open the chat", 14f, Type.medium, K.MUTE).apply { setPadding(0, dpi(14f), 0, dpi(4f)); setOnClickListener { it.tap(); closeSheet(); seen(person); runCatching { person.open?.send() } } })
        }
        field.requestFocus(); field.postDelayed({ getSystemService(InputMethodManager::class.java)?.showSoftInput(field, 0) }, 120)
    }
    private fun hideKeyboardOf(v: View) { getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0) }
    private val seenPeople = HashMap<String, Long>()

    private fun nextThing(): CharSequence? {
        val now = System.currentTimeMillis()
        val fmt = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(this)) "H:mm" else "h:mm a")
        fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(fmt)
        fun inWords(ms: Long): String { val m = (ms - now) / 60000; return if (m < 60) "in $m min" else "in ${m / 60} h" }
        fun bold(a: String, b: String) = android.text.SpannableStringBuilder().apply { append(a, android.text.style.ForegroundColorSpan(K.FG), 0); append(" · $b") }
        // an alarm in the next 12 hours
        getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime?.let { t -> if (t - now in 0..12 * 3_600_000L) return bold("Alarm", "${at(t)}, ${inWords(t)}") }
        // the next event today
        if (Prefs.get(this).calendar && checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            runCatching {
                val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, now); ContentUris.appendId(it, now + 12 * 3_600_000L) }.build()
                contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY), "${CalendarContract.Instances.ALL_DAY}=0", null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                    while (c.moveToNext()) { val b = c.getLong(1); if (b >= now) return bold(c.getString(0) ?: "Event", "${at(b)}, ${inWords(b)}") }
                }
            }
        }
        if (charging && battery in 0..99) return bold("Charging", "$battery%")
        return null
    }

    private val ticks = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_BATTERY_CHANGED) {
                val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                battery = if (lvl >= 0) lvl * 100 / max(1, sc) else -1
                charging = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
            }
            renderClock(); renderSmart()
        }
    }

    override fun onResume() {
        super.onResume()
        // if Android restarted home while the screen was off, the unlock wasn't heard: count it now
        if (Prefs.get(this).offAt > 0 && getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == false) Away.unlocked(this)
        // back from a long break: say so, once, then step aside
        Away.pending(this)?.let { ms ->
            Prefs.get(this).awayShown = System.currentTimeMillis()
            val v = AwayView(this, ms, Away.week(this)).apply { setOnClickListener { dismissAway() } }
            awayView = v; v.postDelayed({ dismissAway() }, 7000)
        }
        Ears.start(this) { renderSmart() }
        renderClock(); renderSmart(); renderFavs(); renderPeople()
        // nothing ticks while you're not looking: the clock listens only while home is on screen
        registerReceiver(ticks, IntentFilter().apply { addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_BATTERY_CHANGED); addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) }, Context.RECEIVER_NOT_EXPORTED)
        nudge.visibility = if (isHome()) View.GONE else View.VISIBLE
        // after an update Android sometimes forgets to reconnect the listener; ask it to
        if (!Live.connected && Live.enabled(this)) runCatching { android.service.notification.NotificationListenerService.requestRebind(android.content.ComponentName(this, NotifService::class.java)) }
        Apps.refresh(this)
    }

    override fun onPause() { super.onPause(); runCatching { unregisterReceiver(ticks) }; Ears.stop() }

    private fun dismissAway() {
        val v = awayView ?: return; awayView = null
        v.animate().alpha(0f).setDuration(320).withEndAction { renderSmart() }.start()
    }

    /** Pressing Home while already home closes whatever is open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (sheet != null) closeSheet() else if (isDrawerOpen()) closeDrawer()
    }

    @Deprecated("Back closes the top layer; on home it does nothing")
    override fun onBackPressed() { if (sheet != null) closeSheet() else if (isDrawerOpen()) closeDrawer() }

    private fun isHome(): Boolean = getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
    private fun askToBeHome() = chooseHome()

    // ================================================================== opening things

    fun open(app: App, from: View?) {
        val p = Prefs.get(this)
        if (Live.notes.containsKey(app.pkg)) { p.markSeen(app.pkg); renderFavs() }
        if (app.key in p.paused) { startActivity(Intent(this, PauseActivity::class.java).putExtra("key", app.key)); return }
        launch(this, app, from)
    }

    private fun expandNotifications() {
        runCatching {
            @SuppressLint("WrongConstant") val sbm = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
        }
    }

    // ================================================================== the list of everything

    private fun buildDrawer() {
        drawer = object : FrameLayout(this) {}.apply { setBackgroundColor(K.BG); clipChildren = false; visibility = View.INVISIBLE }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        search = EditText(this).apply {
            hint = "Search apps, people, settings"; setHintTextColor(K.DIM); setTextColor(K.FG); typeface = Type.body; textSize = 16f
            background = rounded(K.GLASS, dp(25f), dpi(1f), K.HAIR); setPadding(dpi(20f), 0, dpi(20f), 0); isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            textCursorDrawable = rounded(K.FG, 0f).apply { setSize(dpi(2f), dpi(20f)) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { refreshList() }
            })
            setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_GO) { adapter.first()?.let { adapter.act(it, this) }; true } else false }
        }
        col.addView(search, LinearLayout.LayoutParams(-1, dpi(50f)).apply { setMargins(dpi(24f), dpi(14f), dpi(24f), dpi(6f)) })
        val body = FrameLayout(this).apply { clipChildren = false }
        list = ListView(this).apply {
            adapter = this@HomeActivity.adapter; divider = null; dividerHeight = 0; selector = rounded(0, 0f); isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER; setPadding(0, 0, 0, dpi(40f)); clipToPadding = false
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(v: AbsListView?, s: Int) { if (s == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) hideKeyboard() }
                override fun onScroll(v: AbsListView?, a: Int, b: Int, c: Int) {}
            })
        }
        strip = AlphaStrip(this) { l -> adapter.positionOf(l)?.let { list.setSelectionFromTop(it, 0) } }.apply { setPadding(0, dpi(12f), 0, dpi(24f)) }
        body.addView(list, FrameLayout.LayoutParams(-1, -1).apply { rightMargin = dpi(28f) })
        body.addView(strip, FrameLayout.LayoutParams(dpi(26f), -1, Gravity.END).apply { rightMargin = dpi(4f) })
        col.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        drawer.addView(col, FrameLayout.LayoutParams(-1, -1))
    }

    private fun refreshList() {
        val q = search.text?.toString().orEmpty()
        val lp = list.layoutParams as FrameLayout.LayoutParams
        if (q.isBlank()) { adapter.showAll(); strip.visibility = View.VISIBLE; strip.letters = adapter.letters; if (lp.rightMargin == 0) { lp.rightMargin = dpi(28f); list.layoutParams = lp }; return }
        // results use the full width; the A–Z strip only makes sense for the whole list
        strip.visibility = View.GONE; if (lp.rightMargin != 0) { lp.rightMargin = 0; list.layoutParams = lp }
        val acts = Smart.actions(q, LocalDateTime.now(), DateFormat.is24HourFormat(this)).mapNotNull { resolveInside(it) }
        val inside = acts.any { it.kind == Smart.INSIDE }
        val apps = if (inside) emptyList() else Search.apps(q); val ans = if (inside) null else Search.answer(q)
        val sets = if (inside) emptyList() else Search.settings(q) + yrsSettings(q)
        val cuts = if (inside) emptyList() else shortcutHits(q)
        adapter.showResults(q, apps, emptyList(), sets, ans, acts, cuts)
        if (!inside && q.trim().length >= 2) bg.execute {
            val people = Search.people(this, q)
            runOnUiThread { if (search.text?.toString() == q && people.isNotEmpty()) adapter.showResults(q, apps, people, sets, ans, acts, cuts) }
        }
    }

    /** ".yt": find the app it means, so the card can say "Search YouTube for…". */
    private fun resolveInside(a: Hit.Action): Hit.Action? {
        if (a.kind != Smart.INSIDE) return a
        val name = a.data["app"] as String; val q = a.data["q"] as String
        if (name == "web") return Hit.Action(Smart.INSIDE, "Web", a.value, a.sub, a.big, mapOf("q" to q))
        val app = Search.apps(name, 1).firstOrNull()?.app
            ?: return Hit.Action(Smart.INSIDE, "Search", if (q.isEmpty()) "…" else q, "No app called “$name”. Tap to search the web", q.isNotEmpty(), mapOf("q" to q))
        return Hit.Action(Smart.INSIDE, "Search ${app.label}", if (q.isEmpty()) "…" else q, if (q.isEmpty()) "Type what to look for in ${app.label}" else "Search inside ${app.label}", q.isNotEmpty(), mapOf("pkg" to app.pkg, "q" to q))
    }

    /** App shortcuts ("New chat", "Scan QR code"), read once when the list opens. Only a home screen may read these. */
    @Volatile private var shortcuts: List<Pair<ShortcutInfo, String>> = emptyList()
    private fun loadShortcuts() = bg.execute {
        val la = getSystemService(LauncherApps::class.java)
        if (runCatching { la.hasShortcutHostPermission() }.getOrDefault(false).not()) return@execute
        val q = LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        val out = ArrayList<Pair<ShortcutInfo, String>>()
        for (u in Apps.all.map { it.user }.distinct()) runCatching { la.getShortcuts(q, u) }.getOrNull()?.forEach { si ->
            if (!si.isEnabled) return@forEach
            val app = Apps.visible.firstOrNull { it.pkg == si.`package` && it.user == u } ?: return@forEach
            out += si to app.label
        }
        shortcuts = out
    }
    private fun shortcutHits(q: String): List<Hit.Shortcut> {
        val n = App.fold(q).trim(); if (n.length < 3) return emptyList()
        return shortcuts.filter { (si, app) -> App.fold(si.shortLabel?.toString() ?: "").let { it.startsWith(n) || it.contains(" $n") } || App.fold("$app ${si.shortLabel}").startsWith(n) && n.length > App.fold(app).length }
            .take(4).map { Hit.Shortcut(it.first, it.second) }
    }

    private fun yrsSettings(q: String): List<Hit.Setting> {
        val f = App.fold(q).trim(); if (f.length < 3) return emptyList()
        val out = ArrayList<Hit.Setting>()
        if ("yrs home settings".contains(f) || f.startsWith("home") || f.startsWith("settings")) out += Hit.Setting("yrs. home settings", "yrs:settings")
        if (listOf("launcher", "default", "home app", "change home", "home screen").any { it.startsWith(f) || f.startsWith(it) }) out += Hit.Setting("Change home app", "yrs:home")
        return out
    }

    fun isDrawerOpen() = drawer.visibility == View.VISIBLE && drawer.translationY < drawer.height * .5f

    private fun setDrawer(y: Float) {
        drawer.visibility = View.VISIBLE
        drawer.translationY = y.coerceIn(0f, drawer.height.toFloat())
        // the home screen eases back and fades as the list rises
        val f = 1f - drawer.translationY / max(1, drawer.height)
        home.alpha = 1f - f * .85f; home.translationY = -f * dp(28f)
    }

    fun openDrawer() {
        loadShortcuts(); refreshList()
        drawer.animate().cancel(); drawer.visibility = View.VISIBLE
        drawer.animate().translationY(0f).setDuration(380).setInterpolator(K.EASE).setUpdateListener { setDrawer(drawer.translationY) }.start()
    }

    fun closeDrawer() {
        hideKeyboard(); search.setText("")
        drawer.animate().cancel()
        drawer.animate().translationY(drawer.height.toFloat()).setDuration(320).setInterpolator(K.EASE).setUpdateListener { setDrawer(drawer.translationY) }
            .withEndAction { drawer.visibility = View.INVISIBLE; list.setSelection(0) }.start()
    }

    private fun hideKeyboard() { getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0); search.clearFocus() }

    // ================================================================== hold an app

    private fun showSheet(app: App) {
        closeSheet()
        val p = Prefs.get(this)
        val scrim = FrameLayout(this).apply { setBackgroundColor(0xB3000000.toInt()); alpha = 0f; setOnClickListener { closeSheet() } }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = android.graphics.drawable.GradientDrawable().apply { setColor(K.SHEET); cornerRadii = floatArrayOf(dp(30f), dp(30f), dp(30f), dp(30f), 0f, 0f, 0f, 0f) }
            setPadding(dpi(26f), dpi(14f), dpi(26f), insetBottom + dpi(28f)); isClickable = true
        }
        col.addView(View(this).apply { background = rounded(K.FAINT, dp(3f)) }, lp(dpi(36f), dpi(5f)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dpi(18f) })
        col.addView(text(app.label + if (app.work) " · Work" else "", 22f, Type.display, K.FG, -0.02f))
        val opens = p.opensToday(app.key)
        if (opens > 0) col.addView(text("Opened $opens ${if (opens == 1) "time" else "times"} today", 14f, Type.body, K.MUTE), lp(-2, -2).apply { topMargin = dpi(4f) })
        // the latest message, readable without opening the app
        Live.notes[app.pkg]?.let { n ->
            val card = Card(this).apply { setPadding(dpi(20f), dpi(16f), dpi(20f), dpi(18f)) }
            val ago = ((System.currentTimeMillis() - n.time) / 60000).let { if (it < 1) "now" else if (it < 60) "$it min ago" else "${it / 60} h ago" }
            card.addView(caption(listOf(n.title, ago).filter { it.isNotEmpty() }.joinToString(" · ")))
            card.addView(text(n.text, 16f, Type.body, K.FG).apply { maxLines = 4; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.35f) }, lp(-1, -2).apply { topMargin = dpi(8f) })
            card.setOnClickListener { closeSheet(); open(app, null) }
            col.addView(card, lp(-1, -2).apply { topMargin = dpi(16f) })
        }
        // its shortcuts, when Android lets a home screen read them
        val shortcuts = shortcutsFor(app)
        if (shortcuts.isNotEmpty()) {
            val card = Card(this)
            for ((i, s) in shortcuts.withIndex()) {
                if (i > 0) card.addView(View(this).apply { setBackgroundColor(K.HAIR) }, lp(-1, dpi(1f)).apply { leftMargin = dpi(20f) })
                card.addView(text(s.shortLabel ?: "", 16f, Type.body, K.FG).apply {
                    setPadding(dpi(20f), dpi(14f), dpi(20f), dpi(14f)); isSingleLine = true
                    setOnClickListener { tap(); closeSheet(); runCatching { getSystemService(LauncherApps::class.java).startShortcut(s, null, null) } }
                })
            }
            col.addView(card, lp(-1, -2).apply { topMargin = dpi(12f) })
        }
        // what you can do with it
        val list = homeList().second
        val onHome = app.key in list
        val chips = mutableListOf<Pair<String, () -> Unit>>(
            (if (onHome) "Remove from home" else "Add to home") to {
                val f = list.toMutableList()
                if (onHome) f.remove(app.key) else if (f.count { it != LIGHT } < 8) f += app.key else flash("Home holds 8 apps. Remove one first")
                saveHomeList(f); renderFavs(); closeSheet()
            },
            "Rename" to { rename(app) },
            (if (app.key in p.paused) "Don't pause" else "Pause first") to { p.paused = if (app.key in p.paused) p.paused - app.key else p.paused + app.key; closeSheet() },
            "Hide" to { p.hidden = p.hidden + app.key; p.favorites = p.favorites - app.key; Apps.refresh(this); closeSheet(); flash("${app.label} hidden. Find it in Settings") },
            "App info" to { closeSheet(); runCatching { getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.component, app.user, null, null) } },
            "Uninstall" to { closeSheet(); runCatching { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))) } },
        )
        // Private Space apps stay inside Private Space
        if (app.profile == 2) chips.removeAt(0)
        val wrap = FlowRow(this)
        for ((label, f) in chips) wrap.addView(text(label, 13.5f, Type.medium, K.MUTE).apply {
            background = rounded(0, dp(16f), dpi(1f), K.HAIR2); setPadding(dpi(14f), dpi(8f), dpi(14f), dpi(8f)); setOnClickListener { tap(); f() }
        })
        col.addView(wrap, lp(-1, -2).apply { topMargin = dpi(16f) })
        scrim.addView(col, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        sheet = scrim
        col.translationY = dp(300f)
        scrim.animate().alpha(1f).setDuration(200).start()
        col.animate().translationY(0f).setDuration(420).setInterpolator(K.EASE).start()
    }

    /** A sheet rising from the bottom, over a dim home. */
    private fun panel(build: LinearLayout.() -> Unit) {
        closeSheet()
        val scrim = FrameLayout(this).apply { setBackgroundColor(0xB3000000.toInt()); alpha = 0f; setOnClickListener { closeSheet() } }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = android.graphics.drawable.GradientDrawable().apply { setColor(K.SHEET); cornerRadii = floatArrayOf(dp(30f), dp(30f), dp(30f), dp(30f), 0f, 0f, 0f, 0f) }
            setPadding(dpi(26f), dpi(14f), dpi(26f), insetBottom + dpi(28f)); isClickable = true
        }
        col.addView(View(this).apply { background = rounded(K.FAINT, dp(3f)) }, lp(dpi(36f), dpi(5f)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dpi(18f) })
        col.build()
        val scroll = android.widget.ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; isFillViewport = false; addView(col) }
        scrim.addView(scroll, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { topMargin = insetTop + dpi(40f) })
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1)); sheet = scrim
        scroll.translationY = dp(300f)
        scrim.animate().alpha(1f).setDuration(200).start()
        scroll.animate().translationY(0f).setDuration(420).setInterpolator(K.EASE).start()
    }

    /** The Hour's digest: everything that waited, one card per app. */
    private fun showHour() {
        val items = Live.hour; if (items.isEmpty()) return
        val h24 = DateFormat.is24HourFormat(this)
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(items.maxOf { it.until }), ZoneId.systemDefault())
        val p = Prefs.get(this)
        val next = LocalDateTime.ofInstant(Instant.ofEpochMilli(Kinds.nextHour(System.currentTimeMillis(), p.hourTimes)), ZoneId.systemDefault())
        panel {
            addView(caption("The ${Kinds.hourName(at.hour * 60 + at.minute, h24)} Hour"))
            addView(text("${items.size} ${if (items.size == 1) "thing" else "things"} waited for you", 30f, Type.display, K.FG, -0.03f).apply { setLineSpacing(0f, 1.05f) }, lp(-1, -2).apply { topMargin = dpi(6f) })
            addView(text("People got through straight away. Next Hour at ${Kinds.hourName(next.hour * 60 + next.minute, h24)}.", 15f, Type.body, K.MUTE).apply { setLineSpacing(0f, 1.3f) }, lp(-1, -2).apply { topMargin = dpi(6f) })
            var firstCard = true
            for ((pkg, list) in items.groupBy { it.pkg }.entries.sortedByDescending { e -> e.value.maxOf { it.time } }) {
                val last = list.maxBy { it.time }
                val card = Card(this@HomeActivity).apply { setPadding(dpi(20f), dpi(14f), dpi(20f), dpi(16f)) }
                val head = FrameLayout(this@HomeActivity)
                head.addView(caption("${label(pkg)} · ${Smart.clock(LocalDateTime.ofInstant(Instant.ofEpochMilli(last.time), ZoneId.systemDefault()).toLocalTime(), h24)}"), FrameLayout.LayoutParams(-2, -2, Gravity.START))
                if (list.size > 1) head.addView(caption("${list.size}"), FrameLayout.LayoutParams(-2, -2, Gravity.END))
                card.addView(head)
                card.addView(text(listOf(last.title, last.text).filter { it.isNotEmpty() }.joinToString(". ").ifEmpty { "New notification" }, 16f, Type.body, K.FG).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.3f) }, lp(-1, -2).apply { topMargin = dpi(6f) })
                card.setOnClickListener { it.tap(); closeSheet(); Live.service?.openHour(pkg) }
                addView(card, lp(-1, -2).apply { topMargin = dpi(if (firstCard) 20f else 10f) }); firstCard = false
            }
            val chips = FlowRow(this@HomeActivity)
            fun chip(t: String, on: Boolean, f: () -> Unit) = chips.addView(text(t, 13.5f, Type.medium, if (on) K.BG else K.MUTE).apply {
                background = if (on) rounded(K.FG, dp(16f)) else rounded(0, dp(16f), dpi(1f), K.HAIR2); setPadding(dpi(14f), dpi(8f), dpi(14f), dpi(8f)); setOnClickListener { it.tap(); f() }
            })
            chip("Clear all", true) { closeSheet(); Live.service?.clearHour(); flash("All clear") }
            chip("Times…", false) { closeSheet(); startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) }
            addView(chips, lp(-1, -2).apply { topMargin = dpi(20f) })
        }
    }

    private fun shortcutsFor(app: App): List<ShortcutInfo> = runCatching {
        val la = getSystemService(LauncherApps::class.java)
        if (!la.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery().setPackage(app.pkg).setActivity(app.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        la.getShortcuts(q, app.user)?.filter { it.isEnabled }?.sortedBy { it.rank }?.take(4) ?: emptyList()
    }.getOrDefault(emptyList())

    private fun rename(app: App) {
        closeSheet()
        val p = Prefs.get(this)
        val field = EditText(this).apply {
            setText(app.label); setSelectAllOnFocus(true); setTextColor(K.FG); typeface = Type.display; textSize = 24f; isSingleLine = true
            background = rounded(K.GLASS, dp(16f), dpi(1f), K.HAIR); setPadding(dpi(18f), dpi(14f), dpi(18f), dpi(14f)); imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val scrim = FrameLayout(this).apply { setBackgroundColor(0xCC000000.toInt()); setOnClickListener { closeSheet() } }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(28f), 0, dpi(28f), 0); isClickable = true }
        col.addView(caption("Rename ${Apps.all.firstOrNull { it.key == app.key }?.let { "" } ?: ""}".trim().ifEmpty { "Rename" }))
        col.addView(field, lp(-1, -2).apply { topMargin = dpi(12f) })
        col.addView(text("Leave it empty to use the app's own name.", 13f, Type.body, K.DIM), lp(-2, -2).apply { topMargin = dpi(10f) })
        fun done() {
            val v = field.text.toString().trim()
            val r = p.renames.toMutableMap(); if (v.isEmpty()) r.remove(app.key) else r[app.key] = v; p.renames = r
            hideKeyboard(); getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(field.windowToken, 0); closeSheet(); Apps.refresh(this)
        }
        field.setOnEditorActionListener { _, _, _ -> done(); true }
        scrim.addView(col, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1)); sheet = scrim
        field.requestFocus(); field.post { getSystemService(InputMethodManager::class.java)?.showSoftInput(field, 0) }
    }

    private fun closeSheet() {
        val s = sheet ?: return; sheet = null
        s.animate().alpha(0f).setDuration(180).withEndAction { root.removeView(s) }.start()
    }

    // ================================================================== touch

    /** Swipes on the home screen, the thumb rail, and closing the list by pulling it down from the top. */
    inner class Root(c: Context) : FrameLayout(c) {
        private val slop = ViewConfiguration.get(c).scaledTouchSlop
        private var downX = 0f; private var downY = 0f; private var startT = 0f
        private var mode = 0 // 0 none, 1 rail, 2 pull up, 3 pull down (notifications), 4 pull the list down
        private var vt: VelocityTracker? = null
        private val gd = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (LockService.lock()) return true
                // not set up yet: say how
                startActivity(Intent(this@HomeActivity, SettingsActivity::class.java).putExtra("why", "lock"))
                return true
            }
            override fun onLongPress(e: MotionEvent) { performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) }
        })
        init { clipChildren = false }

        // keep Android's back swipe off the stretch of edge where the thumb starts the rail (Android allows 200dp)
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            val w = r - l; val h = b - t; val band = dpi(200f); val top = (h * .42f).toInt()
            val edge = dpi(26f)
            systemGestureExclusionRects = listOf(if (Prefs.get(context).railLeft) android.graphics.Rect(0, top, edge, top + band) else android.graphics.Rect(w - edge, top, w, top + band))
        }

        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            if (sheet != null) return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y; mode = 0; vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(e)
                    if (!isDrawerOpen() && drawer.visibility != View.VISIBLE && rail.inZone(e.x) && e.y > insetTop + dp(60f)) {
                        mode = 1; rail.left = Prefs.get(context).railLeft
                        rail.favLetters = homeList().second.mapNotNull { Apps.byKey(it)?.letter }.toSet()
                        rail.begin(e); return true
                    }
                }
                MotionEvent.ACTION_MOVE -> { vt?.addMovement(e); return detect(e) }
            }
            return false
        }

        /** Decides, once the finger has moved far enough, whether this is a pull up, a pull down, or nothing for us. */
        private fun detect(e: MotionEvent): Boolean {
            if (mode != 0) return true
            val dy = e.y - downY; val dx = e.x - downX
            if (abs(dy) > slop && abs(dy) > abs(dx) * 1.2f) {
                if (isDrawerOpen()) {
                    // pulling the list down from its very top closes it
                    if (dy > 0 && !list.canScrollVertically(-1) && e.y > search.bottom + insetTop) { mode = 4; startT = drawer.translationY; downY = e.y; return true }
                } else if (drawer.visibility != View.VISIBLE) {
                    mode = if (dy < 0) 2 else 3; downY = e.y
                    if (mode == 2) { drawer.animate().cancel(); startT = drawer.height.toFloat(); setDrawer(startT) }
                    gd.onTouchEvent(MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL })
                    return true
                }
            }
            return false
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            vt?.addMovement(e)
            // a touch that began on empty home space comes straight here, so look for a swipe here too
            if (mode == 0 && sheet == null && e.actionMasked == MotionEvent.ACTION_MOVE) detect(e)
            when (mode) {
                1 -> { when (e.actionMasked) { MotionEvent.ACTION_MOVE -> rail.move(e); MotionEvent.ACTION_UP -> rail.end(e); MotionEvent.ACTION_CANCEL -> rail.end(null) }; return true }
                2, 4 -> {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_MOVE -> setDrawer(startT + (e.y - downY))
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            vt?.computeCurrentVelocity(1000); val v = vt?.yVelocity ?: 0f
                            val open = if (abs(v) > dp(500f)) v < 0 else drawer.translationY < drawer.height * .6f
                            if (open) openDrawer() else closeDrawer()
                            mode = 0
                        }
                    }
                    return true
                }
                3 -> { if (e.actionMasked == MotionEvent.ACTION_UP && e.y - downY > dp(60f)) expandNotifications(); if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) mode = 0; return true }
            }
            if (drawer.visibility == View.VISIBLE) return false
            return gd.onTouchEvent(e)
        }
    }

    // ================================================================== the rows of the list

    sealed class Row {
        class Letter(val c: Char) : Row()
        class Cap(val s: String) : Row()
        class AppRow(val app: App, val big: Boolean = false, val tag: String? = null) : Row()
        class Hits(val h: Hit) : Row()
        /** "Quiet · 9 apps", which opens to show them. */
        class Quiet(val n: Int, val open: Boolean) : Row()
        /** Work or Private Space, as one card. */
        class Profile(val kind: Int) : Row()
    }

    private var quietOpen = false

    inner class RowAdapter : BaseAdapter() {
        private var rows: List<Row> = emptyList()
        var letters: List<Char> = emptyList(); private set
        private var q = ""

        fun showAll() {
            q = ""
            val p = Prefs.get(this@HomeActivity)
            val now = System.currentTimeMillis(); val opened = p.lastOpens(); val used = Apps.lastUsed; val first = p.firstRun
            val home = homeList().second.toSet()
            val newApps = if (p.tidy) Apps.visible.filter { Apps.isNew(it, now, opened) } else emptyList()
            val quiet = if (p.tidy) Apps.visible.filter { it.key !in home && it !in newApps && Apps.isQuiet(it, now, opened, used, first) } else emptyList()
            val quietSet = quiet.toHashSet()
            val out = ArrayList<Row>(Apps.visible.size + 40); val ls = ArrayList<Char>()
            if (newApps.isNotEmpty()) { out += Row.Cap("New"); newApps.forEach { out += Row.AppRow(it, tag = "New") } }
            var last = ' '
            for (a in Apps.visible) { if (a in quietSet) continue; if (a.letter != last) { last = a.letter; out += Row.Letter(last); ls += last }; out += Row.AppRow(a) }
            if (quiet.isNotEmpty()) { out += Row.Quiet(quiet.size, quietOpen); if (quietOpen) quiet.forEach { out += Row.AppRow(it) } }
            if (Apps.workUser != null) out += Row.Profile(1)
            if (Apps.privateUser != null) { out += Row.Profile(2); if (!Apps.privateLocked) Apps.private.forEach { out += Row.AppRow(it) } }
            // always a way in to settings, and out to another home app
            out += Row.Cap(""); out += Row.Hits(Hit.Setting("yrs. home settings", "yrs:settings")); out += Row.Hits(Hit.Setting("Change home app", "yrs:home"))
            rows = out; letters = ls; notifyDataSetChanged()
        }

        fun showResults(query: String, apps: List<Hit.AppHit>, people: List<Hit.Person>, sets: List<Hit.Setting>, ans: Hit.Answer?, acts: List<Hit.Action> = emptyList(), cuts: List<Hit.Shortcut> = emptyList()) {
            q = query
            val out = ArrayList<Row>()
            // what you can do comes first when the words say so: "7:30", "10 min", ".yt lofi"
            val big = acts.filter { it.big }; val small = acts.filter { !it.big }
            if (big.isNotEmpty() || small.isNotEmpty()) { out += Row.Cap("Do it"); big.forEach { out += Row.Hits(it) }; small.forEach { out += Row.Hits(it) } }
            if (ans != null) out += Row.Hits(ans)
            if (apps.isNotEmpty()) { out += Row.Cap("Apps"); apps.forEachIndexed { i, a -> out += Row.AppRow(a.app, i == 0 && acts.isEmpty()) } }
            if (cuts.isNotEmpty()) { out += Row.Cap("Shortcuts"); cuts.forEach { out += Row.Hits(it) } }
            if (people.isNotEmpty()) { out += Row.Cap("People"); people.forEach { out += Row.Hits(it) } }
            if (sets.isNotEmpty()) { out += Row.Cap("Settings"); sets.forEach { out += Row.Hits(it) } }
            if (acts.none { it.kind == Smart.INSIDE }) { out += Row.Cap(""); out += Row.Hits(Hit.Web(query.trim())) }
            rows = out; notifyDataSetChanged()
        }

        fun first(): Row? = rows.firstOrNull { it is Row.AppRow || (it is Row.Hits) }
        fun positionOf(l: Char): Int? = rows.indexOfFirst { it is Row.Letter && it.c == l }.takeIf { it >= 0 }

        private fun compact(h: Hit) = h is Hit.Shortcut || (h is Hit.Action && !h.big)

        override fun getCount() = rows.size
        override fun getItem(p: Int) = rows[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getViewTypeCount() = 7
        override fun getItemViewType(p: Int) = when (val r = rows[p]) { is Row.Letter -> 0; is Row.Cap -> 1; is Row.AppRow -> 2; is Row.Hits -> if (compact(r.h)) 4 else 3; is Row.Quiet -> 5; is Row.Profile -> 6 }
        override fun isEnabled(p: Int) = rows[p] !is Row.Letter && rows[p] !is Row.Cap

        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            val ctx = this@HomeActivity
            return when (val r = rows[p]) {
                is Row.Letter -> ((convert as? TextView) ?: ctx.caption("").apply { setPadding(dpi(30f), dpi(18f), 0, dpi(6f)) }).apply { text = r.c.toString() }
                is Row.Cap -> ((convert as? TextView) ?: ctx.caption("").apply { setPadding(dpi(30f), dpi(22f), 0, dpi(8f)) }).apply { text = r.s.uppercase(); visibility = if (r.s.isEmpty()) View.INVISIBLE else View.VISIBLE }
                is Row.AppRow -> {
                    val v = (convert as? LinearLayout) ?: LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(30f), dpi(10f), dpi(12f), dpi(10f))
                        addView(ctx.text("", 21f, Type.medium, K.FG, -0.015f).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-2, -2).apply { weight = 0f })
                        addView(ctx.text("", 10.5f, Type.semi, K.DIM, 0.1f), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dpi(10f) })
                        addView(Dot(ctx), LinearLayout.LayoutParams(dpi(6f), dpi(6f)).apply { leftMargin = dpi(10f) })
                    }
                    val name = v.getChildAt(0) as TextView
                    name.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (r.big) 24f else 21f)
                    name.text = highlight(r.app.label, q)
                    val tag = v.getChildAt(1) as TextView
                    val t = r.tag ?: when (r.app.profile) { 1 -> "Work"; 2 -> "Private"; 3 -> "Clone"; else -> null }
                    tag.text = t?.uppercase() ?: ""
                    if (r.tag != null) { tag.setTextColor(K.BG); tag.background = rounded(K.FG, dp(6f)); tag.setPadding(dpi(6f), dpi(3f), dpi(6f), dpi(3f)) } else { tag.setTextColor(K.DIM); tag.background = null; tag.setPadding(0, 0, 0, 0) }
                    tag.visibility = if (t == null) View.GONE else View.VISIBLE
                    v.getChildAt(2).visibility = if (Live.notes.containsKey(r.app.pkg)) View.VISIBLE else View.GONE
                    v.setOnClickListener { act(r, it) }
                    v.setOnLongClickListener { it.confirm(); showSheet(r.app); true }
                    v
                }
                is Row.Hits -> if (compact(r.h)) {
                    // one line: what you typed on the left, what it does on the right
                    val v = (convert as? LinearLayout) ?: LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(dpi(24f), dpi(4f), dpi(24f), dpi(4f))
                        val card = Card(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(20f), dpi(14f), dpi(20f), dpi(14f)) }
                        card.addView(ctx.text("", 16f, Type.medium, K.FG).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
                        card.addView(ctx.text("", 15f, Type.body, K.MUTE).apply { isSingleLine = true }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dpi(12f) })
                        addView(card, LinearLayout.LayoutParams(-1, -2))
                    }
                    val card = v.getChildAt(0) as LinearLayout
                    val l = card.getChildAt(0) as TextView; val rt = card.getChildAt(1) as TextView
                    when (val h = r.h) {
                        is Hit.Action -> { l.text = if (h.kind == Smart.ALARM) h.value else h.caption; rt.text = if (h.kind == Smart.ALARM) "Instead" else h.sub }
                        is Hit.Shortcut -> { l.text = h.info.shortLabel ?: ""; rt.text = h.app }
                        else -> {}
                    }
                    v.setOnClickListener { act(r, card) }
                    v
                } else {
                    val v = (convert as? LinearLayout) ?: LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(dpi(24f), dpi(4f), dpi(24f), dpi(4f))
                        val card = Card(ctx).apply { setPadding(dpi(20f), dpi(14f), dpi(20f), dpi(14f)) }
                        card.addView(ctx.caption("")); card.addView(ctx.text("", 16f, Type.body, K.FG).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
                        card.addView(ctx.text("", 15f, Type.body, K.MUTE).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
                        addView(card, LinearLayout.LayoutParams(-1, -2))
                    }
                    val card = v.getChildAt(0) as LinearLayout
                    val cap = card.getChildAt(0) as TextView; val body = card.getChildAt(1) as TextView; val sub = card.getChildAt(2) as TextView
                    sub.visibility = View.GONE
                    fun plain() { cap.visibility = View.GONE; body.typeface = Type.body; body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); body.letterSpacing = -0.01f }
                    when (val h = r.h) {
                        is Hit.Answer -> { cap.visibility = View.VISIBLE; cap.text = h.title.uppercase(); body.typeface = Type.thin; body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46f); body.text = h.value; body.letterSpacing = -0.04f }
                        is Hit.Action -> {
                            cap.visibility = View.VISIBLE; cap.text = h.caption.uppercase()
                            body.typeface = Type.thin; body.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (h.value.length > 14) 32f else 46f); body.text = h.value; body.letterSpacing = -0.04f
                            sub.text = h.sub.ifEmpty { "Tap to go" }; sub.visibility = View.VISIBLE
                        }
                        is Hit.Person -> { plain(); body.text = highlight(h.name, q).let { s -> android.text.SpannableStringBuilder(s).append(if (h.phone != null) "   ·  Call" else "", android.text.style.ForegroundColorSpan(K.MUTE), 0) } }
                        is Hit.Setting -> { plain(); body.text = highlight(h.title, q) }
                        is Hit.Web -> { plain(); body.text = "Search the web for “${h.query}”  ↗" }
                        else -> {}
                    }
                    v.setOnClickListener { act(r, card) }
                    v.setOnLongClickListener { if (r.h is Hit.Answer) { copy(r.h.value); flash("Copied"); true } else false }
                    v
                }
                is Row.Quiet -> {
                    val v = (convert as? LinearLayout) ?: LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(dpi(24f), dpi(22f), dpi(24f), dpi(10f))
                        addView(View(ctx).apply { setBackgroundColor(K.HAIR) }, LinearLayout.LayoutParams(-1, dpi(1f)))
                        val head = FrameLayout(ctx).apply { setPadding(dpi(6f), dpi(18f), dpi(6f), 0) }
                        head.addView(ctx.caption(""), FrameLayout.LayoutParams(-2, -2, Gravity.START)); head.addView(ctx.caption("").apply { setTextColor(K.MUTE) }, FrameLayout.LayoutParams(-2, -2, Gravity.END))
                        addView(head)
                        addView(ctx.text("", 14f, Type.body, K.DIM).apply { setLineSpacing(0f, 1.35f); setPadding(dpi(6f), dpi(8f), dpi(6f), 0) })
                    }
                    val head = v.getChildAt(1) as FrameLayout
                    (head.getChildAt(0) as TextView).text = "QUIET · ${r.n} ${if (r.n == 1) "APP" else "APPS"}"
                    (head.getChildAt(1) as TextView).text = if (r.open) "HIDE" else "SHOW"
                    (v.getChildAt(2) as TextView).apply { text = "Not opened in a month. They still show up in search."; visibility = if (r.open) View.GONE else View.VISIBLE }
                    v.setOnClickListener { it.tap(); quietOpen = !quietOpen; showAll() }
                    v
                }
                is Row.Profile -> {
                    val v = (convert as? LinearLayout) ?: LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(dpi(24f), dpi(14f), dpi(24f), dpi(4f))
                        val card = Card(ctx).apply { setPadding(dpi(20f), dpi(16f), dpi(20f), dpi(16f)) }
                        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                        head.addView(ctx.text("", 22f, Type.display, K.FG, -0.02f), LinearLayout.LayoutParams(0, -2, 1f))
                        head.addView(ctx.text("", 13.5f, Type.medium, K.MUTE).apply { setPadding(dpi(14f), dpi(7f), dpi(14f), dpi(7f)) })
                        card.addView(head)
                        card.addView(ctx.text("", 15f, Type.body, K.MUTE).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(6f) })
                        card.addView(ctx.text("", 13f, Type.body, K.DIM), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(8f) })
                        addView(card, LinearLayout.LayoutParams(-1, -2))
                    }
                    val card = v.getChildAt(0) as LinearLayout; val head = card.getChildAt(0) as LinearLayout
                    val title = head.getChildAt(0) as TextView; val chip = head.getChildAt(1) as TextView
                    val sub = card.getChildAt(1) as TextView; val hint = card.getChildAt(2) as TextView
                    val work = r.kind == 1
                    val off = if (work) Apps.workPaused else Apps.privateLocked
                    title.text = if (work) "Work" else "Private"
                    chip.text = if (work) (if (off) "Paused" else "On") else (if (off) "Locked" else "Open")
                    val lit = if (work) !off else !off
                    chip.setTextColor(if (lit) K.BG else K.MUTE); chip.background = if (lit) rounded(K.FG, dp(15f)) else rounded(0, dp(15f), dpi(1f), K.HAIR2)
                    sub.text = if (work) Apps.all.filter { it.profile == 1 }.take(5).joinToString(" · ") { it.label }.ifEmpty { "Your work profile" }
                        else if (off) "Hidden from the list and from search." else "${Apps.private.size} apps, shown below until you lock it."
                    hint.text = if (work) (if (off) "Tap to turn work apps back on." else "Tap to pause all work apps.") else (if (off) "Tap to unlock with your fingerprint or PIN." else "Tap to lock it again.")
                    v.setOnClickListener {
                        it.tap()
                        val u = (if (work) Apps.workUser else Apps.privateUser) ?: return@setOnClickListener
                        if (!Apps.setQuiet(this@HomeActivity, u, !off)) flash(if (isHome()) "Android didn't allow that just now" else "Make yrs. home your home screen first")
                    }
                    v
                }
            }
        }

        fun act(r: Row, from: View) {
            from.tap()
            when (r) {
                is Row.AppRow -> open(r.app, from)
                is Row.Hits -> when (val h = r.h) {
                    is Hit.Answer -> { copy(h.value); flash("Copied") }
                    is Hit.Person -> runCatching { startActivity(if (h.phone != null) Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(h.phone)}")) else Intent(Intent.ACTION_VIEW, h.uri)) }
                    is Hit.Setting -> if (h.action == "yrs:settings") startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) else if (h.action == "yrs:home") chooseHome() else runCatching { startActivity(Intent(h.action)) }.onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    is Hit.Web -> runCatching { startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, h.query)) }.onFailure { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(h.query)}"))) } }
                    is Hit.AppHit -> open(h.app, from)
                    is Hit.Action -> doAction(h)
                    is Hit.Shortcut -> runCatching { getSystemService(LauncherApps::class.java).startShortcut(h.info, null, null) }.onFailure { flash("That shortcut isn't available right now") }
                }
                is Row.Quiet, is Row.Profile -> {}
                else -> {}
            }
        }
    }

    /** Does what search offered. Everything goes through Android's own apps: the clock, the dialer, the app itself. */
    private fun doAction(h: Hit.Action) {
        val d = h.data
        val ok = runCatching {
            when (h.kind) {
                Smart.ALARM -> { startActivity(Intent(android.provider.AlarmClock.ACTION_SET_ALARM).putExtra(android.provider.AlarmClock.EXTRA_HOUR, d["h"] as Int).putExtra(android.provider.AlarmClock.EXTRA_MINUTES, d["m"] as Int).putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)); closeDrawer(); flash("Alarm set for ${h.value}") }
                Smart.TIMER -> { startActivity(Intent(android.provider.AlarmClock.ACTION_SET_TIMER).putExtra(android.provider.AlarmClock.EXTRA_LENGTH, d["s"] as Int).putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)); closeDrawer(); flash("Timer started: ${h.value}") }
                Smart.CALL -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(d["n"] as String)}")))
                Smart.TEXT -> startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(d["n"] as String)}")))
                Smart.SAVE -> startActivity(Intent(android.provider.ContactsContract.Intents.Insert.ACTION).setType(android.provider.ContactsContract.RawContacts.CONTENT_TYPE).putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, d["n"] as String))
                Smart.LIGHT -> { if (!Torch.toggle()) flash("The torch is busy right now") else { closeDrawer(); flash(if (Torch.on) "Light on" else "Light off") } }
                Smart.INSIDE -> searchInside(d["pkg"] as? String, d["q"] as? String ?: "")
                Smart.DATE -> { copy(h.value); flash("Copied") }
                else -> {}
            }
        }.isSuccess
        if (!ok) flash(when (h.kind) { Smart.ALARM, Smart.TIMER -> "No clock app could take that"; else -> "Nothing on this phone can do that" })
    }

    /** ".yt lofi": hand the words to the app's own search, the way each app likes them. */
    private fun searchInside(pkg: String?, q: String) {
        if (q.isEmpty()) return
        if (pkg == null) { startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q)); return }
        val tries = listOf(
            when (pkg) {
                "com.google.android.apps.maps" -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(q)}")).setPackage(pkg)
                "com.spotify.music" -> Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(q)}")).setPackage(pkg)
                "com.android.vending" -> Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(q)}")).setPackage(pkg)
                else -> null
            },
            Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(android.app.SearchManager.QUERY, q).putExtra("query", q),
            Intent(Intent.ACTION_SEND).setPackage(pkg).setType("text/plain").putExtra(Intent.EXTRA_TEXT, q),
        ).filterNotNull()
        for (i in tries) if (runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it); flash("Opened it. Search there for “$q”") }
    }

    private fun copy(v: String) {
        getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("result", v.replace(",", "")))
    }

    private fun highlight(s: String, q: String): CharSequence {
        val n = App.fold(q).trim(); if (n.isEmpty()) return s
        val i = App.fold(s).indexOf(n); if (i < 0) return s
        return android.text.SpannableStringBuilder(s).apply {
            setSpan(android.text.style.ForegroundColorSpan(K.FG), 0, s.length, 0)
            if (i > 0) setSpan(android.text.style.ForegroundColorSpan(0xFFAEAEB2.toInt()), 0, i, 0)
            if (i + n.length < s.length) setSpan(android.text.style.ForegroundColorSpan(0xFFAEAEB2.toInt()), i + n.length, s.length, 0)
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        // typing on a hardware keyboard on the home screen starts a search
        if (e.action == KeyEvent.ACTION_DOWN && !isDrawerOpen() && sheet == null && e.isPrintingKey) { openDrawer(); search.requestFocus(); search.append(e.unicodeChar.toChar().toString()); return true }
        return super.dispatchKeyEvent(e)
    }

    companion object {
        /** The torch's place in a home list. */
        const val LIGHT = "yrs:light"
        /** Opens an app with a reveal from where you touched, and counts it for "opened today". */
        fun launch(a: Activity, app: App, from: View?) {
            Prefs.get(a).countOpen(app.key)
            val r = Rect(); val opts = from?.let { v -> v.getGlobalVisibleRect(r); ActivityOptions.makeClipRevealAnimation(v, 0, 0, v.width, v.height) }
            runCatching { a.getSystemService(LauncherApps::class.java).startMainActivity(app.component, app.user, if (from != null) r else null, opts?.toBundle()) }
                .onFailure { runCatching { a.startActivity(a.packageManager.getLaunchIntentForPackage(app.pkg)) } }
        }
    }
}

/** Chips that wrap onto as many lines as they need. */
class FlowRow(c: Context) : ViewGroup(c) {
    private val gap = c.dpi(8f)
    override fun onMeasure(w: Int, h: Int) {
        val max = MeasureSpec.getSize(w); var x = 0; var y = 0; var line = 0
        for (i in 0 until childCount) {
            val ch = getChildAt(i); ch.measure(MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
            if (x > 0 && x + ch.measuredWidth > max) { x = 0; y += line + gap; line = 0 }
            x += ch.measuredWidth + gap; line = max(line, ch.measuredHeight)
        }
        setMeasuredDimension(max, y + line)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val max = r - l; var x = 0; var y = 0; var line = 0
        for (i in 0 until childCount) {
            val ch = getChildAt(i)
            if (x > 0 && x + ch.measuredWidth > max) { x = 0; y += line + gap; line = 0 }
            ch.layout(x, y, x + ch.measuredWidth, y + ch.measuredHeight); x += ch.measuredWidth + gap; line = max(line, ch.measuredHeight)
        }
    }
}
