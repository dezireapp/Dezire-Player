package com.dezire.music.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.dezire.music.data.model.Album
import com.dezire.music.data.model.Artist
import com.dezire.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Reads the device's local audio library through MediaStore (all
 * disk/MediaStore access happens off the main thread) and exposes it as
 * observable state. [publish] recomputes every derived list whenever the
 * queried tracks change.
 */
class MusicRepository(
    private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val recentStore = RecentlyPlayedStore(context)
    private val favoritesStore = FavoritesStore(context)
    private val playCountStore = PlayCountStore(context)

    private val _localSongs = MutableStateFlow<List<Song>>(emptyList())

    /*
     * The full local library. Exposed so callers that need real local
     * files (tag reading, for instance) use the same list as the UI.
     */
    val localSongs: StateFlow<List<Song>> = _localSongs.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    /*
     * Persisted favorite ids (newest first) and the resolved songs. Ids
     * come from MediaStore and stay stable across restarts, so favorites
     * are keyed by song id rather than title.
     */
    private val _favoriteIds = MutableStateFlow(favoritesStore.ids().toSet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    private val _favorites = MutableStateFlow<List<Song>>(emptyList())
    val favorites: StateFlow<List<Song>> = _favorites.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    /*
     * Persisted play counts keyed by song id, and the resolved "most
     * played" list ordered by that count (highest first).
     */
    private val _playCounts = MutableStateFlow(playCountStore.counts())
    val playCounts: StateFlow<Map<Long, Int>> = _playCounts.asStateFlow()

    private val _mostPlayed = MutableStateFlow<List<Song>>(emptyList())
    val mostPlayed: StateFlow<List<Song>> = _mostPlayed.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        scope.launch {
            _localSongs.collect { publish(it) }
        }
    }

    suspend fun refresh() {
        if (!hasAudioPermission(context)) {
            _localSongs.value = emptyList()
            return
        }

        _isLoading.value = true
        val loaded = withContext(Dispatchers.IO) { querySongs() }
        _localSongs.value = loaded
        _isLoading.value = false
    }

    /*
     * Recomputes every derived list from the library. Runs on a
     * background dispatcher: grouping a large library is not free.
     */
    private suspend fun publish(all: List<Song>) = withContext(Dispatchers.Default) {
        _songs.value = all
        _artists.value = all
            .groupBy { it.artist }
            .map { (name, list) -> Artist(name, list.size, list.first()) }
            .sortedBy { it.name.lowercase() }
        _albums.value = all
            .groupBy { it.albumId }
            .map { (id, list) ->
                Album(
                    id = id,
                    name = list.first().album,
                    artist = list.first().artist,
                    songCount = list.size,
                    artworkSong = list.first()
                )
            }
            .sortedBy { it.name.lowercase() }
        _recentlyPlayed.value = recentStore.resolve(all)
        _mostPlayed.value = resolveMostPlayed(all, _playCounts.value)
        recomputeFavorites(favoritesStore.ids())
    }

    /*
     * Records a completed/started play: updates the recent list and
     * increments the persistent play count so Most Played stays current.
     */
    fun recordPlayed(song: Song) {
        recentStore.record(song.id)
        _recentlyPlayed.value = (listOf(song) + _recentlyPlayed.value)
            .distinctBy { it.id }
            .take(20)

        val counts = playCountStore.increment(song.id)
        _playCounts.value = counts
        _mostPlayed.value = resolveMostPlayed(_songs.value, counts)
    }

    private fun resolveMostPlayed(all: List<Song>, counts: Map<Long, Int>): List<Song> =
        all.mapNotNull { song -> counts[song.id]?.takeIf { it > 0 }?.let { song to it } }
            .sortedWith(
                compareByDescending<Pair<Song, Int>> { it.second }
                    .thenBy { it.first.title.lowercase() }
            )
            .map { it.first }

    /*
     * Adds or removes [song] from favorites and persists the change.
     */
    fun toggleFavorite(song: Song) {
        val ids = favoritesStore.toggle(song.id)
        _favoriteIds.value = ids.toSet()
        recomputeFavorites(ids)
    }

    private fun recomputeFavorites(ids: List<Long> = favoritesStore.ids()) {
        val byId = _songs.value.associateBy { it.id }
        _favorites.value = ids.mapNotNull { byId[it] }
    }

    private fun querySongs(): List<Song> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.provider.MediaStore.Audio.Media.getContentUri(
                android.provider.MediaStore.VOLUME_EXTERNAL
            )
        } else {
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            android.provider.MediaStore.Audio.Media._ID,
            android.provider.MediaStore.Audio.Media.TITLE,
            android.provider.MediaStore.Audio.Media.ARTIST,
            android.provider.MediaStore.Audio.Media.ALBUM,
            android.provider.MediaStore.Audio.Media.ALBUM_ID,
            android.provider.MediaStore.Audio.Media.DURATION,
            android.provider.MediaStore.Audio.Media.YEAR,
            android.provider.MediaStore.Audio.Media.TRACK
        )

        val selection = "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${android.provider.MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val result = mutableListOf<Song>()

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
            val titleColumn =
                cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
            val artistColumn =
                cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
            val albumColumn =
                cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
            val albumIdColumn =
                cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn =
                cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
            val yearColumn = cursor.getColumnIndex(
                android.provider.MediaStore.Audio.Media.YEAR
            )
            val trackColumn = cursor.getColumnIndex(
                android.provider.MediaStore.Audio.Media.TRACK
            )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val rawArtist = cursor.getString(artistColumn)
                val rawAlbum = cursor.getString(albumColumn)
                val rawYear = if (yearColumn >= 0) cursor.getInt(yearColumn) else 0
                val rawTrack = if (trackColumn >= 0) cursor.getInt(trackColumn) else 0
                result += Song(
                    id = id,
                    title = cursor.getString(titleColumn) ?: "Unknown title",
                    artist = if (rawArtist.isNullOrBlank() ||
                        rawArtist == "<unknown>"
                    ) {
                        "Unknown Artist"
                    } else {
                        rawArtist
                    },
                    album = if (rawAlbum.isNullOrBlank() || rawAlbum == "<unknown>") {
                        "Unknown Album"
                    } else {
                        rawAlbum
                    },
                    albumId = cursor.getLong(albumIdColumn),
                    durationMs = cursor.getLong(durationColumn),
                    uri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ),
                    year = rawYear.takeIf { it > 0 } ?: 0,
                    /*
                     * MediaStore packs the disc number and the track number
                     * into a single "track" value: track = value % 1000,
                     * disc = value / 1000. A value of 0 means "unknown".
                     */
                    trackNumber = if (rawTrack > 0) rawTrack % 1000 else 0,
                    discNumber = if (rawTrack > 0) rawTrack / 1000 else 0
                )
            }
        }

        return result
    }

    companion object {
        fun requiredAudioPermission(): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

        fun hasAudioPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                requiredAudioPermission()
            ) == PackageManager.PERMISSION_GRANTED
    }
}

/*
 * Tiny persistence for the recently played list (MediaStore ids).
 */
private class RecentlyPlayedStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("dezire_recently_played", Context.MODE_PRIVATE)

    fun record(songId: Long) {
        val current = ids().toMutableList()
        current.remove(songId)
        current.add(0, songId)
        prefs.edit().putString(KEY, current.take(MAX).joinToString(",")).apply()
    }

    fun resolve(allSongs: List<Song>): List<Song> {
        val byId = allSongs.associateBy { it.id }
        return ids().mapNotNull { byId[it] }
    }

    private fun ids(): List<Long> =
        prefs.getString(KEY, null)
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()

    private companion object {
        const val KEY = "ids"
        const val MAX = 20
    }
}

/*
 * Persistent favorites store. Keeps an ordered list of MediaStore song
 * ids (newest first) in SharedPreferences so favorites survive restarts.
 */
private class FavoritesStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("dezire_favorites", Context.MODE_PRIVATE)

    fun ids(): List<Long> =
        prefs.getString(KEY, null)
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()

    /*
     * Toggles [songId] and returns the updated ordered id list.
     */
    fun toggle(songId: Long): List<Long> {
        val current = ids().toMutableList()
        if (!current.remove(songId)) {
            current.add(0, songId)
        }
        prefs.edit().putString(KEY, current.joinToString(",")).apply()
        return current
    }

    private companion object {
        const val KEY = "ids"
    }
}

/*
 * Persistent play counts keyed by song id. Stored as a compact
 * "id:count,id:count" string so a large library still fits in a single
 * preference entry.
 */
private class PlayCountStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("dezire_play_counts", Context.MODE_PRIVATE)

    fun counts(): Map<Long, Int> =
        prefs.getString(KEY, null)
            ?.split(",")
            ?.mapNotNull { entry ->
                val parts = entry.split(":")
                val id = parts.getOrNull(0)?.toLongOrNull()
                val count = parts.getOrNull(1)?.toIntOrNull()
                if (id != null && count != null && count > 0) id to count else null
            }
            ?.toMap()
            ?: emptyMap()

    fun increment(songId: Long): Map<Long, Int> {
        val current = counts().toMutableMap()
        current[songId] = (current[songId] ?: 0) + 1
        prefs.edit()
            .putString(KEY, current.entries.joinToString(",") { "${it.key}:${it.value}" })
            .apply()
        return current
    }

    private companion object {
        const val KEY = "counts"
    }
}
