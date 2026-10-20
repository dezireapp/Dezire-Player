package com.dezire.music.data

/*
 * Single source of truth for playback-setting defaults and bounds.
 *
 * Kept as a plain Kotlin object (no Android dependencies) so the pure
 * crossfade/loudness logic and the unit tests can share the exact same
 * values the persisted SettingsRepository uses.
 */
object PlaybackDefaults {
    const val GAPLESS_PLAYBACK_ENABLED = true
    const val CROSSFADE_ENABLED = false
    const val CROSSFADE_DURATION_SECONDS = 5
    const val VOLUME_NORMALIZATION_ENABLED = false

    const val MIN_CROSSFADE_SECONDS = 1
    const val MAX_CROSSFADE_SECONDS = 12

    const val MIN_CROSSFADE_MS = MIN_CROSSFADE_SECONDS * 1000L
    const val MAX_CROSSFADE_MS = MAX_CROSSFADE_SECONDS * 1000L
}