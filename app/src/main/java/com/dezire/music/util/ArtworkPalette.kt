package com.dezire.music.util

import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import com.dezire.music.data.model.Song
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Small palette derived from the album artwork. It drives the soft flowing
 * light behind the Now Playing lyrics / title areas and behind the Play
 * Queue, so every overlay colour is literally taken from the current cover
 * instead of being a hardcoded theme colour.
 *
 * Everything in [ArtworkPalette.Companion] is pure colour math (no Android
 * classes) so it can be unit-tested on the JVM; only the pixel sampling in
 * [extractArtworkPalette] touches Android.
 */
data class ArtworkPalette(
    val dominant: Color,
    val accent: Color,
    val dim: Color,
) {
    companion object {
        /*
         * Fallback used before/without artwork. Kept in the app's existing
         * immersive palette so the glow never flashes a jarring colour.
         */
        val FALLBACK_DOMINANT = Color(0xFF2A1060)
        val FALLBACK_ACCENT = Color(0xFFB3402F)
        val FALLBACK_DIM = Color(0xFF1A1130)

        val fallback: ArtworkPalette =
            ArtworkPalette(FALLBACK_DOMINANT, FALLBACK_ACCENT, FALLBACK_DIM)

        /*
         * Builds a complete palette from a single representative colour.
         * Used when extraction only finds one meaningful colour (or in the
         * test-suite to verify the derivation rules).
         */
        fun fromDominant(dominant: Color): ArtworkPalette = ArtworkPalette(
            dominant = dominant,
            accent = shiftedAccent(dominant),
            dim = darkened(dominant, 0.45f),
        )

        /** Euclidean RGB distance, used to pick an accent that actually differs. */
        fun distance(a: Color, b: Color): Float {
            val dr = a.red - b.red
            val dg = a.green - b.green
            val db = a.blue - b.blue
            return sqrt(dr * dr + dg * dg + db * db)
        }

        fun complementary(color: Color): Color = rotateHue(color, 180f)

        /*
         * A warm near-complement for the glow: rotated 150° from the source
         * and nudged up in saturation/lightness so it reads as a second light
         * source rather than a repeat of the first.
         */
        fun shiftedAccent(color: Color): Color {
            val shifted = rgbToHsv(rotateHue(color, 150f))
            return hsvToRgb(
                shifted[0],
                (shifted[1] * 0.9f + 0.1f).coerceIn(0f, 1f),
                (shifted[2] * 1.15f).coerceIn(0f, 1f),
            )
        }

        fun darkened(color: Color, factor: Float): Color = Color(
            red = (color.red * factor).coerceIn(0f, 1f),
            green = (color.green * factor).coerceIn(0f, 1f),
            blue = (color.blue * factor).coerceIn(0f, 1f),
            alpha = color.alpha,
        )

        fun rotateHue(color: Color, degrees: Float): Color {
            val hsv = rgbToHsv(color)
            return hsvToRgb(hsv[0] + degrees, hsv[1], hsv[2])
        }

        fun rgbToHsv(color: Color): FloatArray {
            val r = color.red
            val g = color.green
            val b = color.blue
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min
            var hue = 0f
            if (delta > 0f) {
                hue = when (max) {
                    r -> (g - b) / delta
                    g -> (b - r) / delta + 2f
                    else -> (r - g) / delta + 4f
                }
            }
            hue *= 60f
            if (hue < 0f) hue += 360f
            val saturation = if (max == 0f) 0f else delta / max
            return floatArrayOf(hue, saturation, max)
        }

        fun hsvToRgb(h: Float, s: Float, v: Float): Color {
            val hue = ((h % 360f) + 360f) % 360f
            val c = v * s
            val x = c * (1f - abs((hue / 60f) % 2f - 1f))
            val m = v - c
            val (rr, gg, bb) = when {
                hue < 60f -> Triple(c, x, 0f)
                hue < 120f -> Triple(x, c, 0f)
                hue < 180f -> Triple(0f, c, x)
                hue < 240f -> Triple(0f, x, c)
                hue < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            return Color(rr + m, gg + m, bb + m)
        }
    }
}

/*
 * Artwork palettes are derived once per song and cached exactly like the
 * decoded artwork, so a re-composition or a quick queue back-and-forth never
 * re-samples the bitmap.
 */
object PaletteCache {

    private val cache = LruCache<Long, ArtworkPalette>(40)

    fun get(key: Long): ArtworkPalette? = cache.get(key)

    fun put(key: Long, palette: ArtworkPalette) {
        cache.put(key, palette)
    }
}

/*
 * Samples the artwork with a coarse stride and quantises the pixels into
 * 64 RGB buckets. The dominant colour is the most populated bucket; the
 * accent is the most populated bucket that is clearly distant from it (so
 * the glow gets a genuine second colour), falling back to a hue-rotated
 * accent when the cover is nearly monochrome.
 */
private const val PALETTE_BUCKETS = 64

fun extractArtworkPalette(bitmap: ImageBitmap): ArtworkPalette {
    val android = bitmap.asAndroidBitmap()
    if (android.width <= 0 || android.height <= 0) return ArtworkPalette.fallback

    val stepX = (android.width / 32).coerceAtLeast(1)
    val stepY = (android.height / 32).coerceAtLeast(1)
    val count = LongArray(PALETTE_BUCKETS)
    val sumR = LongArray(PALETTE_BUCKETS)
    val sumG = LongArray(PALETTE_BUCKETS)
    val sumB = LongArray(PALETTE_BUCKETS)

    var x = 0
    while (x < android.width) {
        var y = 0
        while (y < android.height) {
            val pixel = android.getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val bucket = ((r shr 6) shl 4) or ((g shr 6) shl 2) or (b shr 6)
            count[bucket]++
            sumR[bucket] += r.toLong()
            sumG[bucket] += g.toLong()
            sumB[bucket] += b.toLong()
            y += stepY
        }
        x += stepX
    }

    var dominantBucket = 0
    var dominantCount = 0L
    for (i in count.indices) {
        if (count[i] > dominantCount) {
            dominantCount = count[i]
            dominantBucket = i
        }
    }
    val dominant = averageBucket(dominantBucket, count, sumR, sumG, sumB)
        ?: ArtworkPalette.FALLBACK_DOMINANT

    var accentBucket = -1
    var accentCount = 0L
    for (i in count.indices) {
        if (i == dominantBucket || count[i] == 0L) continue
        val candidate = averageBucket(i, count, sumR, sumG, sumB) ?: continue
        if (ArtworkPalette.distance(dominant, candidate) < 0.28f) continue
        if (count[i] > accentCount) {
            accentCount = count[i]
            accentBucket = i
        }
    }
    val accent = if (accentBucket >= 0) {
        averageBucket(accentBucket, count, sumR, sumG, sumB) ?: ArtworkPalette.shiftedAccent(dominant)
    } else {
        ArtworkPalette.shiftedAccent(dominant)
    }

    return ArtworkPalette(
        dominant = dominant,
        accent = accent,
        dim = ArtworkPalette.darkened(dominant, 0.45f),
    )
}

private fun averageBucket(
    bucket: Int,
    count: LongArray,
    sumR: LongArray,
    sumG: LongArray,
    sumB: LongArray,
): Color? {
    if (bucket !in count.indices || count[bucket] == 0L) return null
    val n = count[bucket]
    return Color(
        red = (sumR[bucket].toFloat() / n) / 255f,
        green = (sumG[bucket].toFloat() / n) / 255f,
        blue = (sumB[bucket].toFloat() / n) / 255f,
    )
}

/*
 * Fetches (and caches) the artwork palette for a song. The first call for a
 * song pays for the extraction; subsequent recompositions read the cache.
 * Returns [ArtworkPalette.fallback] behaviour via null until something is
 * available (callers treat null the same way as missing artwork).
 */
@Composable
fun rememberArtworkPalette(song: Song?): ArtworkPalette? {
    val context = LocalContext.current
    val songId = song?.id

    var palette by remember(songId) { mutableStateOf(songId?.let { PaletteCache.get(it) }) }

    LaunchedEffect(songId) {
        val id = songId ?: return@LaunchedEffect
        if (palette == null) {
            val cached = PaletteCache.get(id)
            if (cached != null) {
                palette = cached
            } else {
                val artwork = ArtworkCache.get(id)
                    ?: withContext(Dispatchers.IO) { ArtworkCache.load(context, song!!) }
                val extracted = artwork?.let {
                    withContext(Dispatchers.Default) { extractArtworkPalette(it) }
                } ?: ArtworkPalette.fallback
                PaletteCache.put(id, extracted)
                palette = extracted
            }
        }
    }

    return palette
}

/*
 * Paints the given bitmap down to a tiny sample so palette extraction can
 * run on a fraction of the pixels. Kept separate from the composable so the
 * downscale logic stays testable/introspectable.
 */
fun downscaleForPalette(source: Bitmap, targetShortSide: Int = 48): Bitmap {
    val sourceShortSide = minOf(source.width, source.height).coerceAtLeast(1)
    if (sourceShortSide <= targetShortSide) return source
    val scale = targetShortSide.toFloat() / sourceShortSide
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, width, height, true)
}