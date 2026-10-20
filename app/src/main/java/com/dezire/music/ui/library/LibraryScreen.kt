package com.dezire.music.ui.library

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dezire.music.ui.theme.DezireRed
import com.dezire.music.ui.theme.dezireColors

/*
 * Library tab landing screen: a profile icon on the left, a settings
 * icon on the right, and four entry points into the library (Liked
 * Songs, Recently Played, Playlists, Most Played). No statistics or song
 * lists live here; tapping a row opens its dedicated screen.
 */
@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLikedSongs: () -> Unit,
    onOpenRecentlyPlayed: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenMostPlayed: () -> Unit
) {
    val colors = dezireColors()
    val avatar by libraryViewModel.avatar.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(colors.background)
    ) {
        LibraryHeader(
            avatar = avatar,
            onOpenProfile = onOpenProfile,
            onOpenSettings = onOpenSettings
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                LibraryOption(
                    icon = Icons.Default.Favorite,
                    title = "Liked Songs",
                    subtitle = "Songs you've hearted",
                    onClick = onOpenLikedSongs
                )
            }
            item {
                LibraryOption(
                    icon = Icons.Default.History,
                    title = "Recently Played",
                    subtitle = "Your recent listening",
                    onClick = onOpenRecentlyPlayed
                )
            }
            item {
                LibraryOption(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = "Playlists",
                    subtitle = "Your collections",
                    onClick = onOpenPlaylists
                )
            }
            item {
                LibraryOption(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    title = "Most Played",
                    subtitle = "Your top tracks",
                    onClick = onOpenMostPlayed
                )
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    avatar: ImageBitmap?,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = dezireColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenProfile) {
            if (avatar != null) {
                Image(
                    bitmap = avatar,
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = colors.onBackground
                )
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = colors.onBackground
            )
        }
    }
}

@Composable
private fun LibraryOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
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
                imageVector = icon,
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
                text = title,
                color = colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
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
