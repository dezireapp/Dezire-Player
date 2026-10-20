package com.dezire.music.data.metadata

/*
 * MusicBrainz identifier helpers shared by the local tag parser and the
 * Last.fm response mapper. MBIDs are 36-character UUIDs; anything else
 * (notably the empty string Last.fm returns when it has no match) is
 * treated as missing rather than as a usable value.
 */
object MbidValidator {

    private val UUID_REGEX = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
            "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    fun isValid(value: String?): Boolean =
        value != null && UUID_REGEX.matches(value.trim())

    /*
     * Returns a lower-cased MBID when [value] is a valid UUID, otherwise
     * null. Blank values and the literal "<unknown>" placeholder map to
     * null so callers can fall back to name-based lookups.
     */
    fun normalize(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("<unknown>", ignoreCase = true)) return null
        return if (UUID_REGEX.matches(trimmed)) trimmed.lowercase() else null
    }
}
