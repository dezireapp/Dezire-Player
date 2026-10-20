package com.dezire.music.data.lyrics

import java.io.ByteArrayOutputStream

/** In-memory [SeekableByteSource] used to exercise the parsers on the JVM. */
class ByteArrayByteSource(private val bytes: ByteArray) : SeekableByteSource {
    override val size: Long get() = bytes.size.toLong()

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position < 0 || position >= bytes.size) return -1
        val available = minOf(length.toLong(), bytes.size - position).toInt()
        System.arraycopy(bytes, position.toInt(), buffer, offset, available)
        return available
    }

    override fun close() = Unit
}

// --------------------------------------------------------------------------
// Helpers for building synthetic media files.
// --------------------------------------------------------------------------

internal fun syncSafe(value: Int): ByteArray = byteArrayOf(
    ((value shr 21) and 0x7F).toByte(),
    ((value shr 14) and 0x7F).toByte(),
    ((value shr 7) and 0x7F).toByte(),
    (value and 0x7F).toByte()
)

internal fun u32(value: Int): ByteArray = byteArrayOf(
    ((value ushr 24) and 0xFF).toByte(),
    ((value ushr 16) and 0xFF).toByte(),
    ((value ushr 8) and 0xFF).toByte(),
    (value and 0xFF).toByte()
)

internal fun u32le(value: Int): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value ushr 8) and 0xFF).toByte(),
    ((value ushr 16) and 0xFF).toByte(),
    ((value ushr 24) and 0xFF).toByte()
)

/** An ID3v2.3 frame: 4-byte id, 4-byte size, 2 flag bytes, body. */
internal fun id3v23Frame(id: String, body: ByteArray): ByteArray =
    id.ascii() + u32(body.size) + byteArrayOf(0, 0) + body

/** A complete ID3v2.3 tag wrapping [frames]. */
internal fun id3v23Tag(frames: ByteArray): ByteArray {
    val body = frames
    return "ID3".ascii() + byteArrayOf(3, 0, 0) + syncSafe(body.size) + body
}

/** USLT body: encoding, 3-byte language, empty descriptor, text. */
internal fun usltBody(text: String, encoding: Int = 0): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(encoding)
    out.write("eng".ascii())
    out.write(0)
    val bytes = when (encoding) {
        0 -> text.toByteArray(Charsets.ISO_8859_1)
        1 -> text.toByteArray(Charsets.UTF_16)
        else -> text.toByteArray(Charsets.UTF_8)
    }
    out.write(bytes)
    return out.toByteArray()
}

/** SYLT body: encoding, language, timestamp format 2 (ms), type 1, descriptor, segments. */
internal fun syltBody(segments: List<Pair<String, Int>>, encoding: Int = 0): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(encoding)
    out.write("eng".ascii())
    out.write(2)
    out.write(1)
    out.write(0)
    for ((text, timestamp) in segments) {
        out.write(
            when (encoding) {
                0 -> text.toByteArray(Charsets.ISO_8859_1)
                1 -> text.toByteArray(Charsets.UTF_16)
                else -> text.toByteArray(Charsets.UTF_8)
            }
        )
        out.write(0)
        out.write(u32(timestamp))
    }
    return out.toByteArray()
}

/** Native FLAC file with a single VORBIS_COMMENT block holding [comments]. */
internal fun flacFile(comments: List<String>): ByteArray {
    val vorbis = ByteArrayOutputStream()
    vorbis.write(u32le(0)) // vendor length
    vorbis.write(u32le(comments.size)) // comment count
    for (comment in comments) {
        val bytes = comment.toByteArray(Charsets.UTF_8)
        vorbis.write(u32le(bytes.size))
        vorbis.write(bytes)
    }
    val payload = vorbis.toByteArray()

    val blockHeader = byteArrayOf((0x80 or 4).toByte()) +
        byteArrayOf(
            ((payload.size ushr 16) and 0xFF).toByte(),
            ((payload.size ushr 8) and 0xFF).toByte(),
            (payload.size and 0xFF).toByte(),
        )
    return "fLaC".ascii() + blockHeader + payload
}

/** A generic ISO-BMFF box: 4-byte big-endian size, 4-byte type, payload. */
internal fun atom(type: ByteArray, payload: ByteArray): ByteArray =
    u32(8 + payload.size) + type + payload

/** Minimal M4A file carrying [lyrics] in moov > udta > meta > ilst > ©lyr > data. */
internal fun mp4File(lyrics: String): ByteArray {
    val data = atom(
        "data".ascii(),
        byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + lyrics.toByteArray(Charsets.UTF_8),
    )
    val lyr = atom(byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()), data)
    val ilst = atom("ilst".ascii(), lyr)
    val meta = atom("meta".ascii(), byteArrayOf(0, 0, 0, 0) + ilst)
    val udta = atom("udta".ascii(), meta)
    val moov = atom("moov".ascii(), udta)
    val ftyp = atom("ftyp".ascii(), "M4A ".ascii() + byteArrayOf(0, 0, 0, 0) + "M4A mp42".ascii())
    return ftyp + moov
}

internal fun String.ascii(): ByteArray = toByteArray(Charsets.ISO_8859_1)
