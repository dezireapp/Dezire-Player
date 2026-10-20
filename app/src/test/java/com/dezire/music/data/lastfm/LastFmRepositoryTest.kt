package com.dezire.music.data.lastfm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LastFmRepositoryTest {

    private val apiKey = "test-api-key"

    private val artistInfoBody = """
        {
          "artist": {
            "name": "Radiohead",
            "mbid": "a74b1b7f-71a5-4011-9441-d0b5e4122711",
            "url": "https://www.last.fm/music/Radiohead",
            "stats": {"listeners": "5000000", "playcount": "90000000"},
            "tags": {"tag": [{"name": "alternative", "count": 100}]}
          }
        }
    """.trimIndent()

    @Test
    fun `missing api key returns MissingApiKey without a network call`() = runBlocking {
        val http = FakeHttpClient(mutableListOf())
        val repository = LastFmRepository(apiKey = "", http = http)

        val result = repository.artistInfo("Radiohead")

        assertTrue(result is LastFmResult.MissingApiKey)
        assertEquals(0, http.callCount)
    }

    @Test
    fun `http 429 maps to RateLimited`() = runBlocking {
        val http = FakeHttpClient(
            mutableListOf(HttpOutcome.HttpError(429, "slow down", retryAfterSeconds = 30))
        )
        val repository = LastFmRepository(apiKey = apiKey, http = http)

        val result = repository.artistInfo("Radiohead")
        assertTrue(result is LastFmResult.RateLimited)
        assertEquals(30L, (result as LastFmResult.RateLimited).retryAfterSeconds)
    }

    @Test
    fun `network failure maps to Offline`() = runBlocking {
        val http = FakeHttpClient(
            mutableListOf(HttpOutcome.NetworkError(java.io.IOException("no network")))
        )
        val repository = LastFmRepository(apiKey = apiKey, http = http)

        assertTrue(repository.artistInfo("Radiohead") is LastFmResult.Offline)
    }

    @Test
    fun `malformed body maps to InvalidResponse`() = runBlocking {
        val http = FakeHttpClient(mutableListOf(HttpOutcome.Ok("{not valid json")))
        val repository = LastFmRepository(apiKey = apiKey, http = http)

        assertTrue(repository.artistInfo("Radiohead") is LastFmResult.InvalidResponse)
    }

    @Test
    fun `api level error code 10 maps to MissingApiKey`() = runBlocking {
        val http = FakeHttpClient(
            mutableListOf(HttpOutcome.Ok("""{"error":10,"message":"Invalid API key"}"""))
        )
        val repository = LastFmRepository(apiKey = apiKey, http = http)

        assertTrue(repository.artistInfo("Radiohead") is LastFmResult.MissingApiKey)
    }

    @Test
    fun `successful response is parsed and cached`() = runBlocking {
        val http = FakeHttpClient(mutableListOf(HttpOutcome.Ok(artistInfoBody)))
        val repository = LastFmRepository(
            apiKey = apiKey,
            http = http,
            cache = LastFmCache(directory = null)
        )

        val first = repository.artistInfo("Radiohead")
        val second = repository.artistInfo("Radiohead")

        assertTrue(first is LastFmResult.Success)
        assertEquals("Radiohead", (first as LastFmResult.Success).value.name)
        assertTrue(second is LastFmResult.Success)
        assertEquals(1, http.callCount)
    }

    @Test
    fun `disk cache survives repository recreation`() = runBlocking {
        val cacheDir = Files.createTempDirectory("lastfm-cache").toFile()
        try {
            val firstHttp = FakeHttpClient(mutableListOf(HttpOutcome.Ok(artistInfoBody)))
            LastFmRepository(
                apiKey = apiKey,
                http = firstHttp,
                cache = LastFmCache(directory = cacheDir)
            ).artistInfo("Radiohead")
            assertEquals(1, firstHttp.callCount)

            val secondHttp = FakeHttpClient(mutableListOf())
            val cached = LastFmRepository(
                apiKey = apiKey,
                http = secondHttp,
                cache = LastFmCache(directory = cacheDir)
            ).artistInfo("Radiohead")

            assertTrue(cached is LastFmResult.Success)
            assertEquals(0, secondHttp.callCount)
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `request url uses the api key and json format`() = runBlocking {
        val http = FakeHttpClient(mutableListOf(HttpOutcome.Ok(artistInfoBody)))
        LastFmRepository(apiKey = apiKey, http = http).artistInfo("Sigur Rós")

        val url = http.urls.single()
        assertTrue(url.contains("method=artist.getinfo"))
        assertTrue(url.contains("api_key=test-api-key"))
        assertTrue(url.contains("format=json"))
        assertTrue(url.contains("artist=Sigur+R%C3%B3s"))
    }
}

private class FakeHttpClient(
    private val outcomes: MutableList<HttpOutcome>
) : LastFmHttpClient {

    var callCount = 0
    val urls = mutableListOf<String>()

    override suspend fun get(url: String): HttpOutcome {
        callCount++
        urls += url
        return if (outcomes.isEmpty()) {
            HttpOutcome.NetworkError(IllegalStateException("no canned responses left"))
        } else {
            outcomes.removeAt(0)
        }
    }
}
