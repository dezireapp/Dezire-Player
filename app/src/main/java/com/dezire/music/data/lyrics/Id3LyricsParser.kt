package com.dezire.music.data.lyrics

/*
 * Extracts embedded lyrics from an MP3 ID3v2 tag.
 *
 * Supported frames:
 *   USLT (v2.3/v2.4) / ULT (v2.2) - unsynchronised lyrics (plain text)
 *   SYLT (v2.3/v2.4) / SLT (v2.2) - synchronised lyrics
 *
 * Only the millisecond timestamp format of SYLT is honoured; the MPEG-frame
 * format cannot be converted to wall-clock time without also parsing the
 * MPEG audio header, and is therefore reported as unsupported (the frame is
 * ignored and the caller falls back to the next source).
 *
 * Frame-level unsynchronisation (ID3v2.4) is not applied; the much more
 * common tag-level unsynchronisation (both v2.3 and v2.4) is handled.
 */
internal object Id3LyricsParser {

    private const val TAG = "ID3"

    fun parse(source: SeekableByteSource): Lyrics? {
        val header = source.readFully(0, 10) ?: return null
        if (header.size < 10) return null
        if (String(header, 0, 3, Charsets.ISO_8859_1) != TAG) return null

        val major = header.u8(3)
        if (major !in 2..4) return null

        val flags = header.u8(5)
        val tagSize = header.syncSafeU32(6)
        if (tagSize <= 0L) return null

        val raw = source.readFully(10, tagSize.toInt()) ?: return null
        val unsynchronised = (flags and 0x80) != 0
        val hasExtendedHeader = (flags and 0x40) != 0

        val bytes = if (unsynchronised) deUnsynchronise(raw) else raw

        var position = if (hasExtendedHeader) skipExtendedHeader(bytes, major) else 0

        return when (major) {
            2 -> parseV22(bytes, position)
            else -> parseV23V24(bytes, position, major)
        }
    }

    private fun parseV23V24(bytes: ByteArray, start: Int, major: Int): Lyrics? {
        var position = start
        while (position + 10 <= bytes.size) {
            val id = String(bytes, position, 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            if (!id.all { it.isLetterOrDigit() }) break

            val size = if (major == 4) {
                bytes.syncSafeU32(position + 4)
            } else {
                bytes.u32BE(position + 4)
            }.toInt()

            if (size <= 0 || position + 10 + size > bytes.size) break
            val body = bytes.copyOfRange(position + 10, position + 10 + size)

            when (id) {
                "USLT" -> decodeUnsynchronised(body)?.let { return it }
                "SYLT" -> decodeSynchronised(body)?.let { return it }
            }
            position += 10 + size
        }
        return null
    }

    private fun parseV22(bytes: ByteArray, start: Int): Lyrics? {
        var position = start
        while (position + 6 <= bytes.size) {
            val id = String(bytes, position, 3, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            if (!id.all { it.isLetterOrDigit() }) break

            val size = bytes.u24BE(position + 3)
            if (size <= 0 || position + 6 + size > bytes.size) break
            val body = bytes.copyOfRange(position + 6, position + 6 + size)

            when (id) {
                "ULT" -> decodeUnsynchronised(body)?.let { return it }
                "SLT" -> decodeSynchronised(body)?.let { return it }
            }
            position += 6 + size
        }
        return null
    }

    // -----------------------------------------------------------------------
    // Frame decoding
    // -----------------------------------------------------------------------

    private fun decodeUnsynchronised(body: ByteArray): Lyrics? {
        if (body.size < 4) return null
        val encoding = body.u8(0)
        var index = 4 // encoding byte + 3-byte language
        index = skipTerminated(body, index, encoding)
        if (index >= body.size) {
            // Some writers omit the (empty) content descriptor terminator.
            index = 4
        }
        val text = decodeText(body, index, body.size - index, encoding).trim()
        if (text.isBlank()) return null

        /*
         * Some taggers store LRC / enhanced-LRC markup inside a USLT frame.
         * Parse it so the timing is kept rather than shown as raw text; if
         * there is no usable time tag the frame stays plain and unsynchronised.
         */
        LrcParser.parse(text)?.let { parsed ->
            if (parsed.lines.isNotEmpty()) {
                return parsed.copy(source = LyricsSource.EMBEDDED_SYNCED)
            }
        }

        return Lyrics(
            lines = splitPlainLines(text),
            synced = false,
            source = LyricsSource.EMBEDDED_UNSYNCED
        )
    }

    private fun decodeSynchronised(body: ByteArray): Lyrics? {
        if (body.size < 6) return null
        val encoding = body.u8(0)
        // bytes 1..3 are the language; byte 4 is the timestamp format and
        // byte 5 the content type.
        val timestampFormat = body.u8(4)
        if (timestampFormat != 2) return null // only absolute milliseconds

        var index = 6
        index = skipTerminated(body, index, encoding)

        val segments = ArrayList<Pair<Long, String>>()
        while (index < body.size) {
            val parsed = readTerminated(body, index, encoding) ?: break
            val text = parsed.first
            index = parsed.second
            if (index + 4 > body.size) break
            val timestamp = body.u32BE(index)
            index += 4
            segments += timestamp to text
        }
        if (segments.isEmpty()) return null

        val lines = assembleSyncedLines(segments)
        if (lines.isEmpty()) return null
        return Lyrics(
            lines = lines,
            synced = true,
            source = LyricsSource.EMBEDDED_SYNCED
        )
    }

    /*
     * SYLT stores the lyric as many small text fragments, each with its own
     * timestamp. Fragments are stitched back into full lines at embedded
     * newline characters. The per-fragment timestamps are not thrown away:
     * within a line every fragment is kept as a [LyricWord] so the UI can run
     * true word-by-word karaoke. The line text is rebuilt from the raw
     * fragments so spacing/punctuation survive exactly.
     */
    private fun assembleSyncedLines(segments: List<Pair<Long, String>>): List<LyricLine> {
        val result = ArrayList<LyricLine>()
        val words = ArrayList<LyricWord>()
        val builder = StringBuilder()

        fun flush(defaultTimestamp: Long) {
            val text = builder.toString().trim()
            if (text.isNotEmpty()) {
                val normalized = inferWordEnds(words.toList())
                val start = normalized.firstOrNull()?.startTimeMs ?: defaultTimestamp
                result += LyricLine(start, text, normalized)
            }
            words.clear()
            builder.setLength(0)
        }

        for ((timestamp, raw) in segments) {
            val parts = raw.replace("\r", "").split('\n')
            parts.forEachIndexed { index, part ->
                if (part.isNotEmpty()) {
                    builder.append(part)
                    val word = part.trim()
                    if (word.isNotEmpty()) {
                        words += LyricWord(word, timestamp)
                    }
                }
                if (index < parts.lastIndex) flush(timestamp)
            }
        }
        flush(segments.lastOrNull()?.first ?: 0L)
        return result
    }

    // -----------------------------------------------------------------------
    // Encoding helpers
    // -----------------------------------------------------------------------

    private fun skipTerminated(bytes: ByteArray, start: Int, encoding: Int): Int {
        var i = start
        if (isDoubleByteEncoding(encoding)) {
            while (i + 1 < bytes.size) {
                if (bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte()) return i + 2
                i += 2
            }
            return bytes.size
        }
        while (i < bytes.size) {
            if (bytes[i] == 0.toByte()) return i + 1
            i++
        }
        return bytes.size
    }

    private fun readTerminated(bytes: ByteArray, start: Int, encoding: Int): Pair<String, Int>? {
        var i = start
        if (isDoubleByteEncoding(encoding)) {
            while (i + 1 < bytes.size) {
                if (bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte()) {
                    return decodeText(bytes, start, i - start, encoding) to (i + 2)
                }
                i += 2
            }
            return null
        }
        while (i < bytes.size) {
            if (bytes[i] == 0.toByte()) {
                return decodeText(bytes, start, i - start, encoding) to (i + 1)
            }
            i++
        }
        return null
    }

    private fun decodeText(bytes: ByteArray, offset: Int, length: Int, encoding: Int): String {
        if (length <= 0) return ""
        val safeLength = minOf(length, bytes.size - offset).coerceAtLeast(0)
        if (safeLength == 0) return ""
        return when (encoding) {
            0 -> String(bytes, offset, safeLength, Charsets.ISO_8859_1)
            1 -> String(bytes, offset, safeLength, Charsets.UTF_16)
            2 -> String(bytes, offset, safeLength, Charsets.UTF_16BE)
            else -> String(bytes, offset, safeLength, Charsets.UTF_8)
        }
    }

    private fun isDoubleByteEncoding(encoding: Int): Boolean = encoding == 1 || encoding == 2

    private fun splitPlainLines(text: String): List<LyricLine> =
        text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { LyricsMarkup.stripMarkers(it) }
            .filter { it.isNotEmpty() }
            .map { LyricLine(0L, it) }

    private fun deUnsynchronise(input: ByteArray): ByteArray {
        val out = ByteArray(input.size)
        var o = 0
        var i = 0
        while (i < input.size) {
            val b = input[i]
            out[o++] = b
            if (b == 0xFF.toByte() && i + 1 < input.size && input[i + 1] == 0x00.toByte()) {
                i += 2
            } else {
                i++
            }
        }
        return out.copyOf(o)
    }

    private fun skipExtendedHeader(bytes: ByteArray, major: Int): Int {
        if (bytes.size < 4) return 0
        return if (major == 4) {
            // v2.4 extended header size is synchsafe and includes itself.
            bytes.syncSafeU32(0).toInt().coerceIn(0, bytes.size)
        } else {
            // v2.3 extended header size excludes its own 4 size bytes.
            (bytes.u32BE(0) + 4).toInt().coerceIn(0, bytes.size)
        }
    }
}
