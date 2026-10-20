package com.dezire.music.playback

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.dezire.music.data.SettingsRepository
import com.dezire.music.data.model.Song
import kotlinx.coroutines.CoroutineScope

/*
 * Crossfade controller - STAGED / audio disabled.
 *
 * A genuine crossfade requires two simultaneously decoding players plus a
 * careful audio-session and volume hand-off at the track boundary. That
 * implementation (a second internal ExoPlayer overlapping the final seconds,
 * equal-power volume ramps and a seek-based handoff) was the most probable
 * source of both the abnormal audio and the intermittent
 * "Unexpected runtime error" (Media3 ERROR_CODE_UNSPECIFIED) seen on some
 * tracks after the playback-settings work, because two players were decoding
 * the same content URI at once and the handoff raced with the main-thread
 * poll loop.
 *
 * Per the repair brief, normal playback takes priority over crossfade:
 *  - the controller now deliberately performs NO overlapping playback and NO
 *    volume mutation - the single ExoPlayer stays at its natural volume the
 *    whole time;
 *  - the public API (isCrossfading / cancel / release) is preserved so every
 *    existing caller keeps compiling and queue/gesture logic is untouched;
 *  - the envelope + state-machine math lives on in [CrossfadeEngine] (still
 *    unit-tested) ready for a verified implementation.
 *
 * The setting remains visible and persisted in the UI; until the overlap is
 * re-enabled it simply has no audio effect. Re-enabling must only happen
 * once normal playback is verified on-device and the overlap path is proven
 * thread-safe against the error handling added in PlaybackController.
 */
class CrossfadeController(
    context: Context,
    primary: ExoPlayer,
    queueProvider: () -> List<Song>,
    scope: CoroutineScope,
    settingsRepository: SettingsRepository? = null
) {

    /** Always false: no overlap can run while crossfade audio is staged off. */
    val isCrossfading: Boolean
        get() = false

    /* No-op: nothing to cancel until a fade can actually start. */
    fun cancel(): Unit = Unit

    /* No-op: there is no secondary player to release. */
    fun release(): Unit = Unit
}