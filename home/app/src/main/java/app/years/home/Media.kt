package app.years.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Now playing, on the home screen. Album art, the song and artist, a live hairline of progress, and three controls
 * drawn by hand. Tap it to open the player, swipe it sideways to skip. Pausing keeps it here so you can play again.
 */
@SuppressLint("ViewConstructor")
class MediaCard(c: Context, private val openApp: (MediaController) -> Unit) : LinearLayout(c) {
    private var m: MediaController? = null
    private val art = ImageView(c)
    private val source = c.caption("")
    private val title = c.text("", 20f, Type.display, K.FG, -0.02f).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
    private val artist = c.text("", 15f, Type.body, K.MUTE, -0.005f).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
    private val bar = Progress(c)
    private val elapsed = c.text("", 11.5f, Type.medium, K.DIM, 0.02f).apply { fontFeatureSettings = "tnum" }
    private val remaining = c.text("", 11.5f, Type.medium, K.DIM, 0.02f).apply { fontFeatureSettings = "tnum"; gravity = Gravity.END }
    private val prev = Glyph(c, Glyph.PREV)
    private val play = Glyph(c, Glyph.PLAY)
    private val next = Glyph(c, Glyph.NEXT)
    private var artFor: String? = null
    private var holdPos = 0L; private var holdUntil = 0L
    private val mini = Glyph(c, Glyph.PLAY)
    private val times = FrameLayout(c)
    private val ctl = LinearLayout(c)
    /** When home is full of apps, the card folds to one line: art, song, play. Swipe still skips. */
    var compact = false; set(v) {
        if (v == field) return; field = v
        val full = if (v) GONE else VISIBLE
        // folded, the card keeps its timeline: only the times and the big buttons step aside
        times.visibility = full; ctl.visibility = full; mini.visibility = if (v) VISIBLE else GONE
        (bar.layoutParams as LayoutParams).let { it.topMargin = dpi(if (v) 4f else 8f); it.height = dpi(if (v) 22f else 26f); bar.layoutParams = it }
        setPadding(dpi(if (v) 12f else 16f), dpi(if (v) 12f else 16f), dpi(if (v) 12f else 18f), dpi(12f))
    }
    private var playingNow = false
    private val labels = HashMap<String, String>()

    init {
        orientation = VERTICAL
        background = rounded(K.CARD, dp(26f), dpi(1f), K.HAIR)
        setPadding(dpi(16f), dpi(16f), dpi(18f), dpi(12f))
        isClickable = true

        val top = LinearLayout(c).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.background = rounded(K.GLASS, dp(14f))
        art.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(14f)) } }
        art.clipToOutline = true
        top.addView(art, LayoutParams(dpi(58f), dpi(58f)))
        val words = LinearLayout(c).apply { orientation = VERTICAL }
        words.addView(source); words.addView(title, LayoutParams(-1, -2).apply { topMargin = dpi(4f) }); words.addView(artist, LayoutParams(-1, -2).apply { topMargin = dpi(1f) })
        top.addView(words, LayoutParams(0, -2, 1f).apply { leftMargin = dpi(14f) })
        top.addView(mini, LayoutParams(dpi(44f), dpi(44f)).apply { leftMargin = dpi(10f) })
        addView(top, LayoutParams(-1, -2))

        addView(bar, LayoutParams(-1, dpi(26f)).apply { topMargin = dpi(8f) })
        bar.contentDescription = "Song position"
        bar.onSeek = { f, done ->
            val mc = m; val dur = mc?.let { Live.metaOf(it)?.getLong(MediaMetadata.METADATA_KEY_DURATION) } ?: 0L
            if (mc != null && dur > 0) {
                val to = (f * dur).toLong()
                elapsed.text = time(to); remaining.text = "−" + time(dur - to)
                // jump when the finger lifts, and hold the line there until the player catches up
                if (done) { holdPos = to; holdUntil = SystemClock.elapsedRealtime() + 1500; runCatching { mc.transportControls.seekTo(to) } }
            }
        }
        times.addView(elapsed, FrameLayout.LayoutParams(-2, -2, Gravity.START))
        times.addView(remaining, FrameLayout.LayoutParams(-2, -2, Gravity.END))
        addView(times, LayoutParams(-1, -2))

        ctl.orientation = HORIZONTAL; ctl.gravity = Gravity.CENTER
        ctl.addView(prev, LayoutParams(dpi(56f), dpi(48f)))
        ctl.addView(play, LayoutParams(dpi(56f), dpi(56f)).apply { leftMargin = dpi(22f); rightMargin = dpi(22f) })
        ctl.addView(next, LayoutParams(dpi(56f), dpi(48f)))
        addView(ctl, LayoutParams(-1, -2).apply { topMargin = dpi(2f) })

        prev.setOnClickListener { it.tap(); m?.transportControls?.skipToPrevious() }
        next.setOnClickListener { it.tap(); m?.transportControls?.skipToNext() }
        val toggle = View.OnClickListener {
            it.confirm()
            val tc = m?.transportControls ?: return@OnClickListener
            // answer the tap at once; Android's own update follows a moment later
            if (playingNow) tc.pause() else tc.play()
            playingNow = !playingNow; play.kind = if (playingNow) Glyph.PAUSE else Glyph.PLAY; mini.kind = play.kind
        }
        play.setOnClickListener(toggle); mini.setOnClickListener(toggle)
        mini.visibility = GONE
        setOnClickListener { m?.let(openApp) }
    }

    /** Show this session. Cheap to call often: only what changed is touched. */
    fun bind(mc: MediaController) {
        m = mc
        val md = Live.metaOf(mc)
        val t = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val a = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        if (title.text.toString() != t) title.text = t
        if (artist.text.toString() != a) artist.text = a
        artist.visibility = if (a.isEmpty()) GONE else VISIBLE
        val app = labels.getOrPut(mc.packageName) { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(mc.packageName, 0)).toString() }.getOrDefault("") }
        playingNow = Live.isPlaying(mc)
        source.text = (if (playingNow) "Now playing" else "Paused").let { if (app.isNotEmpty()) "$it · $app" else it }.uppercase()
        play.kind = if (playingNow) Glyph.PAUSE else Glyph.PLAY; mini.kind = play.kind

        val bmp: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        // the player hands over a new picture object every time; compare what it shows, not the object
        val key = "${mc.packageName}|$t|$a|${bmp?.width}x${bmp?.height}|${bmp?.generationId}"
        if (key != artFor) {
            artFor = key
            if (bmp != null) { art.setImageBitmap(bmp); art.setPadding(0, 0, 0, 0) }
            else { art.setImageDrawable(NoteDrawable(context)); val p = dpi(17f); art.setPadding(p, p, p, p) }
        }
        tickProgress()
    }

    private fun tickProgress() {
        val mc = m ?: return
        val dur = Live.metaOf(mc)?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val st = Live.stateOf(mc)
        var pos = st?.position ?: 0L
        if (st != null && st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0)
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        if (dur <= 0 || pos < 0) {
            // a live stream or a player that doesn't say how long: an empty line, nothing to drag
            bar.seekable = false; bar.f = 0f; elapsed.text = ""; remaining.text = ""; return
        }
        bar.seekable = st != null && st.actions and PlaybackState.ACTION_SEEK_TO != 0L
        // your finger is on the line, or you've just let go: don't pull it back
        if (bar.dragging) return
        if (SystemClock.elapsedRealtime() < holdUntil) { if (abs(pos - holdPos) < 2500) holdUntil = 0 else pos = holdPos }
        pos = min(pos, dur)
        bar.f = pos.toFloat() / dur
        elapsed.text = time(pos); remaining.text = "−" + time(dur - pos)
    }

    private fun time(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

    private val ticker = object : Runnable { override fun run() { tickProgress(); if (isAttachedToWindow) postDelayed(this, 500) } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); removeCallbacks(ticker); post(ticker) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(ticker) }
    override fun onWindowVisibilityChanged(v: Int) { super.onWindowVisibilityChanged(v); removeCallbacks(ticker); if (v == VISIBLE) post(ticker) }

    // ---- swipe sideways to skip
    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private var dx0 = 0f; private var dy0 = 0f; private var sliding = false
    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dx0 = e.rawX; dy0 = e.rawY; sliding = false }
            MotionEvent.ACTION_MOVE -> if (abs(e.rawX - dx0) > slop * 1.5f && abs(e.rawX - dx0) > abs(e.rawY - dy0) * 1.5f) { sliding = true; parent.requestDisallowInterceptTouchEvent(true); return true }
        }
        return false
    }
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dx0 = e.rawX; dy0 = e.rawY; sliding = false }
            MotionEvent.ACTION_MOVE -> {
                val d = e.rawX - dx0
                if (!sliding && abs(d) > slop * 1.5f && abs(d) > abs(e.rawY - dy0) * 1.5f) { sliding = true; parent.requestDisallowInterceptTouchEvent(true); isPressed = false }
                if (sliding) { translationX = d * .45f; alpha = 1f - min(.5f, abs(d) / width) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (sliding) {
                val d = e.rawX - dx0
                if (e.actionMasked == MotionEvent.ACTION_UP && abs(d) > dp(80f)) {
                    confirm(); if (d < 0) m?.transportControls?.skipToNext() else m?.transportControls?.skipToPrevious()
                }
                animate().translationX(0f).alpha(1f).setDuration(320).setInterpolator(K.EASE).start()
                sliding = false; return true
            }
        }
        return super.onTouchEvent(e)
    }

    /**
     * The timeline: a line that fills as the song plays. Where the player allows it, it has a small handle you can
     * drag to move through the song; the line follows your finger and the song jumps when you let go.
     */
    class Progress(c: Context) : View(c) {
        var f = 0f; set(v) { val n = v.coerceIn(0f, 1f); if (abs(n - field) > .0005f) { field = n; invalidate() } }
        var seekable = false; set(v) { if (v != field) { field = v; invalidate() } }
        var dragging = false; private set
        /** Called while dragging (so the time can follow) and once, with done = true, when you let go. */
        var onSeek: ((Float, Boolean) -> Unit)? = null
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = K.HAIR2; strokeCap = Paint.Cap.ROUND }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = K.FG; strokeCap = Paint.Cap.ROUND }
        private fun inset() = if (seekable) dp(7f) else dp(1.5f)
        override fun onDraw(canvas: Canvas) {
            val y = height / 2f; val w = dp(if (dragging) 4.5f else 3f); track.strokeWidth = w; fill.strokeWidth = w
            val l = inset(); val r = width - inset(); val x = l + (r - l) * f
            canvas.drawLine(l, y, r, y, track)
            canvas.drawLine(l, y, x, y, fill)
            if (seekable) canvas.drawCircle(x, y, dp(if (dragging) 7f else 4.5f), fill)
        }
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (!seekable || width == 0) return false
            fun at() = ((e.x - inset()) / (width - 2 * inset())).coerceIn(0f, 1f)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dragging = true; parent.requestDisallowInterceptTouchEvent(true); tick(); f = at(); invalidate(); onSeek?.invoke(f, false) }
                MotionEvent.ACTION_MOVE -> if (dragging) { f = at(); onSeek?.invoke(f, false) }
                MotionEvent.ACTION_UP -> if (dragging) { f = at(); dragging = false; invalidate(); confirm(); onSeek?.invoke(f, true) }
                MotionEvent.ACTION_CANCEL -> { dragging = false; invalidate() }
            }
            return true
        }
    }
}

/** Play, pause, previous and next, drawn as shapes so they look the same on every phone. */
class Glyph(c: Context, k: Int) : View(c) {
    companion object { const val PLAY = 0; const val PAUSE = 1; const val PREV = 2; const val NEXT = 3 }
    var kind = k; set(v) { if (v != field) { field = v; invalidate() } }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { pathEffect = android.graphics.CornerPathEffect(c.dp(2f)) }
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = K.FG }
    private val path = Path()
    private val r = RectF()
    init { isClickable = true; isFocusable = true; contentDescription = when (k) { PREV -> "Previous"; NEXT -> "Next"; else -> "Play or pause" } }

    override fun setPressed(p: Boolean) { super.setPressed(p); animate().scaleX(if (p) .9f else 1f).scaleY(if (p) .9f else 1f).setDuration(120).start() }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val main = kind == PLAY || kind == PAUSE
        if (main) { canvas.drawCircle(cx, cy, min(width, height) / 2f, disc); ink.color = K.BG } else ink.color = K.FG
        path.reset()
        val s = dp(if (main) 9f else 8.5f)
        when (kind) {
            PLAY -> { path.moveTo(cx - s * .62f, cy - s); path.lineTo(cx + s * .98f, cy); path.lineTo(cx - s * .62f, cy + s); path.close(); canvas.drawPath(path, ink) }
            PAUSE -> { val w = s * .5f; val g = s * .38f
                r.set(cx - g - w, cy - s * .92f, cx - g, cy + s * .92f); canvas.drawRoundRect(r, dp(1.6f), dp(1.6f), ink)
                r.set(cx + g, cy - s * .92f, cx + g + w, cy + s * .92f); canvas.drawRoundRect(r, dp(1.6f), dp(1.6f), ink) }
            PREV, NEXT -> {
                val d = if (kind == NEXT) 1f else -1f
                // two triangles and a bar, like a tape deck
                for (o in floatArrayOf(-s * .95f, 0f)) {
                    val x0 = cx + d * o
                    path.moveTo(x0, cy - s * .8f); path.lineTo(x0 + d * s * .95f, cy); path.lineTo(x0, cy + s * .8f); path.close()
                }
                canvas.drawPath(path, ink)
                val bx = cx + d * s * 1.05f; r.set(min(bx, bx + d * dp(2.4f)), cy - s * .8f, max(bx, bx + d * dp(2.4f)), cy + s * .8f)
                canvas.drawRoundRect(r, dp(1.2f), dp(1.2f), ink)
            }
        }
    }
}

/** A quiet music note for songs without artwork. */
private class NoteDrawable(c: Context) : android.graphics.drawable.Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = K.DIM; style = Paint.Style.STROKE; strokeWidth = c.dp(2.2f); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = K.DIM }
    override fun draw(canvas: Canvas) {
        val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
        val x1 = b.left + w * .36f; val x2 = b.left + w * .86f; val top = b.top + h * .1f
        canvas.drawLine(x1, b.top + h * .8f, x1, top + h * .08f, p); canvas.drawLine(x2, b.top + h * .7f, x2, top, p)
        canvas.drawLine(x1, top + h * .08f, x2, top, p)
        canvas.drawCircle(x1 - w * .13f, b.top + h * .8f, w * .15f, f); canvas.drawCircle(x2 - w * .13f, b.top + h * .7f, w * .15f, f)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
