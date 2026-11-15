package app.years.home

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors

/** One app you can open. [key] is stable across launches: package/activity/profile. */
class App(
    val key: String, val label: String, val pkg: String, val component: ComponentName, val user: UserHandle, val work: Boolean,
    /** When it was installed, for "new". */
    val installed: Long = 0L,
    /** 0 your own apps, 1 work, 2 Private Space. */
    val profile: Int = if (work) 1 else 0,
) {
    /** The label folded for matching: lower case, accents removed. */
    val norm: String = fold(label)
    /** First letters of each word, for "gm" → Google Maps. */
    val initials: String = norm.split(' ', '-', '.', '_').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }
    /** The letter it is filed under in the list. */
    val letter: Char = norm.firstOrNull()?.let { if (it in 'a'..'z') it.uppercaseChar() else '#' } ?: '#'

    companion object {
        private val marks = Regex("\\p{M}+")
        fun fold(s: String) = marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase(Locale.ROOT).trim()
    }
}

/**
 * The catalogue of apps. Built once on a spare thread and kept in memory as plain text (no icons are ever loaded),
 * then patched when something is installed, updated or removed. Everything that reads it is instant.
 */
object Apps {
    @Volatile var all: List<App> = emptyList(); private set
    /** What the list shows: everything, minus hidden apps, sorted A to Z. */
    @Volatile var visible: List<App> = emptyList(); private set
    @Volatile var buildMs = 0L; private set
    /** Private Space (Android 15+): its apps, only while it's unlocked, and never in the main list or search. */
    @Volatile var private: List<App> = emptyList(); private set
    @Volatile var privateUser: UserHandle? = null; private set
    @Volatile var privateLocked = true; private set
    @Volatile var workUser: UserHandle? = null; private set
    @Volatile var workPaused = false; private set
    /** When each package was last used, from usage access if you allowed it. */
    @Volatile var lastUsed: Map<String, Long> = emptyMap(); private set
    private val listeners = mutableListOf<() -> Unit>()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var started = false

    fun onChange(f: () -> Unit): () -> Unit { listeners += f; return f }
    fun off(f: () -> Unit) { listeners -= f }

    fun start(c: Context) {
        if (started) { refresh(c); return }
        started = true
        val la = c.getSystemService(LauncherApps::class.java)
        la.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(p: String, u: UserHandle) = refresh(c)
            override fun onPackageAdded(p: String, u: UserHandle) = refresh(c)
            override fun onPackageChanged(p: String, u: UserHandle) = refresh(c)
            override fun onPackagesAvailable(p: Array<out String>, u: UserHandle, r: Boolean) = refresh(c)
            override fun onPackagesUnavailable(p: Array<out String>, u: UserHandle, r: Boolean) = refresh(c)
        }, main)
        // work and private profiles locking and unlocking
        val f = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE); addAction(android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction("android.intent.action.PROFILE_AVAILABLE"); addAction("android.intent.action.PROFILE_UNAVAILABLE")
            addAction(android.content.Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
        }
        runCatching { c.applicationContext.registerReceiver(object : android.content.BroadcastReceiver() { override fun onReceive(x: Context, i: android.content.Intent) = refresh(x) }, f, Context.RECEIVER_NOT_EXPORTED) }
        refresh(c)
    }

    private var frozen = false
    fun refresh(c: Context) {
        if (frozen) return
        val ctx = c.applicationContext
        io.execute {
            val t0 = System.nanoTime()
            val la = ctx.getSystemService(LauncherApps::class.java)
            val um = ctx.getSystemService(UserManager::class.java)
            val me = Process.myUserHandle()
            val list = ArrayList<App>(256)
            val priv = ArrayList<App>()
            val renames = Prefs.get(ctx).renames
            var pUser: UserHandle? = null; var pLocked = true; var wUser: UserHandle? = null; var wPaused = false
            val users = runCatching { if (android.os.Build.VERSION.SDK_INT >= 30) la.profiles else um.userProfiles }.getOrDefault(um.userProfiles)
            for (u in users) {
                val userType = if (android.os.Build.VERSION.SDK_INT >= 35) runCatching { la.getLauncherUserInfo(u)?.userType }.getOrNull() else null
                val type = when {
                    u == me -> 0
                    userType == "android.os.usertype.profile.PRIVATE" -> 2
                    // a cloned app ("dual apps") is yours, not work
                    userType != null && userType != "android.os.usertype.profile.MANAGED" -> 3
                    else -> 1
                }
                val quiet = runCatching { um.isQuietModeEnabled(u) }.getOrDefault(false)
                if (type == 2) { pUser = u; pLocked = quiet; if (quiet) continue }
                if (type == 1 && wUser == null) { wUser = u; wPaused = quiet }
                val infos: List<LauncherActivityInfo> = runCatching { la.getActivityList(null, u) }.getOrDefault(emptyList())
                if (infos.isEmpty() && u == me) {
                    // some phones answer LauncherApps with nothing; ask the package manager instead
                    val pm = ctx.packageManager
                    val q = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    for (r in runCatching { pm.queryIntentActivities(q, 0) }.getOrDefault(emptyList())) {
                        val ai = r.activityInfo ?: continue
                        if (ai.packageName == ctx.packageName) continue
                        val cn = ComponentName(ai.packageName, ai.name)
                        val key = "${cn.flattenToShortString()}#${um.getSerialNumberForUser(u)}"
                        val label = renames[key] ?: runCatching { r.loadLabel(pm).toString().trim() }.getOrDefault("").ifEmpty { ai.packageName }
                        list += App(key, label, ai.packageName, cn, u, false)
                    }
                    continue
                }
                for (i in infos) {
                    if (i.componentName.packageName == ctx.packageName) continue
                    val key = "${i.componentName.flattenToShortString()}#${um.getSerialNumberForUser(u)}"
                    val label = renames[key] ?: i.label?.toString()?.trim().orEmpty().ifEmpty { i.componentName.packageName }
                    val app = App(key, label, i.componentName.packageName, i.componentName, u, type == 1, runCatching { i.firstInstallTime }.getOrDefault(0L), type)
                    if (type == 2) priv += app else list += app
                }
            }
            list.sortWith(compareBy<App> { it.letter == '#' }.thenBy { it.norm }.thenBy { it.work })
            val hidden = Prefs.get(ctx).hidden
            val vis = list.filter { it.key !in hidden }
            priv.sortBy { it.norm }
            val used = Usage.lastUsed(ctx)
            buildMs = (System.nanoTime() - t0) / 1_000_000
            main.post {
                all = list; visible = vis; private = priv; privateUser = pUser; privateLocked = pLocked; workUser = wUser; workPaused = wPaused; lastUsed = used
                listeners.toList().forEach { it() }
            }
        }
    }

    fun byKey(key: String): App? = all.firstOrNull { it.key == key } ?: private.firstOrNull { it.key == key }

    /** Lock or unlock a profile. Android asks for your fingerprint when unlocking Private Space. */
    /**
     * Returns false only when Android refused outright. (Unlocking returns false from Android while it shows its own
     * fingerprint or PIN prompt; that's not a failure, the profile opens once you've confirmed.)
     */
    fun setQuiet(c: Context, u: UserHandle, quiet: Boolean): Boolean =
        runCatching { c.getSystemService(UserManager::class.java).requestQuietModeEnabled(quiet, u) }.isSuccess.also { refresh(c) }

    /** New for three days after it's installed, until you open it. */
    fun isNew(a: App, now: Long, opened: Map<String, Long>): Boolean = a.installed > 0 && now - a.installed < 3 * 86_400_000L && (opened[a.key] ?: 0L) == 0L

    /** Not opened in a month (by usage access, or by yrs. home's own count once it has watched for a month). */
    fun isQuiet(a: App, now: Long, opened: Map<String, Long>, used: Map<String, Long>, firstRun: Long): Boolean {
        val month = 30 * 86_400_000L
        if (a.installed > 0 && now - a.installed < month) return false
        val last = maxOf(opened[a.key] ?: 0L, used[a.pkg] ?: 0L)
        if (last > 0) return now - last > month
        return used.isNotEmpty() || now - firstRun > month
    }

    /** For tests: a fixed set of apps instead of the phone's. */
    fun setForTest(list: List<App>, priv: List<App> = emptyList(), privUser: UserHandle? = null, privLocked: Boolean = true, work: UserHandle? = null, used: Map<String, Long> = emptyMap()) {
        started = true; frozen = true; all = list.sortedBy { it.norm }; visible = all; private = priv; privateUser = privUser; privateLocked = privLocked; workUser = work; lastUsed = used
        listeners.toList().forEach { it() }
    }

    /** A sensible first home screen: the phone, messages, camera, browser, maps, music, if they exist. */
    fun defaultFavorites(): List<String> {
        val wants = listOf(listOf("phone", "dialer", "contacts"), listOf("messages", "messaging"), listOf("camera"), listOf("whatsapp", "telegram", "signal"), listOf("chrome", "internet", "browser", "firefox"), listOf("spotify", "music", "youtube music"))
        val out = mutableListOf<String>()
        for (w in wants) all.firstOrNull { a -> !a.work && w.any { a.norm == it || a.norm.startsWith("$it ") } && a.key !in out }?.let { out += it.key }
        return out.take(6)
    }
}
