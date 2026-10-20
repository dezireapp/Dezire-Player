package com.dezire.music.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dezire.music.data.MusicRepository
import com.dezire.music.data.SettingsRepository
import com.dezire.music.data.ThemeMode
import com.dezire.music.data.metadata.MetadataEnrichmentRepository
import com.dezire.music.data.metadata.MetadataRefreshSummary
import com.dezire.music.dezireApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SettingsRepository = application.dezireApp.settingsRepository
    private val musicRepository: MusicRepository = application.dezireApp.musicRepository
    private val metadataRepository: MetadataEnrichmentRepository =
        application.dezireApp.metadataRepository
    private val localMetadataReader = application.dezireApp.localMetadataReader

    val themeMode: StateFlow<ThemeMode> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val gaplessPlaybackEnabled: StateFlow<Boolean> = repository.gaplessPlaybackEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val crossfadeEnabled: StateFlow<Boolean> = repository.crossfadeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val crossfadeDurationSeconds: StateFlow<Int> = repository.crossfadeDurationSeconds
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_CROSSFADE_SECONDS)

    val volumeNormalizationEnabled: StateFlow<Boolean> = repository.volumeNormalizationEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /*
     * Last.fm is configured automatically at build time, so this is only
     * used to display status to the user. No API key entry is required.
     */
    val lastFmConfigured: Boolean = application.dezireApp.lastFmRepository.isConfigured

    private val _metadataState =
        MutableStateFlow<MetadataRefreshUiState>(MetadataRefreshUiState.Idle)
    val metadataState: StateFlow<MetadataRefreshUiState> = _metadataState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setGaplessPlaybackEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setGaplessPlaybackEnabled(enabled) }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setCrossfadeEnabled(enabled) }
    }

    fun setCrossfadeDurationSeconds(seconds: Int) {
        viewModelScope.launch { repository.setCrossfadeDurationSeconds(seconds.coerceIn(1, 12)) }
    }

    fun setVolumeNormalizationEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setVolumeNormalizationEnabled(enabled) }
    }

    /*
     * Refreshes Last.fm metadata for the current library. Runs entirely in
     * the background; the UI only reflects progress and the final summary.
     */
    fun refreshMetadata() {
        if (_metadataState.value is MetadataRefreshUiState.Refreshing) return
        _metadataState.value = MetadataRefreshUiState.Refreshing
        viewModelScope.launch {
            val summary = try {
                val localMetadata =
                    localMetadataReader.readAll(musicRepository.localSongs.value)
                metadataRepository.refreshFromLocalMetadata(localMetadata)
            } catch (t: Throwable) {
                MetadataRefreshSummary(failed = 1)
            }
            _metadataState.value = MetadataRefreshUiState.fromSummary(summary)
        }
    }
}

/*
 * UI state for the metadata refresh action.
 */
sealed interface MetadataRefreshUiState {
    data object Idle : MetadataRefreshUiState
    data object Refreshing : MetadataRefreshUiState
    data class Done(val message: String) : MetadataRefreshUiState

    companion object {
        fun fromSummary(summary: MetadataRefreshSummary): Done = when {
            summary.missingApiKey ->
                Done("Last.fm is not configured for this build.")
            summary.rateLimited ->
                Done("Last.fm rate limit reached. Cached data will be used.")
            summary.offline ->
                Done("You're offline. Cached metadata will be used.")
            summary.requested == 0 ->
                Done("Nothing to refresh yet.")
            else ->
                Done("Updated ${summary.succeeded} of ${summary.requested} artists.")
        }
    }
}