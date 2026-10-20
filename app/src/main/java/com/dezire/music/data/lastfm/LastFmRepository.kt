package com.dezire.music.data.lastfm

import java.net.URLEncoder

/*
 * Read-only Last.fm metadata repository.
 *
 * Uses the public Web API 2.0 endpoints that only require an application
 * API key. None of the methods issue authenticated or signed requests, so
 * the shared secret is never needed here (and never shipped in the APK
 * unless write auth is explicitly enabled at build time).
 *
 * Requests run off the main thread, successful responses are cached (see
 * [LastFmCache]) and every failure mode - missing key, offline, rate limit,
 * malformed body, API error - is surfaced as a [LastFmResult] rather than
 * an exception.
 */
class LastFmRepository(
    private val apiKey: String,
    private val http: LastFmHttpClient = UrlConnectionLastFmHttpClient(),
    private val cache: LastFmCache? = null,
    private val baseUrl: String = DEFAULT_BASE_URL
) {

    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    suspend fun artistInfo(
        artist: String,
        mbid: String? = null
    ): LastFmResult<LastFmArtistInfo> = request(
        method = "artist.getinfo",
        params = artistParams(artist, mbid)
    ) { LastFmResponseParser.artistInfo(it) }

    suspend fun artistTopTags(
        artist: String,
        mbid: String? = null
    ): LastFmResult<List<LastFmTag>> = request(
        method = "artist.gettoptags",
        params = artistParams(artist, mbid)
    ) { LastFmResponseParser.topTags(it) }

    suspend fun albumInfo(
        artist: String,
        album: String,
        mbid: String? = null
    ): LastFmResult<LastFmAlbumInfo> = request(
        method = "album.getinfo",
        params = albumParams(artist, album, mbid)
    ) { LastFmResponseParser.albumInfo(it) }

    suspend fun albumTopTags(
        artist: String,
        album: String,
        mbid: String? = null
    ): LastFmResult<List<LastFmTag>> = request(
        method = "album.gettoptags",
        params = albumParams(artist, album, mbid)
    ) { LastFmResponseParser.topTags(it) }

    suspend fun trackInfo(
        artist: String,
        track: String,
        mbid: String? = null
    ): LastFmResult<LastFmTrackInfo> = request(
        method = "track.getinfo",
        params = trackParams(artist, track, mbid)
    ) { LastFmResponseParser.trackInfo(it) }

    fun clearCache() {
        cache?.clear()
    }

    private fun artistParams(artist: String, mbid: String?): List<Pair<String, String>> =
        buildList {
            if (mbid.isNullOrBlank()) add("artist" to artist) else add("mbid" to mbid)
        }

    private fun albumParams(
        artist: String,
        album: String,
        mbid: String?
    ): List<Pair<String, String>> = buildList {
        if (mbid.isNullOrBlank()) {
            add("artist" to artist)
            add("album" to album)
        } else {
            add("mbid" to mbid)
        }
    }

    private fun trackParams(
        artist: String,
        track: String,
        mbid: String?
    ): List<Pair<String, String>> = buildList {
        if (mbid.isNullOrBlank()) {
            add("artist" to artist)
            add("track" to track)
        } else {
            add("mbid" to mbid)
        }
    }

    private suspend fun <T> request(
        method: String,
        params: List<Pair<String, String>>,
        parse: (Any?) -> T
    ): LastFmResult<T> {
        if (!isConfigured) {
            return LastFmResult.MissingApiKey(
                "No Last.fm API key configured. Add lastfm.apiKey to lastfm.properties."
            )
        }

        val cacheKey = cache?.let { it.key(method, params) }
        if (cacheKey != null) {
            cache?.get(cacheKey)?.let { cached ->
                return try {
                    LastFmResult.Success(parse(MiniJson.parse(cached)))
                } catch (t: Throwable) {
                    // Corrupt cache entry: fall through to the network.
                    null
                } ?: fetch(method, params, cacheKey, parse)
            }
        }

        return fetch(method, params, cacheKey, parse)
    }

    private suspend fun <T> fetch(
        method: String,
        params: List<Pair<String, String>>,
        cacheKey: String?,
        parse: (Any?) -> T
    ): LastFmResult<T> {
        val url = buildUrl(method, params)
        return when (val outcome = http.get(url)) {
            is HttpOutcome.NetworkError -> LastFmResult.Offline(
                outcome.cause.message ?: "Network unavailable"
            )

            is HttpOutcome.HttpError -> when {
                outcome.code == 429 -> LastFmResult.RateLimited(
                    retryAfterSeconds = outcome.retryAfterSeconds,
                    message = "Last.fm rate limit reached"
                )
                else -> LastFmResult.Error(
                    code = outcome.code,
                    message = outcome.body?.takeIf { it.isNotBlank() }
                        ?: "Last.fm request failed with HTTP ${outcome.code}"
                )
            }

            is HttpOutcome.Ok -> parseBody(outcome.body, cacheKey, parse)
        }
    }

    private fun <T> parseBody(
        body: String,
        cacheKey: String?,
        parse: (Any?) -> T
    ): LastFmResult<T> {
        val tree = try {
            MiniJson.parse(body)
        } catch (t: Throwable) {
            return LastFmResult.InvalidResponse("Malformed Last.fm response: ${t.message}")
        }

        LastFmResponseParser.error(tree)?.let { error ->
            return when (error.code) {
                10, 26 -> LastFmResult.MissingApiKey(
                    error.message ?: "Last.fm rejected the API key"
                )
                29 -> LastFmResult.RateLimited(
                    message = error.message ?: "Last.fm rate limit reached"
                )
                else -> LastFmResult.Error(error.code, error.message ?: "Last.fm error")
            }
        }

        return try {
            val value = parse(tree)
            if (cacheKey != null) cache?.put(cacheKey, body)
            LastFmResult.Success(value)
        } catch (t: Throwable) {
            LastFmResult.InvalidResponse("Unexpected Last.fm response: ${t.message}")
        }
    }

    private fun buildUrl(method: String, params: List<Pair<String, String>>): String {
        val encoded = buildList {
            add("method" to method)
            add("api_key" to apiKey)
            add("format" to "json")
            addAll(params)
        }.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "$baseUrl?$encoded"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val DEFAULT_BASE_URL = "https://ws.audioscrobbler.com/2.0/"
    }
}

/*
 * Cache keys must be stable across processes and safe to use as file
 * names, so parameters are sorted and the method is included.
 */
private fun LastFmCache.key(method: String, params: List<Pair<String, String>>): String =
    (listOf(method) + params.sortedBy { it.first }.map { "${it.first}=${it.second}" })
        .joinToString("\u0000")
