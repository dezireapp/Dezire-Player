package com.dezire.music.playback

import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import com.dezire.music.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class EqualizerBand(
    val index: Int,
    val lowerFreqHz: Int,
    val upperFreqHz: Int,
    val centerFreqHz: Int,
    val minGain: Short,
    val maxGain: Short,
    val currentGain: Short
)

data class EqualizerState(
    val enabled: Boolean = false,
    val bands: List<EqualizerBand> = emptyList(),
    val numberOfBands: Short = 0,
    val presets: List<EqualizerPreset> = emptyList(),
    val currentPreset: Int = -1,
    val supported: Boolean = false
)

data class EqualizerPreset(
    val index: Int,
    val name: String
)

/*
 * Snapshot of the persisted equalizer configuration. [bandLevels] holds
 * one gain (millibels) per band in ascending band order.
 */
data class EqualizerSnapshot(
    val enabled: Boolean = false,
    val preset: Int = -1,
    val bandLevels: List<Int> = emptyList()
)

/*
 * Owns the platform Equalizer effect attached to the shared playback audio
 * session. All state is mirrored in a [StateFlow] for the UI, and user
 * changes are persisted through [SettingsRepository] when one is supplied.
 *
 * Every platform call is guarded: on devices where Equalizer construction
 * fails (no audio session, unsupported hardware) the state simply reports
 * [EqualizerState.supported] == false and the UI shows a fallback message
 * instead of crashing. [release] must be called when the player resources
 * are torn down so the native effect is released safely.
 */
class EqualizerManager(
    private val settingsRepository: SettingsRepository? = null,
    private val scope: CoroutineScope? = null
) {

    private var equalizer: Equalizer? = null
    private var audioSessionId: Int = AudioEffect.ERROR_BAD_VALUE
    private var restoreJob: Job? = null

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    /*
     * Attaches the effect to the given audio session, or drops to the
     * unsupported state for an invalid session id.
     *
     * ExoPlayer may fire onAudioSessionIdChanged with the same id several
     * times around a playback (re)start. Recreating the native effect on
     * every callback is wasteful and can briefly detach audio processing
     * mid-stream, so when the session is unchanged and a live effect is
     * already attached we return without touching anything.
     */
    fun attachAudioSession(sessionId: Int) {
        if (sessionId == audioSessionId && equalizer != null) return
        release()
        audioSessionId = sessionId
        if (sessionId == AudioEffect.ERROR_BAD_VALUE || sessionId < 0) {
            _state.value = EqualizerState(supported = false)
            return
        }
        try {
            // The effect is always created disabled and stays disabled unless
            // a persisted snapshot explicitly re-enables it, so attaching can
            // never impose an unrequested EQ curve or gain on playback.
            equalizer = Equalizer(0, sessionId).apply {
                enabled = false
            }
            loadState()
        } catch (e: Exception) {
            equalizer = null
            _state.value = EqualizerState(supported = false)
        }
        startRestoreJob()
    }

    /*
     * Pulls the live band/preset data out of the native effect into the
     * mirrored [EqualizerState]. Preserves [EqualizerState.currentPreset].
     */
    private fun loadState() {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val bands = mutableListOf<EqualizerBand>()
            for (i in 0 until numBands) {
                val range = eq.bandLevelRange
                val freqRange = eq.getBandFreqRange(i.toShort())
                bands.add(
                    EqualizerBand(
                        index = i,
                        lowerFreqHz = freqRange[0].toInt(),
                        upperFreqHz = freqRange[1].toInt(),
                        centerFreqHz = eq.getCenterFreq(i.toShort()).toInt(),
                        minGain = range[0],
                        maxGain = range[1],
                        currentGain = eq.getBandLevel(i.toShort())
                    )
                )
            }
            val presets = mutableListOf<EqualizerPreset>()
            val presetCount = eq.numberOfPresets.toInt()
            for (i in 0 until presetCount) {
                presets.add(EqualizerPreset(i, eq.getPresetName(i.toShort()) ?: "Preset $i"))
            }
            _state.value = _state.value.copy(
                supported = true,
                numberOfBands = eq.numberOfBands,
                bands = bands,
                presets = presets
            )
        } catch (e: Exception) {
            _state.value = EqualizerState(supported = false)
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            _state.value = _state.value.copy(enabled = enabled)
            persist(enabled = enabled, preset = _state.value.currentPreset, bandLevels = null)
        } catch (e: Exception) {
            _state.value = _state.value.copy(supported = false)
        }
    }

    fun setBandLevel(band: Int, gain: Short) {
        val eq = equalizer ?: return
        try {
            eq.setBandLevel(band.toShort(), gain)
            val updated = _state.value.bands.map {
                if (it.index == band) it.copy(currentGain = gain) else it
            }
            _state.value = _state.value.copy(bands = updated, currentPreset = -1)
            persist(enabled = _state.value.enabled, preset = -1, bandLevels = updated.map { it.currentGain.toInt() })
        } catch (e: Exception) {
        }
    }

    fun usePreset(index: Int) {
        val eq = equalizer ?: return
        try {
            if (index in 0 until eq.numberOfPresets.toInt()) {
                eq.usePreset(index.toShort())
                _state.value = _state.value.copy(currentPreset = index)
                loadState()
                persist(enabled = _state.value.enabled, preset = index, bandLevels = emptyList())
            }
        } catch (e: Exception) {
        }
    }

    fun reset() {
        val eq = equalizer ?: return
        try {
            for (i in _state.value.bands.indices) {
                eq.setBandLevel(i.toShort(), 0)
            }
            _state.value = _state.value.copy(currentPreset = -1)
            loadState()
            val flat = _state.value.bands.map { 0 }
            persist(enabled = _state.value.enabled, preset = -1, bandLevels = flat)
        } catch (e: Exception) {
        }
    }

    fun release() {
        restoreJob?.cancel()
        restoreJob = null
        try {
            equalizer?.release()
        } catch (e: Exception) {
        }
        equalizer = null
    }

    /* ---- persistence --------------------------------------------------- */

    /*
     * Applies a restored snapshot without writing back to the repository,
     * so the restore job can never echo its own saves into a loop.
     */
    private fun applySnapshot(snapshot: EqualizerSnapshot) {
        val eq = equalizer ?: return
        if (!_state.value.supported) return
        try {
            if (snapshot.preset >= 0 && snapshot.preset < eq.numberOfPresets.toInt()) {
                eq.usePreset(snapshot.preset.toShort())
                _state.value = _state.value.copy(currentPreset = snapshot.preset)
                loadState()
            } else if (snapshot.bandLevels.isNotEmpty()) {
                val bands = _state.value.bands
                for ((index, level) in snapshot.bandLevels.withIndex()) {
                    if (index < bands.size) {
                        eq.setBandLevel(index.toShort(), level.toShort())
                    }
                }
                _state.value = _state.value.copy(currentPreset = -1)
                loadState()
            }
            eq.enabled = snapshot.enabled
            _state.value = _state.value.copy(enabled = snapshot.enabled)
        } catch (e: Exception) {
        }
    }

    private fun startRestoreJob() {
        val repository = settingsRepository ?: return
        val coroutineScope = scope ?: return
        restoreJob?.cancel()
        restoreJob = coroutineScope.launch {
            combine(
                repository.equalizerEnabled,
                repository.equalizerPreset,
                repository.equalizerBandLevels
            ) { enabled, preset, rawBands ->
                EqualizerSnapshot(
                    enabled = enabled,
                    preset = preset,
                    bandLevels = repository.parseEqualizerBandLevels(rawBands)
                )
            }.collect { snapshot -> applySnapshot(snapshot) }
        }
    }

    private fun persist(enabled: Boolean, preset: Int = -1, bandLevels: List<Int>? = null) {
        val repository = settingsRepository ?: return
        val coroutineScope = scope ?: return
        coroutineScope.launch {
            repository.setEqualizerEnabled(enabled)
            repository.setEqualizerPreset(preset)
            bandLevels?.let { repository.setEqualizerBandLevels(it) }
        }
    }
}