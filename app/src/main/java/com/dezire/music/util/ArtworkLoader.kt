package com.dezire.music.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.collection.LruCache
import com.dezire.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * In-memory cache of decoded album art, keyed by song id.
 */
object ArtworkCache {

    private val cache = LruCache<Long, ImageBitmap>(40)

    fun get(key: Long): ImageBitmap? = cache.get(key)

    suspend fun load(context: Context, song: Song): ImageBitmap? {
        cache.get(song.id)?.let { return it }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, song.uri)
            val picture = retriever.embeddedPicture
            if (picture == null) {
                null
            } else {
                val decoded = BitmapFactory.decodeByteArray(picture, 0, picture.size)
                    ?.asImageBitmap()
                decoded?.let { cache.put(song.id, it) }
                decoded
            }
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}

/*
 * Loads (and caches) the artwork for the given song off the main thread.
 */
@Composable
fun rememberSongArtwork(song: Song?): ImageBitmap? {
    val context = LocalContext.current
    val songId = song?.id

    var artwork by remember(songId) {
        mutableStateOf(songId?.let { ArtworkCache.get(it) })
    }

    LaunchedEffect(songId) {
        if (song != null && artwork == null) {
            artwork = withContext(Dispatchers.IO) {
                ArtworkCache.load(context, song)
            }
        }
    }

    return artwork
}

/*
 * Derives a blurred approximation of the artwork that is visible on every
 * supported API level. Modifier.blur relies on a GPU RenderEffect and is a
 * no-op before Android 12 (API 31), so instead the bitmap is downscaled and
 * kept as an [ImageBitmap]; drawing it full-bleed with bilinear filtering
 * upscales it back into a smooth, colourful blur without any RenderEffect.
 *
 * The downscale is done in two steps - a strong reduction to capture the blur,
 * then a partial upscale - so the result stays soft instead of showing the
 * large blocky facets a single aggressive downscale would produce.
 */
@Composable
fun rememberBlurredArtwork(artwork: ImageBitmap?): ImageBitmap? {
    var blurred by remember(artwork) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(artwork) {
        blurred = artwork?.let {
            withContext(Dispatchers.Default) { downscaleForBlur(it) }
        }
    }

    return blurred
}

/*
 * A much stronger version of [rememberBlurredArtwork] for full-screen, deeply
 * blurred backdrops (the Play Queue). It reduces the artwork to a tiny sample
 * before upscaling, so the result reads as a soft, cinematic field of colour
 * without any readable detail or hard edges - visible on every API level,
 * including before Android 12 where [Modifier.blur] is a no-op.
 *
 * Like the mild version, the expensive work runs once per artwork on a
 * background dispatcher and is cached for the life of that artwork instance,
 * so scrolling and drag animation never trigger reinterpretation.
 */
@Composable
fun rememberDeepBlurredArtwork(artwork: ImageBitmap?): ImageBitmap? {
    var blurred by remember(artwork) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(artwork) {
        blurred = artwork?.let {
            withContext(Dispatchers.Default) {
                downscaleForBlur(
                    it,
                    sampleShortSide = DEEP_BLUR_SAMPLE_SHORT_SIDE,
                    outputShortSide = DEEP_BLUR_OUTPUT_SHORT_SIDE,
                )
            }
        }
    }

    return blurred
}

private const val BLUR_SAMPLE_SHORT_SIDE = 72
private const val BLUR_OUTPUT_SHORT_SIDE = 256

/* Aggressive reduction for the immersive queue backdrop. */
private const val DEEP_BLUR_SAMPLE_SHORT_SIDE = 20
private const val DEEP_BLUR_OUTPUT_SHORT_SIDE = 64

private fun downscaleForBlur(
    image: ImageBitmap,
    sampleShortSide: Int = BLUR_SAMPLE_SHORT_SIDE,
    outputShortSide: Int = BLUR_OUTPUT_SHORT_SIDE,
): ImageBitmap = try {
    val source = image.asAndroidBitmap()
    if (source.width <= 0 || source.height <= 0 ||
        minOf(source.width, source.height) <= outputShortSide
    ) {
        image
    } else {
        val sampled = scaleShortSide(source, sampleShortSide)
        scaleShortSide(sampled, outputShortSide).asImageBitmap()
    }
} catch (_: Throwable) {
    image
}

private fun scaleShortSide(source: Bitmap, targetShortSide: Int): Bitmap {
    val sourceShortSide = minOf(source.width, source.height).coerceAtLeast(1)
    val scale = targetShortSide.toFloat() / sourceShortSide
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, width, height, true)
}

/*
 * Derives a representative tint from the given artwork by averaging a small
 * grid of sampled pixels. Used to build a warm, artwork-derived backdrop.
 * Returns null when there is no artwork (or it cannot be read), so callers
 * can fall back to the theme colours.
 */
@Composable
fun rememberArtworkTint(artwork: ImageBitmap?): Color? {
    var tint by remember(artwork) { mutableStateOf<Color?>(null) }

    LaunchedEffect(artwork) {
        tint = artwork?.let {
            withContext(Dispatchers.Default) { averageColor(it) }
        }
    }

    return tint
}

private fun averageColor(image: ImageBitmap): Color? = try {
    val bitmap = image.asAndroidBitmap()
    if (bitmap.width <= 0 || bitmap.height <= 0) {
        null
    } else {
        val stepX = (bitmap.width / 24).coerceAtLeast(1)
        val stepY = (bitmap.height / 24).coerceAtLeast(1)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0

        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                red += (pixel shr 16) and 0xFF
                green += (pixel shr 8) and 0xFF
                blue += pixel and 0xFF
                count++
                y += stepY
            }
            x += stepX
        }

        if (count == 0) {
            null
        } else {
            Color(
                red = (red.toFloat() / count) / 255f,
                green = (green.toFloat() / count) / 255f,
                blue = (blue.toFloat() / count) / 255f
            )
        }
    }
} catch (_: Throwable) {
    null
}