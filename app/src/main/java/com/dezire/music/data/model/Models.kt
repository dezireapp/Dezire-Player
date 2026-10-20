package com.dezire.music.data.model

import android.net.Uri

/*
 * A single track from the local MediaStore library. [id] is the MediaStore
 * row id, [uri] its content:// location.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    /*
     * Optional tag data. Each is 0 when the file does not expose it, so
     * the UI can hide the field instead of showing a wrong value.
     */
    val year: Int = 0,
    val trackNumber: Int = 0,
    val discNumber: Int = 0
)

/*
 * Aggregated artist entry for the library. [artworkSong] is a
 * representative track by the artist whose embedded artwork stands in for
 * the artist image (there is no separate artist-artwork database), so the
 * existing song-based artwork loader can be reused.
 */
data class Artist(
    val name: String,
    val songCount: Int,
    val artworkSong: Song? = null
)

/*
 * Aggregated album entry for the library. [artworkSong] is a
 * representative track from the album, used as the artwork source so the
 * existing song-based artwork loader can be reused.
 */
data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val artworkSong: Song? = null
)
