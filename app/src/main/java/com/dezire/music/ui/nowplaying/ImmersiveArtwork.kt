package com.dezire.music.ui.nowplaying

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.dezire.music.util.rememberArtworkTint

// Figma reference (390x844): band top = 200dp (0.237), band height = 350dp (0.415).
// The sharp band's top edge is exposed so sibling layout (the lyric area) can
// reserve its space above the artwork and never draw over it.
internal const val SharpBandTopFraction = 0.237f
private const val BandHeightFraction = 0.415f

/*
 * Bottom edge of the sharp artwork band (as a fraction of screen height).
 * The Now Playing glow bands draw strictly above and below this window so
 * the flowing light never overlaps the crisp artwork.
 */
internal const val ArtworkBandBottomFraction = SharpBandTopFraction + BandHeightFraction

private val PlaceholderTint = Color(0xFF3A1A78)
private val FallbackTop = Color(0xFF2A1060)
private val FallbackMid = Color(0xFF6B2A7A)
private val FallbackBottom = Color(0xFFB3402F)

/**
 * Immersive artwork, adapted to the app's real (MediaStore) artwork source.
 *
 *  1. A blurred backdrop of the same image fills the screen. On Android 12+
 *     the full-resolution [painter] is blurred with a GPU RenderEffect; below
 *     that (where [blur] is a no-op) the pre-blurred [blurred] bitmap is
 *     upscaled with bilinear filtering instead. Either way the layer is scaled
 *     1.4x so no blur edge ever shows.
 *  2. An adaptive tint derived from the artwork (animated over 400 ms) plus a
 *     flat dim sits over the backdrop so it stays immersive but subdued.
 *  3. A sharp, full-width band whose top and bottom edges feather into the
 *     backdrop, positioned as fractions of the screen height.
 *
 * When there is no artwork at all, a gradient fallback is shown instead.
 */
@Composable
fun ImmersiveArtwork(
    painter: Painter?,
    blurred: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val artworkTint = rememberArtworkTint(blurred)
    val tint by animateColorAsState(
        targetValue = artworkTint ?: PlaceholderTint,
        animationSpec = tween(400),
        label = "artworkTint",
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        // 1. Full-screen, enlarged blurred backdrop.
        val useGpuBlur = painter != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        when {
            useGpuBlur -> Image(
                painter = painter!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.4f
                        scaleY = 1.4f
                    }
                    .blur(radius = 48.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
            )

            blurred != null -> Image(
                bitmap = blurred,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.4f
                        scaleY = 1.4f
                    },
            )

            painter != null -> Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.4f
                        scaleY = 1.4f
                    }
                    .blur(radius = 48.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
            )

            else -> Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(FallbackTop, FallbackMid, FallbackBottom)),
                    ),
            )
        }

        // 2. Adaptive tint + flat dim.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            tint.copy(alpha = 0.45f),
                            tint.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.60f),
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.40f)),
        )

        // 3. Sharp centered band with feathered top and bottom edges.
        if (painter != null) {
            Box(
                Modifier
                    .padding(top = maxHeight * SharpBandTopFraction)
                    .fillMaxWidth()
                    .height(maxHeight * BandHeightFraction)
                    .featherVertically(),
            ) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Fades the top and bottom edges of this layer to transparent. */
private fun Modifier.featherVertically(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.14f to Color.Black,
                0.78f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
