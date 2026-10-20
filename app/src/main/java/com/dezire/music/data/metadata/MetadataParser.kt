package com.dezire.music.data.metadata

/*
 * Conservative parsing of combined local metadata strings.
 *
 * MediaStore and ID3/FLAC tags frequently pack several values into a
 * single string. The splitting rules here intentionally lean towards
 * under-splitting: an artist such as "Earth, Wind & Fire", "AC/DC" or
 * "Simon & Garfunkel" must survive intact. Only unambiguous delimiters are
 * used, and the raw value is always preserved by [LocalMetadata].
 */
object MetadataParser {

    /*
     * Sentinel used internally while normalising delimiters. A control
     * character cannot appear in real tag values, so it is safe to split
     * on it afterwards.
     */
    private const val SENTINEL = "\u0001"

    /*
     * Collaboration markers. Deliberately excludes "&", "/", "," and
     * standalone "with" because those appear inside legitimate artist
     * names.
     */
    private val COLLABORATION_MARKERS = listOf(
        " featuring ",
        " feat. ",
        " feat ",
        " ft. ",
        " ft "
    )

    /*
     * Splits a raw artist/album-artist string into individual names.
     * Supported delimiters: ";", "|", newlines and collaboration markers.
     * "&" and "/" are never treated as separators.
     */
    fun parseArtistNames(raw: String?): List<String> {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return emptyList()

        var normalized = value
        for (marker in COLLABORATION_MARKERS) {
            normalized = normalized.replace(marker, SENTINEL, ignoreCase = true)
        }
        normalized = normalized
            .replace(";", SENTINEL)
            .replace("|", SENTINEL)
            .replace("\r\n", SENTINEL)
            .replace("\n", SENTINEL)
            .replace("\r", SENTINEL)

        return normalized
            .split(SENTINEL)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.isUnknownPlaceholder() }
            .distinctBy { it.lowercase() }
    }

    /*
     * Splits a raw genre string. Splits on ";", "|" and newlines only, so
     * compound genres such as "R&B", "Pop/Rock" and "Hip-Hop/Rap" are kept
     * whole.
     */
    fun parseGenres(raw: String?): List<String> {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return emptyList()
        return value
            .replace("\r\n", "\n")
            .split(';', '|', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.isUnknownPlaceholder() }
            .distinctBy { it.lowercase() }
    }

    private fun String.isUnknownPlaceholder(): Boolean =
        equals("<unknown>", ignoreCase = true) ||
            equals("unknown", ignoreCase = true)
}
