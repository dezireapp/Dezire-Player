package com.dezire.music.data.lyrics

/*
 * Detects the container format from its magic bytes and delegates to the
 * matching embedded-lyrics parser. Returns null for formats that are not
 * supported (for example Ogg or raw AAC), so the caller can fall back to an
 * external .lrc file.
 */
class EmbeddedLyricsExtractor {

    fun extract(source: SeekableByteSource): Lyrics? {
        val head = source.readFully(0, 12) ?: return null
        if (head.size < 4) return null

        return when {
            head.size >= 3 && String(head, 0, 3, Charsets.ISO_8859_1) == "ID3" ->
                Id3LyricsParser.parse(source)

            String(head, 0, 4, Charsets.ISO_8859_1) == "fLaC" ->
                FlacLyricsParser.parse(source)

            head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" ->
                Mp4LyricsParser.parse(source)

            else -> null
        }
    }
}
