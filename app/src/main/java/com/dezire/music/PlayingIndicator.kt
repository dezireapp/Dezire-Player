package com.dezire.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dezire.music.ui.theme.DezireAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PlayingIndicatorBarCount = 3

/*
 * Small equalizer-style playback indicator: three vertical bars that
 * animate their heights continuously while playback is active and freeze
 * in place when playback is paused (or the screen leaves the foreground).
 *
 * This composable has no reference to any global player. Pass the current
 * play state in via [isPlaying].
 *
 * @param barWidth width of each bar.
 * @param barHeight maximum height of a bar; bars animate between
 *   [minBarHeightFraction] and the full height of this value.
 * @param barSpacing gap between bars.
 * @param contentPadding padding applied around the whole indicator.
 * @param animationDurationMillis duration of one up/down sweep, kept
 *   close to 500 ms with a smooth easing curve.
 */
@Composable
fun PlayingIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = DezireAccent,
    barWidth: Dp = 3.dp,
    barHeight: Dp = 16.dp,
    barSpacing: Dp = 3.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    minBarHeightFraction: Float = 0.25f,
    animationDurationMillis: Int = 500
) {
    /*
     * Request 11: skip animation work while the composable is not active.
     * We track the lifecycle so no animation runs in the background.
     */
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScreenActive by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isScreenActive = true
                Lifecycle.Event.ON_PAUSE -> isScreenActive = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        isScreenActive =
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shouldAnimate = isPlaying && isScreenActive

    val maxBar = barHeight.value

    /*
     * One Animatable per bar. They are remembered so each bar keeps its
     * last height (freezes) when the animation stops instead of snapping.
     */
    val barHeights = remember(barHeight) {
        List(PlayingIndicatorBarCount) { index ->
            Animatable(maxBar * (0.35f + index * 0.15f))
        }
    }

    /*
     * While active, every bar loops between the max and min height. A
     * staggered start delay gives each bar its own phase, producing the
     * equalizer effect. Cancelling this effect (pause / not visible)
     * leaves the Animatable values exactly where they were.
     */
    LaunchedEffect(shouldAnimate, animationDurationMillis, minBarHeightFraction) {
        if (!shouldAnimate) return@LaunchedEffect

        val minBar = maxBar * minBarHeightFraction
        val phaseStepMillis = (animationDurationMillis / 4).toLong()

        barHeights.forEachIndexed { index, bar ->
            launch {
                delay(index * phaseStepMillis)

                while (true) {
                    bar.animateTo(
                        targetValue = maxBar,
                        animationSpec = tween(
                            durationMillis = animationDurationMillis,
                            easing = FastOutSlowInEasing
                        )
                    )
                    bar.animateTo(
                        targetValue = minBar,
                        animationSpec = tween(
                            durationMillis = animationDurationMillis,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
        }
    }

    Row(
        modifier = modifier
            .padding(contentPadding)
            .height(barHeight),
        horizontalArrangement = Arrangement.spacedBy(barSpacing),
        verticalAlignment = Alignment.Bottom
    ) {
        barHeights.forEach { bar ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(bar.value.dp)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(color)
            )
        }
    }
}
