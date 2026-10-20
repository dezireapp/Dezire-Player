package com.dezire.music.data.metadata

import com.dezire.music.data.lastfm.HttpOutcome
import com.dezire.music.data.lastfm.LastFmHttpClient
import com.dezire.music.data.lastfm.LastFmRepository
import com.dezire.music.data.lastfm.LastFmTag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataEnrichmentRepositoryTest {

    private val artistInfoBody = """
        {"artist":{"name":"Radiohead","mbid":"","tags":{"tag":[{"name":"alternative"}]}}}
    """.trimIndent()

    private val topTagsBody = """
        {"toptags":{"tag":[{"name":"art rock","count":80},{"name":"alternative","count":60}]}}
    """.trimIndent()

    @Test
    fun `empty community response never overwrites local tags`() {
        val merged = mergeMetadata(
            localTags = listOf("My Local Rock"),
            communityTags = emptyList(),
            artist = null
        )

        assertEquals(listOf("My Local Rock"), merged.rawLocalTags)
        assertTrue(merged.communityTags.isEmpty())
        assertEquals(listOf("My Local Rock"), merged.combinedTags)
    }

    @Test
    fun `community tags are appended to local tags`() {
        val merged = mergeMetadata(
            localTags = listOf("My Local Rock"),
            communityTags = listOf(LastFmTag("art rock"), LastFmTag("alternative"))
        )

        assertEquals(listOf("My Local Rock"), merged.rawLocalTags)
        assertEquals(
            listOf("My Local Rock", "art rock", "alternative"),
            merged.combinedTags
        )
    }

    @Test
    fun `enrichArtist keeps local and community tags separate`() = runBlocking {
        val repository = LastFmRepository(
            apiKey = "key",
            http = QueueHttpClient(
                mutableListOf(HttpOutcome.Ok(artistInfoBody), HttpOutcome.Ok(topTagsBody))
            )
        )
        val enrichment = MetadataEnrichmentRepository(repository, requestSpacingMs = 0)

        val metadata = enrichment.enrichArtist(
            artist = "Radiohead",
            localTags = listOf("local-only")
        )

        assertEquals(listOf("local-only"), metadata.rawLocalTags)
        assertEquals(listOf("art rock", "alternative"), metadata.communityTagNames)
        assertEquals(
            listOf("local-only", "art rock", "alternative"),
            metadata.combinedTags
        )
    }

    @Test
    fun `refreshArtists reports missing api key`() = runBlocking {
        val enrichment = MetadataEnrichmentRepository(
            LastFmRepository(apiKey = "", http = QueueHttpClient(mutableListOf())),
            requestSpacingMs = 0
        )

        val summary = enrichment.refreshArtists(listOf("Radiohead"))
        assertTrue(summary.missingApiKey)
    }

    @Test
    fun `refreshArtists counts successes failures and offline state`() = runBlocking {
        val enrichment = MetadataEnrichmentRepository(
            LastFmRepository(
                apiKey = "key",
                http = QueueHttpClient(
                    mutableListOf(
                        HttpOutcome.Ok(artistInfoBody),
                        HttpOutcome.Ok(topTagsBody),
                        HttpOutcome.NetworkError(java.io.IOException("offline")),
                        HttpOutcome.NetworkError(java.io.IOException("offline"))
                    )
                )
            ),
            requestSpacingMs = 0
        )

        val summary = enrichment.refreshArtists(listOf("Radiohead", "Portishead"))

        assertEquals(2, summary.requested)
        assertEquals(1, summary.succeeded)
        assertEquals(1, summary.failed)
        assertTrue(summary.offline)
    }

    @Test
    fun `refreshFromLocalMetadata uses the parsed primary artist`() = runBlocking {
        val http = QueueHttpClient(
            mutableListOf(HttpOutcome.Ok(artistInfoBody), HttpOutcome.Ok(topTagsBody))
        )
        val enrichment = MetadataEnrichmentRepository(
            LastFmRepository(apiKey = "key", http = http),
            requestSpacingMs = 0
        )

        val summary = enrichment.refreshFromLocalMetadata(
            listOf(LocalMetadata(rawArtist = "Radiohead; Thom Yorke", rawGenre = "Rock; Alt"))
        )

        assertEquals(1, summary.requested)
        assertEquals(1, summary.succeeded)
        assertTrue(http.urls.first().contains("artist=Radiohead"))
    }

    @Test
    fun `refreshFromLocalMetadata prefers a valid local mbid`() = runBlocking {
        val http = QueueHttpClient(
            mutableListOf(HttpOutcome.Ok(artistInfoBody), HttpOutcome.Ok(topTagsBody))
        )
        val enrichment = MetadataEnrichmentRepository(
            LastFmRepository(apiKey = "key", http = http),
            requestSpacingMs = 0
        )

        enrichment.refreshFromLocalMetadata(
            listOf(
                LocalMetadata(
                    rawArtist = "Radiohead",
                    rawArtistMbid = "a74b1b7f-71a5-4011-9441-d0b5e4122711"
                )
            )
        )

        assertTrue(
            http.urls.first().contains("mbid=a74b1b7f-71a5-4011-9441-d0b5e4122711")
        )
    }

    @Test
    fun `refreshFromLocalMetadata ignores unknown artists`() = runBlocking {
        val enrichment = MetadataEnrichmentRepository(
            LastFmRepository(apiKey = "key", http = QueueHttpClient(mutableListOf())),
            requestSpacingMs = 0
        )

        val summary = enrichment.refreshFromLocalMetadata(
            listOf(LocalMetadata(rawArtist = "Unknown Artist"))
        )

        assertEquals(0, summary.requested)
    }
}

private class QueueHttpClient(
    private val outcomes: MutableList<HttpOutcome>
) : LastFmHttpClient {

    val urls = mutableListOf<String>()

    override suspend fun get(url: String): HttpOutcome {
        urls += url
        return if (outcomes.isEmpty()) {
            HttpOutcome.NetworkError(IllegalStateException("no canned responses left"))
        } else {
            outcomes.removeAt(0)
        }
    }
}
