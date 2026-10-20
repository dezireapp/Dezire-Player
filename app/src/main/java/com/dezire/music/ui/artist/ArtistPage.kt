package com.dezire.music.ui.artist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezire.music.data.model.Album
import com.dezire.music.data.model.Song
import com.dezire.music.ui.components.EmptyState
import com.dezire.music.ui.components.SectionHeader
import com.dezire.music.ui.components.SongListItem
import com.dezire.music.ui.theme.dezireColors
import com.dezire.music.util.rememberSongArtwork

/*
 * Number of songs shown before the "View All" expansion is needed.
 */
private const val TOP_SONGS_LIMIT = 5

/*
 * Full Artist page.
 *
 * Everything on screen is derived from the real local library: the songs
 * are the MediaStore tracks whose artist matches [artistName], the albums
 * are the albums those tracks belong to, and the "Top Songs" order comes
 * from the persisted play counts. Nothing is sample data - a value is only
 * rendered when the library actually provides it (for example a release
 * year is shown only when at least one of its tracks carries a year tag).
 *
 * Playback is never handled here: taps are forwarded to the shared
 * [onSongClick] / [onAddToQueue] callbacks so the single Media3 player,
 * queue and mini-player keep working exactly as everywhere else.
 */
@Composable
fun ArtistPage(
    artistName: String,
    songs: List<Song>,
    albums: List<Album>,
    playCounts: Map<Long, Int>,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onAddToQueue: (Song) -> Unit,
    artworkSong: Song? = null,
    modifier: Modifier = Modifier
) {
    val colors = dezireColors()

    /*
     * Songs ordered by real play count (most played first); ties fall back
     * to the title so the order stays stable and deterministic.
     */
    val rankedSongs = remember(songs, playCounts) {
        songs.sortedWith(
            compareByDescending<Song> { playCounts[it.id] ?: 0 }
                .thenBy { it.title.lowercase() }
        )
    }

    /*
     * Releases ordered newest first using the real year tag when present.
     * Albums without any year information sort after dated ones, then by
     * name.
     */
    val orderedAlbums = remember(albums, songs) {
        albums.sortedWith(
            compareByDescending<Album> { albumYear(it, songs) ?: -1 }
                .thenBy { it.name.lowercase() }
        )
    }

    var showAllSongs by rememberSaveable(artistName) { mutableStateOf(false) }
    val hasMoreSongs = rankedSongs.size > TOP_SONGS_LIMIT
    val visibleSongs = if (showAllSongs || !hasMoreSongs) {
        rankedSongs
    } else {
        rankedSongs.take(TOP_SONGS_LIMIT)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(colors.background)
    ) {
        ArtistTopBar(onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "artist-header") {
                ArtistHeader(
                    artistName = artistName,
                    artworkSong = artworkSong ?: songs.firstOrNull()
                )
            }

            if (orderedAlbums.isNotEmpty()) {
                item(key = "new-release-header") {
                    SectionHeader(title = "New Release")
                }
                items(
                    items = orderedAlbums,
                    key = { "release-${it.id}" }
                ) { album ->
                    ReleaseRow(
                        album = album,
                        albumSongs = songs.filter { it.albumId == album.id },
                        onOpenAlbum = onOpenAlbum,
                        onSongClick = onSongClick,
                        onAddToQueue = onAddToQueue
                    )
                }
            }

            item(key = "top-songs-header") {
                SectionHeader(
                    title = "Top Songs",
                    actionLabel = if (hasMoreSongs) {
                        if (showAllSongs) "Show less" else "View All"
                    } else {
                        null
                    },
                    onAction = if (hasMoreSongs) {
                        { showAllSongs = !showAllSongs }
                    } else {
                        null
                    }
                )
            }

            if (visibleSongs.isEmpty()) {
                item(key = "top-songs-empty") {
                    EmptyState(
                        text = "No songs found for this artist.",
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            } else {
                itemsIndexed(
                    items = visibleSongs,
                    key = { _, song -> song.id }
                ) { _, song ->
                    val isCurrent = song.id == currentSongId
                    SongListItem(
                        song = song,
                        isPlaying = isCurrent && isPlaying,
                        isCurrent = isCurrent,
                        /*
                         * The whole artist's songs become the queue so
                         * playback continues past the visible top entries.
                         */
                        onClick = {
                            onSongClick(rankedSongs, rankedSongs.indexOf(song))
                        }
                    )
                }
            }
        }
    }
}

/*
 * Minimal top bar: a back affordance only. The artist identity lives in the
 * header below, so no title or secondary actions are added here.
 */
@Composable
private fun ArtistTopBar(onBack: () -> Unit) {
    val colors = dezireColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
    }
}

/*
 * Artist identity: a large circular portrait built from the artist's real
 * embedded artwork (there is no separate artist-artwork source), the
 * artist name and a muted "Artist" subtitle.
 */
@Composable
private fun ArtistHeader(
    artistName: String,
    artworkSong: Song?
) {
    val artwork = rememberSongArtwork(artworkSong)
    val colors = dezireColors()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(colors.placeholder),
            contentAlignment = Alignment.Center
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = artistName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = colors.contentSecondary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 18.dp)
        ) {
            Text(
                text = artistName,
                color = colors.onBackground,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = "Artist",
                color = colors.contentSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/*
 * A single release: square artwork on the left, the album title and its
 * real metadata in the middle and a three-dot options menu on the right.
 */
@Composable
private fun ReleaseRow(
    album: Album,
    albumSongs: List<Song>,
    onOpenAlbum: (Long) -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onAddToQueue: (Song) -> Unit
) {
    val artwork = rememberSongArtwork(album.artworkSong)
    val colors = dezireColors()
    var menuOpen by remember { mutableStateOf(false) }

    val metadata = remember(album, albumSongs) { releaseMetadata(album, albumSongs) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenAlbum(album.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.card),
            contentAlignment = Alignment.Center
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = colors.contentSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = album.name,
                color = colors.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (metadata.isNotEmpty()) {
                Text(
                    text = metadata,
                    color = colors.contentSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Release options",
                    tint = colors.contentSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play album") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    },
                    enabled = albumSongs.isNotEmpty(),
                    onClick = {
                        menuOpen = false
                        onSongClick(albumSongs, 0)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    },
                    enabled = albumSongs.isNotEmpty(),
                    onClick = {
                        menuOpen = false
                        albumSongs.forEach(onAddToQueue)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Go to album") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Album, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        onOpenAlbum(album.id)
                    }
                )
            }
        }
    }
}

/*
 * Release metadata built only from values the library actually exposes:
 * the year is included only when at least one track of the album carries a
 * year tag, and the track count always comes from the real grouping.
 */
private fun releaseMetadata(album: Album, albumSongs: List<Song>): String {
    val year = albumSongs.firstOrNull { it.year > 0 }?.year
    return buildList {
        year?.let { add(it.toString()) }
        add(if (album.songCount == 1) "1 track" else "${album.songCount} tracks")
    }.joinToString("  •  ")
}

/*
 * Newest release year for the album, or null when the library exposes no
 * year for any of its tracks.
 */
private fun albumYear(album: Album, songs: List<Song>): Int? =
    songs.firstOrNull { it.albumId == album.id && it.year > 0 }?.year
