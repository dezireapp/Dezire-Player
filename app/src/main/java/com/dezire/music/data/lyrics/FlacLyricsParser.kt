package com.dezire.music.data.lyrics

/*
 * Extracts embedded lyrics from a native FLAC file.
 *
 * FLAC stores text tags as Vorbis comments in a VORBIS_COMMENT metadata
 * block. Lyrics are commonly written to one of the keys below. A value that
 * itself contains LRC timestamps is treated as synchronised; otherwise it is
 * plain text.
 *
 * Note: Ogg-encapsulated Vorbis/Opus files share the same Vorbis comment
 * tag names but a different container. Ogg is not parsed here, so embedded
 * lyrics in .ogg/.opus files are not extracted (external .lrc still works).
 */
internal object FlacLyricsParser {

    private const val MAGIC = "fLaC"
    private const val BLOCK_VORBIS_COMMENT = 4

    private val LYRICS_KEYS = setOf(
        "LYRICS",
        "LYRIC",
        "UNSYNCEDLYRICS",
        "UNSYNCED LYRICS",
        "SYNCEDLYRICS",
        "SYNCLYRICS"
    )

    fun parse(source: SeekableByteSource): Lyrics? {
        val magic = source.readFully(0, 4) ?: return null
        if (String(magic, Charsets.ISO_8859_1) != MAGIC) return null

        var position = 4L
        while (position + 4 <= source.size) {
            val header = source.readFully(position, 4) ?: return null
            val lastBlock = (header.u8(0) and 0x80) != 0
            val type = header.u8(0) and 0x7F
            val length = header.u24BE(1)

            val blockStart = position + 4
            if (type == BLOCK_VORBIS_COMMENT) {
                val block = source.readFully(blockStart, length)
                if (block != null) {
                    parseVorbisComment(block)?.let { return it }
                }
            }

            if (lastBlock) break
            position = blockStart + length
        }
        return null
    }

    private fun parseVorbisComment(block: ByteArray): Lyrics? {
        if (block.size < 8) return null

        var position = 0
        val vendorLength = block.u32LE(position).toInt()
        position += 4
        if (vendorLength < 0 || position + vendorLength > block.size) return null
        position += vendorLength

        if (position + 4 > block.size) return null
        val commentCount = block.u32LE(position).toInt()
        position += 4

        for (i in 0 until commentCount) {
            if (position + 4 > block.size) break
            val length = block.u32LE(position).toInt()
            position += 4
            if (length < 0 || position + length > block.size) break

            val comment = String(block, position, length, Charsets.UTF_8)
            position += length

            val separator = comment.indexOf('=')
            if (separator <= 0) continue

            val key = comment.substring(0, separator).trim().uppercase()
            if (key in LYRICS_KEYS) {
                val value = comment.substring(separator + 1)
                lyricsFromValue(value)?.let { return it }
            }
        }
        return null
    }

    /*
     * A tag value may already be an LRC document (some taggers store
     * timestamped lyrics in FLAC); fall back to plain, unsynchronised text.
     */
    fun lyricsFromValue(value: String): Lyrics? {
        if (value.isBlank()) return null
        LrcParser.parse(value)?.let { lrc ->
            return lrc.copy(source = LyricsSource.EMBEDDED_SYNCED)
        }
        val lines = value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { LyricsMarkup.stripMarkers(it) }
            .filter { it.isNotEmpty() }
            .map { LyricLine(0L, it) }
        if (lines.isEmpty()) return null
        return Lyrics(
            lines = lines,
            synced = false,
            source = LyricsSource.EMBEDDED_UNSYNCED
        )
    }
}
