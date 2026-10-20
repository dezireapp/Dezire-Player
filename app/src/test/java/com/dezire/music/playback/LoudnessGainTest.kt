package com.dezire.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessGainTest {

    @Test
    fun `gain is unity at the target peak`() {
        assertEquals(1f, LoudnessGain.gainForPeak(LoudnessGain.TARGET_PEAK), 0.0001f)
    }

    @Test
    fun `loud passages are attenuated`() {
        val gain = LoudnessGain.gainForPeak(1f)
        assertTrue("loud peak should reduce gain", gain < 1f)
        assertEquals(0.7f, gain, 0.001f)
    }

    @Test
    fun `optimal gain is clamped to avoid excessive boost or cut`() {
        assertEquals(LoudnessGain.MAX_GAIN, LoudnessGain.gainForPeak(0.05f), 0.0001f)
        assertEquals(LoudnessGain.MIN_GAIN, LoudnessGain.gainForPeak(3f), 0.0001f)
    }

    @Test
    fun `limiter keeps the digital signal inside safe bounds`() {
        val loud = LoudnessGain.scale(10f, LoudnessGain.MAX_GAIN)
        assertTrue("output must not clip", loud <= LoudnessGain.LIMIT_MAX)
        assertTrue(loud >= -LoudnessGain.LIMIT_MAX)

        val negative = LoudnessGain.scale(-10f, LoudnessGain.MAX_GAIN)
        assertTrue(negative >= -LoudnessGain.LIMIT_MAX)
        assertTrue(negative <= LoudnessGain.LIMIT_MAX)
    }

    @Test
    fun `quiet samples pass through unchanged under unity gain`() {
        assertEquals(0f, LoudnessGain.scale(0f, 1f), 0f)
        assertEquals(0.3f, LoudnessGain.scale(0.3f, 1f), 0.0001f)
    }

    @Test
    fun `gain adapts towards the target but never leaves the clamp`() {
        // Boost: needs more gain, uses the fast attack factor.
        val boosted = LoudnessGain.nextGain(1f, 1.5f, LoudnessGain.ATTACK_FACTOR, LoudnessGain.RELEASE_FACTOR)
        assertTrue("should move towards 1.5", boosted > 1f)
        assertTrue(boosted <= LoudnessGain.MAX_GAIN)

        // Cut: backs off with the slow release factor.
        val cut = LoudnessGain.nextGain(1f, 0.5f, LoudnessGain.ATTACK_FACTOR, LoudnessGain.RELEASE_FACTOR)
        assertTrue(cut < 1f)
        assertTrue(cut >= LoudnessGain.MIN_GAIN)

        // Extreme targets stay clamped.
        assertEquals(LoudnessGain.MAX_GAIN, LoudnessGain.nextGain(1f, 99f, 1f, 1f), 0f)
        assertEquals(LoudnessGain.MIN_GAIN, LoudnessGain.nextGain(1f, 0f, 1f, 1f), 0f)
    }

    @Test
    fun `disabled equivalently means unity gain passthrough`() {
        // When the processor is disabled the controller leaves gain at 1;
        // scale() with gain 1 is a pure passthrough for in-range samples.
        for (sample in listOf(-0.9f, -0.5f, 0f, 0.25f, 0.9f)) {
            assertEquals(sample, LoudnessGain.scale(sample, 1f), 0.0001f)
        }
    }

    @Test
    fun `zero gain silences the stream`() {
        assertEquals(0f, LoudnessGain.scale(0.8f, 0f), 0f)
    }
}