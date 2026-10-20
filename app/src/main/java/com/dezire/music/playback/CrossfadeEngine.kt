package com.dezire.music.playback

import com.dezire.music.data.PlaybackDefaults
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/*
 * Pure state machine + envelope math for crossfading, kept free of any
 * Android/media3 types so every playback decision is unit-testable.
 *
 * A true crossfade needs two simultaneously playing decoders, so this
 * engine only decides *when* to start the overlap, produces the per-layer
 * volume envelopes, and knows when a transition must be cancelled. The
 * audio side (the second ExoPlayer) lives in [CrossfadeController].
 *
 * REPAIR STATUS: [CrossfadeController] is currently STAGED with the overlap
 * audio disabled (see its class doc), so the engine is not being driven by
 * the player. It is retained purely as tested math for the re-enabled
 * implementation - these tests document the wanted envelope behaviour.
 *
 * Equal-power crossfade curve: outgoing = cos(t), incoming = sin(t),
 * t in [0, pi/2], so overall perceived loudness stays roughly constant
 * instead of dipping in the middle as a linear fade would.
 */
class CrossfadeEngine {

    @Volatile
    var enabled: Boolean = PlaybackDefaults.CROSSFADE_ENABLED

    @Volatile
    var durationMs: Long = PlaybackDefaults.CROSSFADE_DURATION_SECONDS * 1000L

    @Volatile
    var baseVolume: Float = 1f

    val active: Boolean
        get() = session != null

    private var session: Session? = null

    data class Session(
        val outgoingIndex: Int,
        val nextIndex: Int,
        val trackDurationMs: Long,
        /* Position (ms) in the outgoing track where the fade starts. */
        val startPositionMs: Long
    )

    /*
     * Equal-power volume pair at [elapsedMs] into the crossfade. The
     * outgoing layer starts at [baseVolume] and the incoming layer ends at
     * [baseVolume]; at the end the outgoing layer is silent.
     */
    fun volumes(elapsedMs: Long): Pair<Float, Float> {
        val base = baseVolume
        if (durationMs <= 0L) return 0f to base
        val t = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val theta = (t * PI / 2.0).toFloat()
        return (cos(theta) * base) to (sin(theta) * base)
    }

    /*
     * True when a crossfade should begin: enabled, not already running,
     * currently playing, a real next item exists, and the outgoing track
     * is within the fade window of its end (with a small safety margin so
     * the fade completes before the track's natural boundary).
     */
    fun shouldStart(
        positionMs: Long,
        trackDurationMs: Long,
        isPlaying: Boolean,
        hasNext: Boolean,
        nextIndex: Int,
        currentIndex: Int
    ): Boolean {
        if (session != null) return false
        if (!enabled) return false
        if (!isPlaying) return false
        if (!hasNext) return false
        if (nextIndex < 0 || nextIndex == currentIndex) return false
        if (trackDurationMs <= 0L) return false
        if (durationMs <= 0L) return false
        val fadeWindow = durationMs + START_MARGIN_MS
        return positionMs >= trackDurationMs - fadeWindow &&
            positionMs < trackDurationMs - HANDOFF_EPS_MS
    }

    /*
     * Begins a crossfade for the described transition and returns the
     * active session. Returns null when the fade cannot legally start.
     */
    fun start(
        outgoingIndex: Int,
        nextIndex: Int,
        trackDurationMs: Long,
        isPlaying: Boolean,
        hasNext: Boolean
    ): Session? {
        if (session != null) return null
        if (!enabled || !isPlaying || !hasNext || nextIndex < 0) return null
        if (trackDurationMs <= 0L || durationMs <= 0L) return null
        val startPositionMs = (trackDurationMs - durationMs - START_MARGIN_MS).coerceAtLeast(0L)
        return Session(
            outgoingIndex = outgoingIndex,
            nextIndex = nextIndex,
            trackDurationMs = trackDurationMs,
            startPositionMs = startPositionMs
        ).also { session = it }
    }

    /*
     * True when the overlap finished cleanly and the audio layer should
     * hand the incoming audio over to the primary player:
     *  - the primary auto-advanced to the incoming item, or
     *  - the outgoing track reached its natural end (gapless-off pauses
     *    right at the boundary, which is still a clean hand-off point).
     */
    fun shouldHandoff(
        positionMs: Long,
        currentIndex: Int
    ): Boolean {
        val s = session ?: return false
        if (currentIndex == s.nextIndex) return true
        return positionMs >= s.trackDurationMs - HANDOFF_EPS_MS
    }

    /*
     * True when a running crossfade must be aborted: the setting was
     * switched off, the queue reordered/shuffled, the primary moved away
     * from the outgoing track by anything other than the expected
     * advance, or the user paused/seeking backwards.
     *
     * Note: pausing close to the natural end is *not* a cancel - the
     * caller checks [shouldHandoff] first and will treat that as the
     * clean boundary.
     */
    fun shouldCancel(
        positionMs: Long,
        isPlaying: Boolean,
        currentIndex: Int,
        nextIndex: Int,
        queueChanged: Boolean
    ): Boolean {
        val s = session ?: return true
        if (!enabled) return true
        if (queueChanged) return true
        if (nextIndex != s.nextIndex) return true
        if (currentIndex != s.outgoingIndex) return true
        if (!isPlaying) return positionMs < s.trackDurationMs - 300L
        if (positionMs < s.startPositionMs - 250L) return true
        return false
    }

    /*
     * Completes cleanly (hand-off happened). The audio layer restores the
     * primary volume and stops the secondary.
     */
    fun onTransitionComplete(): Session? {
        val s = session
        session = null
        return s
    }

    /*
     * Cancels an ongoing crossfade. The audio layer must restore
     * [baseVolume] on the primary and stop the secondary player.
     */
    fun cancel(): Session? {
        val s = session
        session = null
        return s
    }

    /** Clamps a user-intended duration (seconds) into 1..12. */
    fun sanitizeDurationSeconds(seconds: Int): Int =
        seconds.coerceIn(
            PlaybackDefaults.MIN_CROSSFADE_SECONDS,
            PlaybackDefaults.MAX_CROSSFADE_SECONDS
        )

    fun sanitizeDurationMs(seconds: Int): Long = sanitizeDurationSeconds(seconds) * 1000L

    companion object {
        /* Lead so the fade completes just before the track's natural end. */
        const val START_MARGIN_MS = 120L

        /* Tolerance when deciding the outgoing track reached its end. */
        const val HANDOFF_EPS_MS = 120L
    }
}