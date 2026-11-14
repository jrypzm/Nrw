package app.years

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class WidgetKind { LIFE, NUMBER, DECADE, YEAR, DAYS, TODAY, PEOPLE }

/** Pixel drawing shared by widgets, the live wallpaper, the lock screen image and the share card. */
object Render {
    /** Set only by screenshot tests: draw everything at rest, with no entrance animation. */
    @JvmField var still = false
    const val EMPTY = 0xFF232323.toInt()
    const val PARTIAL = 0xFF3A3A3C.toInt()
    const val MUTE = 0xFF8E8E93.toInt()
    private const val RING = 0x8CFFFFFF.toInt()

    private var fonts: Map<Int, Typeface> = emptyMap()
    fun font(c: Context, id: Int): Typeface {
        fonts[id]?.let { return it }
        val tf = runCatching { ResourcesCompat.getFont(c.applicationContext, id) }.getOrNull() ?: Typeface.DEFAULT
        fonts = fonts + (id to tf)
        return tf
    }

    fun dot(cv: Canvas, p: Paint, cx: Float, cy: Float, r: Float, color: Int, frac: Float? = null) {
        p.style = Paint.Style.FILL
        if (frac == null) {
            p.color = color; cv.drawCircle(cx, cy, r, p); return
        }
        p.color = PARTIAL; cv.drawCircle(cx, cy, r, p)
        cv.save()
        // a fresh path per dot: widgets, the worker and the wallpaper draw on different threads
        val clip = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        cv.clipPath(clip)
        p.color = color
        cv.drawRect(cx - r, cy + r - 2 * r * frac, cx + r, cy + r, p)
        cv.restore()
    }

    /** Draws a dot grid whose outer dots sit flush with [left] and [left]+[width]. Returns its height. */
    fun grid(
        cv: Canvas, left: Float, top: Float, width: Float, cols: Int, total: Int, done: Double, lit: Int,
        named: Set<Int> = emptySet(), ratio: Float = 0.6f, scaleOf: ((Int) -> Float)? = null,
    ): Float {
        val cell = width / (cols - (1 - ratio))
        val inset = cell * (1 - ratio) / 2
        val r = cell * ratio / 2
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val doneInt = floor(done).toInt()
        for (i in 0 until total) {
            val cx = left - inset + (i % cols + .5f) * cell
            val cy = top - inset + (i / cols + .5f) * cell
            val k = scaleOf?.invoke(i) ?: 1f
            when {
                i < doneInt -> dot(cv, p, cx, cy, r * k, lit)
                i == doneInt && done < total -> dot(cv, p, cx, cy, r * k, lit, (done - doneInt).toFloat())
                else -> dot(cv, p, cx, cy, r, EMPTY)
            }
            if (i in named) {
                p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, cell * 0.035f); p.color = RING
                cv.drawCircle(cx, cy, cell * 0.43f, p)
                p.style = Paint.Style.FILL
            }
        }
        val rows = ceil(total / cols.toFloat())
        return rows * cell - 2 * inset
    }

    fun gridHeight(width: Float, cols: Int, total: Int, ratio: Float = 0.6f): Float {
        val cell = width / (cols - (1 - ratio))
        return ceil(total / cols.toFloat()) * cell - cell * (1 - ratio)
    }

    private fun text(c: Context, cv: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, fontId: Int, maxW: Float = Float.MAX_VALUE, align: Paint.Align = Paint.Align.LEFT, tracking: Float = 0f): Float {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        p.typeface = font(c, fontId); p.color = color; p.textAlign = align; p.letterSpacing = tracking
        p.fontFeatureSettings = "tnum"
        var sz = size
        p.textSize = sz
        while (p.measureText(s) > maxW && sz > 6f) { sz *= 0.92f; p.textSize = sz }
        cv.drawText(s, x, y, p)
        return sz
    }

    private fun lit(s: Settings): Int = if (s.sun) Life.litColor(Life.sunLevel()) else Color.WHITE

    // ---------------------------------------------------------------- widgets

    fun widget(c: Context, kind: WidgetKind, wDp: Float, hDp: Float, settings: Settings? = null): Bitmap {
        val d = min(c.resources.displayMetrics.density, 3f)
        val w = (wDp * d).roundToInt().coerceIn(48, 1500)
        val h = (hDp * d).roundToInt().coerceIn(48, 1500)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val s = settings ?: Prefs.load(c)
        val W = w.toFloat(); val H = h.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rad = min(26 * d, min(W, H) / 2.2f)
        if (s.widgetLook == WidgetLook.BLACK) {
            p.color = Color.BLACK; cv.drawRoundRect(0f, 0f, W, H, rad, rad, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = d; p.color = 0xFF262628.toInt()
            cv.drawRoundRect(d / 2, d / 2, W - d / 2, H - d / 2, rad, rad, p)
        } else {
            p.color = 0x99151517.toInt(); cv.drawRoundRect(0f, 0f, W, H, rad, rad, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = d; p.color = 0x24FFFFFF
            cv.drawRoundRect(d / 2, d / 2, W - d / 2, H - d / 2, rad, rad, p)
        }
        p.style = Paint.Style.FILL
        val pad = min(18 * d, min(W, H) * 0.14f)
        if (s.birth == null) {
            text(c, cv, "Open yrs. to begin", W / 2, H / 2 + 5 * d, 13 * d, MUTE, R.font.inter_medium, W - 2 * pad, Paint.Align.CENTER)
            return bmp
        }
        val lit = lit(s)
        when (kind) {
            WidgetKind.LIFE -> {
                val m = Life.model(s.copy(zoom = Life.ZOOM_YEARS), ViewKind.LIFE)
                val hl = Life.headline(s.copy(unit = 0), m)
                val num = if (s.pct) "${floor(hl.pct).toInt()}%" else Life.whole(hl.value)
                val label = if (s.pct) hl.word else "${hl.noun} ${hl.word}"
                val named = s.names.keys.toSet()
                if (W / H >= 1.45f) {
                    val leftW = W * 0.36f
                    val gx = pad + leftW
                    val gw0 = W - gx - pad
                    val gh0 = H - 2 * pad
                    val gw = min(gw0, gh0 / gridHeight(1f, 10, s.span))
                    val gh = gridHeight(gw, 10, s.span)
                    grid(cv, W - pad - gw, (H - gh) / 2, gw, 10, s.span, m.done, lit, named)
                    val size = min(H * 0.40f, leftW * 0.62f)
                    text(c, cv, num, pad - size * 0.04f, H / 2 + size * 0.28f, size, Color.WHITE, R.font.interdisplay_extralight, leftW - 8 * d, tracking = -0.04f)
                    text(c, cv, label, pad, H / 2 + size * 0.28f + 18 * d, 12 * d, MUTE, R.font.inter_medium, leftW - 8 * d)
                } else {
                    val sz = text(c, cv, "$num $label", pad, pad + 14 * d, 15 * d, Color.WHITE, R.font.interdisplay_semibold, W - 2 * pad)
                    val top = pad + sz + 10 * d
                    val gw = min(W - 2 * pad, (H - top - pad) / gridHeight(1f, 10, s.span))
                    grid(cv, (W - gw) / 2, top, gw, 10, s.span, m.done, lit, named)
                }
            }
            WidgetKind.NUMBER -> {
                val m = Life.model(s.copy(zoom = Life.ZOOM_YEARS), ViewKind.LIFE)
                val hl = Life.headline(s.copy(unit = 0), m)
                val cap = if (s.lived) "Years lived" else "Years left"
                text(c, cv, if (s.pct) (if (s.lived) "Lived" else "Left") else cap, pad, pad + 12 * d, 12 * d, MUTE, R.font.inter_medium, W - 2 * pad)
                val num = if (s.pct) "${floor(hl.pct).toInt()}%" else Life.whole(hl.value)
                text(c, cv, num, pad - 3 * d, H - pad, min(H * 0.5f, W * 0.55f), Color.WHITE, R.font.interdisplay_extralight, W - 2 * pad, tracking = -0.04f)
            }
            WidgetKind.DECADE -> {
                val m = Life.model(s, ViewKind.DECADE)
                val textW = min(W * 0.34f, 120 * d)
                text(c, cv, m.context ?: "", pad, H / 2 - 2 * d, 14 * d, Color.WHITE, R.font.inter_semibold, textW)
                text(c, cv, "${floor(m.done).toInt()} of 10 years", pad, H / 2 + 15 * d, 12 * d, MUTE, R.font.inter_medium, textW)
                val gx = pad + textW + 10 * d
                val gw = min(W - gx - pad, (H - 2 * pad) / gridHeight(1f, 10, 10, 0.62f))
                val gh = gridHeight(gw, 10, 10, 0.62f)
                grid(cv, W - pad - gw, (H - gh) / 2, gw, 10, 10, m.done, lit, s.names.keys.mapNotNull { k -> m.yearIndex?.let { yi -> (0 until 10).firstOrNull { yi(it) == k } } }.toSet(), ratio = 0.62f)
            }
            WidgetKind.YEAR -> {
                val m = Life.model(s, ViewKind.YEAR)
                val footer = 38 * d
                val gw = min(W - 2 * pad, (H - 2 * pad - footer) / gridHeight(1f, 4, 12, 0.62f))
                grid(cv, pad, pad, gw, 4, 12, m.done, lit, ratio = 0.62f)
                text(c, cv, m.context ?: "", pad, H - pad - 16 * d, 14 * d, Color.WHITE, R.font.inter_semibold, W - 2 * pad)
                text(c, cv, "${floor(m.done).toInt()} of 12 months", pad, H - pad, 12 * d, MUTE, R.font.inter_medium, W - 2 * pad)
            }
            WidgetKind.DAYS -> {
                val birth = s.birth!!
                text(c, cv, "Days left", pad, pad + 12 * d, 12 * d, MUTE, R.font.inter_medium, W - 2 * pad)
                text(c, cv, Life.whole(Life.daysLeft(s).toDouble()), pad - 2 * d, H - pad - 20 * d, min(H * 0.34f, W * 0.30f), Color.WHITE, R.font.interdisplay_extralight, W - 2 * pad, tracking = -0.04f)
                text(c, cv, "Today is day ${Life.whole(Life.dayOfLife(birth).toDouble())}", pad, H - pad, 12 * d, MUTE, R.font.inter_medium, W - 2 * pad)
            }
            WidgetKind.TODAY -> {
                val birth = s.birth!!
                val today = java.time.LocalDate.now()
                text(c, cv, "Day ${Life.whole(Life.dayOfLife(birth, today).toDouble())}", pad, pad + 16 * d, 17 * d, Color.WHITE, R.font.interdisplay_semibold, W - 2 * pad)
                val line = Lines.get(c, today)
                val body = line ?: "Tap to write one line about today"
                val p2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font(c, R.font.inter_regular); textSize = 13 * d; color = if (line != null) Color.WHITE else MUTE }
                var y = pad + 40 * d
                wrap(body, p2, W - 2 * pad).take(max(1, ((H - y - pad) / (18 * d)).toInt() + 1)).forEach { ln -> cv.drawText(ln, pad, y, p2); y += 18 * d }
            }
            WidgetKind.PEOPLE -> {
                val people = s.people.take(if (H > 130 * d) 3 else 2)
                if (people.isEmpty()) {
                    text(c, cv, "Add the people you love in yrs.", pad, H / 2 + 5 * d, 13 * d, MUTE, R.font.inter_medium, W - 2 * pad)
                } else {
                    val rowH = (H - 2 * pad) / people.size
                    val wide = W >= 230 * d
                    people.forEachIndexed { i, person ->
                        val t = Life.together(s, person)
                        val y = pad + rowH * i + rowH / 2
                        val unit = if (person.often == Often.DAILY) "days" else "visits"
                        if (wide) {
                            val textW = W * 0.40f
                            text(c, cv, person.name, pad, y - 2 * d, 14 * d, Color.WHITE, R.font.inter_semibold, textW)
                            text(c, cv, "${Life.whole(t.visits.toDouble())} $unit left", pad, y + 14 * d, 12 * d, MUTE, R.font.inter_medium, textW)
                            // up to 20 visits as readable dots, in two rows of ten
                            val n = min(t.visits, 20L).toInt()
                            val gw = min(W - pad - (pad + textW + 8 * d), 130 * d)
                            if (n > 0) {
                                val gh = gridHeight(gw, 10, 20, 0.6f)
                                grid(cv, W - pad - gw, y - gh / 2, gw, 10, 20, n.toDouble(), lit, ratio = 0.6f)
                            }
                        } else {
                            text(c, cv, person.name, pad, y - 4 * d, 13 * d, MUTE, R.font.inter_medium, W - 2 * pad)
                            text(c, cv, "${Life.whole(t.visits.toDouble())} $unit", pad, y + 16 * d, 19 * d, Color.WHITE, R.font.interdisplay_semibold, W - 2 * pad)
                        }
                    }
                }
            }
        }
        return bmp
    }

    private fun wrap(t: String, p: Paint, maxW: Float): List<String> {
        val out = mutableListOf<String>(); var cur = ""
        t.split(" ").forEach { w -> val next = if (cur.isEmpty()) w else "$cur $w"; if (p.measureText(next) > maxW && cur.isNotEmpty()) { out += cur; cur = w } else cur = next }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    fun describe(c: Context, kind: WidgetKind): String {
        val s = Prefs.load(c)
        if (s.birth == null) return "yrs."
        if (kind == WidgetKind.DAYS || kind == WidgetKind.TODAY) return "${Life.whole(Life.daysLeft(s).toDouble())} days left"
        val m = Life.model(s.copy(zoom = Life.ZOOM_YEARS), if (kind == WidgetKind.DECADE) ViewKind.DECADE else if (kind == WidgetKind.YEAR) ViewKind.YEAR else ViewKind.LIFE)
        val hl = Life.headline(s.copy(unit = 0), m)
        return "${Life.whole(hl.value)} ${hl.noun} ${hl.word}"
    }

    // ---------------------------------------------------------------- wallpapers

    /** [ripple] 0..1 plays the unlock wave, anything else draws the resting state. */
    fun wallpaper(c: Context, cv: Canvas, w: Int, h: Int, s: Settings, style: WallStyle, timeMs: Long = 0L, ripple: Float = -1f, caption: Boolean = true) {
        cv.drawColor(Color.BLACK)
        val birth = s.birth ?: return
        val W = w.toFloat(); val H = h.toFloat()
        val age = Life.age(birth, ZonedDateTime.now())
        val lived = floor(age).toInt()
        val span = s.span
        val done = age.coerceAtMost(span.toDouble())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val sun = Life.sunLevel()
        when (style) {
            WallStyle.GRID, WallStyle.DAYLIGHT -> {
                val lit = if (style == WallStyle.DAYLIGHT) Life.litColor(sun) else Color.WHITE
                val gw = W * 0.62f
                val gh = gridHeight(gw, 10, span)
                val top = H * 0.46f - gh / 2
                grid(cv, (W - gw) / 2, top, gw, 10, span, done, lit, s.names.keys.toSet(), scaleOf = rippleScale(ripple, lived))
                if (caption) {
                    val left = max(0, floor(span - age).toInt())
                    val capColor = if (style == WallStyle.DAYLIGHT) Color.argb((150 + 105 * sun).roundToInt(), 142, 142, 147) else MUTE
                    text(c, cv, "$left ${if (left == 1) "year" else "years"} left", W / 2, top + gh + W * 0.085f, W * 0.036f, capColor, R.font.inter_medium, W, Paint.Align.CENTER)
                    Life.lineOfDay(c, s)?.let { ln ->
                        text(c, cv, ln, W / 2, top + gh + W * 0.135f, W * 0.030f, Color.argb(150, 142, 142, 147), R.font.inter_regular, W * 0.8f, Paint.Align.CENTER)
                    }
                }
            }
            WallStyle.HOURGLASS -> {
                val cx = W / 2; val r = W * 0.019f; val step = r * 2.6f; val hw = W * 0.3f
                val neck = H * 0.50f; val top = H * 0.30f; val bottom = H * 0.72f
                p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, W * 0.003f); p.color = 0xFF2A2A2C.toInt()
                val glass = Path().apply {
                    moveTo(cx - hw, top - step * 1.5f); lineTo(cx - r * 1.6f, neck); lineTo(cx - hw, bottom + step * 0.6f)
                    moveTo(cx + hw, top - step * 1.5f); lineTo(cx + r * 1.6f, neck); lineTo(cx + hw, bottom + step * 0.6f)
                }
                cv.drawPath(glass, p)
                p.style = Paint.Style.FILL
                var leftN = span - min(lived, span) - (if (age < span) 1 else 0)
                var row = 0
                while (leftN > 0 && row < 60) {
                    val y = neck - step * 1.3f - row * step
                    val width = max(step, (neck - y) / (neck - top + step * 1.5f) * hw * 1.8f)
                    val n = max(1, min(leftN, (width / step).toInt()))
                    for (k in 0 until n) dot(cv, p, cx + (k - (n - 1) / 2f) * step, y, r, 0xFF2C2C2E.toInt())
                    leftN -= n; row++
                }
                var l = min(lived, span); row = 0
                while (l > 0 && row < 60) {
                    val y = bottom - row * step
                    val width = max(step, (y - neck) / (bottom - neck) * hw * 1.85f)
                    val n = max(1, min(l, (width / step).toInt()))
                    for (k in 0 until n) {
                        val kk = rippleScale(ripple, lived)?.invoke(lived - l + k) ?: 1f
                        dot(cv, p, cx + (k - (n - 1) / 2f) * step, y, r * kk, Color.WHITE)
                    }
                    l -= n; row++
                }
                if (age < span) {
                    // the year you're in, slipping through the neck
                    val fall = ((timeMs % 6000L) / 6000f)
                    dot(cv, p, cx, neck + step * (0.2f + 0.15f * sin(fall * 2 * PI).toFloat()), r, Color.WHITE, (age - lived).toFloat())
                }
            }
            WallStyle.STARS -> {
                var seed = (birth.year * 10000L + birth.monthValue * 100 + birth.dayOfMonth) % 2147483647L
                fun rnd(): Float { seed = (seed * 16807L) % 2147483647L; return seed / 2147483647f }
                val t = timeMs / 1000f
                for (i in 0 until span) {
                    val bx = W * (0.1f + rnd() * 0.8f); val by = H * (0.30f + rnd() * 0.42f)
                    val sz = 0.6f + rnd() * 0.8f; val ph = rnd() * 6.283f
                    val dx = sin(t / 40f + ph) * W * 0.01f; val dy = cos(t / 55f + ph) * W * 0.01f
                    val r = W * 0.011f * sz
                    val k = rippleScale(ripple, lived)?.invoke(i) ?: 1f
                    when {
                        i < lived -> {
                            val tw = 0.55f + 0.45f * (0.5f + 0.5f * sin(t / 9f + ph * 3))
                            dot(cv, p, bx + dx, by + dy, r * k, Color.argb((255 * tw).roundToInt(), 255, 255, 255))
                        }
                        i == lived -> dot(cv, p, bx + dx, by + dy, r * 1.5f * k, Color.WHITE, (age - lived).toFloat())
                        else -> dot(cv, p, bx + dx, by + dy, r, 0xFF262628.toInt())
                    }
                }
            }
        }
    }

    fun rippleScale(p: Float, lived: Int): ((Int) -> Float)? {
        if (p < 0f || p >= 1f) return null
        return { i ->
            if (i > lived) 1f else {
                val front = p * (lived + 10)
                val dd = front - i
                if (dd > 0 && dd < 7) 1f + 0.34f * sin(dd / 7f * PI).toFloat() else 1f
            }
        }
    }

    // ---------------------------------------------------------------- share card

    fun shareCard(c: Context, s: Settings): Bitmap {
        val W = 1080; val H = 1350
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(Color.BLACK)
        val m = Life.model(s.copy(zoom = Life.ZOOM_YEARS), ViewKind.LIFE)
        val hl = Life.headline(s.copy(unit = 0, lived = false, pct = false), m)
        val pad = 110f
        text(c, cv, Life.whole(hl.value), pad - 8f, 330f, 230f, Color.WHITE, R.font.interdisplay_extralight, tracking = -0.045f)
        text(c, cv, "${hl.noun} left", pad, 410f, 44f, MUTE, R.font.inter_medium)
        val gw = W - 2 * pad
        grid(cv, pad, 500f, gw, 10, s.span, m.done, Color.WHITE, s.names.keys.toSet())
        text(c, cv, "yrs.", pad, H - 90f, 34f, 0xFF48484A.toInt(), R.font.interdisplay_bold)
        return bmp
    }

    fun rect(l: Float, t: Float, r: Float, b: Float) = RectF(l, t, r, b)
}
