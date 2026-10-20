package com.dezire.music.ui.explore

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dezire.music.data.model.Album
import com.dezire.music.data.model.Artist
import com.dezire.music.data.model.Song
import com.dezire.music.ui.components.EmptyState
import com.dezire.music.ui.components.PermissionBlock
import com.dezire.music.ui.components.SectionHeader
import com.dezire.music.ui.components.SongListItem
import com.dezire.music.ui.library.LibraryViewModel
import com.dezire.music.ui.theme.DezireRed
import com.dezire.music.ui.theme.dezireColors
import com.dezire.music.util.rememberSongArtwork

private val BrandLogoLift = 14.dp

/*
 * Explore tab: recently played, a preview of all songs, and horizontal
 * artist / album rails.
 */
@Composable
fun ExploreScreen(
    libraryViewModel: LibraryViewModel,
    currentSongId: Long?,
    isPlaying: Boolean,
    onShowAllSongs: () -> Unit,
    onShowFavorites: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onRequestPermission: () -> Unit
) {
    val songs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val artists by libraryViewModel.artists.collectAsStateWithLifecycle()
    val albums by libraryViewModel.albums.collectAsStateWithLifecycle()
    val recentlyPlayed by libraryViewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val favorites by libraryViewModel.favorites.collectAsStateWithLifecycle()
    val hasPermission by libraryViewModel.hasPermission.collectAsStateWithLifecycle()
    val isLoading by libraryViewModel.isLoading.collectAsStateWithLifecycle()

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topInset = (statusBarTop - BrandLogoLift).coerceAtLeast(0.dp)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            DezireBrandTitle()
        }

        if (!hasPermission) {
            item {
                PermissionBlock(
                    granted = false,
                    onRequest = onRequestPermission
                )
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = DezireRed,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        if (hasPermission && !isLoading && songs.isEmpty()) {
            item {
                EmptyState(text = "No songs found on this device yet.")
            }
        }

        if (recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeader(title = "Recently played")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentlyPlayed.take(10)) { song ->
                        RecentCard(
                            song = song,
                            onClick = { onSongClick(recentlyPlayed, 0) }
                        )
                    }
                }
            }
        }

        if (favorites.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Favorites",
                    actionLabel = "All",
                    onAction = onShowFavorites
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(favorites) { index, song ->
                        RecentCard(
                            song = song,
                            onClick = { onSongClick(favorites, index) }
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Songs",
                actionLabel = "All",
                onAction = onShowAllSongs
            )
        }

        if (songs.isEmpty()) {
            item { EmptyState("Nothing here yet.") }
        } else {
            items(
                items = songs.take(12),
                key = { it.id }
            ) { song ->
                val isCurrent = currentSongId != null && song.id == currentSongId
                SongListItem(
                    song = song,
                    isPlaying = isCurrent && isPlaying,
                    isCurrent = isCurrent,
                    onClick = { onSongClick(songs, songs.indexOf(song)) }
                )
            }
            if (songs.size > 12) {
                item {
                    Text(
                        text = "View all ${songs.size} songs",
                        color = DezireRed,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable(onClick = onShowAllSongs)
                    )
                }
            }
        }

        if (artists.isNotEmpty()) {
            item {
                SectionHeader(title = "Artists")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(artists.take(20)) { artist ->
                        ArtistCard(
                            artist = artist,
                            onClick = { onArtistClick(artist.name) }
                        )
                    }
                }
            }
        }

        if (albums.isNotEmpty()) {
            item {
                SectionHeader(title = "Albums")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(albums.take(20)) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                }
            }
        }
    }
}

/*
 * The "Dezire" brand wordmark at the top of Explore.
 *
 * It prefers the licensed "Busther" typeface. Add the font asset at
 * app/src/main/res/font/busther.ttf (resource name "busther") and it is picked
 * up automatically; while that asset is missing the title falls back to the
 * app's default typeface rather than substituting a different font and calling
 * it Busther. The family is resolved once and cached per context.
 */
@Composable
private fun DezireBrandTitle() {
    val context = LocalContext.current
    val brandFamily = remember(context) {
        val fontId = context.resources.getIdentifier("busther", "font", context.packageName)
        val typeface = if (fontId != 0) ResourcesCompat.getFont(context, fontId) else null
        if (typeface != null) FontFamily(typeface) else FontFamily.Default
    }
    Text(
        text = "Dezire",
        color = dezireColors().onBackground,
        fontSize = 35.sp,
        fontFamily = brandFamily,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 10.dp,
            bottom = 10.dp + BrandLogoLift,
        ),
    )
}

@Composable
private fun RecentCard(
    song: Song,
    onClick: () -> Unit
) {
    val artwork = rememberSongArtwork(song)
    val colors = dezireColors()

    Row(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
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
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = colors.contentSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                text = song.title,
                color = colors.onBackground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = colors.contentSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistCard(
    artist: Artist,
    onClick: () -> Unit
) {
    val artwork = rememberSongArtwork(artist.artworkSong)
    val colors = dezireColors()

    Column(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(32.dp))
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
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = colors.contentSecondary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Text(
            text = artist.name,
            color = colors.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "${artist.songCount} songs",
            color = colors.contentSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    onClick: () -> Unit
) {
    val artwork = rememberSongArtwork(album.artworkSong)
    val colors = dezireColors()

    Column(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
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
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = colors.contentSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Text(
            text = album.name,
            color = colors.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = album.artist,
            color = colors.contentSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}