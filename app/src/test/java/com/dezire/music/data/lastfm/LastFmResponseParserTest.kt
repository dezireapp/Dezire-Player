package com.dezire.music.data.lastfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LastFmResponseParserTest {

    @Test
    fun `parses artist info with stats tags and similar artists`() {
        val tree = MiniJson.parse(
            """
            {
              "artist": {
                "name": "Radiohead",
                "mbid": "a74b1b7f-71a5-4011-9441-d0b5e4122711",
                "url": "https://www.last.fm/music/Radiohead",
                "stats": {"listeners": "5000000", "playcount": "90000000"},
                "similar": {"artist": [{"name": "Thom Yorke"}, {"name": "Atoms for Peace"}]},
                "tags": {"tag": [{"name": "alternative", "count": 100}]},
                "bio": {"summary": "English rock band"}
              }
            }
            """.trimIndent()
        )

        val info = LastFmResponseParser.artistInfo(tree)
        assertEquals("Radiohead", info.name)
        assertEquals("a74b1b7f-71a5-4011-9441-d0b5e4122711", info.mbid)
        assertEquals(5000000L, info.listeners)
        assertEquals(90000000L, info.playCount)
        assertEquals(listOf("Thom Yorke", "Atoms for Peace"), info.similarArtists)
        assertEquals("alternative", info.tags.first().name)
        assertEquals(100, info.tags.first().count)
    }

    @Test
    fun `missing mbid maps to null`() {
        val tree = MiniJson.parse("""{"artist":{"name":"Local Band","mbid":""}}""")
        assertNull(LastFmResponseParser.artistInfo(tree).mbid)
    }

    @Test
    fun `top tags accepts both single object and array`() {
        val single = MiniJson.parse("""{"toptags":{"tag":{"name":"rock","count":10}}}""")
        val array = MiniJson.parse(
            """{"toptags":{"tag":[{"name":"rock","count":10},{"name":"pop","count":5}]}}"""
        )

        assertEquals(listOf("rock"), LastFmResponseParser.topTags(single).map { it.name })
        assertEquals(
            listOf("rock", "pop"),
            LastFmResponseParser.topTags(array).map { it.name }
        )
    }

    @Test
    fun `parses track info with album and tags`() {
        val tree = MiniJson.parse(
            """
            {
              "track": {
                "name": "Karma Police",
                "mbid": "",
                "duration": "263000",
                "artist": {"name": "Radiohead"},
                "album": {"title": "OK Computer"},
                "toptags": {"tag": [{"name": "alternative rock"}]}
              }
            }
            """.trimIndent()
        )

        val info = LastFmResponseParser.trackInfo(tree)
        assertEquals("Karma Police", info.name)
        assertEquals("Radiohead", info.artist)
        assertEquals("OK Computer", info.album)
        assertNull(info.mbid)
        assertEquals(263000L, info.durationMs)
        assertEquals("alternative rock", info.tags.first().name)
    }

    @Test
    fun `detects api error payloads`() {
        val tree = MiniJson.parse("""{"error":10,"message":"Invalid API key"}""")
        val error = LastFmResponseParser.error(tree)
        assertEquals(10, error?.code)
        assertEquals("Invalid API key", error?.message)
    }

    @Test
    fun `throws on structurally invalid response`() {
        val tree = MiniJson.parse("""{"unexpected":true}""")
        assertThrows(JsonParseException::class.java) {
            LastFmResponseParser.artistInfo(tree)
        }
    }
}
