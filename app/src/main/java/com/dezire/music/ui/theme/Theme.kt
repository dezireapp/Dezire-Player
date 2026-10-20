package com.dezire.music.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DezireRed,
    secondary = DezireAccent,
    tertiary = DezireAccent,
    background = DezireDarkColors.background,
    surface = DezireDarkColors.surface,
    surfaceVariant = DezireDarkColors.card,
    onBackground = DezireDarkColors.onBackground,
    onSurface = DezireDarkColors.onSurface,
    onSurfaceVariant = DezireOnSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = DezireRed,
    secondary = DezireAccent,
    tertiary = DezireAccent,
    background = DezireLightColors.background,
    surface = DezireLightColors.surface,
    surfaceVariant = DezireLightColors.placeholder,
    onBackground = DezireLightColors.onBackground,
    onSurface = DezireLightColors.onSurface,
    onSurfaceVariant = Color(0xFF6E6E73),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White
)

/*
 * Applies the Dezire theme. [darkTheme] is decided in MainActivity from
 * the persisted Appearance preference (System / Dark / Light), so the
 * whole app - including every screen that reads dezireColors() - follows
 * the selected appearance instantly.
 *
 * Dynamic color is intentionally off: Dezire is a branded, red-accented
 * player and the palette must stay consistent on every device.
 */
@Composable
fun MusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val dezireColors = if (darkTheme) DezireDarkColors else DezireLightColors

    provideDezireColors(dezireColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}