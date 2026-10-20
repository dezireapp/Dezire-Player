package com.dezire.music.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dezireDataStore by preferencesDataStore(name = "dezire_settings")

class SettingsRepository(private val context: Context) {

    val themeMode: Flow<ThemeMode> = context.dezireDataStore.data
        .map { prefs ->
            prefs[KEY_THEME]?.let { name ->
                ThemeMode.entries.firstOrNull { it.name == name }
            } ?: ThemeMode.SYSTEM
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dezireDataStore.edit { prefs ->
            prefs[KEY_THEME] = mode.name
        }
    }

    val swipeHintShown: Flow<Boolean> = context.dezireDataStore.data
        .map { prefs -> prefs[KEY_SWIPE_HINT_SHOWN] ?: false }

    suspend fun markSwipeHintShown() {
        context.dezireDataStore.edit { prefs ->
            prefs[KEY_SWIPE_HINT_SHOWN] = true
        }
    }

    /*
     * Persistent flag for the first-launch onboarding. Defaults to false so
     * a brand new install starts on the Welcome screen; once the user has
     * either granted audio access, skipped scanning or dismissed the flow it
     * is set to true and the onboarding is never shown again. Only this one
     * key is written, so the rest of the settings are preserved.
     */
    val onboardingCompleted: Flow<Boolean> = context.dezireDataStore.data
        .map { prefs -> prefs[KEY_ONBOARDING_COMPLETED] ?: false }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dezireDataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    val gaplessPlaybackEnabled: Flow<Boolean> = context.dezireDataStore.data
        .map { prefs -> prefs[KEY_GAPLESS_ENABLED] ?: PlaybackDefaults.GAPLESS_PLAYBACK_ENABLED }

    suspend fun setGaplessPlaybackEnabled(enabled: Boolean) {
        context.dezireDataStore.edit { prefs ->
            prefs[KEY_GAPLESS_ENABLED] = enabled
        }
    }

    val crossfadeEnabled: Flow<Boolean> = context.dezireDataStore.data
        .map { prefs -> prefs[KEY_CROSSFADE_ENABLED] ?: PlaybackDefaults.CROSSFADE_ENABLED }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        context.dezireDataStore.edit { prefs ->
            prefs[KEY_CROSSFADE_ENABLED] = enabled
        }
    }

    val crossfadeDurationMs: Flow<Long> = context.dezireDataStore.data
        .map { prefs ->
            val seconds =
                prefs[KEY_CROSSFADE_DURATION] ?: PlaybackDefaults.CROSSFADE_DURATION_SECONDS
            (seconds.coerceIn(1, 12) * 1000L)
        }

    val crossfadeDurationSeconds: Flow<Int> = context.dezireDataStore.data
        .map { prefs ->
            prefs[KEY_CROSSFADE_DURATION] ?: PlaybackDefaults.CROSSFADE_DURATION_SECONDS
        }

    suspend fun setCrossfadeDurationSeconds(seconds: Int) {
        context.dezireDataStore.edit { prefs ->
            prefs[KEY_CROSSFADE_DURATION] = seconds.coerceIn(1, 12)
        }
    }

    val volumeNormalizationEnabled: Flow<Boolean> = context.dezireDataStore.data
        .map { prefs ->
            prefs[KEY_VOLUME_NORMALIZATION_ENABLED] ?: PlaybackDefaults.VOLUME_NORMALIZATION_ENABLED
        }

    suspend fun setVolumeNormalizationEnabled(enabled: Boolean) {
        context.dezireDataStore.edit { prefs ->
            prefs[KEY_VOLUME_NORMALIZATION_ENABLED] = enabled
        }
    }

    /*
     * Equalizer persistence. Band levels are stored as a compact comma
     * separated string ("-150,0,80,...") so a single DataStore key holds
     * the whole band list without needing a second store instance.
     */
    val equalizerEnabled: Flow<Boolean> = context.dezireDataStore.data
        .map { prefs -> prefs[KEY_EQUALIZER_ENABLED] ?: false }

    val equalizerPreset: Flow<Int> = context.dezireDataStore.data
        .map { prefs -> prefs[KEY_EQUALIZER_PRESET] ?: -1 }

    val equalizerBandLevels: Flow<String> = context.dezireDataStore.data
        .map { prefs -> prefs[KEY_EQUALIZER_BAND_LEVELS].orEmpty() }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.dezireDataStore.edit { prefs ->
            prefs[KEY_EQUALIZER_ENABLED] = enabled
        }
    }

    suspend fun setEqualizerPreset(preset: Int) {
        context.dezireDataStore.edit { prefs ->
            if (preset >= 0) prefs[KEY_EQUALIZER_PRESET] = preset else prefs.remove(KEY_EQUALIZER_PRESET)
        }
    }

    suspend fun setEqualizerBandLevels(levels: List<Int>) {
        context.dezireDataStore.edit { prefs ->
            if (levels.isEmpty()) {
                prefs.remove(KEY_EQUALIZER_BAND_LEVELS)
            } else {
                prefs[KEY_EQUALIZER_BAND_LEVELS] = levels.joinToString(",")
            }
        }
    }

    /*
     * Parses the persisted equalizer band string back into a list of
     * gains (in millibels). Malformed or empty values become an empty
     * list so the EqualizerManager never applies garbage.
     */
    fun parseEqualizerBandLevels(raw: String): List<Int> =
        SettingsRepository.parseEqualizerBandLevels(raw)

    companion object {
        const val DEFAULT_CROSSFADE_SECONDS = PlaybackDefaults.CROSSFADE_DURATION_SECONDS
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_SWIPE_HINT_SHOWN = booleanPreferencesKey("swipe_hint_shown")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_GAPLESS_ENABLED = booleanPreferencesKey("gapless_playback_enabled")
        val KEY_CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        val KEY_CROSSFADE_DURATION = intPreferencesKey("crossfade_duration_seconds")
        val KEY_VOLUME_NORMALIZATION_ENABLED = booleanPreferencesKey("volume_normalization_enabled")
        val KEY_EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val KEY_EQUALIZER_PRESET = intPreferencesKey("equalizer_preset")
        val KEY_EQUALIZER_BAND_LEVELS = stringPreferencesKey("equalizer_band_levels")

        /*
         * Pure parser (no Context needed) so the persistence round-trip can
         * be unit-tested on the JVM.
         */
        fun parseEqualizerBandLevels(raw: String): List<Int> {
            if (raw.isBlank()) return emptyList()
            return raw.split(',')
                .mapNotNull { token ->
                    token.trim().toIntOrNull()?.coerceIn(-1500, 1500)
                }
        }
    }
}

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}
