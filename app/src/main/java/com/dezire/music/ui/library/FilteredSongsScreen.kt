package com.dezire.music.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dezire.music.data.model.Song
import com.dezire.music.playback.PlaybackController
import com.dezire.music.ui.artist.ArtistPage
import com.dezire.music.ui.components.EmptyState
import com.dezire.music.ui.components.SongListItem
import com.dezire.music.ui.theme.dezireColors
import com.dezire.music.util.rememberSongArtwork

private const val TYPE_SONG = "song"
private const val TYPE_ARTIST = "artist"
private const val TYPE_ALBUM = "album"
private const val TYPE_FAVORITES = "favorites"
private const val TYPE_LIKED = "liked"
private const val TYPE_RECENT = "recent"
private const val TYPE_MOST_PLAYED = "most_played"

/*
 * Detail screen for "all songs", a single artist's songs or a single
 * album's songs. The bottom bar is hidden here, so a back affordance is
 * rendered at the top; the mini player stays visible below the list.
 */
@Composable
fun FilteredSongsScreen(
    libraryViewModel: LibraryViewModel,
    playbackController: PlaybackController,
    type: String,
    value: String,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onOpenAlbum: (Long) -> Unit
) {
    val songs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val albums by libraryViewModel.albums.collectAsStateWithLifecycle()
    val playCounts by libraryViewModel.playCounts.collectAsStateWithLifecycle()
    val favoriteIds by libraryViewModel.favoriteIds.collectAsStateWithLifecycle()
    val recentlyPlayed by libraryViewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val mostPlayed by libraryViewModel.mostPlayed.collectAsStateWithLifecycle()
    val colors = dezireColors()

    /*
     * The artist destination uses the dedicated Artist page (portrait
     * header, New Release row and Top Songs). Everything on it is filtered
     * from the real library and driven by the shared player, so the
     * existing Media3 queue and mini-player behaviour is preserved.
     */
    if (type == TYPE_ARTIST) {
        ArtistPage(
            artistName = value,
            songs = songs.filter { it.artist == value },
            albums = albums.filter { it.artist == value },
            playCounts = playCounts,
            currentSongId = currentSongId,
            isPlaying = isPlaying,
            onBack = onBack,
            onSongClick = onSongClick,
            onOpenAlbum = onOpenAlbum,
            onAddToQueue = playbackController::addToQueue
        )
        return
    }

    val filtered = remember(
        type,
        value,
        songs,
        favoriteIds,
        recentlyPlayed,
        mostPlayed
    ) {
        when (type) {
            TYPE_SONG -> songs
            TYPE_ARTIST -> songs.filter { it.artist == value }
            TYPE_ALBUM -> songs.filter { it.album == value }
            TYPE_FAVORITES, TYPE_LIKED -> songs.filter { it.id in favoriteIds }
            TYPE_RECENT -> recentlyPlayed
            TYPE_MOST_PLAYED -> mostPlayed
            else -> songs
        }
    }

    val title = when (type) {
        TYPE_SONG -> "All songs"
        TYPE_ARTIST -> value
        TYPE_ALBUM -> value
        TYPE_FAVORITES -> "Favorites"
        TYPE_LIKED -> "Liked Songs"
        TYPE_RECENT -> "Recently Played"
        TYPE_MOST_PLAYED -> "Most Played"
        else -> "Songs"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.onBackground
                )
            }
            Text(
                text = title,
                color = colors.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            )
        }

        if (type == TYPE_ARTIST || type == TYPE_ALBUM) {
            val artworkSong = filtered.firstOrNull()
            MediaHeader(
                type = type,
                name = value,
                subtitle = subtitleFor(type),
                artworkSong = artworkSong
            )
        }

        if (filtered.isEmpty()) {
            EmptyState(
                text = when (type) {
                    TYPE_FAVORITES, TYPE_LIKED -> "No liked songs yet."
                    TYPE_RECENT -> "Nothing played yet."
                    TYPE_MOST_PLAYED -> "No play history yet."
                    else -> "No songs found."
                },
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = filtered,
                    key = { it.id }
                ) { song ->
                    val isCurrent = song.id == currentSongId
                    SongListItem(
                        song = song,
                        isPlaying = isCurrent && isPlaying,
                        isCurrent = isCurrent,
                        onClick = { onSongClick(filtered, filtered.indexOf(song)) }
                    )
                }
            }
        }
    }
}

/*
 * Artwork header for Artist / Album detail screens. The artist image is
 * derived from a representative track's embedded artwork (there is no
 * separate artist-artwork source), and a person icon stands in when the
 * track has no embedded art.
 */
@Composable
private fun MediaHeader(
    type: String,
    name: String,
    subtitle: String,
    artworkSong: Song?
) {
    val artwork = rememberSongArtwork(artworkSong)
    val colors = dezireColors()
    val isArtist = type == TYPE_ARTIST

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(if (isArtist) CircleShape else RoundedCornerShape(14.dp))
                .background(colors.placeholder),
            contentAlignment = Alignment.Center
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = if (isArtist) Icons.Default.Person else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = colors.contentSecondary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = name,
                color = colors.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = colors.contentSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun subtitleFor(type: String): String = when (type) {
    TYPE_ARTIST -> "Artist"
    TYPE_ALBUM -> "Album"
    else -> ""
}