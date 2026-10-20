package com.dezire.music.data.metadata

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.dezire.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/*
 * Reads local file tags for a track using the platform metadata retriever.
 *
 * Only the tags the platform exposes are read (artist, album artist, album
 * and, on API 30+, genre). The raw strings are handed to [LocalMetadata],
 * which keeps them intact and performs the conservative parsing. Results
 * are cached per MediaStore id so refreshing a large library does not
 * repeatedly open the same files.
 *
 * MusicBrainz ids are not exposed by MediaMetadataRetriever, so local
 * MBIDs remain null unless a caller has another source for them; the
 * Last.fm responses supply artist/album/track MBIDs when available.
 */
class LocalMetadataReader(private val context: Context) {

    private val cache = ConcurrentHashMap<Long, LocalMetadata>()

    suspend fun read(song: Song): LocalMetadata = read(song.id, song.uri)

    suspend fun read(songId: Long, uri: Uri): LocalMetadata = withContext(Dispatchers.IO) {
        cache[songId] ?: extract(uri).also { cache[songId] = it }
    }

    suspend fun readAll(songs: List<Song>): List<LocalMetadata> = songs.map { read(it) }

    fun clearCache() {
        cache.clear()
    }

    private fun extract(uri: Uri): LocalMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            LocalMetadata(
                rawArtist = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ARTIST
                ),
                rawAlbumArtist = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST
                ),
                rawAlbum = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ALBUM
                ),
                rawGenre = extractGenre(retriever)
            )
        } catch (t: Throwable) {
            LocalMetadata()
        } finally {
            runCatching { retriever.release() }
        }
    }

    @Suppress("InlinedApi")
    private fun extractGenre(retriever: MediaMetadataRetriever): String? =
        runCatching {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
        }.getOrNull()
}
