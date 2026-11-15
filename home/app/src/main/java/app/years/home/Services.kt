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
    /** Music state as Android last told us, so home never has to ask the player app (a call into another process). */
    val meta = HashMap<String, MediaMetadata?>()
    val state = HashMap<String, PlaybackState?>()
    private val listeners = mutableListOf<() -> Unit>()
    private val mediaListeners = mutableListOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    fun onChange(f: () -> Unit): () -> Unit { listeners += f; return f }
    fun off(f: () -> Unit) { listeners -= f; mediaListeners -= f }
    /** Only the music changed: just the player redraws. */
    fun onMedia(f: () -> Unit): () -> Unit { mediaListeners += f; return f }
    // many updates in a burst (a group of notifications, a player's progress) become one redraw
    private val fire = Runnable { listeners.toList().forEach { it() } }
    private val fireMedia = Runnable { mediaListeners.toList().forEach { it() } }
    fun changed() { main.removeCallbacks(fire); main.postDelayed(fire, 60) }
    fun mediaChanged() { main.removeCallbacks(fireMedia); main.postDelayed(fireMedia, 60) }
    /** Remember when music was last playing, or was paused, so paused music stays a while. */
    fun played(pkg: String, s: PlaybackState?) { if (s?.state == PlaybackState.STATE_PLAYING || s?.state == PlaybackState.STATE_PAUSED) lastPlayed[pkg] = System.currentTimeMillis() }

    fun enabled(c: Context): Boolean =
        Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")?.contains(ComponentName(c, NotifService::class.java).flattenToString()) == true

    fun stateOf(m: MediaController): PlaybackState? = if (state.containsKey(m.packageName)) state[m.packageName] else m.playbackState
    fun metaOf(m: MediaController): MediaMetadata? = if (meta.containsKey(m.packageName)) meta[m.packageName] else m.metadata
    fun isPlaying(m: MediaController) = stateOf(m)?.state.let { it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING || it == PlaybackState.STATE_CONNECTING }

    /** How long paused music stays on home, so you can press play again. */
    const val KEEP_PAUSED_MS = 20 * 60_000L

    /** The music worth showing: whatever is playing, or else what you paused in the last few minutes. */
    fun media(): MediaController? {
        val list = sessions
        list.firstOrNull { isPlaying(it) && metaOf(it) != null }?.let { return it }
        val t = System.currentTimeMillis()
        return list.filter { metaOf(it) != null && stateOf(it)?.state == PlaybackState.STATE_PAUSED }
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

    /** Looks quiet, but can't wait for The Hour. */
    fun urgent(importanceHigh: Boolean, priorityHigh: Boolean, conversationShortcut: Boolean, people: Boolean, replyBox: Boolean) =
        importanceHigh || priorityHigh || conversationShortcut || people || replyBox

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
        watch(null); Live.notes = emptyMap(); Live.people = emptyList(); Live.now = emptyList(); Live.hour = emptyList(); Live.holding = 0; Live.changed()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) runCatching { maybeHold(sbn) }
        main.removeCallbacks(rebuildSoon); main.postDelayed(rebuildSoon, 120)
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { main.removeCallbacks(rebuildSoon); main.postDelayed(rebuildSoon, 120) }
    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        // the app took back something we were holding: it won't come back, so stop counting it
        if (sbn != null && (reason == REASON_APP_CANCEL || reason == REASON_APP_CANCEL_ALL) && held.remove(sbn.key) != null) dirty = true
        onNotificationRemoved(sbn)
    }

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
        val now = System.currentTimeMillis()
        // first: is this one coming back from its Hour? (checked even when The Hour has been switched off since)
        val h = held[sbn.key]
        if (h != null && now >= h.until - 90_000) {
            held.remove(sbn.key); batch.removeAll { it.key == sbn.key }; batch += h; dirty = true; return
        }
        if (!p.hourOn || sbn.packageName == packageName || sbn.packageName == "android" || sbn.packageName == "com.android.systemui") return
        if (sbn.packageName in p.hourAlways || sbn.packageName == defaultSms() || sbn.packageName == defaultDialer()) return
        val n = sbn.notification
        // never a group summary: snoozing a summary makes Android snooze the whole group, messages included
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || sbn.tag == "ranker_group") return
        if (kindOf(sbn) != Kinds.Kind.QUIET || urgent(sbn)) return
        if (batch.any { it.key == sbn.key }) return
        // it shares a group with something that matters: leave the whole group alone
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        if (active.any { it.key != sbn.key && it.groupKey == sbn.groupKey && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 && runCatching { kindOf(it) != Kinds.Kind.QUIET || urgent(it) }.getOrDefault(true) }) return
        val until = Kinds.nextHour(now, p.hourTimes)
        val ex = n.extras
        snoozeNotification(sbn.key, until - now)
        held[sbn.key] = Live.Held(sbn.key, sbn.packageName, title(ex), body(ex), sbn.postTime, until)
        dirty = true
    }

    /** Things that look quiet but can't wait: high importance, someone's name on it, or a reply box. */
    private fun urgent(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification; val ex = n.extras
        val important = runCatching { val r = Ranking(); currentRanking.getRanking(sbn.key, r) && r.importance >= android.app.NotificationManager.IMPORTANCE_HIGH }.getOrDefault(false)
        @Suppress("DEPRECATION") val loud = n.priority >= Notification.PRIORITY_HIGH
        val people = runCatching { ex.getParcelableArrayList<android.app.Person>(Notification.EXTRA_PEOPLE_LIST)?.isNotEmpty() == true }.getOrDefault(false)
        val reply = n.actions?.any { a -> a.remoteInputs?.isNotEmpty() == true } == true
        return Kinds.urgent(important, loud, n.shortcutId != null, people, reply)
    }

    private fun defaultSms() = runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull()
    private fun defaultDialer() = runCatching { getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()

    private fun toJson(l: Collection<Live.Held>) = JSONArray().also { a -> l.forEach { a.put(JSONObject().put("k", it.key).put("p", it.pkg).put("t", it.title).put("x", it.text).put("w", it.time).put("u", it.until).put("s", it.summary)) } }.toString()
    private fun fromJson(s: String): List<Live.Held> = runCatching { val a = JSONArray(s); (0 until a.length()).map { a.getJSONObject(it).let { o -> Live.Held(o.getString("k"), o.getString("p"), o.optString("t"), o.optString("x"), o.optLong("w"), o.optLong("u"), o.optBoolean("s")) } } }.getOrDefault(emptyList())
    private var dirty = false
    private fun save() { if (!dirty) return; dirty = false; val p = Prefs.get(this); p.hourHeld = toJson(held.values); p.hourBatch = toJson(batch) }
    private fun load() { held.clear(); fromJson(Prefs.get(this).hourHeld).forEach { held[it.key] = it }; batch.clear(); batch += fromJson(Prefs.get(this).hourBatch) }

    /** Clear this Hour: every notification it handed back. */
    fun clearHour() { runCatching { cancelNotifications(batch.map { it.key }.toTypedArray()) }; batch.clear(); dirty = true; save(); rebuild() }
    /** Open one app's part of the Hour and clear it. */
    fun openHour(pkg: String) {
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        val keys = batch.filter { it.pkg == pkg }.map { it.key }.toSet()
        active.filter { it.key in keys }.maxByOrNull { it.postTime }?.notification?.contentIntent?.let { pi -> pi.go(this) }
        runCatching { cancelNotifications(keys.toTypedArray()) }
        batch.removeAll { it.key in keys }; dirty = true; save(); rebuild()
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
            // another app's odd extras must never crash the home screen: skip that one
            runCatching { read(sbn, notes, people, now) }
        }
        // the Hour's digest is what it handed back and you haven't cleared
        if (batch.retainAll { it.key in keys }) dirty = true
        Live.notes = notes
        Live.people = people.values.sortedByDescending { it.time }
        Live.now = now.sortedByDescending { it.base }
        Live.hour = batch.filter { !it.summary }
        Live.holding = held.values.count { !it.summary && it.until > System.currentTimeMillis() - 120_000 }
        // forget anything held long ago that Android never handed back
        if (held.values.removeAll { System.currentTimeMillis() - it.until > 24 * 3_600_000L }) dirty = true
        save()
        Live.changed()
    }

    private fun read(sbn: StatusBarNotification, notes: HashMap<String, Live.Note>, people: LinkedHashMap<String, Live.Person>, now: ArrayList<Live.Now>) {
            val n = sbn.notification; val ex = n.extras
            val summary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
            when (kindOf(sbn)) {
                Kinds.Kind.MEDIA, Kinds.Kind.PASS -> if (!sbn.isOngoing && !summary) note(notes, sbn, title(ex), body(ex))
                Kinds.Kind.NOW -> {
                    val t = title(ex); val x = body(ex)
                    if (t.isEmpty() && x.isEmpty()) return
                    val countDown = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
                    now += Live.Now(sbn.key, sbn.packageName, t, x, ex.getInt(Notification.EXTRA_PROGRESS), ex.getInt(Notification.EXTRA_PROGRESS_MAX), ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE),
                        n.`when`, ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER), countDown, n.contentIntent)
                }
                Kinds.Kind.PERSON -> {
                    if (summary) return
                    val msgs = messages(ex)
                    val group = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
                    val conv = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
                    val last = msgs.lastOrNull()
                    val name = (if (group) conv else null) ?: last?.second?.takeIf { it.isNotEmpty() } ?: title(ex)
                    if (name.isEmpty()) return
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
            val pkg = m.packageName
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) { Live.state[pkg] = state; Live.played(pkg, state); Live.mediaChanged() }
                override fun onMetadataChanged(metadata: MediaMetadata?) { Live.meta[pkg] = metadata; Live.mediaChanged() }
                override fun onSessionDestroyed() { runCatching { watch(msm?.getActiveSessions(me())) } }
            }
            runCatching {
                Live.meta[pkg] = m.metadata; Live.state[pkg] = m.playbackState.also { Live.played(pkg, it) }
                m.registerCallback(cb, main); callbacks[m] = cb
            }
        }
        Live.meta.keys.retainAll(now.map { it.packageName }.toSet()); Live.state.keys.retainAll(now.map { it.packageName }.toSet())
        Live.sessions = now
        Live.mediaChanged()
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
        fun notifications(): Boolean = me?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true
        fun on(): Boolean = me != null
    }
}

/**
 * Opens what a notification points to. From Android 14 an app must say it allows the activity start it's sending
 * on someone's behalf; without this, taps on the home screen could do nothing.
 */
fun PendingIntent.go(c: Context, fill: android.content.Intent? = null): Boolean = runCatching {
    if (android.os.Build.VERSION.SDK_INT >= 34) {
        val o = android.app.ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
        send(c, 0, fill, null, null, null, o)
    } else send(c, 0, fill)
}.isSuccess
