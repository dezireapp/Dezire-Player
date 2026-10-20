package com.dezire.music.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/*
 * Cinematic onboarding palette: deep black canvas with electric-blue and
 * indigo ambient light. Kept local to the onboarding package so the rest
 * of the app keeps its branded red accent.
 */
val OnboardingBlack = Color(0xFF04060F)
val OnboardingElectricBlue = Color(0xFF3D7BFF)
val OnboardingIndigo = Color(0xFF5A46FF)
val OnboardingWhite = Color(0xFFF5F7FF)
val OnboardingMuted = Color(0xFF9AA3C0)

/*
 * Full-screen ambient backdrop. Two soft radial glows (indigo top-left,
 * electric-blue bottom-right) sit on a deep black canvas and fill the
 * whole window, including behind the status and navigation bars.
 */
@Composable
fun OnboardingBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingBlack)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val indigoCenter = Offset(size.width * 0.12f, size.height * 0.04f)
            val indigoRadius = size.minDimension * 1.0f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        OnboardingIndigo.copy(alpha = 0.38f),
                        OnboardingIndigo.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = indigoCenter,
                    radius = indigoRadius
                ),
                radius = indigoRadius,
                center = indigoCenter
            )

            val blueCenter = Offset(size.width * 0.94f, size.height * 0.88f)
            val blueRadius = size.minDimension * 0.95f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        OnboardingElectricBlue.copy(alpha = 0.30f),
                        OnboardingElectricBlue.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = blueCenter,
                    radius = blueRadius
                ),
                radius = blueRadius,
                center = blueCenter
            )
        }
        content()
    }
}

/*
 * Neutral full-black placeholder shown for the brief moment the persisted
 * onboarding flag is still loading, so the app never flashes the wrong
 * start destination.
 */
@Composable
fun OnboardingSplash(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingBlack),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Dezire",
            color = OnboardingWhite,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )
    }
}

/*
 * The small uppercased blue eyebrow shown above each headline.
 */
@Composable
fun OnboardingEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = OnboardingElectricBlue,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 3.sp,
        modifier = modifier
    )
}

/*
 * Circular, white, high-contrast primary action used on the welcome
 * screen. 64dp keeps it well above the 48dp minimum touch target.
 */
@Composable
fun CircularNextButton(
    onClick: () -> Unit,
    contentDescription: String = "Next",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(OnboardingWhite)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = contentDescription,
            tint = OnboardingBlack,
            modifier = Modifier.size(28.dp)
        )
    }
}

/*
 * Two-step page indicator: the active dot stretches into a pill.
 */
@Composable
fun OnboardingPageIndicator(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) 24.dp else 8.dp,
                animationSpec = tween(300),
                label = "indicatorWidth"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (active) OnboardingWhite else OnboardingWhite.copy(alpha = 0.28f)
                    )
            )
        }
    }
}

/*
 * Abstract "vinyl record" artwork for the welcome screen: ambient glow,
 * concentric grooves and a glowing blue centre. Drawn with Compose so no
 * extra image asset is needed.
 */
@Composable
fun MusicArtwork(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = tween(900),
        label = "artworkScale"
    )

    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f * progress

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    OnboardingElectricBlue.copy(alpha = 0.40f),
                    OnboardingIndigo.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = center,
                radius = maxRadius
            ),
            radius = maxRadius,
            center = center
        )

        val ringCount = 7
        for (index in 0 until ringCount) {
            val fraction = 0.32f + 0.62f * index / (ringCount - 1f)
            drawCircle(
                color = OnboardingWhite.copy(alpha = 0.05f + 0.03f * index),
                radius = maxRadius * fraction,
                center = center,
                style = Stroke(width = 1.4.dp.toPx())
            )
        }

        drawCircle(
            color = Color(0xFF0A1024),
            radius = maxRadius * 0.30f,
            center = center
        )
        drawCircle(
            color = OnboardingElectricBlue.copy(alpha = 0.85f),
            radius = maxRadius * 0.115f,
            center = center
        )
        drawCircle(
            color = OnboardingBlack,
            radius = maxRadius * 0.045f,
            center = center
        )
    }
}

/*
 * Elegant audio waveform used on the music access screen. Bar heights
 * follow a smooth bell curve so the graphic reads as a balanced wave.
 */
@Composable
fun WaveformArtwork(
    modifier: Modifier = Modifier,
    barCount: Int = 25
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val reveal by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(700),
        label = "waveformReveal"
    )

    Canvas(modifier) {
        val barWidth = size.width / (barCount * 1.7f)
        val gap = (size.width - barWidth * barCount) / (barCount - 1).coerceAtLeast(1)
        val centerY = size.height / 2f

        for (index in 0 until barCount) {
            val t = index / (barCount - 1f)
            val envelope = 0.18f + 0.82f * sin(t * PI).toFloat()
            val ripple = 0.72f + 0.28f * abs(sin(t * PI * 3.2)).toFloat()
            val barHeight = size.height * envelope * ripple * reveal
            val x = index * (barWidth + gap)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(OnboardingElectricBlue, OnboardingIndigo)
                ),
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
