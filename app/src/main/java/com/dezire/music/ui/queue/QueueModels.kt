package com.dezire.music.ui.queue

import com.dezire.music.data.model.Song
import com.dezire.music.playback.PlayerUiState
import com.dezire.music.playback.RepeatMode

/*
 * The Play Queue's UI model.
 *
 * Identity is the stable ExoPlayer media-item index ([QueueItem.queueIndex]),
 * never the song id, so two genuine queue entries that reference the same
 * track stay distinct. The model itself is derived from the real
 * [PlayerUiState]; it is never a second, authoritative queue.
 */
data class QueueItem(
    val queueIndex: Int,
    val song: Song,
) {
    val title: String get() = song.title
    val artist: String get() = song.artist
}

enum class QueueRepeatMode { Off, All, One }

/*
 * Everything the queue screen renders. [nowPlaying] is shown once in the
 * header; [upNext] is the real playback tail (what will play next). The
 * current entry is deliberately NOT part of [upNext], so a queue entry can
 * never be drawn twice.
 *
 * [canReorder] is true only when the visible tail is the real linear playback
 * order (shuffle off, repeat off), because only then does a visible move map
 * onto an unambiguous ExoPlayer position move. When false the screen hides the
 * drag handles rather than pretend to reorder a shuffled/wrapped list.
 */
data class QueueState(
    val nowPlaying: QueueItem?,
    val upNext: List<QueueItem>,
    val currentIndex: Int,
    val isFavorite: Boolean,
    val shuffleOn: Boolean,
    val repeatMode: QueueRepeatMode,
    val canReorder: Boolean,
)

/*
 * User intents raised by the queue screen. They carry no playback policy of
 * their own: the integration layer translates each one into a real
 * [com.dezire.music.playback.PlaybackController] call.
 */
sealed interface QueueEvent {
    data object Back : QueueEvent
    data object ToggleFavorite : QueueEvent
    data object ToggleShuffle : QueueEvent
    data object CycleRepeat : QueueEvent
    data object ClearQueue : QueueEvent

    /** Plays the queue entry at the given real ExoPlayer queue index. */
    data class PlayItem(val queueIndex: Int) : QueueEvent

    /** Removes the queue entry at the given real ExoPlayer queue index. */
    data class RemoveItem(val queueIndex: Int) : QueueEvent

    /**
     * Reorders the visible up-next list. Indexes are relative to
     * [QueueState.upNext] (0 = first visible up-next entry).
     */
    data class MoveItem(val fromIndex: Int, val toIndex: Int) : QueueEvent
}

/*
 * Pure mapping between the real [PlayerUiState] and the UI-facing
 * [QueueState], plus the visible-index -> playback-index conversion used by
 * the drag reorder. Kept free of Android/Compose/ExoPlayer code so it is
 * unit-testable on the JVM and so the screen cannot invent its own queue.
 */
object QueueMapping {

    fun toState(ui: PlayerUiState, favoriteIds: Set<Long>): QueueState {
        val now = ui.current?.let { QueueItem(ui.currentIndex, it) }
        val upNext = ui.upcoming.map { QueueItem(it.queueIndex, it.song) }
        val canReorder = !ui.shuffleEnabled &&
            ui.repeatMode == RepeatMode.OFF &&
            upNext.size > 1
        return QueueState(
            nowPlaying = now,
            upNext = upNext,
            currentIndex = ui.currentIndex,
            isFavorite = now != null && now.song.id in favoriteIds,
            shuffleOn = ui.shuffleEnabled,
            repeatMode = when (ui.repeatMode) {
                RepeatMode.OFF -> QueueRepeatMode.Off
                RepeatMode.ALL -> QueueRepeatMode.All
                RepeatMode.ONE -> QueueRepeatMode.One
            },
            canReorder = canReorder,
        )
    }

    /*
     * Maps a position in the visible up-next list to its real ExoPlayer queue
     * index. The visible list is the playback tail (everything after the
     * current track), so the offset is exactly one past the current index.
     * Returns -1 when there is no valid playing index, so callers can reject
     * the move instead of acting on a stale position.
     */
    fun visibleToPlaybackIndex(currentIndex: Int, visibleIndex: Int): Int =
        if (currentIndex < 0 || visibleIndex < 0) -1
        else currentIndex + 1 + visibleIndex

    /*
     * The single (from, to) queue move that realises a visible reorder, or
     * null when the move is invalid or a no-op. A single ExoPlayer
     * moveMediaItem is exact here because the visible list is contiguous with
     * the playback tail, so no intermediate swaps are required.
     */
    fun reorderMove(
        currentIndex: Int,
        fromVisible: Int,
        toVisible: Int,
    ): Pair<Int, Int>? {
        if (fromVisible < 0 || toVisible < 0 || fromVisible == toVisible) return null
        val from = visibleToPlaybackIndex(currentIndex, fromVisible)
        val to = visibleToPlaybackIndex(currentIndex, toVisible)
        return if (from < 0 || to < 0) null else from to to
    }
}
