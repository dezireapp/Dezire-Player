package com.dezire.music.data.metadata

import com.dezire.music.data.lastfm.LastFmMetadata
import com.dezire.music.data.lastfm.LastFmRepository
import com.dezire.music.data.lastfm.LastFmResult
import com.dezire.music.data.model.Song
import kotlinx.coroutines.delay

/*
 * High-level metadata service that turns the low-level [LastFmRepository]
 * into library-aware operations.
 *
 * Artist, album and track lookups are combined here and returned as
 * [LocalMetadata]-backed [com.dezire.music.data.lastfm.LastFmMetadata].
 * Local file tags passed in by the caller are never modified: community
 * tags are appended alongside them, so an empty API response can never
 * erase the user's own tags.
 */
class MetadataEnrichmentRepository(
    private val lastFmRepository: LastFmRepository,
    private val requestSpacingMs: Long = 250L
) {

    val isConfigured: Boolean
        get() = lastFmRepository.isConfigured

    suspend fun enrichArtist(
        artist: String,
        mbid: String? = null,
        localTags: List<String> = emptyList()
    ): LastFmMetadata {
        if (artist.isBlank() && mbid.isNullOrBlank()) return mergeMetadata(localTags, emptyList())
        val info = lastFmRepository.artistInfo(artist, mbid).orNull()
        val tags = lastFmRepository.artistTopTags(artist, mbid).orNull()
        return mergeMetadata(
            localTags = localTags,
            communityTags = tags ?: info?.tags.orEmpty(),
            artist = info
        )
    }

    suspend fun enrichAlbum(
        artist: String,
        album: String,
        mbid: String? = null,
        localTags: List<String> = emptyList()
    ): LastFmMetadata {
        if (album.isBlank() && mbid.isNullOrBlank()) return mergeMetadata(localTags, emptyList())
        val info = lastFmRepository.albumInfo(artist, album, mbid).orNull()
        val tags = lastFmRepository.albumTopTags(artist, album, mbid).orNull()
        return mergeMetadata(
            localTags = localTags,
            communityTags = tags ?: info?.tags.orEmpty(),
            album = info
        )
    }

    suspend fun enrichTrack(
        artist: String,
        track: String,
        mbid: String? = null,
        localTags: List<String> = emptyList()
    ): LastFmMetadata {
        if (track.isBlank() && mbid.isNullOrBlank()) return mergeMetadata(localTags, emptyList())
        val info = lastFmRepository.trackInfo(artist, track, mbid).orNull()
        return mergeMetadata(
            localTags = localTags,
            communityTags = info?.tags.orEmpty(),
            track = info
        )
    }

    /*
     * Warms the Last.fm cache for the artists in [songs]. Requests are
     * de-duplicated by artist and spaced out to respect the public rate
     * limit. Never throws: individual failures are counted and reported.
     */
    suspend fun refreshLibrary(
        songs: List<Song>,
        maxArtists: Int = 50
    ): MetadataRefreshSummary =
        refreshArtists(songs.map { it.artist }, maxArtists)

    /*
     * Artist-only variant of [refreshLibrary] that does not depend on the
     * Android MediaStore types, which keeps it directly unit-testable.
     */
    suspend fun refreshArtists(
        artists: List<String>,
        maxArtists: Int = 50
    ): MetadataRefreshSummary = refreshByArtist(
        candidates = artists
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("Unknown Artist", ignoreCase = true) }
            .map { it to null },
        maxItems = maxArtists
    )

    /*
     * Refreshes metadata using the parsed local file tags. Local tags are
     * passed through to Last.fm lookups but are never overwritten; the
     * artist MBID from the local tags is used when present.
     */
    suspend fun refreshFromLocalMetadata(
        localItems: List<LocalMetadata>,
        maxItems: Int = 50
    ): MetadataRefreshSummary = refreshByArtist(
        candidates = localItems
            .mapNotNull { local ->
                val artist = local.primaryArtist?.trim()
                if (artist.isNullOrEmpty() || artist.equals("Unknown Artist", ignoreCase = true)) {
                    null
                } else {
                    artist to local.artistMbid
                }
            },
        maxItems = maxItems
    )

    private suspend fun refreshByArtist(
        candidates: List<Pair<String, String?>>,
        maxItems: Int
    ): MetadataRefreshSummary {
        if (!lastFmRepository.isConfigured) {
            return MetadataRefreshSummary(missingApiKey = true)
        }

        val uniqueArtists = candidates
            .distinctBy { it.first.lowercase() }
            .take(maxItems)

        var succeeded = 0
        var failed = 0
        var offline = false
        var rateLimited = false

        uniqueArtists.forEachIndexed { index, (artist, mbid) ->
            when (lastFmRepository.artistInfo(artist, mbid)) {
                is LastFmResult.Success -> succeeded++
                is LastFmResult.Offline -> {
                    offline = true
                    failed++
                }
                is LastFmResult.RateLimited -> {
                    rateLimited = true
                    failed++
                }
                else -> failed++
            }
            // Warm the tag cache as well; failures here are non-fatal.
            lastFmRepository.artistTopTags(artist, mbid)
            if (index < uniqueArtists.lastIndex && requestSpacingMs > 0) {
                delay(requestSpacingMs)
            }
        }

        return MetadataRefreshSummary(
            requested = uniqueArtists.size,
            succeeded = succeeded,
            failed = failed,
            offline = offline,
            rateLimited = rateLimited
        )
    }
}

/*
 * Result of a library-wide metadata refresh.
 */
data class MetadataRefreshSummary(
    val requested: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val offline: Boolean = false,
    val rateLimited: Boolean = false,
    val missingApiKey: Boolean = false
)

private fun <T> LastFmResult<T>.orNull(): T? =
    (this as? LastFmResult.Success<T>)?.value
