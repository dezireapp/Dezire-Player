package com.dezire.music.ui.album

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dezire.music.PlayingIndicator
import com.dezire.music.data.model.Album
import com.dezire.music.data.model.Song
import com.dezire.music.playback.PlaybackController
import com.dezire.music.ui.components.EmptyState
import com.dezire.music.ui.components.SectionHeader
import com.dezire.music.ui.components.formatDuration
import com.dezire.music.ui.library.LibraryViewModel
import com.dezire.music.ui.nowplaying.SongDetailsDialog
import com.dezire.music.ui.theme.DezireAlbumGray
import com.dezire.music.ui.theme.DezireRed
import com.dezire.music.ui.theme.DezireSurfaceGray
import com.dezire.music.ui.theme.dezireColors
import com.dezire.music.util.rememberArtworkTint
import com.dezire.music.util.rememberSongArtwork

/*
 * Full album details destination.
 *
 * The screen is driven entirely by the shared library and playback state:
 * tracks are filtered from the real MediaStore library by album id,
 * the artwork comes from the existing song-artwork loader, and every
 * playback control drives the single shared ExoPlayer through
 * [PlaybackController]. No sample data and no second player are created.
 *
 * The whole page is a single LazyColumn so an album with hundreds of
 * tracks stays scrollable, followed by an information block and (when the
 * library actually contains them) more albums by the same artist.
 */
@Composable
fun AlbumDetailScreen(
    libraryViewModel: LibraryViewModel,
    playbackController: PlaybackController,
    albumId: Long,
    currentSongId: Long?,
    isPlaying: Boolean,
    favoriteIds: Set<Long>,
    onBack: () -> Unit,
    onViewArtist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit
) {
    val allSongs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val albums by libraryViewModel.albums.collectAsStateWithLifecycle()
    val colors = dezireColors()
    val context = LocalContext.current

    /*
     * Tracks for this album. When any track carries disc/track numbers the
     * list is sorted by them; otherwise the repository's existing order is
     * kept, exactly as required.
     */
    val tracks = remember(allSongs, albumId) {
        allSongs.filter { it.albumId == albumId }.let { list ->
            if (list.any { it.trackNumber > 0 }) {
                list.sortedWith(
                    compareBy({ it.discNumber }, { it.trackNumber }, { it.title.lowercase() })
                )
            } else {
                list
            }
        }
    }

    val album = remember(albums, tracks, albumId) {
        albums.firstOrNull { it.id == albumId }
            ?: tracks.firstOrNull()?.let {
                Album(
                    id = albumId,
                    name = it.album,
                    artist = it.artist,
                    songCount = tracks.size,
                    artworkSong = it
                )
            }
    }

    val artworkSong = album?.artworkSong ?: tracks.firstOrNull()
    val artwork = rememberSongArtwork(artworkSong)
    val tint = rememberArtworkTint(artwork)

    /*
     * Genre is only shown when the file actually embeds one (read from the
     * shared tag reader), so nothing is invented for tagless files.
     */
    var genre by remember(albumId) { mutableStateOf<String?>(null) }
    LaunchedEffect(albumId, tracks) {
        genre = null
        val local = tracks.firstOrNull() ?: return@LaunchedEffect
        genre = libraryViewModel.albumGenre(local)
    }

    val year = remember(tracks) { tracks.firstOrNull { it.year > 0 }?.year }
    val totalDurationMs = remember(tracks) { tracks.sumOf { it.durationMs } }

    val relatedAlbums = remember(albums, albumId, album) {
        val artist = album?.artist
        if (artist.isNullOrBlank()) {
            emptyList()
        } else {
            albums.filter { it.artist == artist && it.id != albumId }.take(10)
        }
    }

    var optionsSong by remember { mutableStateOf<Song?>(null) }
    var detailsSong by remember { mutableStateOf<Song?>(null) }

    val backdrop = remember(tint, colors.background, colors.surface) {
        Brush.verticalGradient(
            listOf(
                lerp(colors.background, tint ?: colors.surface, 0.5f),
                colors.background
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "top-bar") {
                AlbumTopBar(
                    album = album,
                    onBack = onBack,
                    onShare = { shareAlbum(context, album) },
                    onAddToQueue = {
                        tracks.forEach { playbackController.addToQueue(it) }
                    }
                )
            }

            item(key = "artwork") {
                ArtworkHeader(artwork = artwork, album = album)
            }

            item(key = "info") {
                AlbumTitleBlock(
                    album = album,
                    trackCount = tracks.size,
                    year = year,
                    genre = genre,
                    onViewArtist = onViewArtist
                )
            }

            if (tracks.isNotEmpty()) {
                item(key = "actions") {
                    AlbumActions(
                        onPlay = { playbackController.playAlbum(tracks) },
                        onShuffle = { playbackController.playAlbumShuffled(tracks) }
                    )
                }
            }

            item(key = "tracks-header") {
                SectionHeader(
                    title = "Tracks",
                    actionLabel = if (tracks.isNotEmpty()) tracks.size.toString() else null
                )
            }

            if (tracks.isEmpty()) {
                item(key = "empty") {
                    EmptyState(text = "No tracks found for this album.")
                }
            } else {
                itemsIndexed(
                    items = tracks,
                    key = { _, song -> song.id }
                ) { index, song ->
                    val isCurrent = song.id == currentSongId
                    AlbumTrackRow(
                        position = song.trackNumber.takeIf { it > 0 } ?: (index + 1),
                        song = song,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        onClick = {
                            playbackController.setQueue(tracks, tracks.indexOf(song))
                        },
                        onMore = { optionsSong = song }
                    )
                }
            }

            item(key = "album-info") {
                AlbumInformation(
                    year = year,
                    genre = genre,
                    trackCount = tracks.size,
                    totalDurationMs = totalDurationMs
                )
            }

            if (relatedAlbums.isNotEmpty() && !album?.artist.isNullOrBlank()) {
                item(key = "related-header") {
                    SectionHeader(title = "More by ${album?.artist}")
                }
                items(
                    items = relatedAlbums,
                    key = { "related-${it.id}" }
                ) { related ->
                    RelatedAlbumRow(
                        album = related,
                        onClick = { onOpenAlbum(related.id) }
                    )
                }
            }
        }
    }

    optionsSong?.let { song ->
        AlbumSongOptionsSheet(
            song = song,
            isFavorite = song.id in favoriteIds,
            onDismiss = { optionsSong = null },
            onAddToQueue = { playbackController.addToQueue(song) },
            onToggleFavorite = { libraryViewModel.toggleFavorite(song) },
            onViewArtist = { onViewArtist(song.artist) },
            onSongDetails = { detailsSong = song }
        )
    }

    detailsSong?.let { song ->
        SongDetailsDialog(song = song, onDismiss = { detailsSong = null })
    }
}

/*
 * Transparent navigation row: back on the left, share and an album
 * options menu on the right. Only actions backed by real functionality
 * are offered.
 */
@Composable
private fun AlbumTopBar(
    album: Album?,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onAddToQueue: () -> Unit
) {
    val colors = dezireColors()
    var menuOpen by remember { mutableStateOf(false) }

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

        Spacer(modifier = Modifier.weight(1f))

        if (album != null) {
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share album",
                    tint = colors.onBackground
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Album options",
                        tint = colors.onBackground
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add album to queue") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onAddToQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share album") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onShare()
                        }
                    )
                }
            }
        }
    }
}

/*
 * Large, centered album artwork with a graceful placeholder when the album
 * has no embedded art.
 */
@Composable
private fun ArtworkHeader(
    artwork: ImageBitmap?,
    album: Album?
) {
    val colors = dezireColors()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val size = minOf(maxWidth * 0.62f, 320.dp)
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card),
            contentAlignment = Alignment.Center
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = album?.name ?: "Album artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = colors.contentSecondary,
                    modifier = Modifier.size(72.dp)
                )
            }
        }
    }
}

/*
 * Album identity: title, artist and a single compact metadata line built
 * only from values that actually exist.
 */
@Composable
private fun AlbumTitleBlock(
    album: Album?,
    trackCount: Int,
    year: Int?,
    genre: String?,
    onViewArtist: (String) -> Unit
) {
    val colors = dezireColors()
    val name = album?.name ?: "Unknown Album"
    val artist = album?.artist ?: "Unknown Artist"

    val metadata = buildList {
        add(if (trackCount == 1) "1 track" else "$trackCount tracks")
        year?.let { add(it.toString()) }
        genre?.let { add(it) }
    }.joinToString("  •  ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Text(
            text = name,
            color = colors.onBackground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = artist,
            color = DezireRed,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { onViewArtist(artist) }
                .padding(vertical = 2.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = metadata,
            color = colors.contentSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/*
 * Shuffle + prominent Play. Both operate on the album's real tracks and the
 * shared player, so they never spin up a second queue or player instance.
 */
@Composable
private fun AlbumActions(
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    val colors = dezireColors()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .clickable(onClick = onShuffle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle album",
                tint = DezireRed,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(DezireRed)
                .clickable(onClick = onPlay)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Play",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/*
 * A single compact track row: position/indicator, title, artist, duration
 * and a three-dot menu. The currently playing track is accented and its
 * playing indicator animates through [PlayingIndicator].
 */
@Composable
private fun AlbumTrackRow(
    position: Int,
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val colors = dezireColors()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) colors.surfaceHighlight else Color.Transparent)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrent) {
                PlayingIndicator(
                    isPlaying = isPlaying,
                    barWidth = 2.5.dp,
                    barHeight = 14.dp,
                    barSpacing = 2.dp
                )
            } else {
                Text(
                    text = position.toString(),
                    color = colors.contentSecondary,
                    fontSize = 14.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = song.title,
                color = if (isCurrent) DezireRed else colors.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = colors.contentSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = formatDuration(song.durationMs),
            color = colors.contentSecondary,
            fontSize = 12.sp
        )

        IconButton(onClick = onMore) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Song options",
                tint = colors.contentSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/*
 * After the tracks, a factual album information block. Each row is shown
 * only when its value is actually known; the record label and audio quality
 * are omitted entirely because the library exposes no reliable source for
 * them.
 */
@Composable
private fun AlbumInformation(
    year: Int?,
    genre: String?,
    trackCount: Int,
    totalDurationMs: Long
) {
    val hasInfo = year != null || genre != null || trackCount > 0 || totalDurationMs > 0
    if (!hasInfo) return

    SectionHeader(title = "About this album")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        year?.let { InfoRow(label = "Year", value = it.toString()) }
        genre?.let { InfoRow(label = "Genre", value = it) }
        InfoRow(label = "Tracks", value = trackCount.toString())
        if (totalDurationMs > 0) {
            InfoRow(label = "Duration", value = formatLongDuration(totalDurationMs))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = dezireColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colors.contentSecondary,
            fontSize = 14.sp,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            color = colors.onBackground,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/*
 * Compact "more albums by this artist" row. Only rendered when the local
 * library genuinely contains other albums by the same artist.
 */
@Composable
private fun RelatedAlbumRow(
    album: Album,
    onClick: () -> Unit
) {
    val artwork = rememberSongArtwork(album.artworkSong)
    val colors = dezireColors()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.card),
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
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = colors.contentSecondary,
                    modifier = Modifier.size(22.dp)
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
            Text(
                text = album.artist,
                color = colors.contentSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (album.songCount == 1) "1 track" else "${album.songCount} tracks",
            color = colors.contentSecondary,
            fontSize = 12.sp
        )
    }
}

/*
 * Bottom sheet of the supported song actions for a track in this album.
 * Mirrors the actions already available from Now Playing so behaviour stays
 * consistent across the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumSongOptionsSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onViewArtist: () -> Unit,
    onSongDetails: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DezireSurfaceGray
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Text(
                text = song.artist,
                color = DezireAlbumGray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            AlbumActionRow(
                icon = Icons.Default.Add,
                label = "Add to queue",
                onClick = {
                    onAddToQueue()
                    onDismiss()
                }
            )
            AlbumActionRow(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                tint = if (isFavorite) DezireRed else Color.White,
                label = if (isFavorite) "Remove from favorites" else "Add to favorites",
                onClick = {
                    onToggleFavorite()
                    onDismiss()
                }
            )
            AlbumActionRow(
                icon = Icons.Default.Person,
                label = "View artist",
                onClick = {
                    onViewArtist()
                    onDismiss()
                }
            )
            AlbumActionRow(
                icon = Icons.Default.Info,
                label = "Song details",
                onClick = {
                    onSongDetails()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun AlbumActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp
        )
    }
}

/*
 * Shares the album as plain text through the system chooser. No extra
 * permission or dependency is required.
 */
private fun shareAlbum(context: Context, album: Album?) {
    if (album == null) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, album.name)
        putExtra(Intent.EXTRA_TEXT, "${album.name} • ${album.artist}")
    }
    context.startActivity(Intent.createChooser(intent, "Share album"))
}

/*
 * Album durations can exceed an hour, so they are formatted h:mm:ss rather
 * than the per-track m:ss helper.
 */
private fun formatLongDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
