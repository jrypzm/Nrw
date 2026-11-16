package app.years.home

import android.annotation.SuppressLint
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A column that shows only the children that fit, top down, and quietly leaves the rest out. Home never overlaps. */
class FitColumn(c: Context) : ViewGroup(c) {
    var gap = c.dpi(10f)
    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        var used = 0
        for (i in 0 until childCount) {
            val ch = getChildAt(i); if (ch.visibility == GONE) continue
            ch.measure(MeasureSpec.makeMeasureSpec(width - paddingLeft - paddingRight, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            used += ch.measuredHeight + gap
        }
        val hh = if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED) used + paddingTop + paddingBottom else MeasureSpec.getSize(h)
        setMeasuredDimension(width, hh)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var y = paddingTop; val limit = b - t - paddingBottom
        var full = false; var shown = 0
        for (i in 0 until childCount) {
            val ch = getChildAt(i); if (ch.visibility == GONE) continue
            // the first that doesn't fit ends the column (so order is kept); the rest are laid out at no size, not drawn or touched
            if (full || y + ch.measuredHeight > limit) { full = true; ch.layout(0, 0, 0, 0); continue }
            ch.layout(paddingLeft, y, paddingLeft + ch.measuredWidth, y + ch.measuredHeight); y += ch.measuredHeight + gap; shown++
        }
        // a heading with nothing under it says nothing: leave it out too
        if (shown == 1 && childCount > 1) getChildAt(0).layout(0, 0, 0, 0)
    }
}

/** Someone waiting for you. Tap to open the chat, Reply to answer here, swipe it away when you've seen it. */
@SuppressLint("ViewConstructor")
class PersonCard(c: Context, val p: Live.Person, private val appName: String, private val onSeen: (Live.Person) -> Unit, private val onReply: (Live.Person) -> Unit) : LinearLayout(c) {
    init {
        orientation = VERTICAL; background = rounded(K.CARD, dp(20f), dpi(1f), K.HAIR); setPadding(dpi(18f), dpi(12f), dpi(12f), dpi(12f)); isClickable = true
        // one line: who, how many, and the reply; the next: what they said, where and when
        val head = LinearLayout(c).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val name = c.text("", 19f, Type.display, K.FG, -0.02f).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
        name.text = SpannableStringBuilder(p.name).apply { if (p.count > 1) append("  ${p.count}", ForegroundColorSpan(K.MUTE), 0) }
        head.addView(name, LayoutParams(0, -2, 1f))
        if (p.missedCall) head.addView(chip("Call back") { open() })
        else if (p.reply != null) head.addView(chip("Reply") { onReply(p) })
        addView(head)
        val line = c.text("", 14.5f, Type.body, 0xFFD1D1D6.toInt()).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
        line.text = SpannableStringBuilder(p.text).apply { append("  ·  " + listOf(appName, ago(p.time)).filter { it.isNotEmpty() }.joinToString(" · "), ForegroundColorSpan(K.DIM), 0) }
        addView(line, LayoutParams(-1, -2).apply { topMargin = dpi(2f); rightMargin = dpi(6f) })
        setOnClickListener { tap(); open() }
    }

    private fun chip(t: String, f: () -> Unit) = context.text(t, 13f, Type.medium, K.FG).apply {
        background = rounded(0, dp(17f), dpi(1f), K.HAIR2); setPadding(dpi(14f), dpi(9f), dpi(14f), dpi(9f)); setOnClickListener { it.tap(); f() }
        layoutParams = LayoutParams(-2, -2).apply { leftMargin = dpi(10f) }
    }

    private fun open() { onSeen(p); p.open?.go(context) }

    companion object Reply {
        /** Answers through the app's own reply action, exactly as the notification's reply box would. */
        fun send(c: Context, p: Live.Person, msg: String): Boolean {
            val a = p.reply ?: return false
            return runCatching {
                val i = Intent(); val b = Bundle()
                a.remoteInputs.forEach { b.putCharSequence(it.resultKey, msg) }
                RemoteInput.addResultsToIntent(a.remoteInputs, i, b)
                a.actionIntent.go(c, i)
            }.getOrDefault(false)
        }
        fun ago(t: Long): String { val m = (System.currentTimeMillis() - t) / 60000; return when { m < 1 -> "now"; m < 60 -> "$m min"; m < 1440 -> "${m / 60} h"; else -> "${m / 1440} d" } }
    }

    // swipe sideways: seen, off home (the notification stays)
    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private var x0 = 0f; private var y0 = 0f; private var sliding = false
    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { x0 = e.rawX; y0 = e.rawY; sliding = false }
            MotionEvent.ACTION_MOVE -> if (abs(e.rawX - x0) > slop * 1.5f && abs(e.rawX - x0) > abs(e.rawY - y0) * 1.5f) { sliding = true; parent.requestDisallowInterceptTouchEvent(true); return true }
        }
        return false
    }
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { x0 = e.rawX; y0 = e.rawY; sliding = false }
            MotionEvent.ACTION_MOVE -> {
                val d = e.rawX - x0
                if (!sliding && abs(d) > slop * 1.5f && abs(d) > abs(e.rawY - y0) * 1.5f) { sliding = true; parent.requestDisallowInterceptTouchEvent(true); isPressed = false }
                if (sliding) { translationX = d; alpha = 1f - min(.9f, abs(d) / width) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (sliding) {
                val d = e.rawX - x0; sliding = false
                if (e.actionMasked == MotionEvent.ACTION_UP && abs(d) > width * .3f) {
                    tap(); animate().translationX(if (d > 0) width.toFloat() else -width.toFloat()).alpha(0f).setDuration(200).withEndAction { onSeen(p) }.start()
                } else animate().translationX(0f).alpha(1f).setDuration(260).setInterpolator(K.EASE).start()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

}

/** One live thing: "Timer · 4:12 left", "Uber Eats · Arriving 7:42" with a progress line. Counts by itself while on screen. */
@SuppressLint("ViewConstructor")
class NowRow(c: Context, private val appName: String) : LinearLayout(c) {
    var item: Live.Now? = null; private set
    private val line = c.text("", 15f, Type.medium, K.MUTE).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
    private val right = c.text("", 15f, Type.medium, K.MUTE).apply { fontFeatureSettings = "tnum"; gravity = Gravity.END }
    private val bar = MediaCard.Progress(c)
    init {
        orientation = VERTICAL; isClickable = true; setPadding(0, dpi(4f), 0, dpi(4f))
        val r = LinearLayout(c).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r.addView(line, LayoutParams(0, -2, 1f)); r.addView(right, LayoutParams(-2, -2).apply { leftMargin = dpi(10f) })
        addView(r); addView(bar, LayoutParams(-1, dpi(12f)).apply { topMargin = dpi(4f) })
        setOnClickListener { tap(); item?.open?.go(context) }
    }
    fun bind(n: Live.Now) {
        item = n
        val head = n.title.ifEmpty { appName }
        line.text = SpannableStringBuilder().apply { append(head, ForegroundColorSpan(K.FG), 0); val rest = n.text.takeIf { it.isNotEmpty() && it != head }; if (rest != null) append(" · $rest") }
        tick()
    }
    private fun tick() {
        val n = item ?: return
        right.text = when {
            n.chrono && n.base > 0 -> { val d = if (n.countDown) n.base - System.currentTimeMillis() else System.currentTimeMillis() - n.base; clock(max(0, d)) + if (n.countDown) " left" else "" }
            n.max > 0 -> "${(n.progress * 100L / n.max).toInt()}%"
            else -> ""
        }
        right.visibility = if (right.text.isEmpty()) GONE else VISIBLE
        bar.visibility = if (n.max > 0 && !n.indeterminate) VISIBLE else GONE
        if (n.max > 0) bar.f = n.progress / n.max.toFloat()
    }
    private fun clock(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }
    private val ticker = object : Runnable { override fun run() { tick(); if (isAttachedToWindow && windowVisibility == VISIBLE) postDelayed(this, 1000) } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); removeCallbacks(ticker); post(ticker) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(ticker) }
    // nothing ticks while home is hidden behind an app
    override fun onWindowVisibilityChanged(v: Int) { super.onWindowVisibilityChanged(v); removeCallbacks(ticker); if (v == VISIBLE) post(ticker) }
}

/** "2 h 41 m away from your phone." with a quiet week of bars. Shown once when you come back, then it steps aside. */
class AwayView(c: Context, ms: Long, week: List<Pair<java.time.LocalDate, Int>>) : LinearLayout(c) {
    init {
        orientation = VERTICAL; setPadding(0, dpi(10f), 0, dpi(8f))
        addView(c.text(Away.words(ms), 60f, Type.thin, K.FG, -0.045f).apply { fontFeatureSettings = "tnum"; includeFontPadding = false })
        val best = week.maxOf { it.second }; val today = week.last().second
        addView(c.text("away from your phone." + if (ms >= 3_600_000L) " Nice." else "", 17f, Type.medium, K.MUTE), LayoutParams(-2, -2).apply { topMargin = dpi(8f) })
        addView(Bars(c, week.map { it.second }), LayoutParams(-1, dpi(44f)).apply { topMargin = dpi(22f) })
        val caps = FrameLayout(c)
        caps.addView(c.caption(week.first().first.dayOfWeek.name.take(3)), FrameLayout.LayoutParams(-2, -2, Gravity.START))
        caps.addView(c.caption(if (today >= best && today > 0) "Today · longest this week" else "Today").apply { setTextColor(K.FG) }, FrameLayout.LayoutParams(-2, -2, Gravity.END))
        addView(caps, LayoutParams(-1, -2).apply { topMargin = dpi(8f) })
    }
    private class Bars(c: Context, val v: List<Int>) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val n = v.size; val gap = dp(5f); val w = (width - gap * (n - 1)) / n; val top = max(1, v.max())
            for ((i, x) in v.withIndex()) {
                val h = max(dp(3f), height * x / top.toFloat())
                p.color = if (i == n - 1) K.FG else K.FAINT
                canvas.drawRoundRect(i * (w + gap), height - h, i * (w + gap) + w, height.toFloat(), dp(2f), dp(2f), p)
            }
        }
    }
}

/** A wide pill you slide: the torch brightness. */
class Slider(c: Context, private val onSet: (Float) -> Unit) : View(c) {
    var f = 1f; set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    // a screen reader can step it up and down
    init { isFocusable = true; contentDescription = "Brightness" }
    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.SeekBar::class.java.name
        info.rangeInfo = android.view.accessibility.AccessibilityNodeInfo.RangeInfo.obtain(android.view.accessibility.AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT, 0f, 100f, f * 100)
        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD); info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }
    override fun performAccessibilityAction(action: Int, args: android.os.Bundle?): Boolean = when (action) {
        android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { f = (f + .1f).coerceIn(.05f, 1f); onSet(f); true }
        android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { f = (f - .1f).coerceIn(.05f, 1f); onSet(f); true }
        else -> super.performAccessibilityAction(action, args)
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = K.GLASS }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = K.HAIR2; style = Paint.Style.STROKE; strokeWidth = c.dp(1f) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = K.FG }
    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, track)
        canvas.drawRoundRect(.5f, .5f, width - .5f, height - .5f, r, r, edge)
        canvas.drawRoundRect(0f, 0f, max(height.toFloat(), width * f), height.toFloat(), r, r, fill)
    }
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true)
        val nf = (e.x / width).coerceIn(.05f, 1f)
        // a tick for every tenth, like detents on a dial
        if ((nf * 10).toInt() != (f * 10).toInt()) tick()
        f = nf; onSet(nf)
        return true
    }
}
