package com.dezire.music.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Covers removal of internal voice / format markers ("v1:", "v2:", ...) from
 * parsed lyrics, in both the karaoke (word-timed) and line-by-line / plain
 * paths, without damaging genuine lyric text that merely contains "v1"/"v2".
 */
class VoiceMarkerLyricsTest {

    // ------------------------------------------------------------------
    // Prefix removal
    // ------------------------------------------------------------------

    @Test
    fun `v1 marker is removed from word lyrics`() {
        val lyrics = LrcParser.parse(
            "v1: <00:04.000>Apna <00:04.500>bana <00:05.000>le"
        )!!
        assertEquals("Apna bana le", lyrics.lines[0].text)
        assertFalse(lyrics.lines[0].text.contains("v1:"))
        assertEquals(LyricSyncMode.WORD_BY_WORD, lyrics.mode)
    }

    @Test
    fun `v2 and v3 markers are removed`() {
        val v2 = LrcParser.parse("v2: <00:04.000>Apna <00:04.500>bana")!!
        assertEquals("Apna bana", v2.lines[0].text)

        val v3 = LrcParser.parse("v3: <00:04.000>Solo")!!
        assertEquals("Solo", v3.lines[0].text)

        val upper = LrcParser.parse("V2: <00:04.000>Loud")!!
        assertEquals("Loud", upper.lines[0].text)
    }

    @Test
    fun `marker is removed from line by line lyrics`() {
        val lyrics = LrcParser.parse("v1: [00:12.34]Apna bana le")!!
        assertEquals(LyricSyncMode.LINE_BY_LINE, lyrics.mode)
        assertEquals(1, lyrics.lines.size)
        assertEquals(12_340L, lyrics.lines[0].timeMs)
        assertEquals("Apna bana le", lyrics.lines[0].text)
    }

    @Test
    fun `marker after the line timestamp is removed`() {
        val lyrics = LrcParser.parse("[00:12.34]v2: Apna bana le")!!
        assertEquals("Apna bana le", lyrics.lines[0].text)
        assertEquals(12_340L, lyrics.lines[0].timeMs)
    }

    @Test
    fun `marker is removed from plain embedded lyrics`() {
        val lyrics = FlacLyricsParser.lyricsFromValue(
            "v1: Tu mera koi na hoke bhi kuchh laage\n" +
                "v2: Some other line"
        )!!
        assertEquals(LyricSyncMode.UNSYNCHRONIZED, lyrics.mode)
        assertEquals(
            listOf("Tu mera koi na hoke bhi kuchh laage", "Some other line"),
            lyrics.lines.map { it.text }
        )
    }

    // ------------------------------------------------------------------
    // Timing / karaoke preserved
    // ------------------------------------------------------------------

    @Test
    fun `timestamps are preserved after prefix removal`() {
        val lyrics = LrcParser.parse(
            "v1: <00:04.000>Apna <00:04.500>bana <00:05.000>le"
        )!!
        val words = lyrics.lines[0].words
        assertEquals(listOf(4_000L, 4_500L, 5_000L), words.map { it.startTimeMs })
        assertEquals(listOf("Apna", "bana", "le"), words.map { it.text })
    }

    @Test
    fun `word highlighting still works after prefix removal`() {
        val lyrics = LrcParser.parse(
            "v1: <00:04.000>Apna <00:04.500>bana <00:05.000>le"
        )!!
        assertEquals(-1, lyrics.activeWordIndex(0, 3_999L))
        assertEquals(0, lyrics.activeWordIndex(0, 4_000L))
        assertEquals(1, lyrics.activeWordIndex(0, 4_500L))
        assertEquals(2, lyrics.activeWordIndex(0, 5_000L))
    }

    @Test
    fun `embedded USLT with a voice marker strips it`() {
        val tag = id3v23Tag(
            id3v23Frame(
                "USLT",
                usltBody("v1: Tu mera koi na hoke bhi kuchh laage", encoding = 3)
            )
        )
        val lyrics = EmbeddedLyricsExtractor().extract(ByteArrayByteSource(tag))
        assertNotNull(lyrics)
        assertEquals(
            listOf("Tu mera koi na hoke bhi kuchh laage"),
            lyrics!!.lines.map { it.text }
        )
    }

    // ------------------------------------------------------------------
    // Genuine lyric text must survive
    // ------------------------------------------------------------------

    @Test
    fun `genuine v1 and v2 text inside a line is preserved`() {
        val lyrics = LrcParser.parse(
            "[00:01.00]I love my v1 synth\n" +
                "[00:02.00]v2 is just a word here"
        )!!
        assertEquals("I love my v1 synth", lyrics.lines[0].text)
        assertEquals("v2 is just a word here", lyrics.lines[1].text)
    }

    @Test
    fun `v1 without a colon is not treated as a marker`() {
        val lyrics = FlacLyricsParser.lyricsFromValue("v1 rocks the chorus")!!
        assertEquals("v1 rocks the chorus", lyrics.lines[0].text)
    }

    @Test
    fun `v2 as a sung word is preserved`() {
        val lyrics = LrcParser.parse("v1: <00:04.000>v2 <00:04.500>rocks")!!
        assertEquals("v2 rocks", lyrics.lines[0].text)
        assertEquals(listOf("v2", "rocks"), lyrics.lines[0].words.map { it.text })
    }

    @Test
    fun `stripMarkers leaves ordinary lines untouched`() {
        assertEquals("Hello world", LyricsMarkup.stripMarkers("  Hello world  "))
        assertEquals("Version 1: hello", LyricsMarkup.stripMarkers("Version 1: hello"))
        assertTrue(LyricsMarkup.stripMarkers("").isEmpty())
    }

    // ------------------------------------------------------------------
    // No regressions for ordinary LRC
    // ------------------------------------------------------------------

    @Test
    fun `ordinary lrc parsing is unchanged`() {
        val lyrics = LrcParser.parse("[00:12.34]Hello\n[00:15.00]World")!!
        assertEquals(LyricSyncMode.LINE_BY_LINE, lyrics.mode)
        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals("World", lyrics.lines[1].text)
        assertEquals(12_340L, lyrics.lines[0].timeMs)
    }
}
