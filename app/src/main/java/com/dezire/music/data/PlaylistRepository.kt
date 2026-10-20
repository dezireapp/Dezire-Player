package com.dezire.music.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/*
 * A locally created playlist. [songIds] references the shared [Song] ids
 * (MediaStore ids for local tracks, negative synthetic ids for Drive
 * tracks), so deleting or reordering the underlying library never leaves a
 * stale duplicate copy behind.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long>
)

/*
 * Persists playlists in SharedPreferences as JSON. The in-memory
 * [playlists] flow is the single source of truth read by the UI.
 */
class PlaylistRepository(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("dezire_playlists", Context.MODE_PRIVATE)

    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun create(name: String): Playlist {
        val clean = name.trim().ifBlank { "New Playlist" }
        val playlist = Playlist(
            id = System.currentTimeMillis(),
            name = clean,
            songIds = emptyList()
        )
        update(_playlists.value + playlist)
        return playlist
    }

    fun rename(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        update(_playlists.value.map { if (it.id == id) it.copy(name = clean) else it })
    }

    fun delete(id: Long) {
        update(_playlists.value.filterNot { it.id == id })
    }

    fun addSongs(id: Long, songIds: List<Long>) {
        if (songIds.isEmpty()) return
        update(
            _playlists.value.map { playlist ->
                if (playlist.id != id) {
                    playlist
                } else {
                    val fresh = songIds.filterNot { it in playlist.songIds }
                    playlist.copy(songIds = playlist.songIds + fresh)
                }
            }
        )
    }

    fun removeSong(id: Long, songId: Long) {
        update(
            _playlists.value.map { playlist ->
                if (playlist.id != id) {
                    playlist
                } else {
                    playlist.copy(songIds = playlist.songIds.filterNot { it == songId })
                }
            }
        )
    }

    private fun update(list: List<Playlist>) {
        _playlists.value = list
        persist(list)
    }

    private fun persist(list: List<Playlist>) {
        val array = JSONArray()
        list.forEach { playlist ->
            array.put(
                JSONObject().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("songs", JSONArray(playlist.songIds))
                }
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun load(): List<Playlist> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val songs = obj.optJSONArray("songs") ?: JSONArray()
                Playlist(
                    id = obj.optLong("id"),
                    name = obj.optString("name"),
                    songIds = (0 until songs.length()).mapNotNull { songIndex ->
                        if (songs.isNull(songIndex)) null else songs.getLong(songIndex)
                    }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val KEY = "playlists"
    }
}
