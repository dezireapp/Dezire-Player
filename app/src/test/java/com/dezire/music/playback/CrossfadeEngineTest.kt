package com.dezire.music.playback

import com.dezire.music.data.PlaybackDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeEngineTest {

    private fun engine(enabled: Boolean = true, durationSec: Int = 5): CrossfadeEngine =
        CrossfadeEngine().apply {
            this.enabled = enabled
            durationMs = sanitizeDurationMs(durationSec)
        }

    @Test
    fun `defaults disable crossfade and use five second duration`() {
        val engine = CrossfadeEngine()
        assertFalse(engine.enabled)
        assertEquals(PlaybackDefaults.CROSSFADE_DURATION_SECONDS * 1000L, engine.durationMs)
        assertEquals(PlaybackDefaults.CROSSFADE_ENABLED, engine.enabled)
    }

    @Test
    fun `duration is clamped into the one to twelve second range`() {
        val engine = engine()
        assertEquals(1, engine.sanitizeDurationSeconds(0))
        assertEquals(1, engine.sanitizeDurationSeconds(-3))
        assertEquals(1, engine.sanitizeDurationSeconds(1))
        assertEquals(5, engine.sanitizeDurationSeconds(5))
        assertEquals(12, engine.sanitizeDurationSeconds(12))
        assertEquals(12, engine.sanitizeDurationSeconds(99))
        assertEquals(5_000L, engine.sanitizeDurationMs(5))
        assertEquals(12_000L, engine.sanitizeDurationMs(13))
    }

    @Test
    fun `does not start when disabled or not playing or without a next track`() {
        val e = engine(enabled = false)
        assertFalse(
            e.shouldStart(
                positionMs = 25_000, trackDurationMs = 30_000,
                isPlaying = true, hasNext = true, nextIndex = 1, currentIndex = 0
            )
        )

        val e2 = engine()
        assertFalse(
            e2.shouldStart(
                positionMs = 25_000, trackDurationMs = 30_000,
                isPlaying = false, hasNext = true, nextIndex = 1, currentIndex = 0
            )
        )
        assertFalse(
            e2.shouldStart(
                positionMs = 25_000, trackDurationMs = 30_000,
                isPlaying = true, hasNext = false, nextIndex = -1, currentIndex = 0
            )
        )
    }

    @Test
    fun `only begins inside the fade window near the end of the track`() {
        val e = engine()
        val trackMs = 30_000L

        // Mid-track: far from the end.
        assertFalse(
            e.shouldStart(10_000, trackMs, true, true, 1, 0)
        )
        // Inside the 5s fade window (+120ms margin).
        assertTrue(
            e.shouldStart(25_000, trackMs, true, true, 1, 0)
        )
        // Too close to the very end: natural transition is imminent.
        assertFalse(
            e.shouldStart(trackMs - 100, trackMs, true, true, 1, 0)
        )
    }

    @Test
    fun `start creates a session and never runs two fades at once`() {
        val e = engine()
        val first = e.start(outgoingIndex = 0, nextIndex = 1, trackDurationMs = 30_000, isPlaying = true, hasNext = true)
        assertNotNull(first)
        assertTrue(e.active)

        val duplicate = e.start(outgoingIndex = 1, nextIndex = 2, trackDurationMs = 30_000, isPlaying = true, hasNext = true)
        assertNull(duplicate)
        assertTrue(e.active)
    }

    @Test
    fun `volume envelope is equal power and ends on the base volume`() {
        val e = engine()
        e.baseVolume = 1f
        e.start(0, 1, 30_000, true, true)

        val (startOut, startIn) = e.volumes(0L)
        assertEquals("incoming silent at start", 1f, startOut, 0.001f)
        assertEquals("outgoing full at start", 0f, startIn, 0.001f)

        val (midOut, midIn) = e.volumes(2_500L)
        assertEquals(0.707f, midOut, 0.01f)
        assertEquals(0.707f, midIn, 0.01f)

        val (endOut, endIn) = e.volumes(5_000L)
        assertEquals("outgoing silent at end", 0f, endOut, 0.01f)
        assertEquals("incoming reaches base at end", 1f, endIn, 0.001f)
    }

    @Test
    fun `cancellation on skip restores volume for a fresh transition`() {
        val e = engine()
        e.baseVolume = 1f
        e.start(0, 1, 30_000, true, true)

        // User skipped away to a totally different track index.
        assertTrue(
            e.shouldCancel(positionMs = 5_000, isPlaying = true, currentIndex = 3, nextIndex = 1, queueChanged = false)
        )
        // User paused mid-transition away from the end.
        assertTrue(
            e.shouldCancel(positionMs = 5_000, isPlaying = false, currentIndex = 0, nextIndex = 1, queueChanged = false)
        )
        // Queue was replaced or reordered.
        assertTrue(
            e.shouldCancel(positionMs = 5_000, isPlaying = true, currentIndex = 0, nextIndex = 1, queueChanged = true)
        )
        // Setting switched off.
        e.enabled = false
        assertTrue(
            e.shouldCancel(positionMs = 5_000, isPlaying = true, currentIndex = 0, nextIndex = 1, queueChanged = false)
        )

        e.enabled = true
        val session = e.cancel()
        assertNotNull(session)
        assertFalse(e.active)
        // Base volume is untouched by cancellation; the controller restores
        // it on the actual player.
        assertEquals(1f, e.baseVolume, 0f)
    }

    @Test
    fun `handoff triggers when the primary advances or the track ends`() {
        val e = engine()
        e.start(0, 1, 30_000, true, true)

        // Still mid-track on the outgoing item: keep fading.
        assertFalse(e.shouldHandoff(positionMs = 20_000, currentIndex = 0))

        // Primary auto-advanced to the incoming item.
        assertTrue(e.shouldHandoff(positionMs = 0L, currentIndex = 1))

        // Or the outgoing track reached its natural end (gapless-off pause).
        assertTrue(e.shouldHandoff(positionMs = 30_000, currentIndex = 0))

        assertNotNull(e.onTransitionComplete())
        assertFalse(e.active)
    }
}