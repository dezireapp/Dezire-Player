package com.dezire.music.ui.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezire.music.data.Playlist
import com.dezire.music.data.model.Song
import com.dezire.music.playback.RepeatMode
import com.dezire.music.ui.components.formatDuration
import com.dezire.music.ui.theme.DezireAccent
import com.dezire.music.ui.theme.DezireAlbumGray
import com.dezire.music.ui.theme.DezireRed
import com.dezire.music.ui.theme.DezireSurfaceGray

private const val MINUTE_MS = 60_000L

/*
 * Sleep timer sheet with preset durations plus a custom minute input and
 * a cancel action. The timer itself lives in PlaybackController.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    remainingMs: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onCancelTimer: () -> Unit
) {
    var customMinutes by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DezireSurfaceGray
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Sleep timer",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (remainingMs != null) {
                Text(
                    text = "Pausing in ${formatTimer(remainingMs)}",
                    color = DezireAccent,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                TextButton(
                    onClick = onCancelTimer,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(text = "Cancel timer", color = DezireRed)
                }
            } else {
                Text(
                    text = "Playback will pause when the timer ends.",
                    color = DezireAlbumGray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            val presets = listOf(5L, 10L, 15L, 30L, 45L, 60L)
            presets.forEach { minutes ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(minutes * MINUTE_MS)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$minutes minutes",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = { input ->
                        customMinutes = input.filter { it.isDigit() }.take(4)
                    },
                    label = { Text("Custom minutes") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    onClick = {
                        val minutes = customMinutes.toLongOrNull()
                        if (minutes != null && minutes > 0) {
                            onSelect(minutes * MINUTE_MS)
                            onDismiss()
                        }
                    },
                    enabled = customMinutes.toLongOrNull()?.let { it > 0 } == true
                ) {
                    Text(text = "Set", color = DezireRed)
                }
            }
        }
    }
}

/*
 * Three-dot action sheet for the current track. Only actions backed by
 * real functionality are shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreActionsSheet(
    isFavorite: Boolean,
    hasSong: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onDismiss: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onViewAlbum: () -> Unit,
    onViewArtist: () -> Unit,
    onSongDetails: () -> Unit,
    onSleepTimer: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit
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
                text = "Song options",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (!hasSong) {
                Text(
                    text = "No song is currently selected.",
                    color = DezireAlbumGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                return@Column
            }

            ActionRow(
                icon = Icons.Default.Add,
                label = "Add to queue",
                onClick = {
                    onAddToQueue()
                    onDismiss()
                }
            )
            ActionRow(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                tint = if (isFavorite) DezireRed else Color.White,
                label = if (isFavorite) "Remove from favorites" else "Add to favorites",
                onClick = {
                    onToggleFavorite()
                    onDismiss()
                }
            )
            ActionRow(
                icon = Icons.Default.Album,
                label = "View album",
                onClick = {
                    onViewAlbum()
                    onDismiss()
                }
            )
            ActionRow(
                icon = Icons.Default.Person,
                label = "View artist",
                onClick = {
                    onViewArtist()
                    onDismiss()
                }
            )
            ActionRow(
                icon = Icons.Default.Info,
                label = "Song details",
                onClick = {
                    onSongDetails()
                    onDismiss()
                }
            )
            ActionRow(
                icon = Icons.Default.Shuffle,
                tint = if (isShuffleEnabled) DezireRed else Color.White,
                label = if (isShuffleEnabled) "Shuffle on" else "Shuffle off",
                onClick = {
                    onShuffleToggle()
                    onDismiss()
                }
            )
            ActionRow(
                icon = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                tint = if (repeatMode == RepeatMode.OFF) Color.White else DezireRed,
                label = when (repeatMode) {
                    RepeatMode.OFF -> "Repeat off"
                    RepeatMode.ALL -> "Repeat all"
                    RepeatMode.ONE -> "Repeat one"
                },
                onClick = {
                    onRepeatToggle()
                    onDismiss()
                }
            )
            ActionRow(
                icon = Icons.Default.Timer,
                label = "Sleep timer",
                onClick = {
                    onSleepTimer()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ActionRow(
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
 * Bottom sheet for adding the currently playing song to one of the user's
 * local playlists, or creating a new one. Playlists come from the shared
 * [com.dezire.music.data.PlaylistRepository], so created playlists and song
 * membership persist across restarts and duplicate entries are already
 * rejected by the repository.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Playlist,
    onAdd: (Long) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DezireSurfaceGray
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "Add to playlist",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (playlists.isEmpty()) {
                Text(
                    text = "No playlists yet. Create one to start adding songs.",
                    color = DezireAlbumGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistChoiceRow(
                            playlist = playlist,
                            onClick = { onAdd(playlist.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            ActionRow(
                icon = Icons.Default.Add,
                label = "Create playlist",
                onClick = { showCreateDialog = true }
            )
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                val created = onCreate(name)
                showCreateDialog = false
                onAdd(created.id)
            }
        )
    }
}

@Composable
private fun PlaylistChoiceRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (playlist.songIds.size == 1) {
                    "1 song"
                } else {
                    "${playlist.songIds.size} songs"
                },
                color = DezireAlbumGray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DezireSurfaceGray,
        title = {
            Text(
                text = "New playlist",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Playlist name") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text(text = "Create", color = DezireRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = DezireAlbumGray)
            }
        }
    )
}

/*
 * Simple metadata dialog for the current song.
 */
@Composable
fun SongDetailsDialog(
    song: Song,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DezireSurfaceGray,
        title = {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailLine("Artist", song.artist)
                DetailLine("Album", song.album)
                DetailLine(
                    label = "Duration",
                    value = if (song.durationMs > 0) {
                        formatDuration(song.durationMs)
                    } else {
                        // Duration is unknown when the file does not report one.
                        "Unknown"
                    }
                )
                DetailLine(
                    label = "Location",
                    value = song.uri.toString()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close", color = DezireRed)
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = DezireAlbumGray,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

private fun formatTimer(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
