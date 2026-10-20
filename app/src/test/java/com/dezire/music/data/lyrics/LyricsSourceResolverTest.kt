package com.dezire.music.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsSourceResolverTest {

    private fun synced(text: String, time: Long = 0L) = Lyrics(
        lines = listOf(LyricLine(time, text)),
        synced = true,
    )

    private fun unsynced(text: String) = Lyrics(
        lines = listOf(LyricLine(0L, text)),
        synced = false,
    )

    @Test
    fun `synchronised embedded wins over lrc and plain embedded`() {
        val resolved = LyricsSourceResolver.resolve(
            embedded = synced("embedded"),
            external = synced("lrc"),
        )
        assertEquals("embedded", resolved!!.lines[0].text)
        assertEquals(LyricsSource.EMBEDDED_SYNCED, resolved.source)
    }

    @Test
    fun `external lrc wins over plain embedded`() {
        val resolved = LyricsSourceResolver.resolve(
            embedded = unsynced("plain embedded"),
            external = synced("lrc"),
        )
        assertEquals("lrc", resolved!!.lines[0].text)
        assertEquals(LyricsSource.LRC, resolved.source)
    }

    @Test
    fun `plain embedded is used when no lrc exists`() {
        val resolved = LyricsSourceResolver.resolve(
            embedded = unsynced("plain embedded"),
            external = null,
        )
        assertEquals("plain embedded", resolved!!.lines[0].text)
        assertEquals(LyricsSource.EMBEDDED_UNSYNCED, resolved.source)
    }

    @Test
    fun `plain embedded is used when the lrc is invalid`() {
        // A parser reports an unusable .lrc as an empty Lyrics instance.
        val emptyLrc = Lyrics(emptyList(), synced = true)
        val resolved = LyricsSourceResolver.resolve(
            embedded = unsynced("plain embedded"),
            external = emptyLrc,
        )
        assertEquals("plain embedded", resolved!!.lines[0].text)
    }

    @Test
    fun `no source yields no lyrics`() {
        assertNull(LyricsSourceResolver.resolve(embedded = null, external = null))
        assertNull(
            LyricsSourceResolver.resolve(
                embedded = Lyrics(emptyList(), synced = false),
                external = Lyrics(emptyList(), synced = true),
            )
        )
    }

    @Test
    fun `track change swaps to the new track lyrics`() {
        val firstTrack = LyricsSourceResolver.resolve(synced("track one"), null)
        val secondTrack = LyricsSourceResolver.resolve(null, synced("track two"))
        assertEquals("track one", firstTrack!!.lines[0].text)
        assertEquals("track two", secondTrack!!.lines[0].text)
        assertEquals(LyricsSource.LRC, secondTrack.source)
    }
}
