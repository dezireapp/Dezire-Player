package com.dezire.music.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsActiveIndexTest {

    private val synced = Lyrics(
        lines = listOf(
            LyricLine(0L, "line 0"),
            LyricLine(1_000L, "line 1"),
            LyricLine(2_000L, "line 2"),
        ),
        synced = true,
    )

    @Test
    fun `before the first timestamp no line is active`() {
        val later = Lyrics(
            lines = listOf(LyricLine(5_000L, "later")),
            synced = true,
        )
        assertEquals(-1, later.activeIndex(0L))
        assertEquals(-1, later.activeIndex(4_999L))
        assertEquals(0, later.activeIndex(5_000L))
    }

    @Test
    fun `active index advances with playback position`() {
        assertEquals(0, synced.activeIndex(0L))
        assertEquals(0, synced.activeIndex(999L))
        assertEquals(1, synced.activeIndex(1_000L))
        assertEquals(1, synced.activeIndex(1_999L))
        assertEquals(2, synced.activeIndex(2_000L))
        assertEquals(2, synced.activeIndex(60_000L))
    }

    @Test
    fun `seeking backwards moves the active index back`() {
        assertEquals(2, synced.activeIndex(2_500L))
        assertEquals(0, synced.activeIndex(500L))
    }

    @Test
    fun `unsynchronised lyrics always report line zero`() {
        val plain = Lyrics(
            lines = listOf(LyricLine(0L, "a"), LyricLine(0L, "b")),
            synced = false,
        )
        assertEquals(0, plain.activeIndex(0L))
        assertEquals(0, plain.activeIndex(120_000L))
    }

    @Test
    fun `empty lyrics have no active line`() {
        assertEquals(-1, Lyrics(emptyList(), synced = true).activeIndex(1_000L))
    }
}
