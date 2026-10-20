package com.dezire.music.data.metadata

import com.dezire.music.data.lastfm.LastFmArtistInfo
import com.dezire.music.data.lastfm.LastFmAlbumInfo
import com.dezire.music.data.lastfm.LastFmMetadata
import com.dezire.music.data.lastfm.LastFmTag
import com.dezire.music.data.lastfm.LastFmTrackInfo

/*
 * Local metadata read from a file's tags.
 *
 * The raw strings are preserved exactly as they were read so nothing is
 * lost, while the parsed views expose the individual artists, album
 * artists, genres and MBIDs. Parsing is lazy so repeated access does not
 * re-split the strings.
 */
data class LocalMetadata(
    val rawArtist: String? = null,
    val rawAlbumArtist: String? = null,
    val rawAlbum: String? = null,
    val rawGenre: String? = null,
    val rawArtistMbid: String? = null,
    val rawAlbumMbid: String? = null,
    val rawTrackMbid: String? = null
) {
    val artistNames: List<String> by lazy { MetadataParser.parseArtistNames(rawArtist) }
    val albumArtistNames: List<String> by lazy { MetadataParser.parseArtistNames(rawAlbumArtist) }
    val genres: List<String> by lazy { MetadataParser.parseGenres(rawGenre) }
    val artistMbid: String? = MbidValidator.normalize(rawArtistMbid)
    val albumMbid: String? = MbidValidator.normalize(rawAlbumMbid)
    val trackMbid: String? = MbidValidator.normalize(rawTrackMbid)

    /*
     * Best-effort primary artist. Falls back to the raw value when no
     * separator was found, which is the common case for a single artist.
     */
    val primaryArtist: String? by lazy {
        artistNames.firstOrNull()
            ?: rawArtist?.trim()?.takeIf { it.isNotEmpty() }
    }

    val primaryAlbumArtist: String? by lazy {
        albumArtistNames.firstOrNull()
            ?: rawAlbumArtist?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/*
 * Combines local file tags with Last.fm community tags without ever
 * dropping a local tag. An empty community response therefore leaves the
 * local tags exactly as they were.
 */
fun mergeMetadata(
    localTags: List<String>,
    communityTags: List<LastFmTag>,
    artist: LastFmArtistInfo? = null,
    album: LastFmAlbumInfo? = null,
    track: LastFmTrackInfo? = null
): LastFmMetadata = LastFmMetadata(
    rawLocalTags = localTags,
    communityTags = communityTags,
    artist = artist,
    album = album,
    track = track
)
