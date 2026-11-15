package app.years.home

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

/** What the home screen knows about notifications and music. Filled by [NotifService] when you allow it. */
object Live {
    /** The latest news per app: who it's from, what it says, how many. */
    class Note(val title: String, val text: String, val time: Long, val count: Int = 1)
    /** Someone waiting for you: one per person, across apps. */
    class Person(val name: String, val text: String, val pkg: String, val time: Long, val count: Int, val open: PendingIntent?, val reply: Notification.Action?, val missedCall: Boolean = false, val key: String = "")
    /** Something happening right now: a timer, a call, a delivery, directions. */
    class Now(val key: String, val pkg: String, val title: String, val text: String, val progress: Int, val max: Int, val indeterminate: Boolean,
              val base: Long, val chrono: Boolean, val countDown: Boolean, val open: PendingIntent?)
    /** A notification The Hour is holding (or has just handed back). */
    class Held(val key: String, val pkg: String, val title: String, val text: String, val time: Long, val until: Long, val summary: Boolean = false)

    @Volatile var notes: Map<String, Note> = emptyMap()
    @Volatile var people: List<Person> = emptyList()
    @Volatile var now: List<Now> = emptyList()
    /** Handed back at the last Hour and still unread. */
    @Volatile var hour: List<Held> = emptyList()
    @Volatile var holding = 0

    /** Every music session Android reports. */
    @Volatile var sessions: List<MediaController> = emptyList()
    /** True while Android has the listener connected. */
    @Volatile var connected = false
    @Volatile var service: NotifService? = null
    private val lastPlayed = HashMap<String, Long>()
    private val listeners = mutableListOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    fun onChange(f: () -> Unit) { listeners += f }
    fun changed() { main.post { remember(); listeners.toList().forEach { it() } } }

    fun enabled(c: Context): Boolean =
        Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")?.contains(ComponentName(c, NotifService::class.java).flattenToString()) == true

    fun isPlaying(m: MediaController) = m.playbackState?.state.let { it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING || it == PlaybackState.STATE_CONNECTING }
    private fun remember() { val t = System.currentTimeMillis(); for (m in sessions) if (isPlaying(m)) lastPlayed[m.packageName] = t }

    /** How long paused music stays on home, so you can press play again. */
    const val KEEP_PAUSED_MS = 20 * 60_000L

    /** The music worth showing: whatever is playing, or else what you paused in the last few minutes. */
    fun media(): MediaController? {
        val list = sessions
        list.firstOrNull { isPlaying(it) && it.metadata != null }?.let { return it }
        val t = System.currentTimeMillis()
        return list.filter { it.metadata != null && it.playbackState?.state == PlaybackState.STATE_PAUSED }
            .maxByOrNull { lastPlayed[it.packageName] ?: 0L }
            ?.takeIf { t - (lastPlayed[it.packageName] ?: 0L) < KEEP_PAUSED_MS }
    }
}

/** How a notification is treated. Pure, so it can be tested without a phone. */
object Kinds {
    enum class Kind { PERSON, NOW, MEDIA, QUIET, PASS }

    /** Categories that are never held for The Hour: they're about you, now. */
    private val PASS = setOf(Notification.CATEGORY_ALARM, Notification.CATEGORY_CALL, "missed_call", Notification.CATEGORY_REMINDER, Notification.CATEGORY_EVENT,
        Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_SYSTEM, Notification.CATEGORY_ERROR, Notification.CATEGORY_STATUS,
        Notification.CATEGORY_SERVICE, Notification.CATEGORY_STOPWATCH, Notification.CATEGORY_PROGRESS, "location_sharing", "workout", Notification.CATEGORY_MESSAGE)
    private val LIVE = setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_PROGRESS, Notification.CATEGORY_STOPWATCH,
        Notification.CATEGORY_ALARM, "location_sharing", "workout")

    fun kind(category: String?, ongoing: Boolean, media: Boolean, messages: Boolean, conversation: Boolean, progress: Boolean, chrono: Boolean, promoted: Boolean,
             fullScreen: Boolean = false): Kind = when {
        media -> Kind.MEDIA
        ongoing && (category in LIVE || progress || chrono || promoted) -> Kind.NOW
        ongoing -> Kind.PASS
        messages || conversation || category == Notification.CATEGORY_MESSAGE || category == "missed_call" -> Kind.PERSON
        fullScreen || category in PASS -> Kind.PASS
        else -> Kind.QUIET
    }

    /** The next Hour after [nowMs], given times as minutes after midnight. */
    fun nextHour(nowMs: Long, times: List<Int>, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone)
        val sorted = times.sorted().ifEmpty { listOf(780) }
        for (d in 0..1) for (m in sorted) {
            val t = now.toLocalDate().plusDays(d.toLong()).atStartOfDay().plusMinutes(m.toLong())
            if (t.isAfter(now.plusSeconds(30))) return t.atZone(zone).toInstant().toEpochMilli()
        }
        return nowMs + 6 * 3_600_000L
    }

    /** "1 pm", "7:30 pm", "13:00". */
    fun hourName(minutes: Int, h24: Boolean): String {
        val h = minutes / 60; val m = minutes % 60
        if (h24) return "%d:%02d".format(h, m)
        val hh = if (h % 12 == 0) 12 else h % 12
        return (if (m == 0) "$hh" else "%d:%02d".format(hh, m)) + if (h < 12) " am" else " pm"
    }
}

/**
 * Reads notifications to light up words, gather people, show what's happening now, and (only if you switch it on)
 * hold the rest for The Hour. Nothing is sent anywhere; held notifications are snoozed by Android itself and come back.
 */
class NotifService : NotificationListenerService() {
    private var msm: MediaSessionManager? = null
    private val callbacks = HashMap<MediaController, MediaController.Callback>()
    private val main = Handler(Looper.getMainLooper())
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list) }
    private fun me() = ComponentName(this, NotifService::class.java)
    private val held = LinkedHashMap<String, Live.Held>()
    private val batch = ArrayList<Live.Held>()
    private val rebuildSoon = Runnable { rebuild() }

    override fun onListenerConnected() {
        Live.connected = true; Live.service = this
        load()
        rebuild()
        runCatching {
            msm = getSystemService(MediaSessionManager::class.java)
            msm?.addOnActiveSessionsChangedListener(sessionsChanged, me(), main)
            watch(msm?.getActiveSessions(me()))
        }
    }
    override fun onListenerDisconnected() {
        Live.connected = false; Live.service = null
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        watch(null); Live.notes = emptyMap(); Live.people = emptyList(); Live.now = emptyList(); Live.changed()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) runCatching { maybeHold(sbn) }
        main.removeCallbacks(rebuildSoon); main.postDelayed(rebuildSoon, 120)
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { main.removeCallbacks(rebuildSoon); main.postDelayed(rebuildSoon, 120) }

    // ---------------------------------------------------------------- reading one notification

    private fun kindOf(sbn: StatusBarNotification): Kinds.Kind {
        val n = sbn.notification; val ex = n.extras
        val media = ex.containsKey(Notification.EXTRA_MEDIA_SESSION) || (ex.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true)
        val messages = (ex.getParcelableArray(Notification.EXTRA_MESSAGES)?.isNotEmpty() == true)
        val conversation = android.os.Build.VERSION.SDK_INT >= 30 && runCatching { val r = Ranking(); currentRanking.getRanking(sbn.key, r) && r.isConversation }.getOrDefault(false)
        val progress = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 || ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        val promoted = n.flags and 0x40000 != 0 // Android 16 "live update"
        return Kinds.kind(n.category, sbn.isOngoing, media, messages, conversation, progress, chrono, promoted, n.fullScreenIntent != null)
    }

    private fun title(ex: Bundle) = (ex.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim().orEmpty()
    private fun body(ex: Bundle) = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()

    // ---------------------------------------------------------------- The Hour

    private fun maybeHold(sbn: StatusBarNotification) {
        val p = Prefs.get(this)
        if (!p.hourOn || sbn.packageName == packageName || sbn.packageName == "android" || sbn.packageName == "com.android.systemui") return
        if (sbn.packageName in p.hourAlways || sbn.packageName == defaultSms() || sbn.packageName == defaultDialer()) return
        if (kindOf(sbn) != Kinds.Kind.QUIET) return
        val now = System.currentTimeMillis()
        val h = held[sbn.key]
        if (h != null && now >= h.until - 90_000) {
            // Android handed it back at its Hour: let it be, and list it in this Hour's digest
            held.remove(sbn.key); batch.removeAll { it.key == sbn.key }; batch += h; save(); return
        }
        if (batch.any { it.key == sbn.key }) return
        val until = Kinds.nextHour(now, p.hourTimes)
        val ex = sbn.notification.extras
        snoozeNotification(sbn.key, until - now)
        // summaries are remembered too, so they're let through when they come back, but never listed
        held[sbn.key] = Live.Held(sbn.key, sbn.packageName, title(ex), body(ex), sbn.postTime, until, sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        save()
    }

    private fun defaultSms() = runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull()
    private fun defaultDialer() = runCatching { getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()

    private fun toJson(l: Collection<Live.Held>) = JSONArray().also { a -> l.forEach { a.put(JSONObject().put("k", it.key).put("p", it.pkg).put("t", it.title).put("x", it.text).put("w", it.time).put("u", it.until).put("s", it.summary)) } }.toString()
    private fun fromJson(s: String): List<Live.Held> = runCatching { val a = JSONArray(s); (0 until a.length()).map { a.getJSONObject(it).let { o -> Live.Held(o.getString("k"), o.getString("p"), o.optString("t"), o.optString("x"), o.optLong("w"), o.optLong("u"), o.optBoolean("s")) } } }.getOrDefault(emptyList())
    private fun save() { val p = Prefs.get(this); p.hourHeld = toJson(held.values); p.hourBatch = toJson(batch) }
    private fun load() { held.clear(); fromJson(Prefs.get(this).hourHeld).forEach { held[it.key] = it }; batch.clear(); batch += fromJson(Prefs.get(this).hourBatch) }

    /** Clear this Hour: every notification it handed back. */
    fun clearHour() { runCatching { cancelNotifications(batch.map { it.key }.toTypedArray()) }; batch.clear(); save(); rebuild() }
    /** Open one app's part of the Hour and clear it. */
    fun openHour(pkg: String) {
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        val keys = batch.filter { it.pkg == pkg }.map { it.key }.toSet()
        active.filter { it.key in keys }.maxByOrNull { it.postTime }?.notification?.contentIntent?.let { pi -> runCatching { pi.send() } }
        runCatching { cancelNotifications(keys.toTypedArray()) }
        batch.removeAll { it.key in keys }; save(); rebuild()
    }

    // ---------------------------------------------------------------- everything at once

    private fun rebuild() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val notes = HashMap<String, Live.Note>()
        val people = LinkedHashMap<String, Live.Person>()
        val now = ArrayList<Live.Now>()
        val keys = HashSet<String>()
        for (sbn in active) {
            if (sbn.packageName == packageName) continue
            keys += sbn.key
            val n = sbn.notification; val ex = n.extras
            val summary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
            when (kindOf(sbn)) {
                Kinds.Kind.MEDIA, Kinds.Kind.PASS -> if (!sbn.isOngoing && !summary) note(notes, sbn, title(ex), body(ex))
                Kinds.Kind.NOW -> {
                    val t = title(ex); val x = body(ex)
                    if (t.isEmpty() && x.isEmpty()) continue
                    val countDown = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
                    now += Live.Now(sbn.key, sbn.packageName, t, x, ex.getInt(Notification.EXTRA_PROGRESS), ex.getInt(Notification.EXTRA_PROGRESS_MAX), ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE),
                        n.`when`, ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER), countDown, n.contentIntent)
                }
                Kinds.Kind.PERSON -> {
                    if (summary) continue
                    val msgs = messages(ex)
                    val group = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
                    val conv = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
                    val last = msgs.lastOrNull()
                    val name = (if (group) conv else null) ?: last?.second?.takeIf { it.isNotEmpty() } ?: title(ex)
                    if (name.isEmpty()) continue
                    val text = when {
                        last != null && group && last.second.isNotEmpty() -> "${last.second}: ${last.first}"
                        last != null -> last.first
                        else -> body(ex)
                    }
                    val count = msgs.size.coerceAtLeast(1)
                    val missed = n.category == "missed_call"
                    val reply = n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true && (a.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY || a.semanticAction == Notification.Action.SEMANTIC_ACTION_NONE) }
                    note(notes, sbn, name, text, count)
                    val k = App.fold(name)
                    val prev = people[k]
                    people[k] = if (prev == null || sbn.postTime >= prev.time) Live.Person(name, if (missed && text.isEmpty()) "Missed call" else text, sbn.packageName, sbn.postTime, count + (prev?.count ?: 0), n.contentIntent, reply, missed, sbn.key)
                        else Live.Person(prev.name, prev.text, prev.pkg, prev.time, prev.count + count, prev.open, prev.reply, prev.missedCall, prev.key)
                }
                Kinds.Kind.QUIET -> if (!summary) note(notes, sbn, title(ex), body(ex))
            }
        }
        // the Hour's digest is what it handed back and you haven't cleared
        batch.retainAll { it.key in keys }
        Live.notes = notes
        Live.people = people.values.sortedByDescending { it.time }
        Live.now = now.sortedByDescending { it.base }
        Live.hour = batch.filter { !it.summary }
        Live.holding = held.values.count { !it.summary && it.until > System.currentTimeMillis() - 120_000 }
        // forget anything held long ago that Android never handed back (cleared, or the app withdrew it)
        held.values.removeAll { System.currentTimeMillis() - it.until > 24 * 3_600_000L }
        save()
        Live.changed()
    }

    private fun note(out: HashMap<String, Live.Note>, sbn: StatusBarNotification, t: String, x: String, count: Int = 1) {
        val prev = out[sbn.packageName]
        val total = (prev?.count ?: 0) + count
        out[sbn.packageName] = if (prev == null || sbn.postTime > prev.time) Live.Note(t, x, sbn.postTime, total) else Live.Note(prev.title, prev.text, prev.time, total)
    }

    /** MessagingStyle messages as (text, sender), oldest first. Read by hand: the bundle keys are stable across versions. */
    private fun messages(ex: Bundle): List<Pair<String, String>> = runCatching {
        ex.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull { p ->
            val b = p as? Bundle ?: return@mapNotNull null
            val text = b.getCharSequence("text")?.toString()?.trim() ?: return@mapNotNull null
            val person = runCatching { b.getParcelable<android.app.Person>("sender_person")?.name?.toString() }.getOrNull()
            text to (person ?: b.getCharSequence("sender")?.toString() ?: "").trim()
        } ?: emptyList()
    }.getOrDefault(emptyList())

    // ---------------------------------------------------------------- music

    /** Listens to every music session, not just one, so whichever app starts playing shows up on home. */
    private fun watch(list: List<MediaController>?) {
        for ((m, cb) in callbacks) runCatching { m.unregisterCallback(cb) }
        callbacks.clear()
        val now = list.orEmpty()
        for (m in now) {
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = Live.changed()
                override fun onMetadataChanged(metadata: MediaMetadata?) = Live.changed()
                override fun onSessionDestroyed() { runCatching { watch(msm?.getActiveSessions(me())) } }
            }
            runCatching { m.registerCallback(cb, main); callbacks[m] = cb }
        }
        Live.sessions = now
        Live.changed()
    }
}

/** Only here so a double-tap on the home screen can lock the phone. It watches nothing. */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { me = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { me = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    companion object {
        @Volatile private var me: LockService? = null
        fun lock(): Boolean = me?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
        fun on(): Boolean = me != null
    }
}
