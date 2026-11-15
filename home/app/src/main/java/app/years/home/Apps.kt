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
    private val listeners = mutableListOf<() -> Unit>()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var started = false

    fun onChange(f: () -> Unit) { listeners += f }

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
            val renames = Prefs.get(ctx).renames
            for (u in um.userProfiles) {
                val infos: List<LauncherActivityInfo> = runCatching { la.getActivityList(null, u) }.getOrDefault(emptyList())
                for (i in infos) {
                    if (i.componentName.packageName == ctx.packageName) continue
                    val key = "${i.componentName.flattenToShortString()}#${um.getSerialNumberForUser(u)}"
                    val label = renames[key] ?: i.label?.toString()?.trim().orEmpty().ifEmpty { i.componentName.packageName }
                    list += App(key, label, i.componentName.packageName, i.componentName, u, u != me)
                }
            }
            list.sortWith(compareBy<App> { it.letter == '#' }.thenBy { it.norm }.thenBy { it.work })
            val hidden = Prefs.get(ctx).hidden
            val vis = list.filter { it.key !in hidden }
            buildMs = (System.nanoTime() - t0) / 1_000_000
            main.post { all = list; visible = vis; listeners.forEach { it() } }
        }
    }

    fun byKey(key: String): App? = all.firstOrNull { it.key == key }

    /** For tests: a fixed set of apps instead of the phone's. */
    fun setForTest(list: List<App>) { started = true; frozen = true; all = list.sortedBy { it.norm }; visible = all; listeners.forEach { it() } }

    /** A sensible first home screen: the phone, messages, camera, browser, maps, music, if they exist. */
    fun defaultFavorites(): List<String> {
        val wants = listOf(listOf("phone", "dialer", "contacts"), listOf("messages", "messaging"), listOf("camera"), listOf("whatsapp", "telegram", "signal"), listOf("chrome", "internet", "browser", "firefox"), listOf("spotify", "music", "youtube music"))
        val out = mutableListOf<String>()
        for (w in wants) all.firstOrNull { a -> !a.work && w.any { a.norm == it || a.norm.startsWith("$it ") } && a.key !in out }?.let { out += it.key }
        return out.take(6)
    }
}
