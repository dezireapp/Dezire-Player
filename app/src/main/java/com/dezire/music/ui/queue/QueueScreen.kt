package com.dezire.music.ui.queue

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dezire.music.PlayingIndicator
import com.dezire.music.data.model.Song
import com.dezire.music.playback.PlaybackController
import com.dezire.music.ui.components.GlowConfig
import com.dezire.music.ui.components.dynamicArtworkGlow
import com.dezire.music.ui.theme.DezireAlbumGray
import com.dezire.music.ui.theme.DezireRed
import com.dezire.music.util.ArtworkPalette
import com.dezire.music.util.rememberArtworkPalette
import com.dezire.music.util.rememberDeepBlurredArtwork
import com.dezire.music.util.rememberSongArtwork
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Design tokens. Cards are deliberately TRANSLUCENT charcoal/purple so the
// current album's blurred backdrop and flowing light read through them, but
// the alpha stays high enough (0x99) that the text and artwork remain sharp
// and the surface can never be flooded by a warm/red cover. DezireRed is kept
// for genuine active states only (the now-playing card edge, swipe affordance).
// ---------------------------------------------------------------------------
private val ActivePill = Color(0xFF9E92CC)
private val ActivePillIcon = Color(0xFF1B1535)
private val PillBackground = Color.White.copy(alpha = 0.16f)
private val CircleButtonBackground = Color.White.copy(alpha = 0.22f)
private val Lavender = Color(0xFFCCC2EB)
private val HandleColor = Color(0xFFCFC4EB)

private val QueueCardShape = RoundedCornerShape(16.dp)

/* Slightly more solid, purple-shifted surface for the lifted drag copy. */
private val QueueDragSurface = Color(0xE61E1728)

/* Immersive backdrop base and the deep tone warm artwork is pulled toward. */
private val QueueBaseDark = Color(0xFF0C0912)
private val QueueDeepTone = Color(0xFF171230)

private val PlaceholderPalette = listOf(
    Color(0xFF8A4E2E) to Color(0xFFD98C26),
    Color(0xFFC73349) to Color(0xFFF2D9BF),
    Color(0xFF338C47) to Color(0xFFD9E680),
    Color(0xFF5980CC) to Color(0xFFE68C99),
    Color(0xFFEB8CB3) to Color(0xFF804D99),
    Color(0xFF5933B8) to Color(0xFFEB4D99),
    Color(0xFFD9A68C) to Color(0xFF734D4D),
    Color(0xFFF29E26) to Color(0xFFCC334D),
    Color(0xFF6699B3) to Color(0xFFD9BFB3),
)

// Gesture / visual tunables (dp; converted to pixels once per composition).
private val SwipeRemoveThreshold = 96.dp
private val DragEdgeZone = 96.dp
private val DragScrollStep = 28.dp
private val GhostElevation = 24.dp
private val DefaultRowHeight = 64.dp

/*
 * Pull a colour toward a deep, desaturated indigo so the backdrop and flowing
 * light stay artwork-derived without letting a strongly red/warm cover flood
 * the whole queue. The hue survives; saturation and brightness are tamed.
 */
private fun toneForQueue(color: Color): Color {
    val luma = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    val desaturated = Color(
        red = luma + (color.red - luma) * 0.6f,
        green = luma + (color.green - luma) * 0.6f,
        blue = luma + (color.blue - luma) * 0.6f,
    )
    return lerp(desaturated, QueueDeepTone, 0.45f)
}

private fun ArtworkPalette.tonedForQueue(): ArtworkPalette = ArtworkPalette(
    dominant = toneForQueue(dominant),
    accent = toneForQueue(accent),
    dim = lerp(toneForQueue(dim), QueueBaseDark, 0.4f),
)

/*
 * Top of a list slot relative to the list viewport, resolved from the live
 * layout (so it already accounts for auto-scroll). Slots outside the visible
 * window are extrapolated from the nearest visible slot using the uniform row
 * height.
 */
private fun slotTop(info: LazyListLayoutInfo, index: Int, rowHeightPx: Float): Float {
    for (item in info.visibleItemsInfo) {
        if (item.index == index) return (info.viewportStartOffset + item.offset).toFloat()
    }
    val anchor = info.visibleItemsInfo.minByOrNull { abs(it.index - index) }
        ?: return info.viewportStartOffset + index * rowHeightPx
    return (info.viewportStartOffset + anchor.offset) +
        (index - anchor.index) * rowHeightPx
}

/*
 * Which list slot the floating row's centre currently points at. Layout
 * information is read fresh on every call, so the answer is correct after
 * auto-scrolls. Returns -1 when the list has no measured slots yet.
 */
private fun slotTargetIndex(
    info: LazyListLayoutInfo,
    centerY: Float,
    rowHeightPx: Float,
): Int {
    if (info.visibleItemsInfo.isEmpty()) return -1
    for (item in info.visibleItemsInfo.sortedBy { it.index }) {
        val top = (info.viewportStartOffset + item.offset).toFloat()
        if (centerY < top + rowHeightPx) return item.index
    }
    return info.visibleItemsInfo.maxOf { it.index }
}

/*
 * Live drag gesture state. Everything the reorder needs lives here, so the
 * whole state is cleared in one place on drop/cancel and can be reconciled
 * against the real queue at any time.
 *
 *  - [queueIndex]        the real queue index of the entry being dragged
 *                        (stable identity, so duplicates stay distinct)
 *  - [startVisibleIndex] its position in the visible up-next list at start
 *  - [originTopPx]       the dragged row's top, relative to the list viewport,
 *                        at gesture start
 *  - [accumY]            total finger movement since the gesture started
 *  - [rowHeightPx]       the dragged row's measured height
 *  - [order]             the visual order the list shows while dragging
 *  - [entry]             the exact QueueItem being dragged
 *  - [upNextAtStart]     the visible tail captured at gesture start; if the
 *                        real queue changed underneath (removal, clear, track
 *                        change) the drag is cancelled rather than committed
 *                        against stale indices.
 */
private data class DragState(
    val queueIndex: Int,
    val startVisibleIndex: Int,
    val originTopPx: Float,
    val accumY: Float,
    val rowHeightPx: Float,
    val order: List<QueueItem>,
    val entry: QueueItem,
    val upNextAtStart: List<QueueItem>,
)

/*
 * QueueScreen
 *
 * The Play Queue. It renders the real playback state from [PlaybackController]
 * and forwards every intent back to it - it never keeps a queue of its own.
 *
 * The current track is described once in the header; the "Continue Playing"
 * list renders the real playback tail exactly once. Tapping an entry plays its
 * real queue index; swiping removes it; dragging the three-line handle
 * reorders the tail (only when the visible order is the real linear order), and
 * the settled order is committed to ExoPlayer exactly once on drop.
 */
@Composable
fun QueueScreen(
    playbackController: PlaybackController,
    favoriteIds: Set<Long>,
    onToggleFavorite: (Song) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by playbackController.state.collectAsStateWithLifecycle()
    val state = QueueMapping.toState(uiState, favoriteIds)

    val onEvent: (QueueEvent) -> Unit = { event ->
        when (event) {
            QueueEvent.Back -> onBack()
            QueueEvent.ToggleFavorite -> state.nowPlaying?.song?.let(onToggleFavorite)
            QueueEvent.ToggleShuffle -> playbackController.toggleShuffle()
            QueueEvent.CycleRepeat -> playbackController.cycleRepeatMode()
            QueueEvent.ClearQueue ->
                playbackController.clearQueue(keepCurrent = uiState.current != null)

            is QueueEvent.PlayItem -> playbackController.playQueueItem(event.queueIndex)
            is QueueEvent.RemoveItem -> playbackController.removeFromQueue(event.queueIndex)

            is QueueEvent.MoveItem -> QueueMapping
                .reorderMove(state.currentIndex, event.fromIndex, event.toIndex)
                ?.let { (from, to) -> playbackController.moveQueueItem(from, to) }
        }
    }

    QueueContent(
        state = state,
        isPlaying = uiState.isPlaying,
        onEvent = onEvent,
        modifier = modifier,
    )
}

@Composable
private fun QueueContent(
    state: QueueState,
    isPlaying: Boolean,
    onEvent: (QueueEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSong = state.nowPlaying?.song
    val currentArtwork = rememberSongArtwork(currentSong)
    val currentBlurredArtwork = rememberDeepBlurredArtwork(currentArtwork)
    val palette = rememberArtworkPalette(currentSong)
    val queuePalette = palette?.tonedForQueue()

    var showClearConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        // Immersive album-art backdrop: full-screen enlarged blurred artwork,
        // adaptive artwork colours and a darkening scrim. It sits behind the
        // header, the list and the system-bar regions.
        QueueImmersiveBackground(
            sharpArtwork = currentArtwork,
            blurredArtwork = currentBlurredArtwork,
            palette = palette,
        )

        // Slow, artwork-derived flowing light over the blurred backdrop.
        // Toned down so warm covers cannot tint the whole queue red, but
        // still visible through the translucent cards.
        Box(
            Modifier
                .fillMaxSize()
                .dynamicArtworkGlow(
                    queuePalette,
                    GlowConfig(intensity = 0.55f, driftMs = 18_000, breatheMs = 9_000),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            DismissHandle(onBack = { onEvent(QueueEvent.Back) })

            Spacer(Modifier.height(4.dp))

            state.nowPlaying?.let { item ->
                NowPlayingHeader(
                    item = item,
                    artwork = currentArtwork,
                    isPlaying = isPlaying,
                    isFavorite = state.isFavorite,
                    onToggleFavorite = { onEvent(QueueEvent.ToggleFavorite) },
                    onMore = { showClearConfirm = true },
                )
            } ?: Text(
                text = "Nothing is playing",
                color = Lavender.copy(alpha = 0.8f),
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            Spacer(Modifier.height(16.dp))
            ModeRow(state = state, onEvent = onEvent)

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Continue Playing",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(12.dp))

            if (state.upNext.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Nothing is up next",
                        color = DezireAlbumGray,
                        fontSize = 15.sp,
                    )
                }
            } else {
                UpNextList(
                    upNext = state.upNext,
                    canReorder = state.canReorder,
                    onEvent = onEvent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear queue") },
            text = { Text("This removes every other track from the queue.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onEvent(QueueEvent.ClearQueue)
                    },
                ) {
                    Text("Clear", color = DezireRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingHeader(
    item: QueueItem,
    artwork: ImageBitmap?,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Card-free header: no container, border or shadow. The current
            // song sits directly on the immersive backdrop (30dp horizontal +
            // 10dp vertical matches the previous card's content inset, so the
            // layout, spacing and alignment are unchanged).
            .padding(horizontal = 30.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Artwork(item = item, artwork = artwork, size = 56.dp)

        Column(
            modifier = Modifier
                .weight(1f)
                .horizontalFade(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PlayingIndicator(
                    isPlaying = isPlaying,
                    barWidth = 2.5.dp,
                    barHeight = 14.dp,
                    barSpacing = 2.dp,
                )
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(iterations = Int.MAX_VALUE),
                )
            }
            Text(
                text = item.artist,
                color = Lavender.copy(alpha = 0.8f),
                fontSize = 16.sp,
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            )
        }

        CircleIconButton(
            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            description = if (isFavorite) "Remove from favorites" else "Add to favorites",
            onClick = onToggleFavorite,
        )
        CircleIconButton(
            icon = Icons.Filled.MoreVert,
            description = "More options",
            onClick = onMore,
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(CircleButtonBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ModeRow(state: QueueState, onEvent: (QueueEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModePill(
            icon = Icons.Filled.Shuffle,
            description = "Shuffle",
            active = state.shuffleOn,
            onClick = { onEvent(QueueEvent.ToggleShuffle) },
            modifier = Modifier.weight(1f),
        )
        ModePill(
            icon = if (state.repeatMode == QueueRepeatMode.One) {
                Icons.Filled.RepeatOne
            } else {
                Icons.Filled.Repeat
            },
            description = "Repeat",
            active = state.repeatMode != QueueRepeatMode.Off,
            onClick = { onEvent(QueueEvent.CycleRepeat) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModePill(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(if (active) ActivePill else PillBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) ActivePillIcon else Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

/*
 * UpNextList
 *
 * The scrollable tail list, its drag-and-drop gestures and the floating drag
 * copy, in one composable so they share the same coordinate space and
 * lifecycle.
 *
 * The reorder gesture is attached to each row's three-line handle. A handle is
 * a child of the LazyColumn, so it also beats the LazyColumn's own scrollable
 * for vertical drags (child-before-parent dispatch) - dragging the handle never
 * scrolls the list, and scrolling anywhere else never starts a drag.
 *
 * The dragged row is NOT swapped for a placeholder: losing the row would tear
 * down the very input node that owns the in-flight gesture, which is what left
 * a ghost glued to the screen. Instead the row stays mounted and is drawn fully
 * transparent while it relocates through the reordered list; the gesture node
 * survives, so on drag end/cancel the state always clears.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpNextList(
    upNext: List<QueueItem>,
    canReorder: Boolean,
    onEvent: (QueueEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var dragState by remember { mutableStateOf<DragState?>(null) }

    val density = LocalDensity.current
    val dragEdgeZonePx = with(density) { DragEdgeZone.toPx() }
    val dragScrollStepPx = with(density) { DragScrollStep.toPx() }
    val ghostElevationPx = with(density) { GhostElevation.toPx() }
    val defaultRowHeightPx = with(density) { DefaultRowHeight.toPx() }

    fun onDragStart(queueIndex: Int) {
        if (dragState != null || !canReorder) return
        val start = upNext.indexOfFirst { it.queueIndex == queueIndex }
        if (start < 0) return
        val measured = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == start }?.size?.toFloat()
        dragState = DragState(
            queueIndex = queueIndex,
            startVisibleIndex = start,
            originTopPx = slotTop(listState.layoutInfo, start, defaultRowHeightPx),
            accumY = 0f,
            rowHeightPx = measured ?: defaultRowHeightPx,
            order = upNext,
            entry = upNext[start],
            upNextAtStart = upNext,
        )
    }

    fun onDragDelta(deltaY: Float) {
        val current = dragState ?: return
        val accum = current.accumY + deltaY

        val position = current.order.indexOfFirst { it.queueIndex == current.queueIndex }
        if (position < 0) {
            dragState = null
            return
        }

        val centerY = current.originTopPx + accum + current.rowHeightPx / 2f
        val target = slotTargetIndex(listState.layoutInfo, centerY, current.rowHeightPx)
        val newOrder = if (target in 0..current.order.lastIndex && target != position) {
            current.order.toMutableList().apply {
                removeAt(position)
                add(target, current.entry)
            }
        } else {
            current.order
        }

        dragState = current.copy(order = newOrder, accumY = accum)
    }

    fun endDrag() {
        val current = dragState ?: return
        dragState = null
        // Do not commit against a queue that changed underneath the drag
        // (content is compared, so a benign recomposition of the same tail
        // does not cancel a legitimate reorder).
        if (current.upNextAtStart != upNext) return
        val to = current.order.indexOfFirst { it.queueIndex == current.queueIndex }
        if (to < 0 || to == current.startVisibleIndex) return
        onEvent(QueueEvent.MoveItem(current.startVisibleIndex, to))
    }

    // Safety net: if the screen leaves composition mid-drag, drop the drag
    // state so no floating ghost or auto-scroll can survive it.
    DisposableEffect(Unit) {
        onDispose { dragState = null }
    }

    // While a drag is live, keep scrolling the list when the floating row is
    // held near the top or bottom edge. The effect is keyed on the dragged
    // entry so it is torn down (and the loop cannot keep running) the moment
    // the drag ends or the screen leaves composition.
    LaunchedEffect(dragState?.queueIndex) {
        if (dragState == null) return@LaunchedEffect
        while (true) {
            delay(16L)
            val ds = dragState ?: break
            val position = ds.order.indexOfFirst { it.queueIndex == ds.queueIndex }
            if (position < 0) break
            val top = slotTop(listState.layoutInfo, position, ds.rowHeightPx)
            val viewportHeight =
                listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
            when {
                top < dragEdgeZonePx && listState.canScrollBackward ->
                    listState.scrollBy(-(dragEdgeZonePx - top).coerceAtMost(dragScrollStepPx))

                top + ds.rowHeightPx > viewportHeight - dragEdgeZonePx &&
                    listState.canScrollForward ->
                    listState.scrollBy(
                        ((top + ds.rowHeightPx) - (viewportHeight - dragEdgeZonePx))
                            .coerceAtMost(dragScrollStepPx),
                    )
            }
        }
    }

    val bottomInset =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = bottomInset + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(
                items = dragState?.order ?: upNext,
                key = { _, item -> "qe-${item.queueIndex}" },
            ) { _, item ->
                val dragging = dragState?.queueIndex == item.queueIndex
                QueueRow(
                    item = item,
                    isDraggable = canReorder,
                    interactive = true,
                    ghostRow = false,
                    onClick = { onEvent(QueueEvent.PlayItem(item.queueIndex)) },
                    onRemove = { onEvent(QueueEvent.RemoveItem(item.queueIndex)) },
                    onDragStart = { onDragStart(item.queueIndex) },
                    onDragDelta = { delta -> onDragDelta(delta) },
                    onDragEnd = { endDrag() },
                    onDragCancel = { dragState = null },
                    // Keep the dragged row mounted (so its handle gesture
                    // survives) but invisible; the floating copy is what the
                    // user sees while it moves through the list.
                    modifier = Modifier
                        .then(if (dragging) Modifier.alpha(0f) else Modifier)
                        .animateItemPlacement(),
                )
            }
        }

        // Floating copy of the dragged row, drawn above the whole list. It
        // follows the finger; the shuffled rows part around the placeholder
        // below it.
        dragState?.let { ds ->
            QueueRow(
                item = ds.entry,
                isDraggable = false,
                interactive = false,
                ghostRow = true,
                onClick = {},
                onRemove = {},
                onDragStart = {},
                onDragDelta = {},
                onDragEnd = {},
                onDragCancel = {},
                modifier = Modifier
                    .offset { IntOffset(0, (ds.originTopPx + ds.accumY).roundToInt()) }
                    .graphicsLayer {
                        scaleX = 1.03f
                        scaleY = 1.03f
                        shadowElevation = ghostElevationPx
                        shape = QueueCardShape
                        clip = false
                    }
                    .zIndex(10f)
                    // Match the LazyColumn's horizontal contentPadding so the
                    // floating copy lines up exactly with the real rows.
                    .padding(horizontal = 20.dp),
            )
        }
    }
}

/*
 * Top edge of the queue: back chevron plus a grab handle. A vertical drag down
 * here accumulates distance and dismisses the screen once the finger has
 * clearly travelled downward, without stealing taps.
 */
@Composable
private fun DismissHandle(onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(onBack) {
                var accumulated = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 0) {
                            accumulated += dragAmount
                        } else {
                            accumulated = 0f
                        }
                    },
                    onDragEnd = {
                        if (accumulated > 140f) onBack()
                    },
                    onDragCancel = { accumulated = 0f },
                )
            }
            .padding(horizontal = 8.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to player",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f)),
        )
    }
}

/*
 * One queue row. The title/artwork area plays that queue position on tap;
 * swiping horizontally in either direction removes it (a red surface with a
 * delete glyph is revealed and the row animates off before the queue is
 * updated); the three-line handle on the right starts the drag-and-drop
 * reorder.
 *
 * Every row is the opaque charcoal/purple card, so the artwork glow behind the
 * list can never tint it. With [interactive] false the row is purely
 * presentational, used for the elevated floating copy of an in-progress drag.
 */
@Composable
private fun QueueRow(
    item: QueueItem,
    isDraggable: Boolean,
    interactive: Boolean,
    ghostRow: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnPlay by rememberUpdatedState(onClick)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragDelta by rememberUpdatedState(onDragDelta)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onDragCancel)

    val artwork = rememberSongArtwork(item.song)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
    ) {
        val surface: @Composable () -> Unit = {
            QueueRowSurface(
                item = item,
                artwork = artwork,
                isDraggable = isDraggable && interactive,
                interactive = interactive,
                ghostRow = ghostRow,
                onPlay = { latestOnPlay() },
                onDragStart = { latestOnDragStart() },
                onDragDelta = latestOnDragDelta,
                onDragEnd = { latestOnDragEnd() },
                onDragCancel = { latestOnDragCancel() },
            )
        }

        if (interactive) {
            SwipeDismiss(
                onRemove = onRemove,
                key = item.queueIndex.toLong(),
                modifier = Modifier.fillMaxWidth(),
                content = { surface() },
            )
        } else {
            surface()
        }
    }
}

@Composable
private fun QueueRowSurface(
    item: QueueItem,
    artwork: ImageBitmap?,
    isDraggable: Boolean,
    interactive: Boolean,
    ghostRow: Boolean,
    onPlay: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Normal rows are card-free: no background, border or shadow, so
            // they sit directly on the immersive artwork backdrop. Only the
            // floating drag copy gets a subtle temporary highlight.
            .then(
                if (ghostRow) {
                    Modifier
                        .clip(QueueCardShape)
                        .background(QueueDragSurface)
                        .border(1.dp, Color.White.copy(alpha = 0.14f), QueueCardShape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .then(if (interactive) Modifier.clickable(onClick = onPlay) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(item = item, artwork = artwork, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${item.artist} • ${item.song.album}",
                    color = DezireAlbumGray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (interactive && isDraggable) {
            DragHandle(
                onDragStart = onDragStart,
                onDragDelta = onDragDelta,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
        } else if (ghostRow) {
            DragHandleIcon(
                modifier = Modifier
                    .size(width = 40.dp, height = 44.dp)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

/*
 * The three-line drag handle. A press-and-drag here (no long press) drives the
 * reorder; the handler consumes its movement so the enclosing LazyColumn never
 * scrolls while a row is being dragged. Because the handle is a child of the
 * list, it claims vertical drags before the list's own scroll gesture.
 */
@Composable
private fun DragHandle(
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val latestStart by rememberUpdatedState(onDragStart)
    val latestDelta by rememberUpdatedState(onDragDelta)
    val latestEnd by rememberUpdatedState(onDragEnd)
    val latestCancel by rememberUpdatedState(onDragCancel)

    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { latestStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        latestDelta(amount.y)
                    },
                    onDragEnd = { latestEnd() },
                    onDragCancel = { latestCancel() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        DragHandleIcon()
    }
}

@Composable
private fun DragHandleIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val stroke = 1.6.dp.toPx()
        val x0 = size.width * 5f / 24f
        val x1 = size.width * 19f / 24f

        listOf(7f, 10.3f, 13.6f, 17f).forEach { y ->
            val py = size.height * y / 24f
            drawLine(
                color = HandleColor,
                start = Offset(x0, py),
                end = Offset(x1, py),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/*
 * Wraps a row in the swipe-to-remove behaviour: a red surface with a delete
 * glyph is revealed behind the row (anchored to the side being swiped), the
 * content follows the finger, and past the threshold it keeps sliding and then
 * reports [onRemove]. The red is only painted while a swipe is actually in
 * progress, so at rest it can never tint the opaque card above it.
 */
@Composable
private fun SwipeDismiss(
    onRemove: () -> Unit,
    key: Long = 0L,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var rowWidthPx by remember(key) { mutableIntStateOf(0) }
    var revealSide by remember(key) { mutableIntStateOf(0) }
    var swipeDistance by remember(key) { mutableFloatStateOf(0f) }
    val latestRemove by rememberUpdatedState(onRemove)
    val thresholdPx = with(density) { SwipeRemoveThreshold.toPx() }

    Box(
        modifier = modifier
            .clip(QueueCardShape)
            .onSizeChanged {
                if (it.width > 0) rowWidthPx = it.width
            },
    ) {
        val swiping = abs(swipeDistance) > 0f || revealSide != 0
        Box(
            Modifier
                .matchParentSize()
                .background(if (swiping) DezireRed else Color.Transparent),
            contentAlignment = if (revealSide >= 0) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            if (swiping) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove from queue",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(22.dp)
                        .padding(horizontal = 16.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(swipeDistance.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { revealSide = 0 },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            if (amount < 0) revealSide = -1 else if (amount > 0) revealSide = 1
                            swipeDistance = (swipeDistance + amount)
                                .coerceIn(-rowWidthPx.toFloat(), rowWidthPx.toFloat())
                        },
                        onDragEnd = {
                            val moving = swipeDistance
                            if (abs(moving) > thresholdPx) {
                                scope.launch { latestRemove() }
                            }
                            swipeDistance = 0f
                            revealSide = 0
                        },
                        onDragCancel = {
                            swipeDistance = 0f
                            revealSide = 0
                        },
                    )
                },
            content = content,
        )
    }
}

/*
 * Local album artwork for a row (or the header), loaded through the app's
 * existing artwork loader and cached by song id. Falls back to a deterministic
 * two-colour gradient tile keyed by the queue index so every row still has a
 * distinct, stable identity without any network access.
 */
@Composable
private fun Artwork(item: QueueItem, artwork: ImageBitmap?, size: Dp) {
    val (c1, c2) = PlaceholderPalette[Math.floorMod(item.queueIndex, PlaceholderPalette.size)]

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(c1, c2))),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = item.title.take(1).uppercase(),
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.37f).sp,
            )
        }
    }
}

/*
 * Full-screen immersive queue backdrop.
 *
 * Layers, back to front:
 *  1. A near-black charcoal base.
 *  2. The current album art, enlarged and cropped to cover the whole screen,
 *     then deeply blurred. On Android 12+ (API 31) the sharp bitmap is blurred
 *     on the GPU with a strong radius; below that (where [Modifier.blur] is a
 *     no-op) a deeply pre-blurred bitmap is upscaled with bilinear filtering.
 *     Both the pre-blur and the palette are computed once per artwork and
 *     cached, so nothing heavy runs per animation frame or recomposition.
 *  3. Adaptive washes derived from the artwork palette, animated so a track
 *     change fades smoothly instead of flashing.
 *  4. A darkening scrim so text and the card-free rows stay readable.
 *
 * The flowing light is a separate layer ([dynamicArtworkGlow]) drawn on top and
 * blended into the blurred backdrop.
 */
private val QueueBlurRadius = 72.dp

@Composable
private fun QueueImmersiveBackground(
    sharpArtwork: ImageBitmap?,
    blurredArtwork: ImageBitmap?,
    palette: ArtworkPalette?,
) {
    val source = palette?.tonedForQueue() ?: ArtworkPalette.fallback
    val dominant by animateColorAsState(
        targetValue = source.dominant,
        animationSpec = tween(900),
        label = "queueDominant",
    )
    val accent by animateColorAsState(
        targetValue = source.accent,
        animationSpec = tween(900),
        label = "queueAccent",
    )
    val dim by animateColorAsState(
        targetValue = source.dim,
        animationSpec = tween(900),
        label = "queueDim",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(QueueBaseDark),
    ) {
        // The artwork can be blurred with a GPU RenderEffect only on API 31+;
        // below that the pre-blurred bitmap is the fallback.
        val useGpuBlur =
            sharpArtwork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        when {
            useGpuBlur -> Image(
                bitmap = sharpArtwork!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // Overscale before blurring so no sharp edge strip survives
                    // at the screen borders.
                    .graphicsLayer {
                        scaleX = 1.4f
                        scaleY = 1.4f
                        alpha = 0.62f
                    }
                    .blur(
                        radius = QueueBlurRadius,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
                    ),
            )

            blurredArtwork != null -> Image(
                bitmap = blurredArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.4f
                        scaleY = 1.4f
                        alpha = 0.62f
                    },
            )

            else -> Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF241A44),
                                Color(0xFF140F22),
                                Color(0xFF2A1330),
                            ),
                        ),
                    ),
            )
        }

        // Adaptive artwork washes.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to dim.copy(alpha = 0.85f),
                            0.45f to Color.Transparent,
                            1f to dim.copy(alpha = 0.75f),
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(dominant.copy(alpha = 0.50f), Color.Transparent),
                            center = Offset(size.width * 0.15f, size.height * 0.08f),
                            radius = size.width * 0.95f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.36f), Color.Transparent),
                            center = Offset(size.width * 0.92f, size.height * 0.86f),
                            radius = size.width * 1.05f,
                        ),
                    )
                },
        )

        // Darkening scrim: keeps the white titles and cards readable no matter
        // how bright the source artwork is.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.42f),
                        0.5f to Color.Black.copy(alpha = 0.22f),
                        1f to Color.Black.copy(alpha = 0.62f),
                    ),
                ),
        )
    }
}

/*
 * Horizontal fade used by the header title/artist marquee so text fades out at
 * the edges instead of clipping.
 */
private fun Modifier.horizontalFade(edge: Dp = 20.dp): Modifier = this
    .graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()

        val e = (edge.toPx() / size.width).coerceIn(0f, 0.5f)

        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                e to Color.Black,
                1f - e to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
