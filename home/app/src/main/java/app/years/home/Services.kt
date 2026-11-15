package app.years.home

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent

/** What the home screen knows about notifications and music. Filled by [NotifService] when you allow it. */
object Live {
    /** The latest message per app: who it's from and what it says. */
    class Note(val title: String, val text: String, val time: Long)
    @Volatile var notes: Map<String, Note> = emptyMap()
    /** Every music session Android reports, newest first. */
    @Volatile var sessions: List<MediaController> = emptyList()
    /** True while Android has the listener connected. */
    @Volatile var connected = false
    private val lastPlayed = HashMap<String, Long>()
    private val listeners = mutableListOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    fun onChange(f: () -> Unit) { listeners += f }
    fun changed() { main.post { remember(); listeners.forEach { it() } } }

    fun enabled(c: Context): Boolean =
        Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")?.contains(ComponentName(c, NotifService::class.java).flattenToString()) == true

    fun isPlaying(m: MediaController) = m.playbackState?.state.let { it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING || it == PlaybackState.STATE_CONNECTING }

    private fun remember() { val now = System.currentTimeMillis(); for (m in sessions) if (isPlaying(m)) lastPlayed[m.packageName] = now }

    /** How long paused music stays on home, so you can press play again. */
    const val KEEP_PAUSED_MS = 20 * 60_000L

    /**
     * The music worth showing: whatever is playing, or else what you paused in the last few minutes. Music that
     * has been paused for a while, or that has nothing to show, quietly leaves.
     */
    fun media(): MediaController? {
        val list = sessions
        list.firstOrNull { isPlaying(it) && it.metadata != null }?.let { return it }
        val now = System.currentTimeMillis()
        return list.filter { it.metadata != null && it.playbackState?.state == PlaybackState.STATE_PAUSED }
            .maxByOrNull { lastPlayed[it.packageName] ?: 0L }
            ?.takeIf { now - (lastPlayed[it.packageName] ?: 0L) < KEEP_PAUSED_MS }
    }
}

/** Reads notifications only to show a dot next to an app and its latest message when you hold it. Nothing is stored or sent. */
class NotifService : NotificationListenerService() {
    private var msm: MediaSessionManager? = null
    private val callbacks = HashMap<MediaController, MediaController.Callback>()
    private val main = Handler(Looper.getMainLooper())
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list) }
    private fun me() = ComponentName(this, NotifService::class.java)

    override fun onListenerConnected() {
        Live.connected = true
        rebuild()
        runCatching {
            msm = getSystemService(MediaSessionManager::class.java)
            msm?.addOnActiveSessionsChangedListener(sessionsChanged, me(), main)
            watch(msm?.getActiveSessions(me()))
        }
    }
    override fun onListenerDisconnected() {
        Live.connected = false
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        watch(null); Live.notes = emptyMap(); Live.changed()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = rebuild()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = rebuild()

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

    private fun rebuild() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val out = HashMap<String, Live.Note>()
        for (n in active) {
            val nf = n.notification
            // ongoing things (music, downloads, navigation) and quiet group summaries aren't news
            if (n.isOngoing || nf.flags and Notification.FLAG_GROUP_SUMMARY != 0 && active.count { it.packageName == n.packageName } > 1) continue
            val ex = nf.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            val prev = out[n.packageName]
            if (prev == null || n.postTime > prev.time) out[n.packageName] = Live.Note(title, text, n.postTime)
        }
        Live.notes = out; Live.changed()
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
