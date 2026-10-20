package com.dezire.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dezire.music.data.Playlist
import com.dezire.music.data.model.Song
import com.dezire.music.ui.components.EmptyState
import com.dezire.music.ui.components.SongListItem
import com.dezire.music.ui.theme.DezireRed
import com.dezire.music.ui.theme.dezireColors

/*
 * Lists the user's local playlists and offers creation of new ones.
 */
@Composable
fun PlaylistsScreen(
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit
) {
    val playlists by libraryViewModel.playlists.collectAsStateWithLifecycle()
    val songs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val colors = dezireColors()
    var showCreate by remember { mutableStateOf(false) }
    val songIds = remember(songs) { songs.mapTo(HashSet()) { it.id } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Playlists",
            onBack = onBack,
            actions = {
                IconButton(onClick = { showCreate = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New playlist",
                        tint = colors.onBackground
                    )
                }
            }
        )

        if (playlists.isEmpty()) {
            EmptyState(
                text = "No playlists yet. Tap + to create one.",
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        count = playlist.songIds.count { it in songIds },
                        onClick = { onOpenPlaylist(playlist.id) }
                    )
                }
            }
        }
    }

    if (showCreate) {
        EditTextDialog(
            title = "New playlist",
            initial = "",
            onDismiss = { showCreate = false },
            onSave = { name ->
                libraryViewModel.createPlaylist(name)
                showCreate = false
            }
        )
    }
}

/*
 * Detail screen for a single playlist: play, rename, delete, add and
 * remove tracks.
 */
@Composable
fun PlaylistDetailScreen(
    libraryViewModel: LibraryViewModel,
    playlistId: Long,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit
) {
    val playlists by libraryViewModel.playlists.collectAsStateWithLifecycle()
    val songs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val colors = dezireColors()

    val playlist = playlists.firstOrNull { it.id == playlistId }
    val tracks = remember(playlist, songs) {
        playlist?.let { libraryViewModel.songsInPlaylist(it) } ?: emptyList()
    }

    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showAddSongs by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    if (playlist == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(colors.background)
        ) {
            ScreenHeader(title = "Playlist", onBack = onBack)
            EmptyState(text = "This playlist no longer exists.")
        }
        return
    }

    if (showAddSongs) {
        AddSongsScreen(
            songs = songs,
            existingIds = playlist.songIds.toSet(),
            onBack = { showAddSongs = false },
            onAdd = { ids ->
                libraryViewModel.addToPlaylist(playlist.id, ids)
                showAddSongs = false
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = playlist.name,
            onBack = onBack,
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Playlist options",
                            tint = colors.onBackground
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add songs") },
                            onClick = {
                                menuOpen = false
                                showAddSongs = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuOpen = false
                                showRename = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete playlist") },
                            onClick = {
                                menuOpen = false
                                showDelete = true
                            }
                        )
                    }
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { if (tracks.isNotEmpty()) onSongClick(tracks, 0) },
                enabled = tracks.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DezireRed,
                    contentColor = androidx.compose.ui.graphics.Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text("Play all")
            }
            TextButton(onClick = { showAddSongs = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = DezireRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text("Add songs", color = DezireRed)
            }
        }

        if (tracks.isEmpty()) {
            EmptyState(
                text = "No songs in this playlist yet.",
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tracks, key = { it.id }) { song ->
                    val isCurrent = song.id == currentSongId
                    SongListItem(
                        song = song,
                        isPlaying = isCurrent && isPlaying,
                        isCurrent = isCurrent,
                        onClick = { onSongClick(tracks, tracks.indexOf(song)) },
                        trailing = {
                            IconButton(
                                onClick = {
                                    libraryViewModel.removeFromPlaylist(playlist.id, song.id)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RemoveCircleOutline,
                                    contentDescription = "Remove from playlist",
                                    tint = colors.contentSecondary
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (showRename) {
        EditTextDialog(
            title = "Rename playlist",
            initial = playlist.name,
            onDismiss = { showRename = false },
            onSave = { name ->
                libraryViewModel.renamePlaylist(playlist.id, name)
                showRename = false
            }
        )
    }

    if (showDelete) {
        val dialogColors = dezireColors()
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = dialogColors.surface,
            titleContentColor = dialogColors.onSurface,
            textContentColor = dialogColors.onSurface,
            title = { Text("Delete playlist") },
            text = { Text("Delete \"${playlist.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        libraryViewModel.deletePlaylist(playlist.id)
                        showDelete = false
                        onBack()
                    }
                ) {
                    Text("Delete", color = DezireRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text("Cancel", color = dialogColors.contentSecondary)
                }
            }
        )
    }
}

/*
 * Full-screen track picker. Shows every song with a check mark for the
 * ones already in the playlist; tapping toggles the selection and "Add"
 * commits the newly selected ids.
 */
@Composable
private fun AddSongsScreen(
    songs: List<Song>,
    existingIds: Set<Long>,
    onBack: () -> Unit,
    onAdd: (List<Long>) -> Unit
) {
    val colors = dezireColors()
    val selected = remember { mutableStateOf(existingIds) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Add songs",
            onBack = onBack,
            actions = {
                TextButton(
                    onClick = {
                        val added = selected.value - existingIds
                        onAdd(added.toList())
                    },
                    enabled = (selected.value - existingIds).isNotEmpty()
                ) {
                    Text("Add", color = DezireRed)
                }
            }
        )

        if (songs.isEmpty()) {
            EmptyState(text = "No songs in your library yet.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(songs, key = { it.id }) { song ->
                    val isSelected = song.id in selected.value
                    SongListItem(
                        song = song,
                        isPlaying = false,
                        onClick = {
                            selected.value = if (isSelected) {
                                selected.value - song.id
                            } else {
                                selected.value + song.id
                            }
                        },
                        trailing = {
                            Box(
                                modifier = Modifier.size(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = DezireRed
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add",
                                        tint = colors.contentSecondary
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    count: Int,
    onClick: () -> Unit
) {
    val colors = dezireColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(DezireRed.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = DezireRed,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = playlist.name,
                color = colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (count == 1) "1 song" else "$count songs",
                color = colors.contentSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.contentSecondary
        )
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    actions: (@Composable () -> Unit)? = null
) {
    val colors = dezireColors()
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
            modifier = Modifier.weight(1f)
        )
        actions?.invoke()
    }
}

@Composable
private fun EditTextDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val colors = dezireColors()
    var text by remember(initial) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurface,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DezireRed,
                    cursorColor = DezireRed,
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Save", color = DezireRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.contentSecondary)
            }
        }
    )
}
