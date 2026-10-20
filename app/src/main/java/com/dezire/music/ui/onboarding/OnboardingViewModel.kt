package com.dezire.music.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dezire.music.data.MusicRepository
import com.dezire.music.data.SettingsRepository
import com.dezire.music.dezireApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/*
 * Drives the first-launch onboarding. It owns the persisted
 * "onboarding completed" flag and the local music scan used by the Music
 * Access screen, delegating to the shared [MusicRepository] so no second
 * library state is created.
 *
 * [onboardingCompleted] is null while the DataStore value is still being
 * read, which lets the navigation gate distinguish "not loaded yet" from
 * an explicit "not completed".
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository: SettingsRepository = application.dezireApp.settingsRepository
    private val musicRepository: MusicRepository = application.dezireApp.musicRepository

    val onboardingCompleted: StateFlow<Boolean?> = settingsRepository.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    /*
     * Scans the device through the existing repository and reports the
     * discovered song count. Only marks onboarding complete once the scan
     * that the user asked for has actually finished, so an interrupted or
     * denied flow is never recorded as done.
     */
    fun scanMusic() {
        if (_scanState.value == ScanState.Scanning) return
        _scanState.value = ScanState.Scanning
        viewModelScope.launch {
            musicRepository.refresh()
            _scanState.value = ScanState.Complete(musicRepository.songs.value.size)
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    /*
     * Persists completion for the "Not now" / skipped paths, which open the
     * app without scanning.
     */
    fun markOnboardingCompleted() {
        viewModelScope.launch { settingsRepository.setOnboardingCompleted(true) }
    }

    sealed interface ScanState {
        data object Idle : ScanState
        data object Scanning : ScanState
        data class Complete(val songCount: Int) : ScanState
    }
}
