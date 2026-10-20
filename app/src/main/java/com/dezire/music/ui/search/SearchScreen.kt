package com.dezire.music.ui.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dezire.music.data.model.Song
import com.dezire.music.ui.components.EmptyState
import com.dezire.music.ui.components.PermissionBlock
import com.dezire.music.ui.components.SectionHeader
import com.dezire.music.ui.components.SongListItem
import com.dezire.music.ui.library.LibraryViewModel
import com.dezire.music.ui.theme.dezireColors

/*
 * Search tab: filters the local library by song title, artist or album.
 */
@Composable
fun SearchScreen(
    libraryViewModel: LibraryViewModel,
    currentSongId: Long?,
    isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    onRequestPermission: () -> Unit
) {
    val songs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val hasPermission by libraryViewModel.hasPermission.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }

    val trimmedQuery = query.trim()
    val results = remember(trimmedQuery, songs) {
        if (trimmedQuery.isEmpty()) {
            emptyList()
        } else {
            val lower = trimmedQuery.lowercase()
            songs.filter { song ->
                song.title.lowercase().contains(lower) ||
                    song.artist.lowercase().contains(lower) ||
                    song.album.lowercase().contains(lower)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            SectionHeader(title = "Search")
        }

        item {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (!hasPermission) {
            item {
                PermissionBlock(
                    granted = false,
                    onRequest = onRequestPermission
                )
            }
        }

        if (query.isNotBlank() && results.isEmpty()) {
            item {
                EmptyState(text = "No results for \"${trimmedQuery}\". Try a different name.")
            }
        } else if (query.isBlank()) {
            item {
                EmptyState(text = "Search by song, artist or album from your device library.")
            }
        }

        items(
            items = results,
            key = { it.id }
        ) { song ->
            val isCurrent = song.id == currentSongId
            SongListItem(
                song = song,
                isPlaying = isCurrent && isPlaying,
                isCurrent = isCurrent,
                onClick = { onSongClick(results, results.indexOf(song)) }
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = dezireColors()
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "Songs, artists or albums",
                color = colors.contentSecondary,
                fontSize = 15.sp
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions.Default,
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = colors.onSurface,
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    )
}