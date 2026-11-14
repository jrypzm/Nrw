package app.years

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings as AndroidSettings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/** Android's own screen-time numbers, read on the phone and never sent anywhere. */
object ScreenTime {
    fun allowed(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
    }

    fun openSettings(c: Context) {
        runCatching { c.startActivity(Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Average hours a day over the last 7 days, or null when not allowed. */
    fun hoursPerDay(c: Context): Double? {
        if (!allowed(c)) return null
        val usm = c.getSystemService(UsageStatsManager::class.java) ?: return null
        // exactly the last 7 full days, midnight to midnight
        val zone = java.time.ZoneId.systemDefault()
        val end = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val start = LocalDate.now().minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val launcher = runCatching {
            c.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
        val stats = runCatching { usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) }.getOrNull() ?: return null
        // daily buckets that start inside the window, so a day is never counted twice
        val ms = stats.filter { it.firstTimeStamp >= start - 3_600_000 && it.firstTimeStamp < end && it.packageName != launcher && it.packageName != "com.android.systemui" && it.packageName != c.packageName }
            .sumOf { it.totalTimeInForeground }
        return (ms / 3_600_000.0 / 7.0).coerceIn(0.0, 20.0)
    }
}

/** A heartbeat you can feel: one soft double pulse per second. */
object Heart {
    private fun vibrator(c: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) c.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") c.getSystemService(Vibrator::class.java)

    fun beat(c: Context) {
        val v = vibrator(c) ?: return
        runCatching {
            if (v.hasAmplitudeControl()) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 28, 110, 22), intArrayOf(0, 255, 0, 120), -1))
            else v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 28, 110, 22), -1))
        }
    }
}

/** Monday morning: "Week 1,812 of your life begins." Off unless you turn it on. */
class NudgeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val s = Prefs.load(applicationContext)
        if (s.nudge && s.birth != null) Nudge.show(applicationContext, s)
        return Result.success()
    }
}

object Nudge {
    private const val CHANNEL = "weekly"

    fun permitted(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun schedule(c: Context, on: Boolean) {
        val wm = WorkManager.getInstance(c)
        if (!on) { wm.cancelUniqueWork("years-nudge"); return }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).atTime(9, 0)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        val delay = Duration.between(now, next).toMinutes()
        wm.enqueueUniquePeriodicWork(
            "years-nudge", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<NudgeWorker>(7, TimeUnit.DAYS).setInitialDelay(delay, TimeUnit.MINUTES).build(),
        )
    }

    @SuppressLint("MissingPermission")
    fun show(c: Context, s: Settings) {
        val birth = s.birth ?: return
        if (!permitted(c)) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(NotificationChannel(CHANNEL, "Weekly note", NotificationManager.IMPORTANCE_LOW))
        val week = Life.weekOfLife(birth, LocalDate.now())
        val left = (Life.daysLeft(s) / 7)
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_yrs)
            .setContentTitle("A new week")
            .setContentText("You're in week ${Life.whole(week.toDouble())} of your life. ${Life.whole(left.toDouble())} weeks left. Make this one count.")
            .setContentIntent(open).setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(1812, n) }
    }
}
