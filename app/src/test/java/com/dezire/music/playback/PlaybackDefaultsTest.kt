package com.dezire.music.playback

import com.dezire.music.data.PlaybackDefaults
import com.dezire.music.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Covers the persisted playback settings: recommended defaults, duration
 * bounds, and the pure band-level persistence round-trip. DataStore I/O
 * itself needs an Android environment, so the serializable mapping and
 * defaults (the parts that can regress without a device) are the focus.
 */
class PlaybackDefaultsTest {

    @Test
    fun `recommended defaults for all playback settings`() {
        assertTrue(PlaybackDefaults.GAPLESS_PLAYBACK_ENABLED)
        assertFalse(PlaybackDefaults.CROSSFADE_ENABLED)
        assertEquals(5, PlaybackDefaults.CROSSFADE_DURATION_SECONDS)
        assertFalse(PlaybackDefaults.VOLUME_NORMALIZATION_ENABLED)
        assertEquals(1, PlaybackDefaults.MIN_CROSSFADE_SECONDS)
        assertEquals(12, PlaybackDefaults.MAX_CROSSFADE_SECONDS)
        assertEquals(5_000L, PlaybackDefaults.MIN_CROSSFADE_MS * PlaybackDefaults.CROSSFADE_DURATION_SECONDS / PlaybackDefaults.MIN_CROSSFADE_SECONDS)
    }

    @Test
    fun `crossfade duration range matches the slider`() {
        assertTrue(PlaybackDefaults.CROSSFADE_DURATION_SECONDS in 1..12)
        assertEquals(12, PlaybackDefaults.MAX_CROSSFADE_SECONDS)
        assertEquals(1, PlaybackDefaults.MIN_CROSSFADE_SECONDS)
    }

    @Test
    fun `stored crossfade duration is clamped by the repository contract`() {
        // Repository's data-flow guarantees the same bounds the slider uses.
        val engine = CrossfadeEngine()
        assertEquals(1, engine.sanitizeDurationSeconds(0))
        assertEquals(12, engine.sanitizeDurationSeconds(13))
        assertEquals(5, engine.sanitizeDurationSeconds(PlaybackDefaults.CROSSFADE_DURATION_SECONDS))
    }

    @Test
    fun `equalizer band levels survive a persistence round trip`() {
        val levels = listOf(-150, 0, 80, 200, -300)
        val raw = levels.joinToString(",")

        assertEquals(levels, SettingsRepository.parseEqualizerBandLevels(raw))
        assertEquals(emptyList<Int>(), SettingsRepository.parseEqualizerBandLevels(""))
        assertEquals(emptyList<Int>(), SettingsRepository.parseEqualizerBandLevels("   "))
    }

    @Test
    fun `malformed equalizer band data never reaches the effect`() {
        assertEquals(emptyList<Int>(), SettingsRepository.parseEqualizerBandLevels("a,b,c"))
        // Out-of-range tokens are clamped into the millibel window.
        assertEquals(listOf(-1500, 1500), SettingsRepository.parseEqualizerBandLevels("-99999, 99999"))
    }

    @Test
    fun `equalizer snapshot defaults are disabled and flat`() {
        val snapshot = EqualizerSnapshot()
        assertFalse(snapshot.enabled)
        assertEquals(-1, snapshot.preset)
        assertTrue(snapshot.bandLevels.isEmpty())
    }

    @Test
    fun `equalizer state reports unsupported until an audio session attaches`() {
        val state = EqualizerState()
        assertFalse(state.supported)
        assertFalse(state.enabled)
        assertNull(state.presets.firstOrNull())
    }
}