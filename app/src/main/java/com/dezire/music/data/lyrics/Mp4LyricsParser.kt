package com.dezire.music.data.lyrics

/*
 * Extracts embedded lyrics from an MP4/M4A container.
 *
 * Lyrics live in the iTunes-style metadata atom `©lyr` (0xA9 'l' 'y' 'r'),
 * nested as: moov > udta > meta > ilst > ©lyr > data. The `data` payload is
 * UTF-8 text. Some taggers store LRC timestamps inside the atom, so a value
 * that parses as LRC is treated as synchronised.
 *
 * Box sizes of the 32-bit, 64-bit (size == 1) and "to end of file" (size == 0)
 * forms are handled. The `meta` box is a FullBox and carries a 4-byte
 * version/flags field before its children.
 */
internal object Mp4LyricsParser {

    private const val LYRICS_ATOM = "\u00A9lyr"

    fun parse(source: SeekableByteSource): Lyrics? {
        val head = source.readFully(4, 4) ?: return null
        if (String(head, Charsets.ISO_8859_1) != "ftyp") return null

        val moov = findBox(source, 0L, source.size, "moov") ?: return null
        val udta = findBox(source, moov.contentStart, moov.end, "udta") ?: return null
        val meta = findBox(source, udta.contentStart, udta.end, "meta") ?: return null
        val ilst = findBox(source, meta.contentStart + 4, meta.end, "ilst") ?: return null
        val lyrics = findBox(source, ilst.contentStart, ilst.end, LYRICS_ATOM) ?: return null
        val data = findBox(source, lyrics.contentStart, lyrics.end, "data") ?: return null

        // data payload: 4-byte type/flags + 4-byte locale, then the value.
        val payloadStart = data.contentStart + 8
        val payloadLength = (data.end - payloadStart).toInt()
        if (payloadLength <= 0) return null

        val bytes = source.readFully(payloadStart, payloadLength) ?: return null
        val text = String(bytes, Charsets.UTF_8).trim()
        if (text.isEmpty()) return null

        return FlacLyricsParser.lyricsFromValue(text)
    }

    private data class Box(val start: Long, val size: Long, val headerSize: Long) {
        val contentStart: Long get() = start + headerSize
        val end: Long get() = start + size
    }

    /*
     * Walks the sibling boxes between [start] and [end], returning the first
     * one whose type matches. A malformed size terminates the walk instead of
     * looping forever.
     */
    private fun findBox(source: SeekableByteSource, start: Long, end: Long, type: String): Box? {
        var position = start
        while (position + 8 <= end) {
            val header = source.readFully(position, 8) ?: return null
            var size = header.u32BE(0)
            var headerSize = 8L

            if (size == 1L) {
                val extended = source.readFully(position + 8, 8) ?: return null
                size = extended.u64BE(0)
                headerSize = 16L
            } else if (size == 0L) {
                size = end - position
            }

            if (size < headerSize || position + size > end) return null

            val boxType = String(header, 4, 4, Charsets.ISO_8859_1)
            if (boxType == type) return Box(position, size, headerSize)

            position += size
        }
        return null
    }
}
