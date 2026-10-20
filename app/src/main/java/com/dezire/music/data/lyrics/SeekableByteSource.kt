package com.dezire.music.data.lyrics

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/*
 * Minimal random-access view over a byte stream. Embedded-lyrics parsers
 * (ID3, FLAC, MP4) need to jump around the file - to follow atom/block
 * chains, for instance - without loading the whole track into memory.
 *
 * Production supplies a [FileChannelByteSource]; tests use an in-memory
 * implementation, keeping the parsers free of any Android dependency.
 */
interface SeekableByteSource : Closeable {
    /** Total number of bytes in the source. */
    val size: Long

    /**
     * Reads up to [length] bytes starting at [position] into [buffer] at
     * [offset]. Returns the number of bytes actually read, or -1 at EOF.
     */
    fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

/*
 * Reads exactly [length] bytes at [position], looping over short reads.
 * Returns null for an out-of-range or empty request, so callers can treat
 * truncation as "no data" rather than crashing.
 */
fun SeekableByteSource.readFully(position: Long, length: Int): ByteArray? {
    if (length <= 0 || position < 0 || position >= size) return null
    val capped = minOf(length.toLong(), size - position).toInt()
    if (capped <= 0) return null
    val out = ByteArray(capped)
    var read = 0
    while (read < capped) {
        val n = readAt(position + read, out, read, capped - read)
        if (n <= 0) break
        read += n
    }
    if (read <= 0) return null
    return if (read == capped) out else out.copyOf(read)
}

/** FileChannel-backed source used by the production provider. */
class FileChannelByteSource(private val channel: FileChannel) : SeekableByteSource {
    override val size: Long = channel.size()

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position < 0 || length <= 0) return -1
        val target = ByteBuffer.wrap(buffer, offset, length)
        return channel.read(target, position)
    }

    override fun close() {
        runCatching { channel.close() }
    }
}

// ---------------------------------------------------------------------------
// Big/little-endian helpers shared by the parsers.
// ---------------------------------------------------------------------------

fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF

fun ByteArray.u16BE(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

fun ByteArray.u16LE(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

fun ByteArray.u24BE(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 16) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        (this[offset + 2].toInt() and 0xFF)

fun ByteArray.u32BE(offset: Int): Long =
    ((this[offset].toLong() and 0xFF) shl 24) or
        ((this[offset + 1].toLong() and 0xFF) shl 16) or
        ((this[offset + 2].toLong() and 0xFF) shl 8) or
        (this[offset + 3].toLong() and 0xFF)

fun ByteArray.u32LE(offset: Int): Long =
    (this[offset].toLong() and 0xFF) or
        ((this[offset + 1].toLong() and 0xFF) shl 8) or
        ((this[offset + 2].toLong() and 0xFF) shl 16) or
        ((this[offset + 3].toLong() and 0xFF) shl 24)

fun ByteArray.u64BE(offset: Int): Long {
    var value = 0L
    for (i in 0 until 8) {
        value = (value shl 8) or (this[offset + i].toLong() and 0xFF)
    }
    return value
}

/** 28-bit "synchsafe" integer used by ID3v2 headers and v2.4 frame sizes. */
fun ByteArray.syncSafeU32(offset: Int): Long =
    ((this[offset].toLong() and 0x7F) shl 21) or
        ((this[offset + 1].toLong() and 0x7F) shl 14) or
        ((this[offset + 2].toLong() and 0x7F) shl 7) or
        (this[offset + 3].toLong() and 0x7F)
