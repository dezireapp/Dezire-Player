package com.dezire.music.playback

import android.net.StubUri
import android.net.Uri
import com.dezire.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Pure queue-decision tests. These run on the JVM (no ExoPlayer) because the
 * logic under test is a plain functions-only object pulled out of the
 * controller so the queue screen can depend on it without a player instance.
 */
class QueueLogicTest {

    @Test
    fun positionLabel_isOneBased() {
        assertEquals("1 of 4", QueueLogic.positionLabel(0, 4))
        assertEquals("3 of 4", QueueLogic.positionLabel(2, 4))
    }

    @Test
    fun positionLabel_fallsBackToZeroBeforeReady() {
        assertEquals("0 of 4", QueueLogic.positionLabel(-1, 4))
        assertEquals("0 of 4", QueueLogic.positionLabel(5, 4))
        assertEquals("0 of 0", QueueLogic.positionLabel(-1, 0))
    }

    @Test
    fun isCurrent_matchesPlayingIndex() {
        assertFalse(QueueLogic.isCurrent(0, -1))
        assertTrue(QueueLogic.isCurrent(2, 2))
        assertFalse(QueueLogic.isCurrent(2, 3))
    }

    @Test
    fun keptIndexAfterClear_keepsOnlyWhenCurrentExists() {
        assertEquals(2, QueueLogic.keptIndexAfterClear(2, 5, true))
        assertNull(QueueLogic.keptIndexAfterClear(2, 5, false))
        assertNull(QueueLogic.keptIndexAfterClear(-1, 5, true))
        assertNull(QueueLogic.keptIndexAfterClear(2, 0, true))
        assertNull(QueueLogic.keptIndexAfterClear(5, 2, true))
    }

    /*
     * Drag-and-drop reorder commit helpers: given the real queue and the new
     * tail order the screen settled on, derive the exact (from, to) moves.
     */

    @Test
    fun movesToReorderUpcoming_singleNeighbourSwap() {
        val queue = songs("A", "B", "C", "D") // current = A at index 0, tail B C D
        val moves = QueueLogic.movesToReorderUpcoming(queue, 0, songs("C", "B", "D"))
        assertEquals(listOf(2 to 1), moves)
    }

    @Test
    fun movesToReorderUpcoming_movesTailEntryToFront() {
        // queue: A(now) B C D E - drag E to the front of the tail.
        val queue = songs("A", "B", "C", "D", "E")
        val moves = QueueLogic.movesToReorderUpcoming(queue, 0, songs("E", "B", "C", "D"))
        assertEquals(listOf(4 to 1), moves)
    }

    @Test
    fun movesToReorderUpcoming_movesTailEntryToEnd() {
        // queue: A(now) B C D E - drag B to the end of the tail: B C D E -> C D E B
        val queue = songs("A", "B", "C", "D", "E")
        val moves = QueueLogic.movesToReorderUpcoming(queue, 0, songs("C", "D", "E", "B"))
        assertMovesProduce(queue, moves, songs("A", "C", "D", "E", "B"))
    }

    @Test
    fun movesToReorderUpcoming_rotatesWholeTail() {
        // queue: A(now) B C D - drag D to front: B C D -> D B C
        val queue = songs("A", "B", "C", "D")
        val moves = QueueLogic.movesToReorderUpcoming(queue, 0, songs("D", "B", "C"))
        assertEquals(listOf(3 to 1), moves)
    }

    @Test
    fun movesToReorderUpcoming_currentNotFirstStillOnlyMovesTail() {
        // queue: A B(current) C D E; head is A B, tail C D E -> E C D
        val queue = songs("A", "B", "C", "D", "E")
        val moves = QueueLogic.movesToReorderUpcoming(queue, 1, songs("E", "C", "D"))
        assertEquals(listOf(4 to 2), moves)
    }

    @Test
    fun movesToReorderUpcoming_multipleMoves() {
        // queue: A(now) B C D; rotate tail C D B -> needs in-place swaps.
        val queue = songs("A", "B", "C", "D")
        val moves = QueueLogic.movesToReorderUpcoming(queue, 0, songs("C", "D", "B"))
        assertEquals(listOf(2 to 1, 3 to 2), moves)
    }

    @Test
    fun movesToReorderUpcoming_noOpReturnsNull() {
        val queue = songs("A", "B", "C", "D")
        assertNull(QueueLogic.movesToReorderUpcoming(queue, 0, songs("B", "C", "D")))
        assertNull(QueueLogic.movesToReorderUpcoming(queue, 2, songs("D")))
    }

    @Test
    fun movesToReorderUpcoming_rejectsMismatchingTail() {
        val queue = songs("A", "B", "C", "D")
        // Right size but contains a song that is not in the queue.
        assertNull(QueueLogic.movesToReorderUpcoming(queue, 0, songs("X", "C", "D")))
        // Wrong size.
        assertNull(QueueLogic.movesToReorderUpcoming(queue, 0, songs("B", "C")))
    }

    @Test
    fun movesToReorderUpcoming_requiresValidPlayingIndex() {
        val queue = songs("A", "B", "C", "D")
        assertNull(QueueLogic.movesToReorderUpcoming(queue, -1, songs("B", "C", "D")))
        assertNull(QueueLogic.movesToReorderUpcoming(queue, 9, songs("B", "C", "D")))
    }

    /*
     * Entry-identity reorder. The screen settles a list of QueueEntry values
     * (each carrying its stable queue index), so the moves must be derived
     * from entry identity, not the song id.
     */

    @Test
    fun movesToReorderEntries_movesToFrontOfTail() {
        // queue: A(now) B C D E - drag E to the front of the tail.
        val queue = songs("A", "B", "C", "D", "E")
        val desired = listOf(entry(queue, 4, "E"), entry(queue, 1, "B"), entry(queue, 2, "C"), entry(queue, 3, "D"))
        assertEquals(listOf(4 to 1), QueueLogic.movesToReorderUpcomingEntries(queue, 0, desired))
    }

    @Test
    fun movesToReorderEntries_movesToEndOfTail() {
        // queue: A(now) B C D E - drag B to the end of the tail.
        val queue = songs("A", "B", "C", "D", "E")
        val desired = listOf(entry(queue, 2, "C"), entry(queue, 3, "D"), entry(queue, 4, "E"), entry(queue, 1, "B"))
        val moves = QueueLogic.movesToReorderUpcomingEntries(queue, 0, desired)
        assertMovesProduceEntries(queue, moves, desired)
    }

    @Test
    fun movesToReorderEntries_reordersMultipleItems() {
        // queue: A(now) B C D E -> E C B D (reverse the first three tail items)
        val queue = songs("A", "B", "C", "D", "E")
        val desired = listOf(entry(queue, 4, "E"), entry(queue, 2, "C"), entry(queue, 1, "B"), entry(queue, 3, "D"))
        val moves = QueueLogic.movesToReorderUpcomingEntries(queue, 0, desired)
        assertMovesProduceEntries(queue, moves, desired)
    }

    @Test
    fun movesToReorderEntries_preservesCurrentSong() {
        // current B at index 1; head A B must be untouched, only C D E move.
        val queue = songs("A", "B", "C", "D", "E")
        val desired = listOf(entry(queue, 4, "E"), entry(queue, 2, "C"), entry(queue, 3, "D"))
        val moves = QueueLogic.movesToReorderUpcomingEntries(queue, 1, desired)!!
        assertEquals(listOf(4 to 2), moves)
        // The head is never part of any move.
        assertTrue(moves.all { it.first >= 2 && it.second >= 2 })
    }

    @Test
    fun movesToReorderEntries_rejectsInvalidIndices() {
        val queue = songs("A", "B", "C", "D")
        // Head index 0 is not part of the tail and must be rejected.
        assertNull(
            QueueLogic.movesToReorderUpcomingEntries(
                queue, 0,
                listOf(entry(queue, 0, "A"), entry(queue, 1, "B"), entry(queue, 2, "C")),
            ),
        )
        // Out-of-range queue index.
        assertNull(
            QueueLogic.movesToReorderUpcomingEntries(
                queue, 0,
                listOf(entry(queue, 1, "B"), entry(queue, 2, "C"), entry(queue, 9, "A")),
            ),
        )
        // Duplicate queue index in the desired tail.
        assertNull(
            QueueLogic.movesToReorderUpcomingEntries(
                queue, 0,
                listOf(entry(queue, 1, "B"), entry(queue, 1, "B"), entry(queue, 2, "C")),
            ),
        )
    }

    @Test
    fun movesToReorderEntries_currentNotFirstStillOnlyMovesTail() {
        val queue = songs("A", "B", "C", "D", "E")
        val desired = listOf(entry(queue, 4, "E"), entry(queue, 2, "C"), entry(queue, 3, "D"))
        assertEquals(listOf(4 to 2), QueueLogic.movesToReorderUpcomingEntries(queue, 1, desired))
    }

    @Test
    fun movesToReorderEntries_emptyAndSingleItemQueues() {
        // Empty queue.
        assertNull(QueueLogic.movesToReorderUpcomingEntries(emptyList(), 0, emptyList()))
        // Single item: no tail exists.
        val single = songs("A")
        assertNull(QueueLogic.movesToReorderUpcomingEntries(single, 0, emptyList()))
    }

    @Test
    fun movesToReorderEntries_noOpReturnsNull() {
        val queue = songs("A", "B", "C", "D")
        val desired = listOf(entry(queue, 1, "B"), entry(queue, 2, "C"), entry(queue, 3, "D"))
        // The tail already matches, so there is nothing to commit. This is
        // what stops a drop from applying the same reorder twice.
        assertNull(QueueLogic.movesToReorderUpcomingEntries(queue, 0, desired))
    }

    @Test
    fun movesToReorderEntries_duplicateSongIdsAreSeparateEntries() {
        // Two genuine queue entries for the same track (same id, different
        // title), plus distinct neighbours.
        val q = listOf(
            song(id = 1L, title = "Now"),
            song(id = 2L, title = "Dup-first"),
            song(id = 3L, title = "Middle"),
            song(id = 2L, title = "Dup-second"),
        )
        // Move the second duplicate (queue index 3) to the front of the tail.
        val desired = listOf(
            QueueEntry(q[3], 3),
            QueueEntry(q[1], 1),
            QueueEntry(q[2], 2),
        )
        assertEquals(listOf(3 to 1), QueueLogic.movesToReorderUpcomingEntries(q, 0, desired))
    }

    /*
     * The list the screen renders: the current entry once, followed by every
     * upcoming entry once. Song identity alone is not enough (duplicates), so
     * the result is keyed by distinct queue indices.
     */

    @Test
    fun displayEntries_currentFirstThenUpcoming() {
        val queue = songs("A", "B", "C", "D")
        val upcoming = listOf(
            QueueEntry(queue[1], 1),
            QueueEntry(queue[2], 2),
            QueueEntry(queue[3], 3),
        )
        val display = QueueLogic.displayEntries(queue, 0, upcoming)
        assertEquals(listOf(0, 1, 2, 3), display.map { it.queueIndex })
    }

    @Test
    fun displayEntries_neverRepeatsAQueueIndex() {
        val queue = songs("A", "B", "C")
        // The upcoming walk accidentally still contains the current entry.
        val upcoming = listOf(
            QueueEntry(queue[0], 0),
            QueueEntry(queue[1], 1),
            QueueEntry(queue[2], 2),
        )
        val display = QueueLogic.displayEntries(queue, 0, upcoming)
        assertEquals(listOf(0, 1, 2), display.map { it.queueIndex })
        assertEquals(display.size, display.map { it.queueIndex }.toSet().size)
    }

    @Test
    fun displayEntries_keepsDistinctEntriesForSameSong() {
        val q = listOf(
            song(id = 1L, title = "Now"),
            song(id = 2L, title = "Track"),
            song(id = 2L, title = "Track"),
        )
        val upcoming = listOf(QueueEntry(q[1], 1), QueueEntry(q[2], 2))
        val display = QueueLogic.displayEntries(q, 0, upcoming)
        assertEquals(listOf(0, 1, 2), display.map { it.queueIndex })
    }

    @Test
    fun displayEntries_withoutCurrentReturnsUpcoming() {
        val queue = songs("A", "B", "C")
        val upcoming = listOf(QueueEntry(queue[1], 1), QueueEntry(queue[2], 2))
        assertEquals(upcoming, QueueLogic.displayEntries(queue, -1, upcoming))
    }

    /*
     * Songs are built with a StubUri (see android/net/StubUri.kt): the JVM
     * unit-test runtime cannot produce a real Uri, and these tests only read
     * Song.id. Song's data-class equals/hashCode are never exercised here.
     */
    private fun songs(vararg letters: String): List<Song> =
        letters.mapIndexed { _, letter ->
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

    private fun entry(queue: List<Song>, queueIndex: Int, title: String): QueueEntry =
        QueueEntry(queue.first { it.title == title }, queueIndex)

    /*
     * Applies the produced (from, to) moves exactly as PlaybackController
     * would (sequential, index-exact) and asserts the final order matches
     * the desired songs - the invariant the screen actually cares about.
     */
    private fun assertMovesProduce(
        queue: List<Song>,
        moves: List<Pair<Int, Int>>?,
        expected: List<Song>,
    ) {
        assertTrue(moves != null && moves.isNotEmpty())
        val working = queue.toMutableList()
        val settled = checkNotNull(moves)
        for ((from, to) in settled) {
            assertTrue(from in working.indices && to in working.indices)
            working.add(to, working.removeAt(from))
        }
        assertEquals(expected.map { it.id }, working.map { it.id })
    }

    private fun assertMovesProduceEntries(
        queue: List<Song>,
        moves: List<Pair<Int, Int>>?,
        desiredTail: List<QueueEntry>,
    ) {
        val currentIndex = 0
        val expected = queue
            .subList(0, currentIndex + 1)
            .map { it.id } +
            desiredTail.map { it.song.id }
        assertTrue(moves != null && moves.isNotEmpty())
        val working = queue.toMutableList()
        val settled = checkNotNull(moves)
        for ((from, to) in settled) {
            assertTrue(from in working.indices && to in working.indices)
            working.add(to, working.removeAt(from))
        }
        assertEquals(expected, working.map { it.id })
    }
}
