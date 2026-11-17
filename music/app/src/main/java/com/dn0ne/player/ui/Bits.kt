// yrs. music: modified from Lotus by dn0ne (GPL-3.0). Small shared pieces.
package com.dn0ne.player.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

// ---------- names, never blank ----------

val Track.name: String get() = title?.takeIf { it.isNotBlank() } ?: data.substringAfterLast('/').substringBeforeLast('.')
val Track.who: String get() = artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist"
val Track.record: String get() = album?.takeIf { it.isNotBlank() } ?: "Unknown album"
fun albumTitle(name: String?) = name?.takeIf { it.isNotBlank() } ?: "Unknown album"

fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(n)
fun songs(n: Int) = if (n == 1) "1 song" else "${count(n)} songs"

fun clock(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun span(ms: Long): String {
    val m = (ms / 60000).toInt()
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

fun spanWords(ms: Long): String {
    val m = ((ms + 30000) / 60000).toInt()
    return when {
        m >= 60 -> "${m / 60} h ${m % 60} min"
        m == 1 -> "1 minute"
        else -> "$m minutes"
    }
}

val Track.lossless: Boolean get() = data.substringAfterLast('.').lowercase() in setOf("flac", "wav", "alac", "aiff", "aif", "ape", "wv")

// ---------- the logo: a big m. with yrs. above it, the full stop half full ----------

@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier) {
    val d = LocalDensity.current
    Box(modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            val u = this.size.width / 100f
            drawRoundRect(Ink.Ghost, topLeft = Offset(u, u), size = androidx.compose.ui.geometry.Size(98 * u, 98 * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24 * u), style = Stroke(2 * u))
            val c = Offset(83.5f * u, 71.5f * u); val r = 5.6f * u
            drawCircle(Color(0xFF3A3A3C), r, c)
            drawArc(Color.White, 0f, 180f, true, topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r))
        }
        with(d) {
            val u = size.toPx() / 100f
            Txt(
                "m",
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (64 * u).toSp(), letterSpacing = (-0.047).em, color = Color.White),
                modifier = Modifier.offset((14 * u).toDp(), (14 * u).toDp()),
            )
            Txt(
                "yrs.",
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = (12.5f * u).toSp(), color = Ink.Dim),
                modifier = Modifier.offset((16.5f * u).toDp(), (20.9f * u).toDp()),
            )
        }
    }
}


// ---------- covers: the art, or a calm tile with one letter when there is none ----------

@Composable
fun Cover(track: Track?, size: Dp, radius: Dp, modifier: Modifier = Modifier, letterOf: String? = null) {
    CoverBox(track?.coverArtUri, letterOf ?: track?.record, radius, modifier.size(size))
}

@Composable
fun CoverBox(model: Any?, letterOf: String?, radius: Dp, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(Ink.Tile), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val px = with(LocalDensity.current) { maxWidth.toPx() }
            val letter = letterOf?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "♪"
            Txt(
                letter,
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, color = Color(0xFF3A3A3C),
                    fontSize = with(LocalDensity.current) { (px * 0.42f).toSp() }),
                maxLines = 1, overflow = TextOverflow.Clip,
            )
            if (model != null) {
                val ctx = LocalContext.current
                val request = remember(model, px) {
                    ImageRequest.Builder(ctx).data(model).size(px.toInt().coerceAtLeast(1)).build()
                }
                AsyncImage(request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Four covers in a square, for playlists. */
@Composable
fun Mosaic(playlist: Playlist, size: Dp, radius: Dp, modifier: Modifier = Modifier) {
    val distinct = remember(playlist) { playlist.trackList.distinctBy { it.album }.take(4) }
    if (distinct.size < 4) {
        Cover(distinct.firstOrNull(), size, radius, modifier, letterOf = playlist.name)
        return
    }
    Box(modifier.size(size).clip(RoundedCornerShape(radius))) {
        val h = size / 2
        distinct.forEachIndexed { i, t ->
            Cover(t, h, 0.dp, Modifier.offset(h * (i % 2), h * (i / 2)))
        }
    }
}


// ---------- one colour from the cover ----------

private val accentCache = HashMap<String, Color>()

@Composable
fun rememberAccent(track: Track?, enabled: Boolean): State<Color> {
    val ctx = LocalContext.current
    var target by remember { mutableStateOf(Ink.Accent) }
    LaunchedEffect(track?.coverArtUri, enabled) {
        val uri = track?.coverArtUri
        target = if (!enabled || uri == null) Ink.Accent else accentCache[uri.toString()] ?: withContext(Dispatchers.IO) {
            try {
                val result = ctx.imageLoader.execute(
                    ImageRequest.Builder(ctx).data(uri).size(48).allowHardware(false).build()
                )
                (result as? SuccessResult)?.image?.toBitmap()?.let { pick(it) } ?: Ink.Accent
            } catch (_: Exception) {
                Ink.Accent
            }
        }.also { accentCache[uri.toString()] = it }
    }
    return animateColorAsState(target, tween(700), label = "accent")
}

/** Weigh every pixel by how alive it is, take the strongest hue, then make it sit well on black. */
fun pick(bitmap: Bitmap): Color {
    val w = bitmap.width; val h = bitmap.height
    val px = IntArray(w * h); bitmap.getPixels(px, 0, w, 0, 0, w, h)
    val bins = DoubleArray(24); val sums = Array(24) { DoubleArray(3) }
    val hsv = FloatArray(3)
    for (c in px) {
        android.graphics.Color.colorToHSV(c, hsv)
        if (hsv[1] < 0.18f || hsv[2] < 0.22f) continue
        val b = ((hsv[0] / 15f).toInt()).coerceIn(0, 23)
        val wgt = (hsv[1] * hsv[2]).toDouble()
        bins[b] += wgt; sums[b][0] += hsv[0] * wgt; sums[b][1] += hsv[1] * wgt; sums[b][2] += hsv[2] * wgt
    }
    val best = bins.indices.maxByOrNull { bins[it] } ?: return Ink.Accent
    if (bins[best] < px.size * 0.012) return Ink.Accent
    val hue = (sums[best][0] / bins[best]).toFloat()
    val sat = (sums[best][1] / bins[best]).toFloat().coerceIn(0.32f, 0.62f)
    val value = 0.95f
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
}

// ---------- what the file is: format, depth, rate, bitrate ----------

private val qualityCache = HashMap<String, String>()

suspend fun quality(track: Track): String = qualityCache[track.data] ?: withContext(Dispatchers.IO) {
    val ext = track.data.substringAfterLast('.').uppercase()
    val parts = mutableListOf<String>()
    try {
        val head = AudioFileIO.read(File(track.data)).audioHeader
        val enc = head.encodingType?.uppercase().orEmpty()
        parts += when {
            ext == "M4A" && "ALAC" in enc -> "ALAC"
            ext == "M4A" -> "AAC"
            else -> ext
        }
        val bits = head.bitsPerSample
        if (bits > 0 && (track.lossless || "ALAC" in enc)) parts += "$bits BIT"
        val rate = head.sampleRateAsNumber
        if (rate > 0) parts += khz(rate)
        val kbps = head.bitRateAsNumber
        if (kbps > 0) parts += "${count(kbps.toInt())} KBPS"
    } catch (_: Throwable) {
        parts.clear(); parts += ext
        track.bitrate?.toLongOrNull()?.let { if (it > 0) parts += "${count((it / 1000).toInt())} KBPS" }
    }
    parts.joinToString(" · ")
}.also { qualityCache[track.data] = it }

private fun khz(rate: Int): String {
    val k = rate / 1000.0
    return if (abs(k - k.toInt()) < 0.05) "${k.toInt()} KHZ" else "%.1f KHZ".format(Locale.ROOT, k)
}

// ---------- pinned albums and playlists ----------

class Pins(context: Context) {
    private val prefs = context.getSharedPreferences("yrs", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(prefs.getString("pins", "")!!.split('\n').filter { it.isNotEmpty() })
    val items = _items.asStateFlow()

    fun has(key: String) = key in _items.value
    fun toggle(key: String) {
        val now = _items.value.toMutableList()
        if (!now.remove(key)) { now.add(0, key); while (now.size > 6) now.removeAt(now.lastIndex) }
        _items.value = now
        prefs.edit().putString("pins", now.joinToString("\n")).apply()
    }
    fun rename(old: String, new: String) {
        val now = _items.value.map { if (it == old) new else it }
        _items.value = now
        prefs.edit().putString("pins", now.joinToString("\n")).apply()
    }

    companion object {
        fun album(name: String?) = "a:${name.orEmpty()}"
        fun list(name: String?) = "p:${name.orEmpty()}"
    }
}
