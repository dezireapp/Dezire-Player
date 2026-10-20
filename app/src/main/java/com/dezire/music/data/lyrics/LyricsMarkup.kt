package com.dezire.music.data.lyrics

/*
 * Recognized structural markers that some lyric sources prepend to a lyric
 * line. These are metadata (voice / track / format tags), not lyric text, so
 * they are removed at parse time - never rendered.
 *
 * The marker is only recognized in its proper syntactic position: at the very
 * start of a line (ignoring leading whitespace) and immediately followed by a
 * colon. This is deliberate - the characters "v1" / "v2" are genuine lyric
 * text when they appear anywhere else, and are then left untouched.
 *
 * Supported markers:
 *   v1:  v2:  v3:  ...   (voice / track index - the common case)
 *   voice0: voice1: ...
 *   lrc:  lyrics:  karaoke:   (format markers)
 *
 * Only one marker per line is typical, but a line such as
 * "v1: v2: text" is fully cleaned by stripping markers repeatedly.
 */
internal object LyricsMarkup {

    private val LEADING_MARKER = Regex(
        """^\s*(?:v\d+|voice\s*\d+|lrc|lyrics|karaoke)\s*:\s*""",
        RegexOption.IGNORE_CASE
    )

    /*
     * Removes any leading structural voice/format markers from [line]. When
     * the line does not begin with a recognized marker it is returned trimmed
     * and otherwise unchanged, so genuine lyric text is preserved exactly.
     */
    fun stripMarkers(line: String): String {
        var result = line
        while (true) {
            val stripped = LEADING_MARKER.replaceFirst(result, "")
            if (stripped == result) return result.trim()
            result = stripped
        }
    }
}
