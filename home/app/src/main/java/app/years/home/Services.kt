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
    @Volatile var media: MediaController? = null
    private val listeners = mutableListOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    fun onChange(f: () -> Unit) { listeners += f }
    fun changed() { main.post { listeners.forEach { it() } } }

    fun enabled(c: Context): Boolean =
        Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")?.contains(ComponentName(c, NotifService::class.java).flattenToString()) == true

    fun playing(): MediaController? = media?.takeIf { it.playbackState?.state == PlaybackState.STATE_PLAYING }
}

/** Reads notifications only to show a dot next to an app and its latest message when you hold it. Nothing is stored or sent. */
class NotifService : NotificationListenerService() {
    private var msm: MediaSessionManager? = null
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { list -> pickMedia(list) }
    private val mediaCb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = Live.changed()
        override fun onMetadataChanged(metadata: MediaMetadata?) = Live.changed()
        override fun onSessionDestroyed() { Live.media = null; Live.changed() }
    }

    override fun onListenerConnected() {
        rebuild()
        runCatching {
            msm = getSystemService(MediaSessionManager::class.java)
            val me = ComponentName(this, NotifService::class.java)
            msm?.addOnActiveSessionsChangedListener(sessions, me)
            pickMedia(msm?.getActiveSessions(me))
        }
    }
    override fun onListenerDisconnected() {
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessions) }
        Live.media?.unregisterCallback(mediaCb); Live.media = null; Live.notes = emptyMap(); Live.changed()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = rebuild()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = rebuild()

    private fun pickMedia(list: List<MediaController>?) {
        Live.media?.unregisterCallback(mediaCb)
        val m = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        Live.media = m; m?.registerCallback(mediaCb, Handler(Looper.getMainLooper())); Live.changed()
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
