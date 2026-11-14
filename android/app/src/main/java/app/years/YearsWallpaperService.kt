package app.years

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * The live wallpaper. It sleeps while the screen is off and draws only when time moves:
 * a one-second ripple each time you wake the screen, then one frame a minute
 * (Daylight brightness, the current year's fill), or a slow drift for Stars.
 */
class YearsWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = YearsEngine()

    inner class YearsEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var rippleStart = 0L
        private var settings = Prefs.load(this@YearsWallpaperService)
        private val tick = Runnable { draw() }

        private fun rippleMs(): Long {
            val lived = settings.birth?.let { Life.age(it).toInt() } ?: 0
            return 700L + lived * 14L
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                settings = Prefs.load(this@YearsWallpaperService)
                rippleStart = if (settings.ripple && !isPreview) SystemClock.uptimeMillis() else 0L
                draw()
            } else handler.removeCallbacks(tick)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height); draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false; handler.removeCallbacks(tick); super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() { handler.removeCallbacks(tick); super.onDestroy() }

        private fun draw() {
            handler.removeCallbacks(tick)
            val holder = surfaceHolder
            val frame = holder.surfaceFrame
            if (frame.width() <= 0 || frame.height() <= 0) return
            val since = SystemClock.uptimeMillis() - rippleStart
            val dur = rippleMs()
            val ripple = if (rippleStart > 0 && since < dur) since / dur.toFloat() else -1f
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                val style = if (settings.wall == WallStyle.GRID && settings.sun) WallStyle.DAYLIGHT else settings.wall
                Render.wallpaper(this@YearsWallpaperService, canvas, frame.width(), frame.height(), settings, style, System.currentTimeMillis(), ripple)
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
            if (!visible) return
            val next = when {
                ripple >= 0f -> 16L
                settings.wall == WallStyle.STARS -> 1000L
                else -> 60_000L
            }
            handler.postDelayed(tick, next)
        }
    }
}
