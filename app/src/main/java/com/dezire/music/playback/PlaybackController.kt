package com.dezire.music.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dezire.music.data.MusicRepository
import com.dezire.music.data.SettingsRepository
import com.dezire.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * Repeat modes surfaced to the UI.
 */
enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

/*
 * UI-facing representation of the real player state.
 */
data class PlayerUiState(
    val queue: List<Song> = emptyList(),
    val current: Song? = null,
    val currentIndex: Int = -1,
    val upcoming: List<QueueEntry> = emptyList(),
    val isPlaying: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/*
 * One entry in the upcoming queue. [queueIndex] is the position of the
 * song in the shared playlist (ExoPlayer media-item index), so the queue
 * sheet can play, remove or reorder the exact entry even when the list is
 * displayed in shuffle order rather than playlist order.
 */
data class QueueEntry(
    val song: Song,
    val queueIndex: Int
)

/*
 * Shared playback controller built on a single ExoPlayer instance.
 *
 * The player lives for the whole app process and is shared by every
 * screen, so navigating between tabs never recreates it and playback is
 * not simulated with a timer - position is polled from ExoPlayer.
 *
 * ARCHITECTURE = MINIMAL KNOWN-GOOD PATH (repair status):
 *  - The player is built with the stock `ExoPlayer.Builder` renderers and
 *    the stock `DefaultAudioSink`. No custom renderer factory and no custom
 *    AudioProcessor are in the audio chain.
 *  - Transitions between tracks are ExoPlayer's native gapless behaviour
 *    (same decoder + audio sink). The previous pause-at-end-of-media-item /
 *    delayed-resume hack that faked a non-gapless gap has been REMOVED: it
 *    poked the player at every track boundary and could interfere with error
 *    recovery. The Gapless setting's DataStore value is still persisted and
 *    shown in the UI, but it currently has no separate audio effect.
 *  - Crossfade is STAGED OFF. [CrossfadeController] is an inert stub: no
 *    second player, no volume mutation. The envelope math remains in
 *    [CrossfadeEngine] for a future, verified implementation.
 *  - Volume normalisation is not wired into the audio path. The previous
 *    custom AudioProcessor was removed because its passthrough never
 *    flipped the output buffer, violating the Media3 AudioProcessor contract
 *    and silently starving the AudioTrack - that broke playback for every
 *    processed track. The setting's DataStore value still persists.
 *  - Automatic failed-track recovery is DISABLED while the root cause is
 *    being confirmed on device. After an error the player parks idle; the
 *    user explicitly retrying (play / next) re-prepares via [ensurePrepared].
 *  - Equalizer: [EqualizerManager] stays attached to the audio session but
 *    is created disabled and remains disabled by default.
 *
 * The visible error message comes from [PlaybackErrorClassifier]; the full
 * Media3 exception (code, name, message, complete cause/stack, media item
 * identity and player state) is always logged via logcat under [TAG] by
 * [logPlaybackError], so nothing is ever swallowed silently.
 */
class PlaybackController(
    context: Context,
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository? = null
) {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /*
     * Sleep timer state. [sleepTimerRemainingMs] is null when no timer is
     * active, otherwise the milliseconds left. The countdown runs on the
     * controller (application-scoped) so it keeps ticking across screens.
     */
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

    private var sleepTimerJob: Job? = null

    /*
     * Last playback failure, surfaced as a snackbar. Cleared as soon as
     * playback resumes so a transient blip does not haunt the UI.
     */
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /*
     * Equalizer attached to the shared playback audio session. Kept disabled
     * by default; the UI toggle only has an effect once the lifecycle is
     * verified.
     */
    private val equalizerManager: EqualizerManager = EqualizerManager(
        settingsRepository = settingsRepository,
        scope = playerScope
    )

    /*
     * One plain ExoPlayer with the stock renderers factory and stock audio
     * sink - the same configuration local tracks used before the playback
     * settings work. Construction failures are logged with their full stack
     * and rethrown, never masked.
     */
    val exoPlayer: ExoPlayer = try {
        ExoPlayer.Builder(appContext).build()
    } catch (e: RuntimeException) {
        Log.e(TAG, "ExoPlayer construction failed", e)
        throw e
    }

    val player: Player = exoPlayer

    /*
     * Inert crossfade stub (staged off). cancel()/release() are safe no-ops.
     */
    private val crossfadeController = CrossfadeController(
        context = appContext,
        primary = exoPlayer,
        queueProvider = { _state.value.queue },
        scope = playerScope,
        settingsRepository = settingsRepository
    )

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) _playbackError.value = null
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlayerError(error: PlaybackException) {
                logPlaybackError(error)
                _playbackError.value = describePlaybackError(error)
                updateCurrent()
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                attachEqualizer()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrent()

                /*
                 * Count genuinely played tracks: automatic advance,
                 * repeat, and manual next/previous (reported as seek).
                 * The initial item of a fresh queue is recorded by
                 * setQueue() instead, so the playlist-changed transition
                 * is intentionally ignored to avoid double counting.
                 */
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                ) {
                    _state.value.current?.let { musicRepository.recordPlayed(it) }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateCurrent()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateCurrent()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateCurrent()
            }
        })

        /*
         * Polls the real ExoPlayer position every 500 ms so every screen
         * shares one live clock.
         */
        playerScope.launch {
            while (true) {
                val position = exoPlayer.currentPosition
                val duration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                _state.update {
                    it.copy(positionMs = position, durationMs = duration)
                }
                delay(500)
            }
        }

        attachEqualizer()
    }

    fun getEqualizerManager(): EqualizerManager = equalizerManager

    fun attachEqualizer() {
        try {
            equalizerManager.attachAudioSession(exoPlayer.audioSessionId)
        } catch (e: Exception) {
            equalizerManager.attachAudioSession(android.media.audiofx.AudioEffect.ERROR_BAD_VALUE)
        }
    }

    fun setQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return

        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        applyQueue(songs, safeIndex)
    }

    /*
     * Plays an album in its natural (album) order. The explicit queue order
     * is authoritative here, so any previously enabled shuffle mode is
     * cleared; otherwise ExoPlayer would keep shuffling the new queue.
     */
    fun playAlbum(songs: List<Song>) {
        if (songs.isEmpty()) return
        player.shuffleModeEnabled = false
        applyQueue(songs, 0)
    }

    /*
     * Plays an album with its tracks shuffled. The queue itself is built
     * in shuffled order (rather than relying on ExoPlayer's shuffle mode)
     * so the up-next list and album screen stay consistent with what
     * actually plays.
     */
    fun playAlbumShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        player.shuffleModeEnabled = false
        applyQueue(songs.shuffled(), 0)
    }

    private fun applyQueue(songs: List<Song>, startIndex: Int) {
        crossfadeController.cancel()
        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        deviceQueue(songs)
        player.setMediaItems(songs.map { it.toMediaItem() }, safeIndex, 0L)
        prepareSafely("applyQueue")
        playSafely()
        updateCurrent()

        songs.getOrNull(safeIndex)?.let { musicRepository.recordPlayed(it) }
    }

    fun togglePlayPause() {
        if (player.mediaItemCount == 0) return
        crossfadeController.cancel()
        ensurePrepared()
        if (player.isPlaying) player.pause() else playSafely()
        updateCurrent()
    }

    fun skipToNext() {
        if (player.mediaItemCount == 0) return
        crossfadeController.cancel()
        if (player.hasNextMediaItem() || player.repeatMode != Player.REPEAT_MODE_OFF) {
            player.seekToNextMediaItem()
        }
        ensurePrepared()
        updateCurrent()
    }

    fun skipToPrevious() {
        if (player.mediaItemCount == 0) return
        crossfadeController.cancel()
        if (player.currentPosition > 3_000) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0L)
            player.pause()
        }
        ensurePrepared()
        updateCurrent()
    }

    /*
     * Moves playback to the previous track for the vertical swipe gesture.
     * Unlike [skipToPrevious] this never restarts the current track when it
     * is past the 3s mark: a downward swipe always reveals the previous
     * track (and does nothing when there is none), matching the short-form
     * swipe gesture the Now Playing screen uses.
     */
    fun seekToPreviousTrack() {
        if (player.mediaItemCount == 0) return
        crossfadeController.cancel()
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
        ensurePrepared()
        updateCurrent()
    }

    /*
     * The track that skipToNext() would move to, resolved from the real
     * ExoPlayer timeline (honouring shuffle and repeat) without changing
     * playback. Used to render the incoming Now Playing screen while the
     * user swipes. Returns null when there is no distinct next track.
     */
    fun peekNext(): Song? {
        if (player.mediaItemCount == 0) return null
        val next = player.nextMediaItemIndex
        if (next == C.INDEX_UNSET || next == player.currentMediaItemIndex) return null
        return _state.value.queue.getOrNull(next)
    }

    /*
     * The track that [seekToPreviousTrack] would move to, resolved from the
     * real ExoPlayer timeline without changing playback. Returns null when
     * there is no distinct previous track, so the swipe can safely return
     * the current page to rest.
     */
    fun peekPrevious(): Song? {
        if (player.mediaItemCount == 0) return null
        val previous = player.previousMediaItemIndex
        if (previous == C.INDEX_UNSET || previous == player.currentMediaItemIndex) return null
        return _state.value.queue.getOrNull(previous)
    }

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    /*
     * ExoPlayer parks in STATE_IDLE after a failure. Re-preparing here is
     * what makes "tap play / next after an error" actually recover instead
     * of silently doing nothing. It is never called automatically.
     */
    private fun ensurePrepared() {
        if (player.mediaItemCount > 0 && player.playbackState == Player.STATE_IDLE) {
            prepareSafely("ensurePrepared")
        }
    }

    private fun describePlaybackError(error: PlaybackException): String =
        PlaybackErrorClassifier.userMessage(
            title = _state.value.queue.getOrNull(player.currentMediaItemIndex)?.title,
            errorCode = error.errorCode,
            rawMessage = error.message
        )

    /*
     * Full diagnostic breadcrumb for developers. The snackbar shows the
     * friendly [describePlaybackError]; this log line carries every piece of
     * evidence needed to diagnose the failure without a second repro: the
     * error code and its name, the raw message, the entire cause chain with
     * the full stack trace (via the [error] throwable argument), the
     * affected media item (id + content uri), the player state and whether
     * the player has been released. Nothing is swallowed.
     */
    private fun logPlaybackError(error: PlaybackException) {
        val causeChain = buildString {
            var cause: Throwable? = error.cause
            var depth = 0
            while (cause != null && depth < 6) {
                append("\n    cause[$depth] ${cause.javaClass.simpleName}: ${cause.message}")
                cause = cause.cause
                depth++
            }
        }
        val item = player.currentMediaItem
        Log.e(
            TAG,
            "Player error: code=${error.errorCode} name=${error.errorCodeName} " +
                "message=${error.message} mediaId=${item?.mediaId} " +
                "uri=${item?.localConfiguration?.uri} " +
                "index=${player.currentMediaItemIndex}/${player.mediaItemCount} " +
                "playbackState=${player.playbackState} released=${exoPlayer.isReleased} " +
                "recoveryEnabled=false" + causeChain,
            error
        )
    }

    private fun prepareSafely(from: String) {
        try {
            player.prepare()
        } catch (e: Exception) {
            Log.e(TAG, "prepare() failed (from $from) state=${player.playbackState}", e)
        }
    }

    private fun playSafely() {
        try {
            player.play()
        } catch (e: Exception) {
            Log.e(TAG, "play() failed state=${player.playbackState}", e)
        }
    }

    /*
     * Appends [song] to the shared queue. Duplicate ids are ignored so
     * the same track is not queued twice. If the player was idle the new
     * item is prepared so it can be played without a full restart.
     */
    fun addToQueue(song: Song) {
        if (_state.value.queue.any { it.id == song.id }) return
        val wasEmpty = player.mediaItemCount == 0
        player.addMediaItem(song.toMediaItem())
        _state.update { it.copy(queue = _state.value.queue + song) }
        if (wasEmpty) prepareSafely("addToQueue")
        updateCurrent()
    }

    /*
     * Removes the queue entry at [index] from both the ExoPlayer queue and
     * the mirrored UI queue.
     */
    fun removeFromQueue(index: Int) {
        val queue = _state.value.queue
        if (index !in queue.indices) return
        crossfadeController.cancel()
        player.removeMediaItem(index)
        _state.update { it.copy(queue = queue.toMutableList().also { list -> list.removeAt(index) }) }
        updateCurrent()
    }

    /*
     * Moves a queue entry, keeping the ExoPlayer queue and UI queue in
     * sync.
     */
    fun moveQueueItem(from: Int, to: Int) {
        val queue = _state.value.queue
        if (from !in queue.indices || to !in queue.indices || from == to) return
        crossfadeController.cancel()
        player.moveMediaItem(from, to)
        _state.update {
            it.copy(
                queue = queue.toMutableList().also { list ->
                    list.add(to, list.removeAt(from))
                }
            )
        }
        updateCurrent()
    }

    /*
     * Clears the playback queue. With [keepCurrent] true (the queue screen's
     * default) every track except the one currently playing is removed and
     * playback continues uninterrupted; with false - or when nothing is
     * playing - the whole queue is emptied and the player is reset. The
     * mirrored UI queue and the real ExoPlayer timeline are updated together
     * so the Play Queue list cannot drift out of sync.
     */
    fun clearQueue(keepCurrent: Boolean) {
        crossfadeController.cancel()
        if (player.mediaItemCount == 0) {
            _state.update { it.copy(queue = emptyList()) }
            updateCurrent()
            return
        }

        val currentIndex = player.currentMediaItemIndex
        val canKeep = keepCurrent &&
            currentIndex != C.INDEX_UNSET &&
            currentIndex < player.mediaItemCount
        if (!canKeep) {
            player.clearMediaItems()
            _state.update { it.copy(queue = emptyList()) }
            updateCurrent()
            return
        }

        // Keep only the playing entry: remove everything after it, then
        // everything before it (the current track lands at index 0 and
        // continues playing).
        if (currentIndex + 1 <= player.mediaItemCount - 1) {
            player.removeMediaItems(currentIndex + 1, player.mediaItemCount - 1)
        }
        if (currentIndex > 0) {
            player.removeMediaItems(0, currentIndex - 1)
        }
        val kept = _state.value.queue.getOrNull(currentIndex)
        _state.update { it.copy(queue = kept?.let(::listOf).orEmpty()) }
        updateCurrent()
    }

    /*
     * Jumps playback to the queue entry at [index] and starts playing it.
     */
    fun playQueueItem(index: Int) {
        if (index !in _state.value.queue.indices) return
        crossfadeController.cancel()
        player.seekTo(index, 0L)
        ensurePrepared()
        playSafely()
        updateCurrent()
    }

    /*
     * Starts (or restarts) a real sleep timer. Only one timer can run at a
     * time; starting a new one cancels the previous countdown.
     */
    fun startSleepTimer(durationMs: Long) {
        if (durationMs <= 0) return
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMs.value = durationMs
        sleepTimerJob = playerScope.launch {
            val endTime = android.os.SystemClock.elapsedRealtime() + durationMs
            while (true) {
                val remaining = endTime - android.os.SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                _sleepTimerRemainingMs.value = remaining
                delay(250L)
            }
            _sleepTimerRemainingMs.value = null
            if (player.isPlaying) player.pause()
            updateCurrent()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = null
    }

    /*
     * Live playback position straight from ExoPlayer, for consumers (lyrics
     * sync) that need finer granularity than the 500 ms [state] poll. Does
     * not touch the player, so it never introduces a second instance.
     */
    fun currentPositionMs(): Long = exoPlayer.currentPosition

    fun seekTo(fraction: Float) {
        val duration = player.duration
        if (duration <= 0 || fraction.isNaN()) return
        crossfadeController.cancel()
        player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        _state.update { it.copy(positionMs = player.currentPosition) }
    }

    fun toggleShuffle() {
        crossfadeController.cancel()
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        crossfadeController.cancel()
        player.repeatMode = when (_state.value.repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_ALL
            RepeatMode.ALL -> Player.REPEAT_MODE_ONE
            RepeatMode.ONE -> Player.REPEAT_MODE_OFF
        }
    }

    fun release() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        crossfadeController.release()
        equalizerManager.release()
        playerScope.coroutineContext.cancel()
        player.release()
    }

    private fun deviceQueue(songs: List<Song>) {
        _state.update { it.copy(queue = songs) }
        updateCurrent()
    }

    private fun updateCurrent() {
        val index = player.currentMediaItemIndex
        val song = _state.value.queue.getOrNull(index)
        _state.update {
            it.copy(
                current = song,
                currentIndex = index,
                upcoming = computeUpcoming(),
                isPlaying = player.isPlaying,
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = toDezireMode(player.repeatMode)
            )
        }
    }

    /*
     * Computes the tracks that will play after the current one, in their
     * real playback order. It walks the ExoPlayer timeline using
     * [Timeline.getNextWindowIndex], which honours both the active shuffle
     * order and repeat mode, so the queue matches what the player will
     * actually do next. Repeat-one is treated as no wrap (only the tracks
     * that follow the current one are listed). The current track is never
     * listed unless it is a genuine, separate playlist entry, and the walk
     * stops as soon as it would revisit an index so a repeating queue is
     * not duplicated.
     */
    private fun computeUpcoming(): List<QueueEntry> {
        val queue = _state.value.queue
        if (queue.size < 2) return emptyList()

        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()

        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET || currentIndex !in queue.indices) return emptyList()

        val shuffleEnabled = player.shuffleModeEnabled
        val repeatMode = if (player.repeatMode == Player.REPEAT_MODE_ALL) {
            Player.REPEAT_MODE_ALL
        } else {
            Player.REPEAT_MODE_OFF
        }

        val upcoming = ArrayList<QueueEntry>(queue.size - 1)
        val visited = HashSet<Int>()
        visited.add(currentIndex)

        var index = currentIndex
        while (upcoming.size < queue.size - 1) {
            val next = timeline.getNextWindowIndex(index, repeatMode, shuffleEnabled)
            if (next == C.INDEX_UNSET || !visited.add(next)) break
            val song = queue.getOrNull(next) ?: break
            upcoming.add(QueueEntry(song, next))
            index = next
        }
        return upcoming
    }

    private fun toDezireMode(repeatMode: Int): RepeatMode = when (repeatMode) {
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        else -> RepeatMode.OFF
    }

    companion object {
        private const val TAG = "PlaybackController"
    }
}

/*
 * Builds the ExoPlayer media item for a track using its MediaStore
 * content:// Uri.
 */
private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .build()
    )
    .build()