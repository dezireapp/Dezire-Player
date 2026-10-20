package com.dezire.music.ui.nowplaying

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dezire.music.R
import com.dezire.music.data.Playlist
import com.dezire.music.data.lyrics.LyricLine
import com.dezire.music.data.lyrics.LyricSyncMode
import com.dezire.music.data.lyrics.Lyrics
import com.dezire.music.data.model.Song
import com.dezire.music.dezireApp
import com.dezire.music.playback.PlaybackController
import com.dezire.music.ui.components.dynamicArtworkGlow
import com.dezire.music.ui.theme.DezireCardGray
import com.dezire.music.util.ArtworkPalette
import com.dezire.music.util.rememberArtworkPalette
import com.dezire.music.util.rememberBlurredArtwork
import com.dezire.music.util.rememberSongArtwork
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Fallback metadata shown before the user has started any real playback.
 * Once a real track is buffered this is replaced by data from MediaStore.
 */
private const val SAMPLE_TITLE = "Maya Maya"
private const val SAMPLE_ARTIST = "Anita Mandal"
private const val SAMPLE_ALBUM = "Maya Maya"

// ---------------------------------------------------------------------------
// Design tokens (from the Figma file)
// ---------------------------------------------------------------------------

private val LikePink = Color(0xFFFF2D55)
private val SecondaryText = Color(0xFFB3B3B8)
private val HandFill = Color(0xD92B2B2B)

// Typography shared by every lyric renderer and by the height reservation, so
// the reserved two-line lyric area always matches what is actually drawn.
private val LyricFontSize = 22.sp
private val LyricLineHeight = 28.sp

// Top bar content height (its icons). Used to reserve the lyric area.
private val TopBarHeight = 28.dp

// ---------------------------------------------------------------------------
// State and events
// ---------------------------------------------------------------------------

enum class SwipeDirection { Next, Previous }

data class NowPlayingState(
    val sourceLabel: String,
    val title: String,
    val artist: String,
    /** Parsed lyrics for this page, or null when none are available. */
    val lyrics: Lyrics?,
    /** Index of the line active at the current playback position (-1 = before first). */
    val activeLyricIndex: Int,
    /** Index of the active word inside [activeLyricIndex] (-1 = none / line mode). */
    val activeWordIndex: Int = -1,
    val isPlaying: Boolean,
    val isLiked: Boolean,
    val isFollowing: Boolean,
    val likeCount: String,
    val commentCount: String,
    val shareCount: String,
    /** 0f..1f */
    val progress: Float,
    val showSwipeHint: Boolean,
    /** Identity of the track this page renders. Drives the swipe animation. */
    val songId: Long = 0L,
    /** Which way the last swipe went. Decides the slide direction. */
    val swipeDirection: SwipeDirection = SwipeDirection.Next,
    /** Artwork URL, e.g. for Coil. */
    val artworkUrl: String? = null,
)

sealed interface NowPlayingEvent {
    data object Back : NowPlayingEvent
    data object More : NowPlayingEvent
    data object TogglePlay : NowPlayingEvent
    data object ToggleLike : NowPlayingEvent
    data object ToggleFollow : NowPlayingEvent
    data object Comment : NowPlayingEvent
    data object Share : NowPlayingEvent
    data object AddToPlaylist : NowPlayingEvent
    data object Download : NowPlayingEvent
    data object Queue : NowPlayingEvent
    data object SwipeNext : NowPlayingEvent
    data object SwipePrevious : NowPlayingEvent
    data class SeekTo(val fraction: Float) : NowPlayingEvent
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

/*
 * NowPlayingScreen
 *
 * Root orchestrator. Every control reads real playback state from
 * [PlaybackController] and drives the shared ExoPlayer instance directly;
 * nothing here is simulated. The design is the layered full-bleed layout:
 *
 *   1) Artwork (or gradient placeholder) filling the whole screen
 *   2) A bottom scrim that keeps the metadata legible
 *   3) Top bar, lyrics, watermark title, song info, actions and controls
 *   4) Modal sheets / dialogs (queue, sleep timer, more options, details)
 *
 * Elements of the Figma design that have no real counterpart in a purely
 * local library are left out rather than wired to fake actions: lyrics,
 * artist follow, comments and downloads are only rendered when the app has
 * real data/behaviour behind them.
 */
@Composable
fun NowPlayingScreen(
    playbackController: PlaybackController,
    favoriteIds: Set<Long>,
    onToggleFavorite: (Song) -> Unit,
    onViewAlbum: (Long) -> Unit,
    onViewArtist: (String) -> Unit,
    onOpenQueue: () -> Unit,
    onCollapse: () -> Unit
) {
    val context = LocalContext.current
    val uiState by playbackController.state.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by playbackController.sleepTimerRemainingMs.collectAsStateWithLifecycle()

    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showSwipeHint by remember { mutableStateOf(false) }
    var detailsSong by remember { mutableStateOf<Song?>(null) }

    val settingsRepository = remember(context) { context.dezireApp.settingsRepository }
    val lyricsRepository = remember(context) { context.dezireApp.lyricsRepository }
    val playlistRepository = remember(context) { context.dezireApp.playlistRepository }
    val playlists by playlistRepository.playlists.collectAsStateWithLifecycle()

    /*
     * First-launch swipe hint. The persisted flag is read once when the
     * screen is entered. Only on a fresh install (flag absent/false) is the
     * hint shown and the flag flipped, so it never reappears on later
     * openings, navigation or process restarts. The persisted value - not a
     * transient remember - is the source of truth.
     */
    LaunchedEffect(Unit) {
        val alreadyShown = runCatching { settingsRepository.swipeHintShown.first() }
            .getOrDefault(true)
        if (!alreadyShown) {
            showSwipeHint = true
            settingsRepository.markSwipeHintShown()
        }
    }

    /*
     * Single source of truth for the current track's artwork. The same
     * image feeds the full-bleed background, so it stays in sync when the
     * track changes.
     */
    val currentSong = uiState.current
    val isFavorite = currentSong != null && currentSong.id in favoriteIds
    val showingFallback = currentSong == null
    val songArtwork = rememberSongArtwork(currentSong)

    val samplePainter = painterResource(id = R.drawable.maya_maya_cover)
    val placeholderPainter = remember { ColorPainter(DezireCardGray) }
    val fallbackPainter = if (showingFallback) samplePainter else placeholderPainter
    val currentArtwork: Painter = songArtwork?.let(::BitmapPainter) ?: fallbackPainter
    val currentBlurredArtwork = rememberBlurredArtwork(songArtwork)

    val totalDuration = uiState.durationMs
    val progress = if (totalDuration > 0) {
        (uiState.positionMs.toFloat() / totalDuration).coerceIn(0f, 1f)
    } else {
        0f
    }

    /*
     * Resolve the tracks that the next swipe would move to, straight from
     * the real ExoPlayer timeline, and preload their artwork so the
     * incoming page is ready the moment a gesture starts.
     */
    val nextSong = playbackController.peekNext()
    val previousSong = playbackController.peekPrevious()
    val nextArtworkBitmap = rememberSongArtwork(nextSong)
    val previousArtworkBitmap = rememberSongArtwork(previousSong)
    val nextArtwork: Painter = nextArtworkBitmap?.let(::BitmapPainter) ?: placeholderPainter
    val previousArtwork: Painter =
        previousArtworkBitmap?.let(::BitmapPainter) ?: placeholderPainter
    val nextBlurredArtwork = rememberBlurredArtwork(nextArtworkBitmap)
    val previousBlurredArtwork = rememberBlurredArtwork(previousArtworkBitmap)

    /*
     * Artwork-derived palettes for every page. Extracted once per song and
     * cached, so they animate (never jump) when the track changes and cost
     * nothing to re-read during recomposition.
     */
    val currentPalette = rememberArtworkPalette(currentSong)
    val nextPalette = rememberArtworkPalette(nextSong)
    val previousPalette = rememberArtworkPalette(previousSong)

    /*
     * Lyrics for the current track, loaded through the shared repository
     * (which caches per track, so re-entering the screen never re-parses the
     * file). While the track is unchanged the loaded value is reused.
     */
    val currentSongId = currentSong?.id
    /*
     * Keyed on the track id, so switching tracks clears the previous song's
     * lyrics immediately - stale lyrics are never rendered while the new
     * lookup is in flight.
     */
    var lyrics by remember(currentSongId) { mutableStateOf<Lyrics?>(null) }
    val latestSongId = rememberUpdatedState(currentSongId)
    LaunchedEffect(currentSongId) {
        val loaded = runCatching { lyricsRepository.lyricsFor(currentSong) }.getOrNull()
        /*
         * Ignore a result that arrives after the track changed, so an earlier
         * song's asynchronous lookup can never overwrite the current one.
         * Empty / malformed payloads collapse to null and simply hide the
         * block rather than showing an empty panel.
         */
        if (latestSongId.value == currentSongId) {
            lyrics = loaded?.takeIf { it.hasRenderableLyrics() }
        }
    }

    /*
     * Fine-grained playback position. The controller already publishes a
     * position at 500 ms for the seek bar, but lyric highlighting wants a
     * tighter loop. This is a composition-scoped coroutine - cancelled
     * automatically when the screen leaves composition - not an unmanaged
     * timer. The raw value is never read during composition of the page; it
     * only feeds the derived active index, so ticking does not recompose the
     * whole screen.
     */
    val lyricPositionTick = remember(currentSong?.id) {
        mutableLongStateOf(uiState.positionMs)
    }
    LaunchedEffect(currentSong?.id, uiState.isPlaying) {
        lyricPositionTick.longValue = playbackController.currentPositionMs()
        while (true) {
            delay(if (uiState.isPlaying) 100L else 350L)
            lyricPositionTick.longValue = playbackController.currentPositionMs()
        }
    }

    /*
     * The controller republishes [PlayerUiState.positionMs] whenever the
     * position jumps (seek, track change), so mirroring it here makes the
     * lyric highlight snap to the new position immediately instead of waiting
     * for the next poll tick. During steady playback this simply re-reads the
     * same value.
     */
    LaunchedEffect(uiState.positionMs) {
        lyricPositionTick.longValue = playbackController.currentPositionMs()
    }

    /*
     * derivedStateOf only invalidates its readers when the active index
     * actually changes, so the page recomposes at line boundaries (and on
     * the existing 500 ms position updates) rather than every tick.
     */
    val activeLyricIndex by remember(lyrics, lyricPositionTick) {
        derivedStateOf { lyrics?.activeIndex(lyricPositionTick.longValue) ?: -1 }
    }

    /*
     * Word cursor for karaoke lyrics. Computed from the same fine-grained tick
     * as the active line, so it advances with playback, reacts immediately to
     * seeking and simply stops changing while playback is paused (the tick
     * keeps reading the same ExoPlayer position). It stays at -1 for
     * line-level and unsynchronised sources.
     */
    val activeWordIndex by remember(lyrics, lyricPositionTick) {
        derivedStateOf {
            val source = lyrics ?: return@derivedStateOf -1
            if (source.mode != LyricSyncMode.WORD_BY_WORD) return@derivedStateOf -1
            val position = lyricPositionTick.longValue
            val lineIndex = source.activeIndex(position)
            if (lineIndex < 0) -1 else source.activeWordIndex(lineIndex, position)
        }
    }

    /*
     * Maps a track (and the progress to show for its page) to the
     * presentational state. The current page shows live progress, its
     * synchronised lyrics and possibly the first-launch hint; incoming
     * pages are static.
     */
    fun pageState(
        song: Song?,
        songProgress: Float,
        showHint: Boolean,
        pageLyrics: Lyrics? = null,
        pageActiveIndex: Int = -1,
        pageActiveWordIndex: Int = -1,
    ) = NowPlayingState(
        sourceLabel = song?.album?.takeIf { it.isNotBlank() } ?: SAMPLE_ALBUM,
        title = song?.title ?: SAMPLE_TITLE,
        artist = song?.artist ?: SAMPLE_ARTIST,
        lyrics = pageLyrics,
        activeLyricIndex = pageActiveIndex,
        activeWordIndex = pageActiveWordIndex,
        isPlaying = uiState.isPlaying,
        isLiked = song != null && song.id in favoriteIds,
        isFollowing = false,
        likeCount = "",
        commentCount = "",
        shareCount = "",
        progress = songProgress,
        showSwipeHint = showHint,
        songId = song?.id ?: 0L,
    )

    NowPlayingHost(
        current = NowPlayingPageData(
            pageState(
                song = currentSong,
                songProgress = progress,
                showHint = showSwipeHint,
                pageLyrics = lyrics,
                pageActiveIndex = activeLyricIndex,
                pageActiveWordIndex = activeWordIndex,
            ),
            currentArtwork,
            currentBlurredArtwork,
            currentPalette,
        ),
        next = nextSong?.let {
            NowPlayingPageData(pageState(it, 0f, false), nextArtwork, nextBlurredArtwork, nextPalette)
        },
        previous = previousSong?.let {
            NowPlayingPageData(pageState(it, 0f, false), previousArtwork, previousBlurredArtwork, previousPalette)
        },
        onEvent = { event ->
            when (event) {
                NowPlayingEvent.Back -> onCollapse()
                NowPlayingEvent.More -> showMoreSheet = true
                NowPlayingEvent.TogglePlay -> playbackController.togglePlayPause()
                NowPlayingEvent.ToggleLike -> currentSong?.let(onToggleFavorite)
                NowPlayingEvent.Share -> currentSong?.let { shareSong(context, it) }
                NowPlayingEvent.AddToPlaylist -> showPlaylistSheet = true
                NowPlayingEvent.Queue -> onOpenQueue()
                NowPlayingEvent.SwipeNext -> {
                    showSwipeHint = false
                    playbackController.skipToNext()
                }
                NowPlayingEvent.SwipePrevious -> {
                    showSwipeHint = false
                    playbackController.seekToPreviousTrack()
                }
                is NowPlayingEvent.SeekTo -> playbackController.seekTo(event.fraction)
                /*
                 * Comment, download and follow have no real backing in a
                 * purely local library, so their controls are never shown.
                 */
                NowPlayingEvent.Comment,
                NowPlayingEvent.Download,
                NowPlayingEvent.ToggleFollow -> Unit
            }
        }
    )

    if (showSleepSheet) {
        SleepTimerSheet(
            remainingMs = sleepTimerRemainingMs,
            onDismiss = { showSleepSheet = false },
            onSelect = { playbackController.startSleepTimer(it) },
            onCancelTimer = { playbackController.cancelSleepTimer() }
        )
    }

    if (showMoreSheet) {
        MoreActionsSheet(
            isFavorite = isFavorite,
            hasSong = currentSong != null,
            isShuffleEnabled = uiState.shuffleEnabled,
            repeatMode = uiState.repeatMode,
            onDismiss = { showMoreSheet = false },
            onAddToQueue = { currentSong?.let(playbackController::addToQueue) },
            onToggleFavorite = { currentSong?.let(onToggleFavorite) },
            onViewAlbum = { currentSong?.let { onViewAlbum(it.albumId) } },
            onViewArtist = { currentSong?.let { onViewArtist(it.artist) } },
            onSongDetails = { detailsSong = currentSong },
            onSleepTimer = { showSleepSheet = true },
            onShuffleToggle = playbackController::toggleShuffle,
            onRepeatToggle = playbackController::cycleRepeatMode
        )
    }

    if (showPlaylistSheet) {
        val song = currentSong
        if (song != null) {
            AddToPlaylistSheet(
                playlists = playlists,
                onDismiss = { showPlaylistSheet = false },
                onCreate = { name -> playlistRepository.create(name) },
                onAdd = { playlistId ->
                    val playlist =
                        playlistRepository.playlists.value.firstOrNull { it.id == playlistId }
                    val already = playlist?.songIds?.contains(song.id) == true
                    if (!already) playlistRepository.addSongs(playlistId, listOf(song.id))
                    val name = playlist?.name ?: "playlist"
                    val message = if (already) "Already in $name" else "Added to $name"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    showPlaylistSheet = false
                }
            )
        }
    }

    detailsSong?.let { song ->
        SongDetailsDialog(
            song = song,
            onDismiss = { detailsSong = null }
        )
    }
}
/*
 * One full-screen Now Playing page: the track state it renders plus its
 * (already loaded) artwork and derived palette.
 */
private data class NowPlayingPageData(
    val state: NowPlayingState,
    val artwork: Painter?,
    val blurredArtwork: ImageBitmap?,
    val palette: ArtworkPalette? = null,
)

/*
 * NowPlayingHost
 *
 * Reels-style vertical track transition. A single [Animatable] holds the
 * vertical offset in pixels of the outgoing page. While the user drags, the
 * current page follows the finger and the incoming page is revealed from the
 * opposite edge, so the whole full-screen layout moves together. Crossing
 * the swipe threshold plays the next/previous track through [onEvent] and
 * finishes the transition; releasing early (or when there is no adjacent
 * track) animates the current page back to rest.
 */
@Composable
private fun NowPlayingHost(
    current: NowPlayingPageData,
    next: NowPlayingPageData?,
    previous: NowPlayingPageData?,
    onEvent: (NowPlayingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(0f) }
    var isAnimating by remember { mutableStateOf(false) }

    /*
     * Post-commit snapshots. Once a swipe is committed the outgoing and
     * incoming pages are frozen so the underlying state change (which drives
     * playback) cannot make either page flicker mid-transition.
     */
    var committedOutgoing by remember { mutableStateOf<NowPlayingPageData?>(null) }
    var committedIncoming by remember { mutableStateOf<NowPlayingPageData?>(null) }
    var committedDirection by remember { mutableFloatStateOf(0f) }

    /*
     * The gesture detector is keyed on Unit so it is not torn down mid-drag,
     * therefore the changing pages/handler must be read through updated
     * state rather than captured once.
     */
    val currentPage = rememberUpdatedState(current)
    val nextPage = rememberUpdatedState(next)
    val previousPage = rememberUpdatedState(previous)
    val eventHandler = rememberUpdatedState(onEvent)
    val latestSongId = rememberUpdatedState(current.state.songId)

    val durationMs = 320

    fun settleBack() {
        if (isAnimating || committedIncoming != null) return
        isAnimating = true
        scope.launch {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
            )
            dragAccum = 0f
            isAnimating = false
        }
    }

    /*
     * Finishes a committed transition: once the player reports the incoming
     * track as current, the snapshots are dropped and the offset reset so
     * the live (already correct) page takes over with no visible jump. A
     * timeout guards the unexpected case where playback never changes.
     */
    LaunchedEffect(committedIncoming) {
        val target = committedIncoming ?: return@LaunchedEffect
        withTimeoutOrNull(700L) {
            snapshotFlow { latestSongId.value }.first { it == target.state.songId }
        }
        offsetY.snapTo(0f)
        dragAccum = 0f
        committedOutgoing = null
        committedIncoming = null
        committedDirection = 0f
        isAnimating = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        if (!isAnimating && committedIncoming == null) {
                            dragAccum = offsetY.value
                        }
                    },
                    onDragCancel = { settleBack() },
                    onDragEnd = {
                        if (isAnimating || committedIncoming != null || heightPx <= 0f) {
                            return@detectVerticalDragGestures
                        }
                        val threshold = (heightPx * 0.2f).coerceAtLeast(1f)
                        val direction = when {
                            dragAccum <= -threshold -> -1f
                            dragAccum >= threshold -> 1f
                            else -> 0f
                        }
                        val target = when (direction) {
                            -1f -> nextPage.value
                            1f -> previousPage.value
                            else -> null
                        }
                        if (target == null) {
                            settleBack()
                        } else {
                            isAnimating = true
                            scope.launch {
                                offsetY.animateTo(
                                    targetValue = direction * heightPx,
                                    animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
                                )
                                dragAccum = offsetY.value
                                committedOutgoing = currentPage.value
                                committedIncoming = target
                                committedDirection = direction
                                eventHandler.value(
                                    if (direction < 0f) NowPlayingEvent.SwipeNext
                                    else NowPlayingEvent.SwipePrevious,
                                )
                            }
                        }
                    },
                    onVerticalDrag = { change, delta ->
                        if (isAnimating || committedIncoming != null || heightPx <= 0f) {
                            return@detectVerticalDragGestures
                        }
                        change.consume()
                        dragAccum = (dragAccum + delta).coerceIn(-heightPx, heightPx)
                        scope.launch { offsetY.snapTo(dragAccum) }
                    },
                )
            },
    ) {
        val offset = offsetY.value
        val committed = committedIncoming != null
        val direction = if (committed) {
            committedDirection
        } else {
            when {
                offset < 0f -> -1f
                offset > 0f -> 1f
                else -> 0f
            }
        }
        val outgoing = if (committed) committedOutgoing else current
        val incoming = if (committed) {
            committedIncoming
        } else {
            when (direction) {
                -1f -> next
                1f -> previous
                else -> null
            }
        }

        // Incoming page enters from the opposite edge (below for next, above
        // for previous) and settles exactly on top of the outgoing page.
        incoming?.let { page ->
            NowPlayingDesign(
                state = page.state,
                onEvent = onEvent,
                artwork = page.artwork,
                blurredArtwork = page.blurredArtwork,
                palette = page.palette,
                modifier = Modifier.offset {
                    IntOffset(0, (offset - direction * heightPx).roundToInt())
                },
            )
        }
        outgoing?.let { page ->
            NowPlayingDesign(
                state = page.state,
                onEvent = onEvent,
                artwork = page.artwork,
                blurredArtwork = page.blurredArtwork,
                palette = page.palette,
                modifier = Modifier.offset { IntOffset(0, offset.roundToInt()) },
            )
        }
    }
}

/*
 * NowPlayingDesign
 *
 * Purely presentational single page: maps a [NowPlayingState] to the layered
 * full-bleed layout and emits [NowPlayingEvent]s for every interaction. The
 * surrounding [NowPlayingHost] owns the vertical swipe gesture and positions
 * the page via [modifier]; the whole page moves as one unit.
 */
@Composable
private fun NowPlayingDesign(
    state: NowPlayingState,
    onEvent: (NowPlayingEvent) -> Unit,
    modifier: Modifier = Modifier,
    artwork: Painter? = null,
    blurredArtwork: ImageBitmap? = null,
    palette: ArtworkPalette? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 1. Immersive artwork background (sharp over blurred over black)
        ImmersiveArtwork(artwork, blurredArtwork)

        /*
         * Flowing artwork-derived light. Two horizontal bands - one strictly
         * above the sharp artwork band, one strictly below it - carry the
         * slow-moving diffuse glow. Because they are framed by the artwork
         * band's fractions, the light blends into the blurred background
         * around the lyrics and around the song title/artist but can never
         * paint over the crisp cover itself.
         */
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(SharpBandTopFraction)
                .clipToBounds()
                .dynamicArtworkGlow(palette),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(1f - ArtworkBandBottomFraction)
                .clipToBounds()
                .dynamicArtworkGlow(palette),
        )

        /*
         * Reserve a fixed lyric area strictly above the sharp artwork band so
         * no lyric can ever draw over the artwork. The band top is a fraction
         * of the screen (see [ImmersiveArtwork]); everything above it - minus
         * the status bar and the top bar - is the maximum the lyrics may
         * occupy. The area is exactly two lines tall when shown, and hidden
         * entirely when the screen is too short to fit it above the artwork.
         */
        val density = LocalDensity.current
        val lineHeightPx = with(density) { LyricLineHeight.toPx() }
        val desiredLyricsPx = lineHeightPx * 2f + with(density) { 16.dp.toPx() }
        val statusBarPx = WindowInsets.statusBars.getTop(density).toFloat()
        val consumedAbovePx = with(density) {
            (12.dp + TopBarHeight + 28.dp + 16.dp).toPx()
        }
        val artworkTopPx = with(density) { maxHeight.toPx() } * SharpBandTopFraction
        val availableLyricsPx = artworkTopPx - statusBarPx - consumedAbovePx
        val showLyricsArea = availableLyricsPx >= desiredLyricsPx
        val lyricsHeight = with(density) {
            minOf(desiredLyricsPx, availableLyricsPx.coerceAtLeast(0f)).toDp()
        }

        // 2. Content
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(12.dp))
            TopBar(
                sourceLabel = state.sourceLabel,
                onBack = { onEvent(NowPlayingEvent.Back) },
                onMore = { onEvent(NowPlayingEvent.More) },
            )
            Spacer(Modifier.height(28.dp))
            /*
             * The lyrics block is only composed when the current track really
             * has renderable lyrics, so a song without lyrics leaves the
             * immersive artwork visible and reserves no empty panel.
             */
            val pageLyrics = state.lyrics
            if (showLyricsArea && pageLyrics != null) {
                LyricsArea(
                    lyrics = pageLyrics,
                    activeIndex = state.activeLyricIndex,
                    activeWordIndex = state.activeWordIndex,
                    reservedHeight = lyricsHeight,
                )
            }

            Spacer(Modifier.weight(1f))

            SongInfo(
                title = state.title,
                artist = state.artist,
                isFollowing = state.isFollowing,
                onFollow = null,
            )
            Spacer(Modifier.height(18.dp))
            ActionRow(
                isLiked = state.isLiked,
                likeCount = state.likeCount,
                onToggleLike = { onEvent(NowPlayingEvent.ToggleLike) },
                onAddToPlaylist = { onEvent(NowPlayingEvent.AddToPlaylist) },
                commentCount = state.commentCount,
                onComment = null,
                shareCount = state.shareCount,
                onShare = { onEvent(NowPlayingEvent.Share) },
                onDownload = null,
                onQueue = { onEvent(NowPlayingEvent.Queue) },
            )
            Spacer(Modifier.height(18.dp))
            PlayerControls(
                isPlaying = state.isPlaying,
                progress = state.progress,
                onTogglePlay = { onEvent(NowPlayingEvent.TogglePlay) },
                onSeek = { onEvent(NowPlayingEvent.SeekTo(it)) },
            )
            Spacer(Modifier.height(8.dp))
        }

        // 4. First-launch swipe hint
        AnimatedVisibility(
            visible = state.showSwipeHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 28.dp)
                .offset(y = 40.dp),
        ) {
            SwipeHint()
        }
    }
}

// ---------------------------------------------------------------------------
// Components
// ---------------------------------------------------------------------------

@Composable
private fun TopBar(
    sourceLabel: String,
    onBack: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
        )
        Text(
            text = sourceLabel,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            imageVector = Icons.Filled.MoreHoriz,
            contentDescription = "More options",
            tint = Color.White,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onMore),
        )
    }
}

/*
 * Readability shadow applied to every lyric line so it stays legible over
 * bright or busy artwork.
 */
private val LyricShadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 8f)

/*
 * LyricsArea
 *
 * A fixed, bounded window for the active lyric block, reserved strictly
 * above the sharp artwork band. The area is left aligned with generous
 * horizontal padding, matching the reference. Its height is supplied by the
 * caller from the space that actually remains above the artwork and it clips
 * its content, so a lyric can never grow into the artwork or shift the layout
 * below it. The subtle scrim keeps the text legible without hiding the
 * artwork. Callers only compose it when the track really has renderable
 * lyrics, so songs without lyrics show no text and no reserved panel.
 */
@Composable
private fun LyricsArea(
    lyrics: Lyrics,
    activeIndex: Int,
    activeWordIndex: Int,
    reservedHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(reservedHeight)
            .clipToBounds()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Transparent,
                )
            )
            .padding(horizontal = 28.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        LyricsLines(
            lyrics = lyrics,
            activeIndex = activeIndex,
            activeWordIndex = activeWordIndex,
        )
    }
}

/*
 * LyricsLines
 *
 * Line-by-line presentation: the active line bright and prominent with the
 * upcoming line beneath it dimmer. The block transitions on the active index
 * only, so it fades/rises when the line changes but is not replayed when the
 * karaoke word cursor advances within the same line.
 */
@Composable
private fun LyricsLines(
    lyrics: Lyrics,
    activeIndex: Int,
    activeWordIndex: Int,
) {
    AnimatedContent(
        targetState = activeIndex,
        transitionSpec = {
            (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 4 })
                .togetherWith(fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 4 })
        },
        label = "lyricLines",
    ) { index ->
        val line = lyrics.lines.getOrNull(index) ?: return@AnimatedContent
        if (line.displayText().isBlank()) return@AnimatedContent
        val nextLine = lyrics.lines.getOrNull(index + 1)
            ?.takeIf { it.displayText().isNotBlank() }
        LyricPair(
            line = line,
            nextLine = nextLine,
            activeWordIndex = if (index == activeIndex) activeWordIndex else -1,
        )
    }
}

/*
 * One active line plus, when it fits on a single line, the upcoming line
 * beneath it. The full line is measured first; a line that would need more
 * than two lines at the available width is skipped entirely (no clipping, no
 * ellipsis, no shrinking) and synchronisation continues at the next line. The
 * next line is only drawn when the active line occupies a single line, so the
 * two-line reserved area can never overflow.
 */
@Composable
private fun LyricPair(
    line: LyricLine,
    nextLine: LyricLine?,
    activeWordIndex: Int,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = constraints.maxWidth
        val measurer = rememberTextMeasurer()
        val activeText = line.displayText()
        val activeLineCount = remember(activeText, widthPx) {
            if (widthPx <= 0) {
                1
            } else {
                measurer.measure(
                    text = AnnotatedString(activeText),
                    style = LyricTextStyle,
                    maxLines = Int.MAX_VALUE,
                    constraints = Constraints(maxWidth = widthPx),
                ).lineCount
            }
        }
        if (activeLineCount > 2) return@BoxWithConstraints

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            if (line.hasWordTiming) {
                KaraokeLine(line = line, activeWordIndex = activeWordIndex)
            } else {
                Text(
                    text = activeText,
                    modifier = Modifier.fillMaxWidth(),
                    style = LyricTextStyle.copy(shadow = LyricShadow),
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                )
            }
            if (nextLine != null && activeLineCount <= 1) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = nextLine.displayText(),
                    modifier = Modifier.fillMaxWidth(),
                    style = LyricNextTextStyle.copy(shadow = LyricShadow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/*
 * The readable text of a line: the word-by-word join when word timing is
 * present, otherwise the plain line. Used for both measurement and drawing
 * so the fit decision and the rendered layout always agree.
 */
private fun LyricLine.displayText(): String =
    if (hasWordTiming) words.joinToString(" ") { it.text } else text

/*
 * True when at least one line carries visible text. Missing, empty and
 * malformed payloads are all treated the same way: the lyrics block stays
 * hidden instead of showing a placeholder.
 */
private fun Lyrics.hasRenderableLyrics(): Boolean =
    lines.any { it.displayText().isNotBlank() }

/*
 * Base style for the active lyric line: left aligned, two-line maximum, with
 * the shared readability shadow. The same style measures whether a line fits,
 * so the fit decision matches the rendered layout exactly.
 */
private val LyricTextStyle = TextStyle(
    color = Color.White,
    fontSize = LyricFontSize,
    lineHeight = LyricLineHeight,
    fontWeight = FontWeight.SemiBold,
    textAlign = TextAlign.Start,
)

/*
 * The dimmer upcoming lyric line shown beneath the active one.
 */
private val LyricNextTextStyle = TextStyle(
    color = Color.White.copy(alpha = 0.45f),
    fontSize = 17.sp,
    lineHeight = 23.sp,
    fontWeight = FontWeight.Medium,
    textAlign = TextAlign.Start,
)

/*
 * One karaoke line rendered as a single left-aligned text block so its
 * wrapping matches the two-line fit measurement exactly. The active word is
 * bright white/bold, already-sung words a softer white and upcoming words
 * dimmer, with each word's colour animating independently. Word timing is
 * only used when the source genuinely provides it.
 */
@Composable
private fun KaraokeLine(line: LyricLine, activeWordIndex: Int) {
    val words = line.words
    if (words.isEmpty()) {
        Text(
            text = line.text,
            modifier = Modifier.fillMaxWidth(),
            style = LyricTextStyle.copy(shadow = LyricShadow),
            maxLines = 2,
            overflow = TextOverflow.Clip,
        )
        return
    }

    val colors = words.indices.map { index ->
        val target = when {
            index == activeWordIndex -> Color.White
            index < activeWordIndex -> Color.White.copy(alpha = 0.82f)
            else -> Color.White.copy(alpha = 0.42f)
        }
        animateColorAsState(
            targetValue = target,
            animationSpec = tween(durationMillis = 220),
            label = "karaokeWordColor",
        ).value
    }
    val text = buildAnnotatedString {
        words.forEachIndexed { index, word ->
            if (index > 0) append(" ")
            withStyle(
                SpanStyle(
                    color = colors[index],
                    fontWeight = if (index == activeWordIndex) {
                        FontWeight.Bold
                    } else {
                        FontWeight.SemiBold
                    },
                )
            ) {
                append(word.text)
            }
        }
    }
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = LyricTextStyle.copy(shadow = LyricShadow),
        maxLines = 2,
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun SongInfo(
    title: String,
    artist: String,
    isFollowing: Boolean,
    onFollow: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 20.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = artist,
                color = SecondaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onFollow != null) {
                FollowChip(isFollowing = isFollowing, onClick = onFollow)
            }
        }
    }
}

@Composable
private fun FollowChip(isFollowing: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = if (isFollowing) "Following" else "Follow",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/*
 * ActionRow
 *
 * Social-style actions. Like toggles the real favorite state, share fires
 * the system chooser and queue opens the real upcoming-queue sheet. Comment
 * and download have no backing in a local library, so they are only shown
 * when the host supplies a handler (they are currently never supplied).
 */
@Composable
private fun ActionRow(
    isLiked: Boolean,
    likeCount: String,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    commentCount: String,
    onComment: (() -> Unit)?,
    shareCount: String,
    onShare: () -> Unit,
    onDownload: (() -> Unit)?,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val likeTint by animateColorAsState(
        targetValue = if (isLiked) LikePink else Color.White,
        label = "likeTint",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            CountedAction(
                icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                description = if (isLiked) "Unlike" else "Like",
                count = likeCount,
                tint = likeTint,
                onClick = onToggleLike,
            )
            ActionIcon(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                description = "Add to playlist",
                onClick = onAddToPlaylist,
            )
            if (onComment != null) {
                CountedAction(
                    icon = Icons.Filled.ChatBubble,
                    description = "Comments",
                    count = commentCount,
                    onClick = onComment,
                )
            }
            CountedAction(
                icon = Icons.Filled.Share,
                description = "Share",
                count = shareCount,
                onClick = onShare,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (onDownload != null) {
                ActionIcon(
                    icon = Icons.Outlined.Download,
                    description = "Download",
                    onClick = onDownload,
                )
            }
            ActionIcon(
                icon = Icons.AutoMirrored.Outlined.QueueMusic,
                description = "Queue",
                onClick = onQueue,
            )
        }
    }
}

@Composable
private fun CountedAction(
    icon: ImageVector,
    description: String,
    count: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (count.isNotBlank()) {
            Text(
                text = count,
                color = SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        ActionIcon(icon = icon, description = description, onClick = onClick, tint = tint)
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.Black,
                modifier = Modifier.size(24.dp),
            )
        }
        SeekBar(
            progress = progress,
            onSeek = onSeek,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Thin 2dp progress bar with an 8dp thumb. Tap or drag to seek. */
@Composable
private fun SeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = (dragFraction ?: progress).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentOnSeek((offset.x / widthPx).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFraction?.let(currentOnSeek)
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                    },
                )
            }
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.3f)),
        )
        Box(
            Modifier
                .fillMaxWidth(shown)
                .height(2.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        Box(
            Modifier
                .offset {
                    val thumb = 8.dp.toPx()
                    IntOffset(
                        x = (shown * widthPx - thumb / 2f)
                            .coerceIn(0f, (widthPx - thumb).coerceAtLeast(0f))
                            .roundToInt(),
                        y = 0,
                    )
                }
                .size(8.dp)
                .background(Color.White, CircleShape),
        )
    }
}

/** Three pulsing chevrons plus a hand icon, shown until the first successful swipe. */
@Composable
private fun SwipeHint(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "swipeHint")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "phase",
    )
    val handLift by transition.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(tween(600), AnimationRepeatMode.Reverse),
        label = "handLift",
    )

    Column(
        modifier = modifier.clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-10).dp),
    ) {
        repeat(3) { index ->
            // index 0 is the top chevron; the pulse travels bottom -> top.
            val fromBottom = 2 - index
            val distance = (phase - fromBottom + 3f) % 3f
            val alpha = (1f - distance / 3f).coerceIn(0.25f, 1f)
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = alpha),
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .graphicsLayer { translationY = handLift.dp.toPx() }
                .size(56.dp)
                .background(HandFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.TouchApp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/*
 * Shares the current song as plain text through the system chooser. No
 * extra permission or dependency is required.
 */
private fun shareSong(context: Context, song: Song) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, song.title)
        putExtra(Intent.EXTRA_TEXT, "${song.title} • ${song.artist}")
    }
    context.startActivity(Intent.createChooser(intent, "Share song"))
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

private val PreviewLyrics = Lyrics(
    lines = listOf(
        LyricLine(0L, "First lyric line fades away"),
        LyricLine(4_000L, "Current lyric line goes here"),
        LyricLine(8_000L, "Next line fades in below"),
    ),
    synced = true,
)

private val PreviewState = NowPlayingState(
    sourceLabel = "Your Daily Mix",
    title = "Song Title",
    artist = "Artist Name",
    lyrics = PreviewLyrics,
    activeLyricIndex = 1,
    isPlaying = true,
    isLiked = true,
    isFollowing = false,
    likeCount = "3.9M",
    commentCount = "6K",
    shareCount = "52K",
    progress = 0.3f,
    showSwipeHint = false,
)

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun NowPlayingDesignPreview() {
    NowPlayingDesign(state = PreviewState, onEvent = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun NowPlayingDesignSwipeHintPreview() {
    NowPlayingDesign(state = PreviewState.copy(showSwipeHint = true), onEvent = {})
}