package app.years

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.view.WindowManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Hourly: redraw widgets, and repaint the lock screen when something visible has changed. */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Background.refresh(applicationContext, inputData.getBoolean("force", false))
        return Result.success()
    }
}

object Background {
    suspend fun refresh(c: Context, force: Boolean) {
        Widgets.updateAll(c)
        val s = Prefs.load(c)
        if (!s.autoLock || s.birth == null) return
        // Daylight changes every hour; the others only when the day or the settings change.
        val hourPart = if (s.wall == WallStyle.DAYLIGHT) LocalTime.now().hour.toString() else ""
        val key = "${LocalDate.now()}|$hourPart|${s.span}|${s.wall}|${s.sun}|${s.birth}|${s.names.hashCode()}|${s.people.hashCode()}|${s.events.hashCode()}"
        if (!force && Prefs.getString(c, "lockKey") == key) return
        runCatching { setLockScreen(c, s) }.onSuccess { Prefs.putString(c, "lockKey", key) }
    }

    fun screen(c: Context): Pair<Int, Int> {
        val (w, h) = if (Build.VERSION.SDK_INT >= 30) {
            val b = c.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            b.width() to b.height()
        } else c.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        // always portrait, even if the phone happens to be sideways when this runs
        return minOf(w, h) to maxOf(w, h)
    }

    fun setLockScreen(c: Context, s: Settings) {
        val (w, h) = screen(c)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Render.wallpaper(c, Canvas(bmp), w, h, s, styleFor(s))
        WallpaperManager.getInstance(c).setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
    }

    /** Grid with Daylight switched on behaves like the Daylight style, everywhere. */
    fun styleFor(s: Settings): WallStyle = if (s.wall == WallStyle.GRID && s.sun) WallStyle.DAYLIGHT else s.wall

    fun schedule(c: Context) {
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(
            "years-refresh", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS).build(),
        )
    }

    fun refreshNow(c: Context, force: Boolean = false) {
        WorkManager.getInstance(c).enqueueUniqueWork(
            "years-now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RefreshWorker>().setInputData(workDataOf("force" to force)).build(),
        )
    }
}
