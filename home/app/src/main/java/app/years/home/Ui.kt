package app.years.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.TextView
import java.io.File

/** The look: pure black, white, and three greys. Thin numbers, quiet captions, hairline cards. */
object K {
    const val BG = Color.BLACK
    const val FG = Color.WHITE
    val MUTE = Color.rgb(0x8e, 0x8e, 0x93)
    val DIM = Color.rgb(0x63, 0x63, 0x66)
    val FAINT = Color.rgb(0x3a, 0x3a, 0x3c)
    val CARD = Color.rgb(0x0c, 0x0c, 0x0d)
    val HAIR = Color.rgb(0x1e, 0x1e, 0x20)
    val HAIR2 = Color.rgb(0x2c, 0x2c, 0x2e)
    val GLASS = Color.rgb(0x12, 0x12, 0x14)
    val SHEET = Color.rgb(0x14, 0x14, 0x16)
    val SURFACE = Color.rgb(0x1c, 0x1c, 0x1e)

    /** The motion curve everything shares: fast out, a long soft landing. */
    val EASE = PathInterpolator(0.22f, 1f, 0.36f, 1f)
}

/** Typefaces, loaded once. "Your own font" replaces all of them with one file you chose. */
object Type {
    lateinit var thin: Typeface; lateinit var display: Typeface; lateinit var body: Typeface; lateinit var medium: Typeface; lateinit var semi: Typeface
    fun load(c: Context) {
        val custom = Prefs.get(c).fontPath?.let { p -> runCatching { if (File(p).exists()) Typeface.createFromFile(p) else null }.getOrNull() }
        thin = custom?.let { Typeface.create(it, 200, false) } ?: c.resources.getFont(R.font.interdisplay_extralight)
        display = custom?.let { Typeface.create(it, 600, false) } ?: c.resources.getFont(R.font.interdisplay_semibold)
        body = custom ?: c.resources.getFont(R.font.inter_regular)
        medium = custom?.let { Typeface.create(it, 500, false) } ?: c.resources.getFont(R.font.inter_medium)
        semi = custom?.let { Typeface.create(it, 600, false) } ?: c.resources.getFont(R.font.inter_semibold)
    }
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Float): Int = (v * resources.displayMetrics.density + .5f).toInt()
fun View.dp(v: Float): Float = context.dp(v)
fun View.dpi(v: Float): Int = context.dpi(v)

fun View.tick() { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
fun View.tap() { performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
fun View.confirm() { performHapticFeedback(HapticFeedbackConstants.CONFIRM) }

/** A text view in the house style. */
fun Context.text(s: CharSequence = "", size: Float = 16f, face: Typeface = Type.body, color: Int = K.FG, track: Float = -0.01f): TextView =
    TextView(this).apply {
        text = s; typeface = face; setTextColor(color); setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        letterSpacing = track; includeFontPadding = false; isSingleLine = false
    }

/** The small uppercase caption above a section. */
fun Context.caption(s: String): TextView = text(s.uppercase(), 11.5f, Type.semi, K.DIM, 0.1f)

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(color); cornerRadius = radius; if (stroke > 0) setStroke(stroke, strokeColor)
}
