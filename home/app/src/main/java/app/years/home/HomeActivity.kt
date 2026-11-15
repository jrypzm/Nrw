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
    private lateinit var nudge: TextView
    private lateinit var rail: RailView
    private lateinit var drawer: FrameLayout
    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var strip: AlphaStrip
    private val adapter = RowAdapter()
    private var sheet: View? = null
    private var insetTop = 0; private var insetBottom = 0
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
        smart = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(date); addView(clock, lp(-2, -2).apply { topMargin = dpi(8f); leftMargin = -dpi(5f) }); addView(smart, lp(-1, -2).apply { topMargin = dpi(16f) })
        }
        favs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nudge = text("Make yrs. home your home screen  ›", 14f, Type.medium, K.MUTE).apply { visibility = View.GONE; setPadding(0, dpi(12f), 0, dpi(12f)); setOnClickListener { tap(); askToBeHome() } }
        home.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        home.addView(favs, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        home.addView(nudge, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
        root.addView(home, FrameLayout.LayoutParams(-1, -1))

        // while the rail is out, home steps back so the apps beside your thumb stand alone
        rail = RailView(this, { on -> home.animate().alpha(if (on) .12f else 1f).setDuration(if (on) 140L else 220L).setInterpolator(K.EASE).start() }) { app, v -> open(app, v) }.apply { left = Prefs.get(context).railLeft }
        root.addView(rail, FrameLayout.LayoutParams(-1, -1))

        buildDrawer()
        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        // white status bar icons on black; only possible once the window has its content
        window.insetsController?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)

        root.setOnApplyWindowInsetsListener { _, ins ->
            val b = ins.getInsets(WindowInsets.Type.systemBars()); val ime = ins.getInsets(WindowInsets.Type.ime())
            insetTop = b.top; insetBottom = b.bottom
            val side = dpi(32f)
            top.setPadding(side, b.top + dpi(64f), side, 0)
            favs.setPadding(side, 0, side + dpi(30f), b.bottom + dpi(64f))
            (nudge.layoutParams as FrameLayout.LayoutParams).apply { leftMargin = side; bottomMargin = b.bottom + dpi(16f) }
            rail.insetTop = b.top; rail.insetBottom = b.bottom
            drawer.setPadding(0, b.top, 0, max(b.bottom, ime.bottom))
            ins
        }

        Apps.onChange { onApps() }
        Live.onChange { renderFavs(); renderSmart(); if (isDrawerOpen()) adapter.notifyDataSetChanged() }
        Apps.start(this)
        drawer.post { drawer.translationY = drawer.height.toFloat(); drawer.visibility = View.INVISIBLE }
    }

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

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

    private fun renderFavs() {
        favs.removeAllViews()
        val notes = Live.notes
        for (key in Prefs.get(this).favorites) {
            val app = Apps.byKey(key) ?: continue
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val t = text(app.label, 34f, Type.display, K.FG, -0.03f).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
            row.addView(t, lp(0, -2).apply { weight = 1f; width = -2 })
            if (notes.containsKey(app.pkg)) row.addView(Dot(this), lp(dpi(7f), dpi(7f)).apply { leftMargin = dpi(10f); bottomMargin = dpi(14f) })
            row.setPadding(0, dpi(10f), 0, dpi(10f))
            row.setOnClickListener { open(app, t) }
            row.setOnLongClickListener { it.confirm(); showSheet(app); true }
            favs.addView(row, lp(-2, -2))
        }
        if (favs.childCount == 0) favs.addView(text(if (Apps.all.isEmpty()) "Finding your apps…" else "Swipe up for all your apps.\nHold one to put it here.", 16f, Type.medium, K.DIM).apply { setLineSpacing(0f, 1.3f) })
    }

    /** One line under the clock, only when it's worth a glance: music, an alarm soon, the next event, charging. */
    private var card: MediaCard? = null
    private fun renderSmart() {
        val m = Live.media()
        if (m != null) {
            val c = card ?: MediaCard(this) { mc ->
                // open the player: its own "now playing" screen if it offers one, else the app
                runCatching { mc.sessionActivity!!.send() }.onFailure { runCatching { startActivity(packageManager.getLaunchIntentForPackage(mc.packageName)) } }
            }.also { card = it }
            c.bind(m)
            if (c.parent !== smart) {
                smart.removeAllViews(); smart.addView(c, lp(-1, -2))
                c.alpha = 0f; c.translationY = dp(12f); c.animate().alpha(1f).translationY(0f).setDuration(420).setInterpolator(K.EASE).start()
            }
            return
        }
        smart.removeAllViews()
        val line = nextThing() ?: return
        smart.addView(text(line, 15f, Type.medium, K.MUTE).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
    }

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
        renderClock(); renderSmart(); renderFavs()
        // nothing ticks while you're not looking: the clock listens only while home is on screen
        registerReceiver(ticks, IntentFilter().apply { addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_BATTERY_CHANGED); addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) }, Context.RECEIVER_NOT_EXPORTED)
        nudge.visibility = if (isHome()) View.GONE else View.VISIBLE
        // after an update Android sometimes forgets to reconnect the listener; ask it to
        if (!Live.connected && Live.enabled(this)) runCatching { android.service.notification.NotificationListenerService.requestRebind(android.content.ComponentName(this, NotifService::class.java)) }
        Apps.refresh(this)
    }

    override fun onPause() { super.onPause(); runCatching { unregisterReceiver(ticks) } }

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
        val apps = Search.apps(q); val ans = Search.answer(q); val sets = Search.settings(q) + yrsSettings(q)
        adapter.showResults(q, apps, emptyList(), sets, ans)
        if (q.trim().length >= 2) bg.execute {
            val people = Search.people(this, q)
            runOnUiThread { if (search.text?.toString() == q && people.isNotEmpty()) adapter.showResults(q, apps, people, sets, ans) }
        }
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
        val onHome = app.key in p.favorites
        val chips = mutableListOf<Pair<String, () -> Unit>>(
            (if (onHome) "Remove from home" else "Add to home") to {
                val f = p.favorites.toMutableList(); if (onHome) f.remove(app.key) else if (f.size < 8) f += app.key; p.favorites = f; renderFavs(); closeSheet()
            },
            "Rename" to { rename(app) },
            (if (app.key in p.paused) "Don't pause" else "Pause first") to { p.paused = if (app.key in p.paused) p.paused - app.key else p.paused + app.key; closeSheet() },
            "Hide" to { p.hidden = p.hidden + app.key; p.favorites = p.favorites - app.key; Apps.refresh(this); closeSheet() },
            "App info" to { closeSheet(); runCatching { getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.component, app.user, null, null) } },
            "Uninstall" to { closeSheet(); runCatching { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))) } },
        )
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

        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            if (sheet != null) return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y; mode = 0; vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(e)
                    if (!isDrawerOpen() && drawer.visibility != View.VISIBLE && rail.inZone(e.x) && e.y > insetTop + dp(60f)) { mode = 1; rail.left = Prefs.get(context).railLeft; rail.begin(e); return true }
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
        class AppRow(val app: App, val big: Boolean = false) : Row()
        class Hits(val h: Hit) : Row()
    }

    inner class RowAdapter : BaseAdapter() {
        private var rows: List<Row> = emptyList()
        var letters: List<Char> = emptyList(); private set
        private var q = ""

        fun showAll() {
            q = ""
            val out = ArrayList<Row>(Apps.visible.size + 30); val ls = ArrayList<Char>()
            var last = ' '
            for (a in Apps.visible) { if (a.letter != last) { last = a.letter; out += Row.Letter(last); ls += last }; out += Row.AppRow(a) }
            // always a way in to settings, and out to another home app
            out += Row.Cap(""); out += Row.Hits(Hit.Setting("yrs. home settings", "yrs:settings")); out += Row.Hits(Hit.Setting("Change home app", "yrs:home"))
            rows = out; letters = ls; notifyDataSetChanged()
        }

        fun showResults(query: String, apps: List<Hit.AppHit>, people: List<Hit.Person>, sets: List<Hit.Setting>, ans: Hit.Answer?) {
            q = query
            val out = ArrayList<Row>()
            if (ans != null) out += Row.Hits(ans)
            if (apps.isNotEmpty()) { out += Row.Cap("Apps"); apps.forEachIndexed { i, a -> out += Row.AppRow(a.app, i == 0) } }
            if (people.isNotEmpty()) { out += Row.Cap("People"); people.forEach { out += Row.Hits(it) } }
            if (sets.isNotEmpty()) { out += Row.Cap("Settings"); sets.forEach { out += Row.Hits(it) } }
            out += Row.Cap(""); out += Row.Hits(Hit.Web(query.trim()))
            rows = out; notifyDataSetChanged()
        }

        fun first(): Row? = rows.firstOrNull { it is Row.AppRow || (it is Row.Hits) }
        fun positionOf(l: Char): Int? = rows.indexOfFirst { it is Row.Letter && it.c == l }.takeIf { it >= 0 }

        override fun getCount() = rows.size
        override fun getItem(p: Int) = rows[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getViewTypeCount() = 4
        override fun getItemViewType(p: Int) = when (rows[p]) { is Row.Letter -> 0; is Row.Cap -> 1; is Row.AppRow -> 2; is Row.Hits -> 3 }
        override fun isEnabled(p: Int) = rows[p] is Row.AppRow || rows[p] is Row.Hits

        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            val ctx = this@HomeActivity
            return when (val r = rows[p]) {
                is Row.Letter -> ((convert as? TextView) ?: ctx.caption("").apply { setPadding(dpi(30f), dpi(18f), 0, dpi(6f)) }).apply { text = r.c.toString() }
                is Row.Cap -> ((convert as? TextView) ?: ctx.caption("").apply { setPadding(dpi(30f), dpi(22f), 0, dpi(8f)) }).apply { text = r.s.uppercase(); visibility = if (r.s.isEmpty()) View.INVISIBLE else View.VISIBLE }
                is Row.AppRow -> {
                    val v = (convert as? LinearLayout) ?: LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(30f), dpi(10f), dpi(12f), dpi(10f))
                        addView(ctx.text("", 21f, Type.medium, K.FG, -0.015f).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-2, -2).apply { weight = 0f })
                        addView(ctx.text("", 12f, Type.medium, K.DIM, 0.02f).apply { setPadding(dpi(8f), 0, 0, 0) })
                        addView(Dot(ctx), LinearLayout.LayoutParams(dpi(6f), dpi(6f)).apply { leftMargin = dpi(10f) })
                    }
                    val name = v.getChildAt(0) as TextView
                    name.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (r.big) 24f else 21f)
                    name.text = highlight(r.app.label, q)
                    (v.getChildAt(1) as TextView).text = if (r.app.work) "WORK" else ""
                    v.getChildAt(2).visibility = if (Live.notes.containsKey(r.app.pkg)) View.VISIBLE else View.GONE
                    v.setOnClickListener { act(r, it) }
                    v.setOnLongClickListener { it.confirm(); showSheet(r.app); true }
                    v
                }
                is Row.Hits -> {
                    val v = (convert as? LinearLayout) ?: LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(dpi(24f), dpi(4f), dpi(24f), dpi(4f))
                        val card = Card(ctx).apply { setPadding(dpi(20f), dpi(14f), dpi(20f), dpi(14f)) }
                        card.addView(ctx.caption("")); card.addView(ctx.text("", 16f, Type.body, K.FG).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
                        addView(card, LinearLayout.LayoutParams(-1, -2))
                    }
                    val card = v.getChildAt(0) as LinearLayout
                    val cap = card.getChildAt(0) as TextView; val body = card.getChildAt(1) as TextView
                    when (val h = r.h) {
                        is Hit.Answer -> { cap.visibility = View.VISIBLE; cap.text = h.title.uppercase(); body.typeface = Type.thin; body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46f); body.text = h.value; body.letterSpacing = -0.04f }
                        is Hit.Person -> { cap.visibility = View.GONE; body.typeface = Type.body; body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); body.letterSpacing = -0.01f; body.text = highlight(h.name, q).let { s -> android.text.SpannableStringBuilder(s).append(if (h.phone != null) "   ·  Call" else "", android.text.style.ForegroundColorSpan(K.MUTE), 0) } }
                        is Hit.Setting -> { cap.visibility = View.GONE; body.typeface = Type.body; body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); body.letterSpacing = -0.01f; body.text = highlight(h.title, q) }
                        is Hit.Web -> { cap.visibility = View.GONE; body.typeface = Type.body; body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); body.letterSpacing = -0.01f; body.text = "Search the web for “${h.query}”  ↗" }
                        is Hit.AppHit -> {}
                    }
                    v.setOnClickListener { act(r, card) }
                    v.setOnLongClickListener { if (r.h is Hit.Answer) { copy(r.h.value); true } else false }
                    v
                }
            }
        }

        fun act(r: Row, from: View) {
            from.tap()
            when (r) {
                is Row.AppRow -> open(r.app, from)
                is Row.Hits -> when (val h = r.h) {
                    is Hit.Answer -> copy(h.value)
                    is Hit.Person -> runCatching { startActivity(if (h.phone != null) Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(h.phone)}")) else Intent(Intent.ACTION_VIEW, h.uri)) }
                    is Hit.Setting -> if (h.action == "yrs:settings") startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) else if (h.action == "yrs:home") chooseHome() else runCatching { startActivity(Intent(h.action)) }.onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    is Hit.Web -> runCatching { startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, h.query)) }.onFailure { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(h.query)}"))) } }
                    is Hit.AppHit -> open(h.app, from)
                }
                else -> {}
            }
        }
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
