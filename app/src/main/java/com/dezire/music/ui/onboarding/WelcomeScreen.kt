package com.dezire.music.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Onboarding step 1. Cinematic welcome with ambient lighting, an abstract
 * vinyl artwork and a large circular Next action. "Skip" advances to the
 * music access step (it never bypasses the audio permission request).
 */
@Composable
fun WelcomeScreen(
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    OnboardingBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onSkip,
                    colors = ButtonDefaults.textButtonColors(contentColor = OnboardingMuted)
                ) {
                    Text(text = "Skip", fontSize = 15.sp)
                }
            }

            Spacer(Modifier.weight(0.9f))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(700)) +
                    slideInVertically(tween(700)) { it / 6 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MusicArtwork(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(700, delayMillis = 120)) +
                    slideInVertically(tween(700, delayMillis = 120)) { it / 8 }
            ) {
                Column {
                    OnboardingEyebrow(text = "YOUR MUSIC. YOUR WORLD.")
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Welcome to Dezire",
                        color = OnboardingWhite,
                        fontSize = 38.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Every song you love, all in one place.",
                        color = OnboardingWhite.copy(alpha = 0.92f),
                        fontSize = 17.sp,
                        lineHeight = 24.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Discover your music. Build your library. " +
                            "Enjoy every beat.",
                        color = OnboardingMuted,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(700, delayMillis = 240))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OnboardingPageIndicator(count = 2, current = 0)
                    CircularNextButton(
                        onClick = onNext,
                        contentDescription = "Continue to music access"
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
