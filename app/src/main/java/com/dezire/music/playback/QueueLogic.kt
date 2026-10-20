package com.dezire.music.playback

import com.dezire.music.data.model.Song

/*
 * Pure queue decisions extracted so the Play Queue screen logic can be
 * unit-tested without an ExoPlayer instance. The screen and the controller
 * both read these, keeping their behaviour in lockstep.
 */
object QueueLogic {

    /*
     * "X of Y" label shown in the queue header. Returns "0 of Y" when there
     * is no valid current index yet.
     */
    fun positionLabel(currentIndex: Int, queueSize: Int): String =
        if (queueSize <= 0 || currentIndex !in 0 until queueSize) {
            "0 of $queueSize"
        } else {
            "${currentIndex + 1} of $queueSize"
        }

    /** True when [queueIndex] is the entry currently playing. */
    fun isCurrent(queueIndex: Int, currentIndex: Int): Boolean =
        currentIndex >= 0 && queueIndex == currentIndex

    /*
     * Which single queue index survives a "clear queue" that keeps the
     * current track playing. Null means nothing survives (either clearing
     * everything, or there was no current track to keep).
     */
    fun keptIndexAfterClear(currentIndex: Int, queueSize: Int, keepCurrent: Boolean): Int? =
        if (keepCurrent && queueSize > 0 && currentIndex in 0 until queueSize) {
            currentIndex
        } else {
            null
        }

    /*
     * The rows the Play Queue screen should draw, in playback order: the
     * currently playing entry first, followed by the real upcoming entries.
     * The screen's header only *describes* the current track; the list is the
     * single place a queue entry is rendered, so nothing can appear twice.
     *
     * Identity is the stable [QueueEntry.queueIndex] (the ExoPlayer media-item
     * index), never the song id, so two genuine queue entries for the same
     * track stay distinct. A defensive de-duplication by queue index means a
     * transient overlap between the current track and the upcoming walk can
     * never re-render the same queue index.
     */
    fun displayEntries(
        queue: List<Song>,
        currentIndex: Int,
        upcoming: List<QueueEntry>,
    ): List<QueueEntry> {
        val current = queue.getOrNull(currentIndex)
            ?: return upcoming.distinctBy { it.queueIndex }
        val entries = ArrayList<QueueEntry>(upcoming.size + 1)
        val seen = HashSet<Int>(upcoming.size + 1)
        entries.add(QueueEntry(current, currentIndex))
        seen.add(currentIndex)
        for (entry in upcoming) {
            if (seen.add(entry.queueIndex)) entries.add(entry)
        }
        return entries
    }

    /*
     * The minimal sequence of [PlaybackController.moveQueueItem] (from, to)
     * pairs that turns [currentQueue] into the queue whose tail (everything
     * after [currentIndex]) is exactly [desiredTail]. Only the tail is ever
     * touched, so the currently playing track stays where it is and keeps
     * playing; the head (index 0..[currentIndex]) is required to be
     * unchanged.
     *
     * The pairs are computed against the same mutable working copy that the
     * caller re-applies with each move, so executing them in order is always
     * index-exact. Returns null when [currentQueue] cannot be reached (no
     * valid playing index, a tail that does not match the current tail, or a
     * reorder that changes nothing).
     */
    fun movesToReorderUpcoming(
        currentQueue: List<Song>,
        currentIndex: Int,
        desiredTail: List<Song>,
    ): List<Pair<Int, Int>>? {
        if (currentIndex < 0 || currentIndex >= currentQueue.size) return null
        val tailStart = currentIndex + 1
        if (desiredTail.size != currentQueue.size - tailStart) return null

        val desired = ArrayList<Song>(currentQueue.size).apply {
            addAll(currentQueue.subList(0, tailStart))
            addAll(desiredTail)
        }
        if (desired.size != currentQueue.size) return null
        if (desired.map(Song::id).sorted().toSet() != currentQueue.map(Song::id).sorted().toSet()) return null

        return movesToReorder(
            tokens = currentQueue.map { it.id },
            desiredTokens = desired.map { it.id },
            tailStart = tailStart,
        )
    }

    /*
     * Entry-identity variant of [movesToReorderUpcoming]. The screen works
     * with [QueueEntry]s (each carrying its own stable [QueueEntry.queueIndex]),
     * so reordering must match entries by that stable identity rather than by
     * the song id. Two separate queue entries can reference the same [Song]
     * (the same track queued twice); matching by song id alone would then move
     * the wrong row. Using each entry's queue index keeps physical queue
     * occurrences distinct while still producing plain (from, to) position
     * moves that the real queue can apply in order.
     *
     * The name is distinct (not an overload) because List<Song> and
     * List<QueueEntry> both erase to List at the JVM level.
     */
    fun movesToReorderUpcomingEntries(
        currentQueue: List<Song>,
        currentIndex: Int,
        desiredEntries: List<QueueEntry>,
    ): List<Pair<Int, Int>>? {
        if (currentIndex < 0 || currentIndex >= currentQueue.size) return null
        val tailStart = currentIndex + 1
        if (desiredEntries.size != currentQueue.size - tailStart) return null

        // The tail must be a permutation of the current queue indices: every
        // desired queue index must exist and no index may repeat.
        val allowed = tailStart until currentQueue.size
        if (desiredEntries.map { it.queueIndex }.any { it !in allowed }) return null
        if (desiredEntries.map { it.queueIndex }.toSet().size != desiredEntries.size) return null

        val desiredTokens = ArrayList<Long>(tailStart + desiredEntries.size).apply {
            repeat(tailStart) { add(it.toLong()) }
            addAll(desiredEntries.map { it.queueIndex.toLong() })
        }

        return movesToReorder(
            tokens = currentQueue.indices.map { it.toLong() },
            desiredTokens = desiredTokens,
            tailStart = tailStart,
        )
    }

    /*
     * Generic index-exact move computation: turns the [tokens] list into
     * [desiredTokens] (both plain identity sequences over the same positions)
     * while only reordering at or after [tailStart]. Each identity appears
     * exactly once on both sides, so the produced (from, to) moves apply
     * cleanly and in order to the real queue. Returns null when no move is
     * needed.
     */
    private fun movesToReorder(
        tokens: List<Long>,
        desiredTokens: List<Long>,
        tailStart: Int,
    ): List<Pair<Int, Int>>? {
        if (tokens.size != desiredTokens.size) return null
        val working = tokens.toMutableList()
        val moves = mutableListOf<Pair<Int, Int>>()
        var i = tailStart
        while (i < desiredTokens.size) {
            if (working[i] != desiredTokens[i]) {
                val from = (i until working.size).firstOrNull { working[it] == desiredTokens[i] }
                    ?: return null
                moves.add(from to i)
                val popped = working.removeAt(from)
                working.add(i, popped)
            }
            i++
        }
        return moves.ifEmpty { null }
    }
}
