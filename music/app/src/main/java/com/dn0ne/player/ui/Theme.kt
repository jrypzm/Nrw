// yrs. music: modified from Lotus by dn0ne (GPL-3.0). The look: black, white and one colour.
package com.dn0ne.player.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dn0ne.player.R

object Ink {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Mute = Color(0xFF8E8E93)
    val Dim = Color(0xFF636366)
    val Faint = Color(0xFF48484A)
    val Ghost = Color(0xFF2C2C2E)
    val Rail = Color(0xFF262628)
    val Hair = Color(0xFF161618)
    val Tile = Color(0xFF111113)
    val Edge = Color(0xFF1F1F22)
    val Accent = Color(0xFFF2A27A)
}

/** The one colour of the moment: borrowed from the cover when allowed. */
val LocalAccent = compositionLocalOf { Ink.Accent }

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
val Display = FontFamily(
    Font(R.font.interdisplay_semibold, FontWeight.SemiBold),
    Font(R.font.interdisplay_bold, FontWeight.Bold),
)

private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

object Type {
    val brand = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.035).em, color = Ink.White)
    val word = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.032).em)
    val page = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.03).em, color = Ink.White)
    val big = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.028).em, color = Ink.White)
    val meta = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.12.em, color = Ink.Dim, fontFeatureSettings = "tnum")
    val caps = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, letterSpacing = 0.14.em, color = Ink.Dim, fontFeatureSettings = "tnum")
    val act = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = (-0.01).em, color = Ink.White)
    val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = (-0.013).em, color = Ink.White, lineHeightStyle = tight)
    val sub = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp, color = Ink.Mute, lineHeightStyle = tight)
    val artist = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, letterSpacing = (-0.01).em, color = Ink.Mute)
    val time = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, color = Ink.Dim, fontFeatureSettings = "tnum")
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = Ink.Mute)
    val setting = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 17.sp, letterSpacing = (-0.012).em, color = Ink.White)
    val small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp, color = Ink.Dim)
    val lyric = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em)
}

/** Small, honest taps of the motor: a tick for moves, a click for choices, a thud for big ones. */
class Haptics(private val view: View) {
    private fun go(id: Int) { view.performHapticFeedback(id) }
    fun tick() = go(HapticFeedbackConstants.CLOCK_TICK)
    fun click() = go(HapticFeedbackConstants.VIRTUAL_KEY)
    fun hold() = go(HapticFeedbackConstants.LONG_PRESS)
    fun confirm() = go(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    fun edge() = go(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.CLOCK_TICK)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/**
 * The only touch feedback in the app: the thing you press softens and, for icons,
 * gives a little. No ripples, no grey boxes.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.press(
    shrink: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val k by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = if (pressed) 3000f else 500f),
        label = "press",
    )
    this
        .graphicsLayer {
            alpha = 1f - 0.42f * k
            if (shrink) { val s = 1f - 0.1f * k; scaleX = s; scaleY = s }
        }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/** Plain text: words first, then how they look. */
@Composable
fun Txt(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    color: androidx.compose.ui.graphics.ColorProducer? = null,
) = androidx.compose.foundation.text.BasicText(
    text = text, modifier = modifier, style = style, maxLines = maxLines, overflow = overflow, color = color,
)
