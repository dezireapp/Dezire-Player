package com.dezire.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.dezire.music.util.ArtworkPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Configuration for [dynamicArtworkGlow]. Tune the strength and the speed of
 * the drift/breathe so the effect can be turned down (or slowed) for lighter
 * scenes without forking the component.
 */
data class GlowConfig(
    /** Overall luminosity of the overlay. Keep well under 1 (0..0.45 used internally). */
    val intensity: Float = 1f,
    /** Duration of one full drift cycle of the light blobs (ms). */
    val driftMs: Int = 16_000,
    /** Duration of one breathe (pulse) cycle of the light blobs (ms). */
    val breatheMs: Int = 8_000,
)

/*
 * DynamicArtworkGlow
 *
 * A shared, artwork-derived "flowing light" overlay. Draws a diffuse base
 * wash plus three large radial-gradient clouds whose centres, radii and
 * alpha drift slowly and sinusoidally, so the light flows organically across
 * the surface instead of spinning in a circle. All colours come from
 * [ArtworkPalette]; when the artwork (and therefore the palette) changes the
 * colours animate smoothly behind the same continuous phase, so there is no
 * jump or flash on track change.
 *
 * Apply it as a Modifier to a Box; the paint is clipped to that Box's bounds
 * (add `.clipToBounds()` on the Box when the glow must not bleed into
 * neighbouring areas, e.g. away from the sharp album artwork band).
 *
 * It uses a single `rememberInfiniteTransition` for the whole composition and
 * only draws two/three radial gradients per frame - no blur passes - so it
 * stays smooth on mid-range devices.
 */
@Composable
fun Modifier.dynamicArtworkGlow(
    palette: ArtworkPalette?,
    config: GlowConfig = GlowConfig(),
): Modifier {
    val source = palette ?: ArtworkPalette.fallback
    val dominant by animateColorAsState(
        targetValue = source.dominant,
        animationSpec = tween(900),
        label = "glowDominant",
    )
    val accent by animateColorAsState(
        targetValue = source.accent,
        animationSpec = tween(900),
        label = "glowAccent",
    )
    val dim by animateColorAsState(
        targetValue = source.dim,
        animationSpec = tween(900),
        label = "glowDim",
    )

    // One transition for the life of this overlay; changing palette muies the
    // colours, never the phase, so a track change continues the flow.
    val transition = rememberInfiniteTransition(label = "artworkGlowFlow")
    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(config.driftMs, easing = LinearEasing),
        ),
        label = "glowPhase1",
    )
    val phase2 by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            tween(config.driftMs, easing = LinearEasing),
        ),
        label = "glowPhase2",
    )
    val arc by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            tween(config.breatheMs, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "glowArc",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(config.breatheMs, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "glowBreathe",
    )

    return this.drawBehind {
        val width = size.width
        val height = size.height
        val shortSide = min(width, height)
        val strength = (0.22f * config.intensity * breathe).coerceIn(0f, 0.45f)

        // Diffuse base wash anchoring the glow to the artwork's dominant
        // colour, fading out before the bottom.
        drawRect(
            brush = Brush.verticalGradient(
                0f to dominant.copy(alpha = strength * 0.55f),
                0.5f to Color.Transparent,
                1f to accent.copy(alpha = strength * 0.45f),
            ),
        )

        // Primary light (dominant colour), drifting on a slow circular orbit
        // with a breathing radius.
        val angle1 = phase1 * 2f * PI.toFloat()
        val cx1 = width * (0.50f + 0.42f * cos(angle1))
        val cy1 = height * (0.42f + 0.34f * sin(angle1))
        val r1 = shortSide * arc
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    dominant.copy(alpha = strength),
                    dominant.copy(alpha = 0f),
                ),
                center = Offset(cx1, cy1),
                radius = r1,
            ),
            radius = r1,
            center = Offset(cx1, cy1),
        )

        // Secondary light (accent colour), counter-phased so the two lights
        // never overlap into a single bright spot.
        val angle2 = phase2 * 2f * PI.toFloat()
        val cx2 = width * (0.46f + 0.40f * cos(angle2))
        val cy2 = height * (0.58f + 0.32f * sin(angle2))
        val r2 = shortSide * (arc * 0.85f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = strength * 0.9f),
                    accent.copy(alpha = 0f),
                ),
                center = Offset(cx2, cy2),
                radius = r2,
            ),
            radius = r2,
            center = Offset(cx2, cy2),
        )

        // Third, darker blob drifting opposite to give the "flow" body.
        val angle3 = -0.5f + phase2 * 2f * PI.toFloat()
        val cx3 = width * (0.52f + 0.38f * sin(angle3))
        val cy3 = height * (0.35f + 0.28f * cos(angle3))
        val r3 = shortSide * (arc * 1.1f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    dim.copy(alpha = strength * 0.55f),
                    dim.copy(alpha = 0f),
                ),
                center = Offset(cx3, cy3),
                radius = r3,
            ),
            radius = r3,
            center = Offset(cx3, cy3),
        )
    }
}