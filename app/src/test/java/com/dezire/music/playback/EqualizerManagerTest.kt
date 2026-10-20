package com.dezire.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Equalizer fallback behaviour. JVM tests cannot create a real platform
 * Equalizer, so these cover the guaranteed-safe paths: before any session
 * is attached, after an invalid session id (the device case), and that
 * release/reset never throw when there is no native effect.
 */
class EqualizerManagerTest {

    @Test
    fun `starts unsupported before any audio session attaches`() {
        val manager = EqualizerManager()
        assertFalse(manager.state.value.supported)
        assertFalse(manager.state.value.enabled)
        assertTrue(manager.state.value.bands.isEmpty())
    }

    @Test
    fun `falls back gracefully when the equalizer is unavailable`() {
        val manager = EqualizerManager()
        // Any negative session id (including AudioEffect.ERROR_BAD_VALUE == -2)
        // must land in the failure path, not crash.
        manager.attachAudioSession(-2)

        val state = manager.state.value
        assertFalse("UI must show the fallback message", state.supported)
        assertFalse(state.enabled)
        assertEquals(0, state.numberOfBands.toInt())
        assertTrue(state.bands.isEmpty())
        assertTrue(state.presets.isEmpty())
    }

    @Test
    fun `user actions are safe when no session is attached`() {
        val manager = EqualizerManager()
        manager.setEnabled(true)
        manager.usePreset(0)
        manager.setBandLevel(0, 300)
        manager.reset()
        manager.release()
        assertFalse(manager.state.value.supported)
    }

    @Test
    fun `release is idempotent and safe`() {
        val manager = EqualizerManager()
        manager.release()
        manager.release()
        manager.attachAudioSession(-1)
        manager.release()
        assertFalse(manager.state.value.supported)
    }
}