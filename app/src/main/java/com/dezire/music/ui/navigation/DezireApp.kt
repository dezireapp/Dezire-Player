package com.dezire.music.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dezire.music.DezireApplication
import com.dezire.music.data.model.Song
import com.dezire.music.ui.album.AlbumDetailScreen
import com.dezire.music.ui.explore.ExploreScreen
import com.dezire.music.ui.library.FilteredSongsScreen
import com.dezire.music.ui.library.LibraryScreen
import com.dezire.music.ui.library.LibraryViewModel
import com.dezire.music.ui.library.PlaylistDetailScreen
import com.dezire.music.ui.library.PlaylistsScreen
import com.dezire.music.ui.nowplaying.NowPlayingScreen
import com.dezire.music.ui.onboarding.MusicAccessScreen
import com.dezire.music.ui.onboarding.OnboardingSplash
import com.dezire.music.ui.onboarding.OnboardingViewModel
import com.dezire.music.ui.onboarding.WelcomeScreen
import com.dezire.music.ui.profile.ProfileScreen
import com.dezire.music.ui.queue.QueueScreen
import com.dezire.music.ui.search.SearchScreen
import com.dezire.music.ui.settings.EqualizerScreen
import com.dezire.music.ui.settings.SettingsScreen
import com.dezire.music.ui.settings.SettingsViewModel
import com.dezire.music.ui.theme.DezireRed
import com.dezire.music.ui.theme.dezireColors
import com.dezire.music.util.rememberSongArtwork

/*
 * All navigation destinations.
 */
object DezireRoutes {
    const val WELCOME = "onboarding_welcome"
    const val MUSIC_ACCESS = "onboarding_music_access"
    const val EXPLORE = "explore"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val NOW_PLAYING = "nowplaying"
    const val QUEUE = "queue"
    const val FILTER = "filter"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST_DETAIL = "playlist_detail"
    const val ALBUM_DETAIL = "album_detail"
    const val EQUALIZER = "equalizer"

    val TAB_ROUTES = setOf(EXPLORE, SEARCH, LIBRARY)

    fun filter(type: String, value: String): String =
        "$FILTER?type=${Uri.encode(type)}&value=${Uri.encode(value)}"

    fun playlistDetail(id: Long): String = "$PLAYLIST_DETAIL?playlistId=$id"

    fun albumDetail(id: Long): String = "$ALBUM_DETAIL?albumId=$id"
}

/*
 * Root composable: Scaffold with a bottom area (mini player + tab bar) and
 * the NavHost with every destination.
 *
 * The mini player stays visible on every screen except Now Playing (which
 * is a full-screen immersive destination with its own collapse control) as
 * long as a track is loaded, so playback remains reachable while browsing
 * All Songs, Album Details, Artist Details, Profile and Settings.
 */
@Composable
fun DezireApp(
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel,
    onboardingViewModel: OnboardingViewModel
) {
    val context = LocalContext.current
    val app = context.applicationContext as DezireApplication
    val playbackController = app.playbackController

    /*
     * First-launch gate. The persisted flag is read asynchronously, so a
     * neutral splash covers the moment before we know the start
     * destination. The resolved value is captured once and never updated
     * again: completing onboarding flips the flag, and reacting to that
     * here would rebuild the NavHost and drop the current destination.
     */
    val onboardingCompleted by onboardingViewModel.onboardingCompleted
        .collectAsStateWithLifecycle()
    var startDestination by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(onboardingCompleted) {
        if (startDestination == null && onboardingCompleted != null) {
            startDestination = if (onboardingCompleted == true) {
                DezireRoutes.EXPLORE
            } else {
                DezireRoutes.WELCOME
            }
        }
    }

    val resolvedStart = startDestination
    if (resolvedStart == null) {
        OnboardingSplash()
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in DezireRoutes.TAB_ROUTES
    val isOnboarding = currentRoute == DezireRoutes.WELCOME ||
        currentRoute == DezireRoutes.MUSIC_ACCESS

    /*
     * Now Playing, the Play Queue and the onboarding flow are full-screen,
     * edge-to-edge destinations: their background must extend behind the
     * status bar. Every other destination keeps the Scaffold's system-bar
     * padding.
     */
    val isNowPlaying = currentRoute == DezireRoutes.NOW_PLAYING
    val isQueue = currentRoute == DezireRoutes.QUEUE
    val isImmersive = isNowPlaying || isQueue || isOnboarding
    val showMiniPlayer = !isImmersive

    val playerState by playbackController.state.collectAsStateWithLifecycle()
    val favoriteIds by libraryViewModel.favoriteIds.collectAsStateWithLifecycle()

    /*
     * Player failures (unsupported file, unreadable source) are surfaced
     * here as a transient snackbar instead of failing silently.
     */
    val playbackError by playbackController.playbackError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(playbackError) {
        val message = playbackError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        playbackController.clearPlaybackError()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        libraryViewModel.onPermissionResult(granted)
    }

    Scaffold(
        containerColor = dezireColors().background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (showMiniPlayer || showBottomBar) {
                Column(Modifier.fillMaxWidth().background(dezireColors().background)) {
                    if (showMiniPlayer) {
                        playerState.current?.let { current ->
                            MiniPlayerCard(
                                song = current,
                                isPlaying = playerState.isPlaying,
                                onClick = {
                                    navController.navigate(DezireRoutes.NOW_PLAYING)
                                },
                                onPlayPause = playbackController::togglePlayPause,
                                onNext = playbackController::skipToNext
                            )
                        }
                    }
                    if (showBottomBar) {
                        DezireNavigationBar(
                            currentRoute = currentRoute,
                            onNavigate = navController::navigateTab
                        )
                    }
                }
            }
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = resolvedStart,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isImmersive) {
                        Modifier
                    } else {
                        Modifier.padding(innerPadding)
                    }
                )
        ) {

            composable(DezireRoutes.WELCOME) {
                WelcomeScreen(
                    onNext = { navController.navigate(DezireRoutes.MUSIC_ACCESS) },
                    onSkip = { navController.navigate(DezireRoutes.MUSIC_ACCESS) }
                )
            }

            composable(DezireRoutes.MUSIC_ACCESS) {
                MusicAccessScreen(
                    viewModel = onboardingViewModel,
                    onBack = { navController.popBackStack() },
                    onPermissionGranted = { libraryViewModel.syncAudioPermission() },
                    onNotNow = {
                        onboardingViewModel.markOnboardingCompleted()
                        libraryViewModel.syncAudioPermission()
                        navController.navigateToExploreAfterOnboarding()
                    },
                    onFinish = {
                        libraryViewModel.syncAudioPermission()
                        navController.navigateToExploreAfterOnboarding()
                    }
                )
            }

            composable(DezireRoutes.EXPLORE) {
                ExploreScreen(
                    libraryViewModel = libraryViewModel,
                    currentSongId = playerState.current?.id,
                    isPlaying = playerState.isPlaying,
                    onShowAllSongs = {
                        navController.navigate(DezireRoutes.filter("song", ""))
                    },
                    onShowFavorites = {
                        navController.navigate(DezireRoutes.filter("favorites", ""))
                    },
                    onSongClick = { songs, index ->
                        playbackController.setQueue(songs, index)
                    },
                    onArtistClick = { name ->
                        navController.navigate(DezireRoutes.filter("artist", name))
                    },
                    onAlbumClick = { albumId ->
                        navController.navigate(DezireRoutes.albumDetail(albumId))
                    },
                    onRequestPermission = {
                        permissionLauncher.launch(
                            com.dezire.music.data.MusicRepository.requiredAudioPermission()
                        )
                    }
                )
            }

            composable(DezireRoutes.SEARCH) {
                SearchScreen(
                    libraryViewModel = libraryViewModel,
                    currentSongId = playerState.current?.id,
                    isPlaying = playerState.isPlaying,
                    onSongClick = { songs, index ->
                        playbackController.setQueue(songs, index)
                    },
                    onRequestPermission = {
                        permissionLauncher.launch(
                            com.dezire.music.data.MusicRepository.requiredAudioPermission()
                        )
                    }
                )
            }

            composable(DezireRoutes.LIBRARY) {
                LibraryScreen(
                    libraryViewModel = libraryViewModel,
                    onOpenProfile = { navController.navigateTo(DezireRoutes.PROFILE) },
                    onOpenSettings = { navController.navigateTo(DezireRoutes.SETTINGS) },
                    onOpenLikedSongs = {
                        navController.navigate(DezireRoutes.filter("liked", ""))
                    },
                    onOpenRecentlyPlayed = {
                        navController.navigate(DezireRoutes.filter("recent", ""))
                    },
                    onOpenPlaylists = { navController.navigateTo(DezireRoutes.PLAYLISTS) },
                    onOpenMostPlayed = {
                        navController.navigate(DezireRoutes.filter("most_played", ""))
                    }
                )
            }

            composable(DezireRoutes.PROFILE) {
                ProfileScreen(
                    libraryViewModel = libraryViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(DezireRoutes.PLAYLISTS) {
                PlaylistsScreen(
                    libraryViewModel = libraryViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenPlaylist = { id ->
                        navController.navigate(DezireRoutes.playlistDetail(id))
                    }
                )
            }

            composable(
                route = "${DezireRoutes.PLAYLIST_DETAIL}?playlistId={playlistId}",
                arguments = listOf(
                    navArgument("playlistId") { type = NavType.LongType }
                )
            ) { entry ->
                val playlistId = entry.arguments?.getLong("playlistId") ?: return@composable
                PlaylistDetailScreen(
                    libraryViewModel = libraryViewModel,
                    playlistId = playlistId,
                    currentSongId = playerState.current?.id,
                    isPlaying = playerState.isPlaying,
                    onBack = { navController.popBackStack() },
                    onSongClick = { songs, index ->
                        playbackController.setQueue(songs, index)
                    }
                )
            }

            composable(
                route = "${DezireRoutes.ALBUM_DETAIL}?albumId={albumId}",
                arguments = listOf(
                    navArgument("albumId") { type = NavType.LongType }
                )
            ) { entry ->
                val albumId = entry.arguments?.getLong("albumId") ?: return@composable
                AlbumDetailScreen(
                    libraryViewModel = libraryViewModel,
                    playbackController = playbackController,
                    albumId = albumId,
                    currentSongId = playerState.current?.id,
                    isPlaying = playerState.isPlaying,
                    favoriteIds = favoriteIds,
                    onBack = { navController.popBackStack() },
                    onViewArtist = { name ->
                        navController.navigate(DezireRoutes.filter("artist", name))
                    },
                    onOpenAlbum = { id ->
                        navController.navigate(DezireRoutes.albumDetail(id))
                    }
                )
            }

            composable(DezireRoutes.SETTINGS) {
                SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    onBack = { navController.popBackStack() },
                    onRescan = libraryViewModel::refresh,
                    onOpenEqualizer = { navController.navigate(DezireRoutes.EQUALIZER) }
                )
            }

            composable(DezireRoutes.EQUALIZER) {
                EqualizerScreen(
                    equalizerManager = playbackController.getEqualizerManager(),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(DezireRoutes.NOW_PLAYING) {
                NowPlayingScreen(
                    playbackController = playbackController,
                    favoriteIds = favoriteIds,
                    onToggleFavorite = { song -> libraryViewModel.toggleFavorite(song) },
                    onViewAlbum = { albumId ->
                        navController.navigate(DezireRoutes.albumDetail(albumId))
                    },
                    onViewArtist = { artist ->
                        navController.navigate(DezireRoutes.filter("artist", artist))
                    },
                    onOpenQueue = { navController.navigate(DezireRoutes.QUEUE) },
                    onCollapse = { navController.popBackStack() }
                )
            }

            composable(DezireRoutes.QUEUE) {
                QueueScreen(
                    playbackController = playbackController,
                    favoriteIds = favoriteIds,
                    onToggleFavorite = { song -> libraryViewModel.toggleFavorite(song) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "${DezireRoutes.FILTER}?type={type}&value={value}",
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("value") { type = NavType.StringType }
                )
            ) { entry ->
                val type = entry.arguments?.getString("type").orEmpty()
                val value = entry.arguments?.getString("value").orEmpty()
                FilteredSongsScreen(
                    libraryViewModel = libraryViewModel,
                    playbackController = playbackController,
                    type = type,
                    value = value,
                    currentSongId = playerState.current?.id,
                    isPlaying = playerState.isPlaying,
                    onBack = { navController.popBackStack() },
                    onSongClick = { songs, index ->
                        playbackController.setQueue(songs, index)
                    },
                    onOpenAlbum = { albumId ->
                        navController.navigate(DezireRoutes.albumDetail(albumId))
                    }
                )
            }
        }
    }
}

/*
 * Bottom navigation behaviour: tabs state is saved/restored so switching
 * back to a tab keeps its scroll position.
 */
private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/*
 * Leaves the onboarding flow for Explore, clearing whichever onboarding
 * steps are on the back stack so the user cannot navigate back into it.
 */
private fun NavHostController.navigateToExploreAfterOnboarding() {
    navigate(DezireRoutes.EXPLORE) {
        popUpTo(DezireRoutes.WELCOME) { inclusive = true }
        launchSingleTop = true
    }
}

/*
 * Pushes a secondary destination (Profile / Settings opened from the
 * Library header) without creating duplicates on the back stack.
 */
private fun NavHostController.navigateTo(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

/*
 * Bottom tab bar for the three primary destinations.
 */
@Composable
private fun DezireNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val colors = dezireColors()
    val items = listOf(
        NavigationItem(DezireRoutes.EXPLORE, "Explore", Icons.AutoMirrored.Outlined.List),
        NavigationItem(DezireRoutes.SEARCH, "Search", Icons.Default.Search),
        NavigationItem(DezireRoutes.LIBRARY, "Library", Icons.Default.LibraryMusic)
    )

    NavigationBar(
        containerColor = colors.bottomBar,
        contentColor = colors.onBackground,
        tonalElevation = 0.dp
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        fontSize = 11.sp
                    )
                },
                colors = NavigationBarItemColors(
                    selectedIconColor = DezireRed,
                    selectedTextColor = DezireRed,
                    selectedIndicatorColor = colors.bottomBarIndicator,
                    unselectedIconColor = colors.onBackground,
                    unselectedTextColor = colors.onBackground,
                    disabledIconColor = colors.contentSecondary,
                    disabledTextColor = colors.contentSecondary
                )
            )
        }
    }
}

/*
 * Compact "now playing" card above the tab bar / system navigation area.
 * Shows the current track, its artwork and a play/pause toggle; tapping it
 * opens Now Playing. It observes the shared playback state, so it stays in
 * sync on every screen without keeping its own song state.
 */
@Composable
fun MiniPlayerCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artwork = rememberSongArtwork(song)
    val colors = dezireColors()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.card)
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
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = colors.contentSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = song.title,
                color = colors.onSurface,
                fontSize = 14.sp,
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

        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = colors.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }

        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = colors.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

private data class NavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)