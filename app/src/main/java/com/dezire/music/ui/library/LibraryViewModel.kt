package com.dezire.music.ui.library

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dezire.music.data.MusicRepository
import com.dezire.music.data.Playlist
import com.dezire.music.data.UserProfile
import com.dezire.music.data.model.Album
import com.dezire.music.data.model.Artist
import com.dezire.music.data.model.Song
import com.dezire.music.dezireApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Shared library state used by Explore, Search, Library, Profile and the
 * filtered song list. Scoped to the activity so it survives tab switches.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MusicRepository = application.dezireApp.musicRepository
    private val profileRepository = application.dezireApp.profileRepository
    private val playlistRepository = application.dezireApp.playlistRepository

    /*
     * Shared reader for embedded file tags. Used by the album detail screen
     * to surface the genre when the file actually carries one; there is no
     * separate genre database, so a missing tag means the field is hidden.
     */
    private val localMetadataReader = application.dezireApp.localMetadataReader

    val songs: StateFlow<List<Song>> = repository.songs
    val artists: StateFlow<List<Artist>> = repository.artists
    val albums: StateFlow<List<Album>> = repository.albums
    val recentlyPlayed: StateFlow<List<Song>> = repository.recentlyPlayed
    val mostPlayed: StateFlow<List<Song>> = repository.mostPlayed

    /*
     * Real persisted play counts keyed by song id. Exposed so the Artist
     * page can order an artist's songs by how often they were actually
     * played instead of inventing a ranking.
     */
    val playCounts: StateFlow<Map<Long, Int>> = repository.playCounts
    val favorites: StateFlow<List<Song>> = repository.favorites
    val favoriteIds: StateFlow<Set<Long>> = repository.favoriteIds
    val isLoading: StateFlow<Boolean> = repository.isLoading

    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists

    /*
     * Local, editable profile and the decoded avatar bitmap. There is no
     * login: the avatar is copied into internal storage on selection, so
     * the decoded bitmap is held here and refreshed when it changes.
     */
    private val _profile = MutableStateFlow(profileRepository.profile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _avatar = MutableStateFlow<ImageBitmap?>(null)
    val avatar: StateFlow<ImageBitmap?> = _avatar.asStateFlow()

    private val _hasPermission = MutableStateFlow(MusicRepository.hasAudioPermission(application))
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    init {
        if (_hasPermission.value) {
            refresh()
        }
        loadAvatar()
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun onPermissionResult(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) {
            refresh()
        }
    }

    /*
     * Re-reads the runtime permission without triggering a scan. Used once
     * the onboarding flow finishes: it already populated the shared
     * MusicRepository, so this only keeps Explore's permission state in
     * sync without a duplicate MediaStore query.
     */
    fun syncAudioPermission() {
        _hasPermission.value = MusicRepository.hasAudioPermission(getApplication())
    }

    fun recordPlayed(song: Song) {
        repository.recordPlayed(song)
    }

    fun toggleFavorite(song: Song) {
        repository.toggleFavorite(song)
    }

    /*
     * Reads the first embedded genre tag for [song]. Returns null when the
     * platform does not expose a genre tag or the file simply has none,
     * so the UI never shows an invented genre.
     */
    suspend fun albumGenre(song: Song): String? {
        return runCatching {
            localMetadataReader.read(song).genres
                .firstOrNull { it.isNotBlank() }
        }.getOrNull()
    }

    // ---- Profile ---------------------------------------------------------

    private fun loadAvatar() {
        val path = _profile.value.avatarPath ?: return
        viewModelScope.launch {
            _avatar.value = withContext(Dispatchers.IO) { decodeAvatar(path) }
        }
    }

    fun setAvatar(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { profileRepository.saveAvatar(uri) }
                ?: return@launch
            val bitmap = withContext(Dispatchers.IO) { decodeAvatar(path) }
            _profile.value = _profile.value.copy(avatarPath = path)
            _avatar.value = bitmap
        }
    }

    fun updateUsername(value: String) {
        profileRepository.setUsername(value)
        _profile.value = profileRepository.profile()
    }

    fun updateDisplayName(value: String) {
        profileRepository.setDisplayName(value)
        _profile.value = profileRepository.profile()
    }

    // ---- Playlists -------------------------------------------------------

    fun createPlaylist(name: String): Playlist = playlistRepository.create(name)

    fun renamePlaylist(id: Long, name: String) = playlistRepository.rename(id, name)

    fun deletePlaylist(id: Long) = playlistRepository.delete(id)

    fun addToPlaylist(playlistId: Long, songIds: List<Long>) =
        playlistRepository.addSongs(playlistId, songIds)

    fun removeFromPlaylist(playlistId: Long, songId: Long) =
        playlistRepository.removeSong(playlistId, songId)

    /*
     * Resolves a playlist's stored ids to the current library songs, so
     * playlists follow the library and never hold stale copies.
     */
    fun songsInPlaylist(playlist: Playlist): List<Song> {
        val byId = songs.value.associateBy { it.id }
        return playlist.songIds.mapNotNull { byId[it] }
    }

    private fun decodeAvatar(path: String): ImageBitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val maxDimension = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDimension / sample > 1024) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
