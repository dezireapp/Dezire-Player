package com.dezire.music.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Exercises the enhanced (word-timed) LRC support, the automatic sync-mode
 * selection and the playback cursor helpers on [Lyrics]. The exact
 * timestamp conventions under test are:
 *
 *   line tag: [mm:ss.xx] / [mm:ss.xxx]
 *   word tag: <mm:ss.xxx>
 *   marker:   a leading "v1:" / "v2:" is stripped and never displayed
 */
class KaraokeLyricsTest {

    private val v1Format = "v1: <00:04.000>Apna <00:04.500>bana <00:05.000>le"

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    @Test
    fun `v1 word format parses to word by word`() {
        val lyrics = LrcParser.parse(v1Format)!!
        assertTrue(lyrics.synced)
        assertEquals(LyricSyncMode.WORD_BY_WORD, lyrics.mode)
        assertEquals(1, lyrics.lines.size)
        assertEquals("Apna bana le", lyrics.lines[0].text)
        assertEquals(listOf("Apna", "bana", "le"), lyrics.lines[0].words.map { it.text })
        assertEquals(listOf(4_000L, 4_500L, 5_000L), lyrics.lines[0].words.map { it.startTimeMs })
    }

    @Test
    fun `visible timestamp markup and version marker are removed`() {
        val text = LrcParser.parse(v1Format)!!.lines[0].text
        assertFalse(text.contains("<"))
        assertFalse(text.contains(">"))
        assertFalse(text.contains("["))
        assertFalse(text.contains("v1:"))
    }

    @Test
    fun `standard lrc stays line by line and is not karaoke`() {
        val lyrics = LrcParser.parse("[00:12.34]Apna bana le\n[00:15.00]Mera dil")!!
        assertEquals(LyricSyncMode.LINE_BY_LINE, lyrics.mode)
        assertEquals("Apna bana le", lyrics.lines[0].text)
        assertEquals("Mera dil", lyrics.lines[1].text)
        assertTrue(lyrics.lines.all { it.words.isEmpty() })
        assertEquals(-1, lyrics.activeWordIndex(0, 12_340L))
    }

    @Test
    fun `plain text embedded lyrics are unsynchronized`() {
        val lyrics = FlacLyricsParser.lyricsFromValue("Just some words\nAnd more words")!!
        assertEquals(LyricSyncMode.UNSYNCHRONIZED, lyrics.mode)
        assertFalse(lyrics.synced)
        assertEquals(-1, lyrics.activeWordIndex(0, 5_000L))
    }

    @Test
    fun `multiple line timestamps on one line duplicate the same content`() {
        val lyrics = LrcParser.parse("[00:10.00][01:20.50]Chorus")!!
        assertEquals(LyricSyncMode.LINE_BY_LINE, lyrics.mode)
        assertEquals(2, lyrics.lines.size)
        assertEquals(10_000L, lyrics.lines[0].timeMs)
        assertEquals(80_500L, lyrics.lines[1].timeMs)
        assertEquals("Chorus", lyrics.lines[1].text)
    }

    @Test
    fun `offset shifts word timestamps too`() {
        val lyrics = LrcParser.parse(
            "[offset:+500]\nv1: <00:04.000>Apna <00:04.500>bana"
        )!!
        assertEquals(3_500L, lyrics.lines[0].timeMs)
        assertEquals(3_500L, lyrics.lines[0].words[0].startTimeMs)
        assertEquals(4_000L, lyrics.lines[0].words[1].startTimeMs)
    }

    @Test
    fun `word end times are inferred from the next word`() {
        val words = LrcParser.parse(v1Format)!!.lines[0].words
        assertEquals(4_500L, words[0].endTimeMs)
        assertEquals(5_000L, words[1].endTimeMs)
        assertNull(words[2].endTimeMs)
    }

    @Test
    fun `trailing word tag becomes the end time of the previous word`() {
        val words = LrcParser.parse("v1: <00:04.000>le<00:04.800>")!!.lines[0].words
        assertEquals(1, words.size)
        assertEquals("le", words[0].text)
        assertEquals(4_000L, words[0].startTimeMs)
        assertEquals(4_800L, words[0].endTimeMs)
    }

    // ------------------------------------------------------------------
    // Playback cursor
    // ------------------------------------------------------------------

    @Test
    fun `active word follows playback position`() {
        val lyrics = LrcParser.parse(v1Format)!!
        assertEquals(-1, lyrics.activeWordIndex(0, 3_999L))
        assertEquals(0, lyrics.activeWordIndex(0, 4_000L))
        assertEquals(0, lyrics.activeWordIndex(0, 4_499L))
        assertEquals(1, lyrics.activeWordIndex(0, 4_500L))
        assertEquals(1, lyrics.activeWordIndex(0, 4_999L))
        assertEquals(2, lyrics.activeWordIndex(0, 5_000L))
        assertEquals(2, lyrics.activeWordIndex(0, 9_999L))
    }

    @Test
    fun `active line selection in word mode`() {
        val lyrics = LrcParser.parse(
            "v1: <00:04.000>Apna <00:04.500>bana\n" +
                "<00:05.000>le<00:06.000>"
        )!!
        assertEquals(2, lyrics.lines.size)
        assertEquals("Apna bana", lyrics.lines[0].text)
        assertEquals("le", lyrics.lines[1].text)
        assertEquals(-1, lyrics.activeIndex(3_999L))
        assertEquals(0, lyrics.activeIndex(4_000L))
        assertEquals(1, lyrics.activeIndex(5_000L))
    }

    @Test
    fun `seeking backward and forward selects the right word`() {
        val lyrics = LrcParser.parse(v1Format)!!
        assertEquals(2, lyrics.activeWordIndex(0, 5_500L))
        assertEquals(0, lyrics.activeWordIndex(0, 4_100L))
        assertEquals(1, lyrics.activeWordIndex(0, 4_700L))
    }

    // ------------------------------------------------------------------
    // Robustness and mode selection
    // ------------------------------------------------------------------

    @Test
    fun `malformed and empty lyrics do not crash`() {
        assertNull(LrcParser.parse(""))
        assertNull(LrcParser.parse("   \n  "))
        assertNull(LrcParser.parse("just words, no tags"))
    }

    @Test
    fun `an invalid word tag is stripped but its text survives`() {
        val lyrics = LrcParser.parse("v1: <99:99>bad <00:04.000>good")!!
        assertNotNull(lyrics)
        assertEquals("bad good", lyrics.lines[0].text)
        assertEquals(listOf("bad", "good"), lyrics.lines[0].words.map { it.text })
    }

    @Test
    fun `arbitrary angle bracket text is not treated as a timestamp`() {
        val lyrics = LrcParser.parse("[00:01.00]Hello <3 world")!!
        assertEquals("Hello <3 world", lyrics.lines[0].text)
        assertEquals(LyricSyncMode.LINE_BY_LINE, lyrics.mode)
    }

    @Test
    fun `mode auto selection across the three shapes`() {
        val word = LrcParser.parse("v1: <00:01.000>a <00:02.000>b")!!
        val line = LrcParser.parse("[00:01.00]a\n[00:02.00]b")!!
        val plain = FlacLyricsParser.lyricsFromValue("a\nb")!!
        assertEquals(LyricSyncMode.WORD_BY_WORD, word.mode)
        assertEquals(LyricSyncMode.LINE_BY_LINE, line.mode)
        assertEquals(LyricSyncMode.UNSYNCHRONIZED, plain.mode)
    }
}
