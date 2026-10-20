package com.dezire.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Shared Dezire palette. Centralised here so the Now Playing screen and
 * the library screens stay visually consistent.
 */
val DezireRed = Color(0xFFE50920)

/* Primary accent used for active controls and highlights. */
val DezireAccent = DezireRed

val DezireTrackGray = Color(0xFF3C3C3C)
val DezireAlbumGray = Color(0xFF9A9A9A)
val DezireBottomBarGray = Color(0xFF202020)
val DezireCardGray = Color(0xFF161616)
val DezireSurfaceGray = Color(0xFF1C1C1C)
val DezireOnSurfaceVariant = Color(0xFFB0B0B0)

/*
 * Semantic, theme aware colours for the whole app.
 *
 * The library screens used to hardcode the dark palette (black
 * backgrounds, white text, dark gray cards), which is why selecting
 * "Light" in Settings had no visible effect. Every screen now reads these
 * colours instead, so the selected appearance actually changes the app.
 *
 * The accent stays DezireRed in both themes. Now Playing keeps its own
 * intentionally immersive dark backdrop.
 */
data class DezireThemeColors(
    /* Root screen background. */
    val background: Color,
    /* Cards, sheets, stat blocks. */
    val surface: Color,
    /* Highlighted row / "now playing" list background. */
    val surfaceHighlight: Color,
    /* Artwork placeholders, input fields, small tiles. */
    val card: Color,
    /* Bottom navigation bar background. */
    val bottomBar: Color,
    /* Selected destination indicator on the bottom bar. */
    val bottomBarIndicator: Color,
    /* Primary text on [background]. */
    val onBackground: Color,
    /* Primary text on [surface]. */
    val onSurface: Color,
    /* Secondary / muted text (artists, durations, hints). */
    val contentSecondary: Color,
    /* Inactive track of sliders / seek bars. */
    val track: Color,
    /* Neutral box when a tile has no artwork yet. */
    val placeholder: Color,
    val isDark: Boolean
)

val DezireDarkColors = DezireThemeColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1C),
    surfaceHighlight = Color(0xFF242426),
    card = Color(0xFF161616),
    bottomBar = Color(0xFF202020),
    bottomBarIndicator = Color(0xFF161616),
    onBackground = Color.White,
    onSurface = Color.White,
    contentSecondary = Color(0xFF9A9A9A),
    track = Color(0xFF3C3C3C),
    placeholder = Color(0xFF262626),
    isDark = true
)

val DezireLightColors = DezireThemeColors(
    background = Color(0xFFF6F6F7),
    surface = Color.White,
    surfaceHighlight = Color(0xFFE8E8EC),
    card = Color.White,
    bottomBar = Color.White,
    bottomBarIndicator = Color(0xFFE3E3E7),
    onBackground = Color(0xFF16161A),
    onSurface = Color(0xFF16161A),
    contentSecondary = Color(0xFF6E6E73),
    track = Color(0xFFD0D0D5),
    placeholder = Color(0xFFE4E4E8),
    isDark = false
)

private val LocalDezireColors = staticCompositionLocalOf { DezireDarkColors }

/**
 * Current theme colours. Falls back to the dark palette until the app
 * supplies its own through [MusicTheme].
 */
val MaterialTheme.dezireColors: DezireThemeColors
    @Composable
    @ReadOnlyComposable
    get() = LocalDezireColors.current

@Composable
@ReadOnlyComposable
fun dezireColors(): DezireThemeColors = LocalDezireColors.current

@Composable
internal fun provideDezireColors(
    colors: DezireThemeColors,
    content: @Composable () -> Unit
) = androidx.compose.runtime.CompositionLocalProvider(
    LocalDezireColors provides colors,
    content = content
)