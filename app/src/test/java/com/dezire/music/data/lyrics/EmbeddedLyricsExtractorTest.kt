package com.dezire.music.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedLyricsExtractorTest {

    private val extractor = EmbeddedLyricsExtractor()

    private fun extract(bytes: ByteArray): Lyrics? =
        extractor.extract(ByteArrayByteSource(bytes))

    @Test
    fun `reads unsynchronised ID3 USLT lyrics`() {
        val tag = id3v23Tag(
            id3v23Frame("USLT", usltBody("First line\nSecond line", encoding = 3))
        )
        val lyrics = extract(tag)
        assertNotNull(lyrics)
        assertFalse(lyrics!!.synced)
        assertEquals(LyricsSource.EMBEDDED_UNSYNCED, lyrics.source)
        assertEquals(listOf("First line", "Second line"), lyrics.lines.map { it.text })
    }

    @Test
    fun `reads synchronised ID3 SYLT lyrics with millisecond timestamps`() {
        val tag = id3v23Tag(
            id3v23Frame(
                "SYLT",
                syltBody(
                    listOf(
                        "Hello\n" to 1_000,
                        "World" to 5_000,
                    ),
                    encoding = 3,
                )
            )
        )
        val lyrics = extract(tag)
        assertNotNull(lyrics)
        assertTrue(lyrics!!.synced)
        assertEquals(LyricsSource.EMBEDDED_SYNCED, lyrics.source)
        assertEquals(2, lyrics.lines.size)
        assertEquals(1_000L, lyrics.lines[0].timeMs)
        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals(5_000L, lyrics.lines[1].timeMs)
        assertEquals("World", lyrics.lines[1].text)
    }

    @Test
    fun `reads plain FLAC vorbis comment lyrics`() {
        val file = flacFile(listOf("ARTIST=Someone", "LYRICS=Line A\nLine B"))
        val lyrics = extract(file)
        assertNotNull(lyrics)
        assertFalse(lyrics!!.synced)
        assertEquals(LyricsSource.EMBEDDED_UNSYNCED, lyrics.source)
        assertEquals(listOf("Line A", "Line B"), lyrics.lines.map { it.text })
    }

    @Test
    fun `reads timestamped FLAC lyrics as synchronised`() {
        val file = flacFile(listOf("UNSYNCEDLYRICS=[00:05.00]Hi\n[00:10.00]There"))
        val lyrics = extract(file)
        assertNotNull(lyrics)
        assertTrue(lyrics!!.synced)
        assertEquals(LyricsSource.EMBEDDED_SYNCED, lyrics.source)
        assertEquals(5_000L, lyrics.lines[0].timeMs)
    }

    @Test
    fun `reads MP4 copyright lyric atom`() {
        val file = mp4File("MP4 line one\nMP4 line two")
        val lyrics = extract(file)
        assertNotNull(lyrics)
        assertFalse(lyrics!!.synced)
        assertEquals(listOf("MP4 line one", "MP4 line two"), lyrics.lines.map { it.text })
    }

    @Test
    fun `returns null for unsupported or garbage data`() {
        assertNull(extract(ByteArray(0)))
        assertNull(extract("not a media file at all".toByteArray()))
        assertNull(extract("OggS".toByteArray() + ByteArray(64)))
    }

    @Test
    fun `returns null when the file has no lyrics tag`() {
        val file = flacFile(listOf("ARTIST=Someone", "TITLE=Song"))
        assertNull(extract(file))
    }
}
