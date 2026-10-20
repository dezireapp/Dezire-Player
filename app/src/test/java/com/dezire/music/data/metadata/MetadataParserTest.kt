package com.dezire.music.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataParserTest {

    @Test
    fun `splits artists on semicolons`() {
        assertEquals(
            listOf("Artist One", "Artist Two"),
            MetadataParser.parseArtistNames("Artist One; Artist Two")
        )
    }

    @Test
    fun `splits artists on collaboration markers`() {
        assertEquals(
            listOf("Main Artist", "Featured Artist"),
            MetadataParser.parseArtistNames("Main Artist feat. Featured Artist")
        )
        assertEquals(
            listOf("Main Artist", "Feature Two", "Feature Three"),
            MetadataParser.parseArtistNames("Main Artist FEATURING Feature Two; Feature Three")
        )
    }

    @Test
    fun `does not split on ampersand`() {
        assertEquals(
            listOf("Simon & Garfunkel"),
            MetadataParser.parseArtistNames("Simon & Garfunkel")
        )
    }

    @Test
    fun `does not split on slash or comma`() {
        assertEquals(listOf("AC/DC"), MetadataParser.parseArtistNames("AC/DC"))
        assertEquals(
            listOf("Earth, Wind & Fire"),
            MetadataParser.parseArtistNames("Earth, Wind & Fire")
        )
    }

    @Test
    fun `deduplicates artist names case-insensitively`() {
        assertEquals(
            listOf("Queen", "Adam Lambert"),
            MetadataParser.parseArtistNames("Queen; Adam Lambert; queen")
        )
    }

    @Test
    fun `blank and unknown artist values are ignored`() {
        assertTrue(MetadataParser.parseArtistNames(null).isEmpty())
        assertTrue(MetadataParser.parseArtistNames("   ").isEmpty())
        assertTrue(MetadataParser.parseArtistNames("<unknown>").isEmpty())
    }

    @Test
    fun `genres keep compound values intact`() {
        assertEquals(
            listOf("R&B", "Hip-Hop/Rap"),
            MetadataParser.parseGenres("R&B; Hip-Hop/Rap")
        )
        assertEquals(listOf("Pop/Rock"), MetadataParser.parseGenres("Pop/Rock"))
    }

    @Test
    fun `local metadata preserves raw values alongside parsed values`() {
        val metadata = LocalMetadata(
            rawArtist = "Queen; Adam Lambert",
            rawAlbumArtist = "Queen",
            rawGenre = "Rock; Pop/Rock",
            rawArtistMbid = "5e4f2f5e-8f1f-4b1a-9c1a-8f1f4b1a9c1a",
            rawAlbumMbid = "",
            rawTrackMbid = "not-a-mbid"
        )

        assertEquals("Queen; Adam Lambert", metadata.rawArtist)
        assertEquals(listOf("Queen", "Adam Lambert"), metadata.artistNames)
        assertEquals(listOf("Queen"), metadata.albumArtistNames)
        assertEquals(listOf("Rock", "Pop/Rock"), metadata.genres)
        assertEquals("5e4f2f5e-8f1f-4b1a-9c1a-8f1f4b1a9c1a", metadata.artistMbid)
        assertNull(metadata.albumMbid)
        assertNull(metadata.trackMbid)
    }
}
