package com.dezire.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FailedTrackRecoveryTest {

    @Test
    fun `advances to a distinct next window`() {
        assertEquals(3, FailedTrackRecovery.nextAfterFailure(2, 3, 5))
    }

    @Test
    fun `does not auto-repeat the failed track`() {
        assertNull(FailedTrackRecovery.nextAfterFailure(0, 0, 1))
        assertNull(FailedTrackRecovery.nextAfterFailure(0, 0, 5))
    }

    @Test
    fun `refuses out of range indices`() {
        assertNull(FailedTrackRecovery.nextAfterFailure(1, 5, 5))
        assertNull(FailedTrackRecovery.nextAfterFailure(1, -1, 5))
        assertNull(FailedTrackRecovery.nextAfterFailure(5, 0, 5))
        assertNull(FailedTrackRecovery.nextAfterFailure(-1, 0, 5))
    }

    @Test
    fun `refuses when there is nothing to move on to`() {
        assertNull(FailedTrackRecovery.nextAfterFailure(0, 0, 0))
        assertNull(FailedTrackRecovery.nextAfterFailure(0, 1, 1))
        assertNull(FailedTrackRecovery.nextAfterFailure(0, 1, 0))
    }

    @Test
    fun `wraparound still needs a genuinely different window`() {
        // A repeating single-track queue reports nextIndex == currentIndex:
        // skipping there is pointless and would loop forever.
        assertNull(FailedTrackRecovery.nextAfterFailure(0, 0, 1))
        assertEquals(0, FailedTrackRecovery.nextAfterFailure(4, 0, 5))
    }
}