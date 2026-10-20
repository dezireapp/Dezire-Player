package com.dezire.music.data.lastfm

/*
 * Typed domain models for the Last.fm read-only metadata endpoints used by
 * the app. Every optional field is nullable because Last.fm frequently
 * omits artists without an MBID (unsigned/local files) or empty tag lists.
 */

/*
 * A single community tag returned by the getTopTags endpoints.
 */
data class LastFmTag(
    val name: String,
    val count: Int? = null,
    val url: String? = null
)

data class LastFmArtistInfo(
    val name: String,
    val mbid: String? = null,
    val url: String? = null,
    val listeners: Long? = null,
    val playCount: Long? = null,
    val tags: List<LastFmTag> = emptyList(),
    val similarArtists: List<String> = emptyList(),
    val bioSummary: String? = null
)

data class LastFmAlbumInfo(
    val name: String,
    val artist: String? = null,
    val mbid: String? = null,
    val url: String? = null,
    val listeners: Long? = null,
    val playCount: Long? = null,
    val tags: List<LastFmTag> = emptyList(),
    val releaseDate: String? = null
)

data class LastFmTrackInfo(
    val name: String,
    val artist: String? = null,
    val album: String? = null,
    val mbid: String? = null,
    val url: String? = null,
    val durationMs: Long? = null,
    val tags: List<LastFmTag> = emptyList()
)

/*
 * A Last.fm API-level error payload (the body is still HTTP 200).
 */
data class LastFmError(
    val code: Int?,
    val message: String?
)

/*
 * Outcome of a Last.fm request. The failure cases are modelled explicitly
 * so callers can react to offline use, missing credentials and rate limits
 * instead of having to inspect exceptions.
 */
sealed interface LastFmResult<out T> {
    data class Success<T>(val value: T) : LastFmResult<T>

    data class MissingApiKey(val message: String) : LastFmResult<Nothing>

    data class Offline(val message: String) : LastFmResult<Nothing>

    data class RateLimited(
        val retryAfterSeconds: Long? = null,
        val message: String
    ) : LastFmResult<Nothing>

    data class InvalidResponse(val message: String) : LastFmResult<Nothing>

    data class Error(
        val code: Int? = null,
        val message: String
    ) : LastFmResult<Nothing>
}

/*
 * Combined metadata for one artist/album/track. Local file tags and Last.fm
 * community tags are deliberately kept in separate lists so a network
 * refresh can never silently overwrite the user's own tags with an empty
 * API response.
 */
data class LastFmMetadata(
    val rawLocalTags: List<String> = emptyList(),
    val communityTags: List<LastFmTag> = emptyList(),
    val artist: LastFmArtistInfo? = null,
    val album: LastFmAlbumInfo? = null,
    val track: LastFmTrackInfo? = null
) {
    val communityTagNames: List<String>
        get() = communityTags.map { it.name }

    /*
     * Local tags always come first; community tags are appended without
     * removing or replacing any local value.
     */
    val combinedTags: List<String>
        get() = (rawLocalTags + communityTagNames).distinctBy { it.lowercase() }
}
