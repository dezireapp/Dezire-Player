package com.dezire.music.data.lastfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertThrows

class MiniJsonTest {

    @Test
    fun `parses nested objects arrays and escapes`() {
        val tree = MiniJson.parse(
            """
            {
              "artist": {
                "name": "Sigur R\u00f3s",
                "tags": {"tag": [{"name": "post-rock"}, {"name": "ambient"}]},
                "listeners": "1234567",
                "ok": true,
                "missing": null
              }
            }
            """.trimIndent()
        )

        val artist = tree.asJsonObject()["artist"].asJsonObject()
        assertEquals("Sigur Rós", artist["name"].asJsonString())
        val tags = artist["tags"].asJsonObject()["tag"].asJsonArray()
        assertEquals(2, tags.size)
        assertEquals("post-rock", tags[0].asJsonObject()["name"].asJsonString())
        assertEquals(1234567L, artist["listeners"].asJsonLong())
        assertEquals(true, artist["ok"].asJsonBoolean())
        assertNull(artist["missing"].asJsonString())
    }

    @Test
    fun `parses empty object and array`() {
        assertEquals(emptyMap<String, Any?>(), MiniJson.parse("{}"))
        assertEquals(emptyList<Any?>(), MiniJson.parse("[]"))
    }

    @Test
    fun `throws on malformed input`() {
        assertThrows(JsonParseException::class.java) { MiniJson.parse("{not json") }
        assertThrows(JsonParseException::class.java) { MiniJson.parse("{\"a\":}") }
        assertThrows(JsonParseException::class.java) { MiniJson.parse("{\"a\":1} trailing") }
    }
}
