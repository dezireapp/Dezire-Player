package com.dezire.music.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezire.music.PlayingIndicator
import com.dezire.music.data.model.Song
import com.dezire.music.ui.theme.DezireAccent
import com.dezire.music.ui.theme.dezireColors
import com.dezire.music.util.rememberSongArtwork

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = dezireColors().onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                color = DezireAccent,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.MusicNote
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = dezireColors().contentSecondary,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = text,
            color = dezireColors().contentSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

/*
 * Standard "Songs / Artists / Albums" stat row used by Library and
 * Profile.
 */
@Composable
fun LibraryStats(
    songCount: Int,
    artistCount: Int,
    albumCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(count = songCount, label = "Songs", modifier = Modifier.weight(1f))
        StatCard(count = artistCount, label = "Artists", modifier = Modifier.weight(1f))
        StatCard(count = albumCount, label = "Albums", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(
    count: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = dezireColors()
    Column(
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            color = colors.onSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = colors.contentSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
fun SongListItem(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val artwork = rememberSongArtwork(song)
    val colors = dezireColors()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isPlaying) colors.surfaceHighlight else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
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
                    tint = if (isPlaying) DezireAccent else colors.contentSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCurrent) {
                    PlayingIndicator(
                        isPlaying = isPlaying,
                        barWidth = 2.5.dp,
                        barHeight = 13.dp,
                        barSpacing = 2.dp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Text(
                    text = song.title,
                    color = if (isPlaying) DezireAccent else colors.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "${song.artist} • ${song.album}",
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

        trailing?.invoke()
    }
}

@Composable
fun PermissionBlock(
    granted: Boolean,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = dezireColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (granted) "Library access granted" else "Grant music access",
            color = colors.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (granted) {
                "Dezire can read the songs stored on this device."
            } else {
                "Allow music access to browse songs, artists and albums " +
                    "stored on this device."
            },
            color = colors.contentSecondary,
            fontSize = 13.sp
        )
        if (!granted) {
            androidx.compose.material3.Button(
                onClick = onRequest,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = DezireAccent,
                    contentColor = Color.White
                )
            ) {
                Text("Grant access")
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}