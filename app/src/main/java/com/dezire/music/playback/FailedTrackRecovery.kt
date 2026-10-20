package com.dezire.music.playback

/*
 * Decides, after a track fails, whether the queue should move on to a
 * different window so a single broken/unreadable file cannot wedge the whole
 * queue. This is deliberately pure (no Player) so the recovery policy is
 * unit-testable; PlaybackController applies the extra guard that only one
 * such auto-skip is allowed per short time window.
 */
object FailedTrackRecovery {

    /**
     * Returns the queue index to advance to after the track at
     * [currentIndex] failed, or null when there is nothing sensible to skip
     * to (no playlist, no distinct next window - including repeat-one at the
     * only item). Mirrors the difference between a real "next" and the
     * current index so wrap-around repeat-all single-track loops can never
     * auto-repeat a broken file forever.
     */
    fun nextAfterFailure(currentIndex: Int, nextIndex: Int, mediaItemCount: Int): Int? {
        if (mediaItemCount <= 0) return null
        if (currentIndex !in 0 until mediaItemCount) return null
        if (nextIndex < 0 || nextIndex >= mediaItemCount) return null
        if (nextIndex == currentIndex) return null
        return nextIndex
    }
}