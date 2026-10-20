package com.dezire.music.data.lyrics

/*
 * Parser for LRC and enhanced (word-timed) LRC lyric documents.
 *
 * Supported syntax:
 *   [mm:ss]         [mm:ss.xx]         [mm:ss.xxx]        [mm:ss:xx]
 *   <mm:ss.xx>      <mm:ss.xxx>        (word-level, enhanced LRC)
 *   multiple line time tags on a single content line
 *   a version / format marker such as "v1:" or "v2:" at the start of a line
 *   ID tags [ti:], [ar:], [al:], [by:], [offset:], comments [:]
 *
 * The parser decides the synchronisation mode from what the source actually
 * contains:
 *
 *   - any <mm:ss.xxx> word tag  -> WORD_BY_WORD
 *   - otherwise line time tags  -> LINE_BY_LINE
 *   - no usable time tag        -> parse() returns null so callers can fall
 *                                  back to plain, unsynchronised lyrics
 *
 * A standard line-based .lrc therefore stays LINE_BY_LINE; it is never
 * falsely promoted to karaoke.
 *
 * Offset semantics follow the original specification: a positive [offset:n]
 * makes the lyrics appear *earlier*, so the effective trigger time is
 * `rawTimestamp - offset`. The value is clamped at zero so an oversized
 * offset cannot produce negative times.
 *
 * The parser is deliberately forgiving. Blank lines, comment lines, malformed
 * timestamps and files without any usable time tag all return null (or are
 * skipped) instead of throwing, so a bad .lrc file can never break playback.
 * Angle-bracket text that is not a valid timestamp (e.g. "<3") is left in the
 * displayed text rather than being silently dropped.
 */
object LrcParser {

    /*
     * A line time tag. Minutes accept 1-3 digits so both standard
     * ([02:15.30]) and some non-standard long forms parse. The fractional
     * part may be separated by '.' or ':' and is 1-3 digits.
     */
    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /*
     * A word time tag as used by enhanced LRC: <mm:ss.xxx>. Deliberately
     * strict - arbitrary angle-bracket text is not a timestamp.
     */
    private val WORD_TAG = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

    /*
     * An ID tag such as [ar:Artist]. Only alphabetic (plus '#' for the
     * comment tag) keys are considered, which keeps [00:12.34] from ever
     * being mistaken for metadata.
     */
    private val ID_TAG = Regex("""\[([a-zA-Z#]+):([^\]]*)]""")

    /** Convenience for callers that only need the synchronised lines. */
    fun parse(text: String): Lyrics? = parseDocument(text)?.lyrics

    /*
     * Full parse. Returns [Lyrics] plus the ID-tag metadata, or null when the
     * text contains no usable time tag.
     */
    fun parseDocument(text: String): LrcDocument? {
        if (text.isBlank()) return null

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var offsetMs = 0L

        val collected = ArrayList<LyricLine>()

        val normalized = text
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        for (rawLine in normalized.split('\n')) {
            var working = LyricsMarkup.stripMarkers(rawLine.trim())
            if (working.isEmpty()) continue

            ID_TAG.findAll(working).forEach { match ->
                val key = match.groupValues[1].lowercase()
                val value = match.groupValues[2].trim()
                when (key) {
                    "offset" -> value.toLongOrNull()?.let { offsetMs = it }
                    "ti" -> title = value.ifBlank { null }
                    "ar" -> artist = value.ifBlank { null }
                    "al" -> album = value.ifBlank { null }
                }
            }

            // ID tags never carry timing; drop them from the lyric content.
            working = ID_TAG.replace(working, "")
            // A marker may sit after a line tag, e.g. "[00:04.00]v2: text".
            working = LyricsMarkup.stripMarkers(working)

            val timeMatches = TIME_TAG.findAll(working).toList()
            val wordMatches = WORD_TAG.findAll(working).toList()
            if (timeMatches.isEmpty() && wordMatches.isEmpty()) continue

            // The readable body: every markup and voice marker removed.
            val body = LyricsMarkup.stripMarkers(
                working.replace(TIME_TAG, "").replace(WORD_TAG, "")
            )

            if (wordMatches.isNotEmpty()) {
                val wordSource = LyricsMarkup.stripMarkers(working.replace(TIME_TAG, ""))
                val words = buildWords(wordSource, offsetMs)
                if (words.isNotEmpty()) {
                    val lineTime = firstEffectiveTime(timeMatches, offsetMs)
                        ?: words.first().startTimeMs
                    collected += LyricLine(
                        timeMs = lineTime,
                        text = words.joinToString(" ") { it.text },
                        words = words
                    )
                } else if (timeMatches.isNotEmpty()) {
                    // Word tags present but unusable: keep the line, drop markup.
                    for (match in timeMatches) {
                        val time = effectiveTime(match, offsetMs) ?: continue
                        collected += LyricLine(time, body)
                    }
                }
            } else {
                for (match in timeMatches) {
                    val time = effectiveTime(match, offsetMs) ?: continue
                    collected += LyricLine(time, body)
                }
            }
        }

        if (collected.isEmpty()) return null

        val mode = if (collected.any { it.hasWordTiming }) {
            LyricSyncMode.WORD_BY_WORD
        } else {
            LyricSyncMode.LINE_BY_LINE
        }

        // stable sort so lines that share a timestamp keep file order
        val lyrics = Lyrics(
            lines = collected.sortedBy { it.timeMs },
            synced = true,
            source = LyricsSource.LRC,
            mode = mode
        )
        return LrcDocument(
            lyrics = lyrics,
            metadata = LrcMetadata(title, artist, album, offsetMs)
        )
    }

    /*
     * Builds the timed word list for a line that contains <mm:ss.xxx> tags.
     * The text between one tag and the next belongs to the earlier tag, so
     * "<t1>Apna <t2>bana" yields [Apna@t1, bana@t2]. A trailing tag with no
     * text after it is read as the end time of the preceding word. A
     * malformed tag is stripped from the output but its text is merged into
     * the previous word rather than being lost.
     */
    private fun buildWords(line: String, offsetMs: Long): List<LyricWord> {
        val matches = WORD_TAG.findAll(line).toList()
        if (matches.isEmpty()) return emptyList()

        val words = ArrayList<LyricWord>()

        // Any text before the first word tag starts the line.
        val leading = line.substring(0, matches.first().range.first).trim()
        if (leading.isNotEmpty()) {
            val leadingTime = effectiveTime(matches.first(), offsetMs) ?: 0L
            words += LyricWord(leading, leadingTime)
        }

        matches.forEachIndexed { index, match ->
            val time = effectiveTime(match, offsetMs)
            val start = match.range.last + 1
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else line.length
            val segment = line.substring(start, end).trim()

            when {
                time == null && segment.isNotEmpty() -> {
                    if (words.isNotEmpty()) {
                        val previous = words.removeAt(words.lastIndex)
                        val merged = listOf(previous.text, segment)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                        words += previous.copy(text = merged)
                    } else {
                        words += LyricWord(segment, 0L)
                    }
                }

                time != null && segment.isEmpty() -> {
                    // Trailing tag: treat it as the end of the previous word.
                    if (index == matches.lastIndex && words.isNotEmpty()) {
                        val previous = words.removeAt(words.lastIndex)
                        words += previous.copy(endTimeMs = time)
                    }
                }

                time != null -> words += LyricWord(segment, time)
            }
        }

        return inferWordEnds(words)
    }

    private fun firstEffectiveTime(matches: List<MatchResult>, offsetMs: Long): Long? {
        for (match in matches) {
            effectiveTime(match, offsetMs)?.let { return it }
        }
        return null
    }

    private fun effectiveTime(match: MatchResult, offsetMs: Long): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        if (seconds > 59) return null
        val fraction = fractionToMillis(match.groupValues[3])
        val raw = minutes * 60_000L + seconds * 1_000L + fraction
        return (raw - offsetMs).coerceAtLeast(0L)
    }

    private fun fractionToMillis(fraction: String): Long {
        if (fraction.isEmpty()) return 0L
        return when (fraction.length) {
            1 -> fraction.toLong() * 100L
            2 -> fraction.toLong() * 10L
            else -> fraction.substring(0, 3).toLong()
        }
    }
}

data class LrcMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val offsetMs: Long
)

data class LrcDocument(
    val lyrics: Lyrics,
    val metadata: LrcMetadata
)
