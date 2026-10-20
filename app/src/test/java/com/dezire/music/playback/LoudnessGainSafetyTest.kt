package com.dezire.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Safety invariants for the normalization DSP, independent of whether the
 * gain is currently gated on: whatever the processor feeds through a sample,
 * the result must be finite, clamped inside +/-LIMIT_MAX and never derived
 * from a garbage (NaN/Infinity) input.
 */
class LoudnessGainSafetyTest {

    @Test
    fun `output is always finite for extreme finite inputs`() {
        val samples = listOf(Float.MAX_VALUE, -Float.MAX_VALUE, 2f, 0f)
        val gains = listOf(0f, 1f, LoudnessGain.MAX_GAIN, Float.MAX_VALUE / 2f)
        for (sample in samples) {
            for (gain in gains) {
                val out = LoudnessGain.scale(sample, gain)
                assertTrue("${sample}/${gain}", out.isFinite())
                assertTrue("${sample}/${gain}", out <= LoudnessGain.LIMIT_MAX)
                assertTrue("${sample}/${gain}", out >= -LoudnessGain.LIMIT_MAX)
            }
        }
    }

    @Test
    fun `non finite samples become silence instead of corrupting the stream`() {
        assertEquals(0f, LoudnessGain.scale(Float.NaN, 1f), 0f)
        assertEquals(0f, LoudnessGain.scale(Float.POSITIVE_INFINITY, 1f), 0f)
        assertEquals(0f, LoudnessGain.scale(Float.NEGATIVE_INFINITY, 1f), 0f)
    }

    @Test
    fun `non finite gain never amplifies a sample`() {
        assertEquals(0f, LoudnessGain.scale(0.9f, Float.NaN), 0f)
        assertEquals(0f, LoudnessGain.scale(0.9f, Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun `limiter never exceeds its ceiling even for extreme input`() {
        for (gain in listOf(LoudnessGain.MAX_GAIN, LoudnessGain.MIN_GAIN, 1f)) {
            val out = LoudnessGain.scale(1e10f, gain)
            assertTrue(out <= LoudnessGain.LIMIT_MAX)
            assertTrue(out >= -LoudnessGain.LIMIT_MAX)
        }
    }

    @Test
    fun `gain for peaks never leaves the clamp, even for degenerate peaks`() {
        // A zero/negative peak is ignored and treated as unity target.
        assertEquals(1f, LoudnessGain.gainForPeak(0f), 0f)
        assertEquals(1f, LoudnessGain.gainForPeak(-1f), 0f)
        assertEquals(LoudnessGain.MIN_GAIN, LoudnessGain.gainForPeak(Float.MAX_VALUE), 0f)
        assertTrue(LoudnessGain.gainForPeak(Float.MAX_VALUE).isFinite())
    }

    @Test
    fun `next gain can not explode beyond the clamp`() {
        val boosted = LoudnessGain.nextGain(1f, LoudnessGain.MAX_GAIN, 1f, 1f)
        assertTrue(boosted <= LoudnessGain.MAX_GAIN)
        val cut = LoudnessGain.nextGain(1f, LoudnessGain.MIN_GAIN, 1f, 1f)
        assertTrue(cut >= LoudnessGain.MIN_GAIN)
    }
}