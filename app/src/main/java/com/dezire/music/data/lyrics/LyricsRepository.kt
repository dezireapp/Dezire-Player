package com.dezire.music.data.lyrics

import com.dezire.music.data.model.Song
import java.util.concurrent.ConcurrentHashMap

/*
 * How a set of lyrics should be synchronised against playback.
 *
 *   WORD_BY_WORD   - individual words carry timestamps (enhanced LRC / SYLT)
 *   LINE_BY_LINE   - only whole lines carry timestamps (standard LRC)
 *   UNSYNCHRONIZED - no timestamps at all; render as plain text
 *
 * The mode is selected per track / per lyric source by the parsers from the
 * timestamp structure that is actually present, never globally.
 */
enum class LyricSyncMode {
    WORD_BY_WORD,
    LINE_BY_LINE,
    UNSYNCHRONIZED
}

/*
 * A single word (or word-sized segment) inside a synchronised line.
 *
 * [startTimeMs] is an absolute playback position. [endTimeMs] is optional:
 * formats such as ID3 SYLT carry it, while enhanced LRC usually only carries
 * a start time. When it is absent, the active interval is inferred from the
 * next word's start time (see [LyricLine.inferredEndTimeMs]) - no finer
 * timing is invented.
 */
data class LyricWord(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long? = null
)

/*
 * A single lyric line. [timeMs] is the playback position at which the line
 * becomes active. [text] is the readable line with every timestamp/marker
 * removed. [words] is populated only when the source carried word-level
 * timing; it is empty for plain and line-level lyrics.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
) {
    val hasWordTiming: Boolean get() = words.isNotEmpty()

    /*
     * End of the active interval for the word at [wordIndex]: the stored end
     * time when known, otherwise the next word's start. The last word of a
     * line has no inferred end (returns null).
     */
    fun inferredEndTimeMs(wordIndex: Int): Long? {
        val word = words.getOrNull(wordIndex) ?: return null
        return word.endTimeMs ?: words.getOrNull(wordIndex + 1)?.startTimeMs
    }
}

/*
 * Where a set of lyrics came from. Used for diagnostics and to make the
 * source-priority rules testable and observable.
 */
enum class LyricsSource {
    /** Timestamped lyrics read from the audio file's own metadata. */
    EMBEDDED_SYNCED,

    /** Timestamped lyrics read from a sibling .lrc file. */
    LRC,

    /** Plain, untimed lyrics read from the audio file's own metadata. */
    EMBEDDED_UNSYNCED
}

/*
 * Lyrics for one track.
 *
 * [synced] is kept for backward compatibility with the existing
 * [LyricsRepository] / [LyricsSourceResolver]; [mode] is the richer,
 * authoritative description. The mode default is derived once from the
 * constructor arguments, so existing call sites such as
 * `Lyrics(lines, synced = true)` keep working and automatically report
 * WORD_BY_WORD when the lines carry word timing.
 */
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean = true,
    val source: LyricsSource? = null,
    val mode: LyricSyncMode = when {
        !synced -> LyricSyncMode.UNSYNCHRONIZED
        lines.any { it.hasWordTiming } -> LyricSyncMode.WORD_BY_WORD
        lines.isNotEmpty() -> LyricSyncMode.LINE_BY_LINE
        else -> LyricSyncMode.UNSYNCHRONIZED
    }
) {
    val isWordByWord: Boolean get() = mode == LyricSyncMode.WORD_BY_WORD

    /*
     * Index of the line active at [positionMs], or -1 before the first
     * timestamp. Plain lyrics always report line 0 so they render as a
     * single block.
     */
    fun activeIndex(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (mode == LyricSyncMode.UNSYNCHRONIZED || !synced) return 0
        var index = -1
        // Linear scan is fine: a song has at most a few hundred lines and
        // this runs at most a handful of times per second.
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs) index = i else break
        }
        return index
    }

    /*
     * Index of the word active at [positionMs] inside the line at
     * [lineIndex], or -1 when that line has no word timing / playback is
     * before its first word. Words are assumed to be in ascending start-time
     * order, which every parser guarantees.
     */
    fun activeWordIndex(lineIndex: Int, positionMs: Long): Int {
        val words = lines.getOrNull(lineIndex)?.words ?: return -1
        if (words.isEmpty()) return -1
        var index = -1
        for (i in words.indices) {
            if (words[i].startTimeMs <= positionMs) index = i else break
        }
        return index
    }
}

/*
 * Fills in missing word end times from the next word's start. This is the
 * only inference permitted: a word's active interval is never extended past
 * the information the source actually provided.
 */
internal fun inferWordEnds(words: List<LyricWord>): List<LyricWord> {
    if (words.size < 2) return words
    return words.mapIndexed { index, word ->
        if (word.endTimeMs == null && index < words.lastIndex) {
            word.copy(endTimeMs = words[index + 1].startTimeMs)
        } else {
            word
        }
    }
}

/*
 * A lyrics data source. Implementations return null when they have nothing
 * for a track, so a missing source can never break playback.
 */
interface LyricsProvider {
    suspend fun lyricsFor(song: Song): Lyrics?
}

/*
 * Application-wide lyrics repository with a per-track cache.
 *
 * Lyrics are read once per track id and then served from memory, including
 * negative results, so Compose recompositions and repeated visits to Now
 * Playing never re-open or re-parse the audio file. Wiring a provider is a
 * single line in [com.dezire.music.DezireApplication]; with none attached the
 * repository simply reports no lyrics and the UI hides the block.
 */
class LyricsRepository(
    private val provider: LyricsProvider? = null
) {
    /*
     * ConcurrentHashMap cannot store null, so a shared sentinel marks
     * "looked up, no lyrics". This keeps negative caching without boxing
     * every miss into an Optional.
     */
    private val noLyrics = Any()
    private val cache = ConcurrentHashMap<Long, Any>()

    val isAvailable: Boolean
        get() = provider != null

    suspend fun lyricsFor(song: Song?): Lyrics? {
        if (song == null) return null
        val configured = provider ?: return null

        cache[song.id]?.let { cached ->
            @Suppress("UNCHECKED_CAST")
            return if (cached === noLyrics) null else cached as Lyrics
        }

        val resolved = runCatching { configured.lyricsFor(song) }.getOrNull()
        cache[song.id] = resolved ?: noLyrics
        return resolved
    }
}
