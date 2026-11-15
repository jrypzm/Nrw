package app.years.home

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import java.time.LocalDate
import java.time.ZoneId

/** The torch, as a word. No camera permission is needed to switch the flash on and off. */
object Torch {
    @Volatile var on = false; private set
    /** 0..1 brightness, where the phone allows it. */
    @Volatile var level = 1f; private set
    private var id: String? = null
    private var max = 1
    private var cm: CameraManager? = null
    private val listeners = mutableListOf<() -> Unit>()
    fun onChange(f: () -> Unit): () -> Unit { listeners += f; return f }
    fun off(f: () -> Unit) { listeners -= f }
    private val main = Handler(Looper.getMainLooper())
    private val cb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(camera: String, enabled: Boolean) { if (camera == id && enabled != on) { on = enabled; listeners.toList().forEach { it() } } }
        override fun onTorchStrengthLevelChanged(camera: String, newStrengthLevel: Int) { if (camera == id && max > 1) level = newStrengthLevel / max.toFloat() }
    }

    fun init(c: Context) {
        // looked up again until it works: the camera service is often not ready when the phone has just started
        if (cm != null && id != null) return
        val m = c.applicationContext.getSystemService(CameraManager::class.java) ?: return
        if (cm == null) runCatching { m.registerTorchCallback(cb, main) }
        cm = m
        runCatching {
            id = m.cameraIdList.firstOrNull { i -> val ch = m.getCameraCharacteristics(i); ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
                ?: m.cameraIdList.firstOrNull { m.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            if (Build.VERSION.SDK_INT >= 33 && id != null) max = m.getCameraCharacteristics(id!!).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
        }
    }
    /** For tests: forget the camera so it's looked up again. */
    fun resetForTest() { cm = null; id = null; on = false; max = 1; level = 1f }
    val available get() = id != null
    /** Whether this phone can dim its torch. */
    val dimmable get() = max > 1

    fun toggle(): Boolean = set(!on)
    fun set(v: Boolean): Boolean {
        val m = cm ?: return false; val i = id ?: return false
        return runCatching {
            if (v && dimmable && Build.VERSION.SDK_INT >= 33) m.turnOnTorchWithStrengthLevel(i, (level * max).toInt().coerceIn(1, max)) else m.setTorchMode(i, v)
            if (on != v) { on = v; listeners.toList().forEach { it() } }
        }.isSuccess
    }
    fun setLevel(f: Float) {
        level = f.coerceIn(.05f, 1f)
        val m = cm ?: return; val i = id ?: return
        if (on && dimmable && Build.VERSION.SDK_INT >= 33) runCatching { m.turnOnTorchWithStrengthLevel(i, (level * max).toInt().coerceIn(1, max)) }
    }
}

/** Haptics with texture: a light tick, and a firmer knock where it matters. Falls back gracefully on older phones. */
object Feel {
    private var vib: Vibrator? = null
    private var rich = false
    fun init(c: Context) {
        if (vib != null) return
        val a = c.applicationContext
        vib = if (Build.VERSION.SDK_INT >= 31) a.getSystemService(VibratorManager::class.java)?.defaultVibrator else @Suppress("DEPRECATION") a.getSystemService(Vibrator::class.java)
        rich = Build.VERSION.SDK_INT >= 31 && vib?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, VibrationEffect.Composition.PRIMITIVE_CLICK) == true
    }
    fun tick(v: View) {
        val ok = rich && Build.VERSION.SDK_INT >= 31 && runCatching { vib?.vibrate(VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, .7f).compose()) }.isSuccess
        if (!ok) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    fun knock(v: View) {
        val ok = rich && Build.VERSION.SDK_INT >= 31 && runCatching { vib?.vibrate(VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f).compose()) }.isSuccess
        if (!ok) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

/** Time away from the phone: from the screen going off to the next unlock. Kept as one number per day. */
object Away {
    /** Breaks shorter than this aren't worth a word. */
    const val MIN_MS = 20 * 60_000L

    fun screenOff(c: Context) { Prefs.get(c).offAt = System.currentTimeMillis() }
    fun unlocked(c: Context) {
        val p = Prefs.get(c); val off = p.offAt; if (off <= 0) return
        p.offAt = 0
        val gone = System.currentTimeMillis() - off
        if (gone < MIN_MS || gone > 7 * 24 * 3_600_000L) return
        p.lastAway = gone; p.lastAwayAt = System.currentTimeMillis()
        p.logAway(LocalDate.now().toString(), (gone / 60_000).toInt())
    }
    /** The break to celebrate now, if you just came back from one and haven't seen it yet. */
    fun pending(c: Context): Long? {
        val p = Prefs.get(c)
        if (!p.away || p.lastAwayAt <= 0 || p.awayShown >= p.lastAwayAt) return null
        return p.lastAway.takeIf { System.currentTimeMillis() - p.lastAwayAt < 5 * 60_000L }
    }
    fun words(ms: Long): String { val m = ms / 60_000; val h = m / 60; return if (h == 0L) "$m m" else "$h h ${m % 60} m" }
    /** The last seven days, oldest first, in minutes. */
    fun week(c: Context): List<Pair<LocalDate, Int>> { val log = Prefs.get(c).awayLog(); val t = LocalDate.now(); return (6 downTo 0).map { t.minusDays(it.toLong()).let { d -> d to (log[d.toString()] ?: 0) } } }
}

/** Headphones in: a wired pair, earbuds or a USB headset. */
object Ears {
    private val TYPES = buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET); add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES); add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP); add(AudioDeviceInfo.TYPE_USB_HEADSET)
        add(AudioDeviceInfo.TYPE_HEARING_AID)
        if (Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
    }
    @Volatile var connected = false; private set
    private var am: AudioManager? = null
    private var onChange: (() -> Unit)? = null
    private val cb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(d: Array<out AudioDeviceInfo>?) = check()
        override fun onAudioDevicesRemoved(d: Array<out AudioDeviceInfo>?) = check()
    }
    private fun check() { val now = am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any { it.type in TYPES } == true; if (now != connected) { connected = now; onChange?.invoke() } }
    fun start(c: Context, f: () -> Unit) { am = c.applicationContext.getSystemService(AudioManager::class.java); onChange = f; runCatching { am?.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper())) }; check() }
    fun stop() { runCatching { am?.unregisterAudioDeviceCallback(cb) }; onChange = null }
}

/** Screen time, only if you allow usage access. Used to say how long today, and to spot apps you never open. */
object Usage {
    fun allowed(c: Context): Boolean = runCatching {
        c.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /**
     * Minutes in the app since midnight, from the open and close events themselves (daily totals can include
     * yesterday). Call it off the main thread.
     */
    fun minutesToday(c: Context, pkg: String): Int? {
        if (!allowed(c)) return null
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); val now = System.currentTimeMillis()
        return runCatching {
            val ev = c.getSystemService(UsageStatsManager::class.java).queryEvents(start, now)
            val e = android.app.usage.UsageEvents.Event(); var since = -1L; var total = 0L
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e); if (e.packageName != pkg) continue
                when (e.eventType) {
                    android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> if (since < 0) since = e.timeStamp
                    android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED, android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED -> if (since >= 0) { total += e.timeStamp - since; since = -1 }
                }
            }
            if (since >= 0) total += now - since
            (total / 60_000).toInt()
        }.getOrNull()
    }
    private var usedAt = 0L
    private var used: Map<String, Long> = emptyMap()
    /** When each app was last used, over the last two months. Read at most once an hour: it's a big query. */
    fun lastUsed(c: Context): Map<String, Long> {
        if (!allowed(c)) return emptyMap()
        val now = System.currentTimeMillis()
        if (now - usedAt < 3_600_000L && used.isNotEmpty()) return used
        used = runCatching { c.getSystemService(UsageStatsManager::class.java).queryAndAggregateUsageStats(now - 60L * 86_400_000L, now).mapValues { it.value.lastTimeUsed } }.getOrDefault(emptyMap())
        usedAt = now
        return used
    }
}
