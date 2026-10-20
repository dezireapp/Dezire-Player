package com.dezire.music.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses centisecond timestamps`() {
        val lyrics = LrcParser.parse("[00:12.34]Hello")
        assertNotNull(lyrics)
        assertEquals(1, lyrics!!.lines.size)
        assertEquals(12_340L, lyrics.lines[0].timeMs)
        assertEquals("Hello", lyrics.lines[0].text)
        assertTrue(lyrics.synced)
    }

    @Test
    fun `parses millisecond and short fractional timestamps`() {
        assertEquals(83_456L, LrcParser.parse("[01:23.456]A")!!.lines[0].timeMs)
        assertEquals(5_500L, LrcParser.parse("[00:05.5]A")!!.lines[0].timeMs)
        assertEquals(7_000L, LrcParser.parse("[00:07]A")!!.lines[0].timeMs)
    }

    @Test
    fun `supports multiple timestamps on one line`() {
        val lyrics = LrcParser.parse("[00:10.00][01:20.50]Chorus")!!
        assertEquals(2, lyrics.lines.size)
        assertEquals(10_000L, lyrics.lines[0].timeMs)
        assertEquals(80_500L, lyrics.lines[1].timeMs)
        assertEquals("Chorus", lyrics.lines[0].text)
        assertEquals("Chorus", lyrics.lines[1].text)
    }

    @Test
    fun `sorts unsorted lines by time`() {
        val lyrics = LrcParser.parse("[00:30.00]Second\n[00:10.00]First")!!
        assertEquals("First", lyrics.lines[0].text)
        assertEquals("Second", lyrics.lines[1].text)
    }

    @Test
    fun `applies positive offset by showing lyrics earlier`() {
        val lyrics = LrcParser.parse("[offset:+500]\n[00:10.00]A")!!
        assertEquals(9_500L, lyrics.lines[0].timeMs)
    }

    @Test
    fun `applies negative offset by showing lyrics later`() {
        val lyrics = LrcParser.parse("[offset:-500]\n[00:10.00]A")!!
        assertEquals(10_500L, lyrics.lines[0].timeMs)
    }

    @Test
    fun `offset never produces negative times`() {
        val lyrics = LrcParser.parse("[offset:5000]\n[00:01.00]A")!!
        assertEquals(0L, lyrics.lines[0].timeMs)
    }

    @Test
    fun `parses id tags`() {
        val document = LrcParser.parseDocument(
            "[ti:My Song]\n[ar:My Artist]\n[al:My Album]\n[00:01.00]Line"
        )
        assertNotNull(document)
        assertEquals("My Song", document!!.metadata.title)
        assertEquals("My Artist", document.metadata.artist)
        assertEquals("My Album", document.metadata.album)
    }

    @Test
    fun `blank and comment lines are ignored`() {
        val lyrics = LrcParser.parse(
            "\n[: this is a comment]\n\n[00:01.00]Real line\n"
        )!!
        assertEquals(1, lyrics.lines.size)
        assertEquals("Real line", lyrics.lines[0].text)
    }

    @Test
    fun `empty and metadata-only files produce no lyrics`() {
        assertNull(LrcParser.parse(""))
        assertNull(LrcParser.parse("   \n  "))
        assertNull(LrcParser.parse("[ar:Artist]\n[ti:Title]"))
    }

    @Test
    fun `malformed lines are skipped without crashing`() {
        val lyrics = LrcParser.parse(
            "[ab:cd]not a timestamp\n" +
                "[00:xx.xx]also bad\n" +
                "[00:12.34]Good line\n" +
                "[99:99]invalid seconds"
        )
        assertNotNull(lyrics)
        assertEquals(1, lyrics!!.lines.size)
        assertEquals("Good line", lyrics.lines[0].text)
        assertEquals(12_340L, lyrics.lines[0].timeMs)
    }

    @Test
    fun `source is reported as external lrc`() {
        assertEquals(LyricsSource.LRC, LrcParser.parse("[00:01.00]A")!!.source)
    }
}
