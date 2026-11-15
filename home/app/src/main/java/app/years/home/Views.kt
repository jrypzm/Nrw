package app.years.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The thumb rail. Put your thumb on the edge of the home screen and slide: the letters appear, the apps for that
 * letter fan out beside your thumb, and letting go on one opens it. One hand, one gesture, no drawer.
 */
class RailView(c: Context, private val onActive: (Boolean) -> Unit, private val onOpen: (App, View) -> Unit) : View(c) {
    var left = false
    private val letters = ('A'..'Z').toList() + '#'
    private var active = false
    private var letter = -1
    private var apps: List<App> = emptyList()
    private var pick = -1
    private var fx = 0f; private var fy = 0f
    private var shown = 0f
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val appPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = K.FG }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private var topY = 0f; private var botY = 0f
    var insetTop = 0; var insetBottom = 0

    init { isClickable = false; alpha = 0f }

    /** The strip of the screen edge that starts the rail. */
    fun inZone(x: Float): Boolean = if (left) x < dp(26f) else x > width - dp(26f)

    fun begin(e: MotionEvent) {
        active = true; letter = -1; pick = -1
        topY = insetTop + dp(110f); botY = height - insetBottom - dp(96f)
        letterPaint.typeface = Type.semi; appPaint.typeface = Type.display; capPaint.typeface = Type.semi
        animate().alpha(1f).setDuration(140).setInterpolator(K.EASE).start()
        onActive(true)
        move(e)
    }

    fun move(e: MotionEvent) {
        if (!active) return
        fx = e.x; fy = e.y
        val railX = if (left) dp(20f) else width - dp(20f)
        val awayFromEdge = abs(fx - railX) > dp(70f)
        if (!awayFromEdge || apps.isEmpty()) {
            // on the edge: the finger picks a letter
            val i = ((fy - topY) / (botY - topY) * letters.size).toInt().coerceIn(0, letters.size - 1)
            if (i != letter) {
                letter = i
                val l = letters[i]
                apps = Apps.visible.filter { it.letter == l }.take(9)
                tick()
            }
            pick = -1
        } else {
            // moved toward the middle: the finger picks one of the apps that fanned out
            val p = appIndexAt(fy)
            if (p != pick && p >= 0) { pick = p; tick() }
        }
        invalidate()
    }

    fun end(e: MotionEvent?) {
        if (!active) return
        active = false
        val chosen = apps.getOrNull(pick)
        animate().alpha(0f).setDuration(160).setInterpolator(K.EASE).start()
        onActive(false)
        if (chosen != null && e != null) { confirm(); onOpen(chosen, this) }
    }

    private fun rowH() = dp(48f)
    private fun listTop(): Float {
        val n = max(1, apps.size)
        val centre = topY + (letter + .5f) / letters.size * (botY - topY)
        return (centre - n * rowH() / 2).coerceIn(topY - dp(40f), botY - n * rowH())
    }
    private fun appIndexAt(y: Float): Int {
        val i = ((y - listTop()) / rowH()).toInt()
        return if (i in apps.indices) i else if (y < listTop()) 0 else apps.lastIndex
    }

    override fun onDraw(canvas: Canvas) {
        if (alpha <= 0f) return
        val railX = if (left) dp(18f) else width - dp(18f)
        val step = (botY - topY) / letters.size
        letterPaint.textSize = dp(11.5f)
        for ((i, l) in letters.withIndex()) {
            val y = topY + (i + .5f) * step
            if (i == letter && pick < 0) {
                canvas.drawCircle(railX, y, dp(13f), bubble)
                letterPaint.color = K.BG
            } else letterPaint.color = if (abs(i - letter) == 1) K.MUTE else K.FAINT
            canvas.drawText(l.toString(), railX, y + dp(4f), letterPaint)
        }
        if (letter < 0) return
        // the apps for this letter, lined up beside the thumb
        val x = if (left) dp(64f) else width - dp(64f)
        appPaint.textAlign = if (left) Paint.Align.LEFT else Paint.Align.RIGHT
        capPaint.textAlign = appPaint.textAlign
        capPaint.textSize = dp(11.5f); capPaint.color = K.DIM; capPaint.letterSpacing = .1f
        val t = listTop()
        canvas.drawText(letters[letter].toString(), x, t - dp(12f), capPaint)
        if (apps.isEmpty()) {
            appPaint.textSize = dp(20f); appPaint.color = K.FAINT
            canvas.drawText("Nothing under ${letters[letter]}", x, t + dp(30f), appPaint)
            return
        }
        for ((i, a) in apps.withIndex()) {
            val on = i == pick || (pick < 0 && i == 0 && false)
            appPaint.textSize = dp(if (on) 32f else 28f)
            appPaint.letterSpacing = -.03f
            appPaint.color = if (on) K.FG else if (pick < 0) K.MUTE else K.FAINT
            canvas.drawText(a.label, x, t + (i + .72f) * rowH(), appPaint)
        }
        // a soft light under the thumb
        glow.shader = android.graphics.RadialGradient(fx, fy, dp(46f), intArrayOf(0x33FFFFFF, 0x00FFFFFF), null, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawCircle(fx, fy, dp(46f), glow)
    }
}

/** The A–Z strip beside the app list. Slide it to jump; a big letter shows where you are. */
@SuppressLint("ViewConstructor")
class AlphaStrip(c: Context, private val onLetter: (Char) -> Unit) : View(c) {
    var letters: List<Char> = emptyList(); set(v) { field = v; invalidate() }
    private var cur = -1
    private var pressing = false
    private var py = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = K.BG }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = K.FG }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (letters.isEmpty()) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                pressing = true; py = e.y
                val i = ((e.y - paddingTop) / (height - paddingTop - paddingBottom) * letters.size).toInt().coerceIn(0, letters.size - 1)
                if (i != cur) { cur = i; tick(); onLetter(letters[i]) }
                invalidate()
            }
            else -> { pressing = false; cur = -1; invalidate() }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (letters.isEmpty()) return
        val h = (height - paddingTop - paddingBottom).toFloat()
        val step = h / letters.size
        p.typeface = Type.semi; p.textSize = min(dp(11f), step * .78f)
        for ((i, l) in letters.withIndex()) {
            p.color = if (i == cur) K.FG else K.FAINT
            canvas.drawText(l.toString(), width / 2f, paddingTop + (i + .5f) * step + p.textSize * .36f, p)
        }
        if (pressing && cur >= 0) {
            // the bubble sits to the left of the strip, beside your thumb
            val cy = py.coerceIn(paddingTop + dp(32f), height - paddingBottom - dp(32f))
            val cx = -dp(44f)
            canvas.drawCircle(cx, cy, dp(32f), dot)
            big.typeface = Type.display; big.textSize = dp(30f)
            canvas.drawText(letters[cur].toString(), cx, cy + dp(10.5f), big)
        }
    }
}

/** A small round dot, white, for "this app has something new". */
class Dot(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = K.FG }
    override fun onDraw(canvas: Canvas) { canvas.drawCircle(width / 2f, height / 2f, min(width, height) / 2f, p) }
}

/** A hairline-edged near-black card. */
class Card(c: Context) : android.widget.LinearLayout(c) {
    private val r = RectF()
    init { orientation = VERTICAL; background = rounded(K.CARD, dp(22f), dpi(1f), K.HAIR) }
}
