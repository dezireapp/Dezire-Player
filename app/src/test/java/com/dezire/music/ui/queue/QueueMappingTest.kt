package com.dezire.music.ui.queue

import android.net.StubUri
import com.dezire.music.data.model.Song
import com.dezire.music.playback.PlayerUiState
import com.dezire.music.playback.QueueEntry
import com.dezire.music.playback.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Pure JVM tests for the Play Queue mapping. These cover the visible-index ->
 * playback-index conversion the drag reorder depends on, and the derivation of
 * the UI QueueState from the real PlayerUiState.
 */
class QueueMappingTest {

    // ---- visible -> playback index conversion ---------------------------

    @Test
    fun visibleToPlaybackIndex_offsetsPastCurrent() {
        assertEquals(1, QueueMapping.visibleToPlaybackIndex(0, 0))
        assertEquals(4, QueueMapping.visibleToPlaybackIndex(0, 3))
        assertEquals(3, QueueMapping.visibleToPlaybackIndex(1, 1))
    }

    @Test
    fun visibleToPlaybackIndex_rejectsInvalid() {
        assertEquals(-1, QueueMapping.visibleToPlaybackIndex(-1, 0))
        assertEquals(-1, QueueMapping.visibleToPlaybackIndex(0, -1))
    }

    @Test
    fun reorderMove_beginning() {
        // current at 0; move visible tail index 2 (playback 3) to the front.
        assertEquals(3 to 1, QueueMapping.reorderMove(0, 2, 0))
    }

    @Test
    fun reorderMove_end() {
        // current at 0; move visible tail index 0 (playback 1) to the end (2).
        assertEquals(1 to 3, QueueMapping.reorderMove(0, 0, 2))
    }

    @Test
    fun reorderMove_preservesCurrentByOnlyEverMovingTheTail() {
        // current at index 2: head 0..2 must never appear in a move.
        val move = QueueMapping.reorderMove(currentIndex = 2, fromVisible = 1, toVisible = 0)
        val (from, to) = checkNotNull(move)
        assertTrue(from > 2 && to > 2)
    }

    @Test
    fun reorderMove_noOpAndInvalid() {
        assertNull(QueueMapping.reorderMove(0, 1, 1))
        assertNull(QueueMapping.reorderMove(0, -1, 2))
        assertNull(QueueMapping.reorderMove(0, 2, -1))
        assertNull(QueueMapping.reorderMove(-1, 0, 1))
    }

    // ---- state derivation -----------------------------------------------

    @Test
    fun toState_mapsCurrentAndTail() {
        val queue = songs("A", "B", "C", "D")
        val ui = PlayerUiState(
            queue = queue,
            current = queue[1],
            currentIndex = 1,
            upcoming = listOf(QueueEntry(queue[2], 2), QueueEntry(queue[3], 3)),
        )
        val state = QueueMapping.toState(ui, emptySet())
        assertEquals(1, state.nowPlaying?.queueIndex)
        assertEquals(listOf(2, 3), state.upNext.map { it.queueIndex })
    }

    @Test
    fun toState_neverDuplicatesCurrentInUpNext() {
        val queue = songs("A", "B", "C")
        val ui = PlayerUiState(
            queue = queue,
            current = queue[0],
            currentIndex = 0,
            upcoming = listOf(QueueEntry(queue[1], 1), QueueEntry(queue[2], 2)),
        )
        val state = QueueMapping.toState(ui, emptySet())
        assertFalse(state.upNext.any { it.queueIndex == state.nowPlaying?.queueIndex })
    }

    @Test
    fun toState_favoriteFollowsTheRealFavoriteIds() {
        val queue = songs("A", "B")
        val ui = PlayerUiState(queue = queue, current = queue[0], currentIndex = 0)
        assertTrue(QueueMapping.toState(ui, setOf(queue[0].id)).isFavorite)
        assertFalse(QueueMapping.toState(ui, setOf(999L)).isFavorite)
    }

    @Test
    fun toState_repeatModeMapping() {
        val queue = songs("A", "B")
        val ui = PlayerUiState(queue = queue, current = queue[0], currentIndex = 0)
        assertEquals(
            QueueRepeatMode.Off,
            QueueMapping.toState(ui.copy(repeatMode = RepeatMode.OFF), emptySet()).repeatMode,
        )
        assertEquals(
            QueueRepeatMode.All,
            QueueMapping.toState(ui.copy(repeatMode = RepeatMode.ALL), emptySet()).repeatMode,
        )
        assertEquals(
            QueueRepeatMode.One,
            QueueMapping.toState(ui.copy(repeatMode = RepeatMode.ONE), emptySet()).repeatMode,
        )
    }

    @Test
    fun toState_canReorderOnlyForLinearTail() {
        val queue = songs("A", "B", "C")
        val tail = listOf(QueueEntry(queue[1], 1), QueueEntry(queue[2], 2))
        val base = PlayerUiState(queue = queue, current = queue[0], currentIndex = 0, upcoming = tail)
        assertTrue(QueueMapping.toState(base, emptySet()).canReorder)
        assertFalse(QueueMapping.toState(base.copy(shuffleEnabled = true), emptySet()).canReorder)
        assertFalse(
            QueueMapping.toState(base.copy(repeatMode = RepeatMode.ALL), emptySet()).canReorder,
        )
        // A single-entry tail has nothing to reorder.
        assertFalse(
            QueueMapping.toState(
                base.copy(upcoming = listOf(QueueEntry(queue[1], 1))),
                emptySet(),
            ).canReorder,
        )
    }

    @Test
    fun toState_duplicateSongIdsRemainSeparateEntries() {
        val queue = listOf(
            song(1L, "Now"),
            song(2L, "Dup"),
            song(3L, "Mid"),
            song(2L, "Dup"),
        )
        val ui = PlayerUiState(
            queue = queue,
            current = queue[0],
            currentIndex = 0,
            upcoming = listOf(
                QueueEntry(queue[1], 1),
                QueueEntry(queue[2], 2),
                QueueEntry(queue[3], 3),
            ),
        )
        val state = QueueMapping.toState(ui, emptySet())
        assertEquals(listOf(1, 2, 3), state.upNext.map { it.queueIndex })
    }

    // ---- helpers --------------------------------------------------------

    private fun songs(vararg letters: String): List<Song> =
        letters.map { letter ->
            Song(
                id = (letter[0].code - 'A'.code).toLong(),
                title = letter,
                artist = "Artist $letter",
                album = "Album",
                albumId = 1L,
                durationMs = 1000L,
                uri = StubUri.instance,
            )
        }

    private fun song(id: Long, title: String): Song = Song(
        id = id,
        title = title,
        artist = "Artist $title",
        album = "Album",
        albumId = 1L,
        durationMs = 1000L,
        uri = StubUri.instance,
    )
}
