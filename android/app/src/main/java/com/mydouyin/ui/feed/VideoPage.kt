package com.mydouyin.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.mydouyin.data.model.Aweme
import com.mydouyin.player.PlayerPool
import com.mydouyin.player.PlayerSurface
import com.mydouyin.util.fmtCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun VideoPage(
    aweme: Aweme,
    index: Int,
    isActive: Boolean,
    pool: PlayerPool,
    fastForward: Boolean,
    onFastForwardChange: (Boolean) -> Unit,
    onComments: () -> Unit,
    onProfile: (String) -> Unit
) {
    val player = remember(index) { pool.playerFor(index) }
    LaunchedEffect(aweme.id) { pool.bind(index, aweme.video.url) }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var isReady by remember { mutableStateOf(player.playbackState == Player.STATE_READY) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) { isReady = state == Player.STATE_READY }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Progress bar state (polled while the page is visible).
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    LaunchedEffect(player, isActive) {
        while (isActive) {
            val d = player.duration
            if (d > 0) durationMs = d
            if (!seeking) positionMs = player.currentPosition.coerceAtLeast(0)
            delay(250)
        }
    }

    val swipePx = with(LocalDensity.current) { 120.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (aweme.isImage) {
            ImageCarousel(aweme)
        } else {
            // No cover placeholder: ExoPlayer paints the surface itself once it has a frame.
            PlayerSurface(player = player, modifier = Modifier.fillMaxSize())

            // Unified gesture scrim above the player:
            //   tap → pause/resume · left-swipe → author profile ·
            //   long-press a side third (1s) → 2x while held · vertical → pager scroll.
            //
            // Uses awaitFirstDown for robust down-detection and does NOT consume events, so
            // the parent VerticalPager keeps owning vertical drags. The 2x timer is cancelled
            // the instant the finger moves (a swipe never arms or fires 2x), and 2x state is
            // ALWAYS reset in a finally — even if the gesture is interrupted, the pager takes
            // over, or the page is disposed mid-press. This is what stops the "stuck between
            // two videos, frozen, can't tap" failure mode.
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    val w = size.width.toFloat()
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startX = down.position.x
                            val startY = down.position.y
                            val inSide = startX < w / 3f || startX > w * 2f / 3f
                            var twoX = false
                            var decided = false  // classified as drag / long-press → not a tap
                            var horiz = false

                            // Arm the 2x timer only for a still press in the side zone.
                            val longJob = if (inSide) scope.launch {
                                delay(1000)
                                // Finger held ≥1s: this is a long-press, never a tap.
                                decided = true
                                // Only fast-forward when actually playing (no point while paused).
                                if (isPlaying) {
                                    twoX = true
                                    onFastForwardChange(true)
                                    player.playbackParameters = PlaybackParameters(2f)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            } else null

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val c = event.changes.firstOrNull() ?: continue
                                    val dx = c.position.x - startX
                                    val dy = c.position.y - startY
                                    val moved = abs(dx) > slop || abs(dy) > slop
                                    // Any real movement ends the "maybe tap / maybe 2x" ambiguity.
                                    if (!decided && moved) {
                                        longJob?.cancel()
                                        decided = true
                                        horiz = abs(dx) > abs(dy)
                                    }
                                    // If 2x was on and the finger then moves (a swipe starts),
                                    // drop 2x and hand the drag to the pager.
                                    if (twoX && moved) {
                                        twoX = false
                                        onFastForwardChange(false)
                                        player.playbackParameters = PlaybackParameters(1f)
                                    }
                                    if (!c.pressed) {
                                        when {
                                            !decided -> pool.toggle(index)                          // clean tap
                                            horiz && dx < -swipePx -> onProfile(aweme.author.secUid) // left swipe
                                            // else: long-press or vertical drag — not a tap
                                        }
                                        break
                                    }
                                }
                            } finally {
                                longJob?.cancel()
                                // Safety net: never leave 2x on if the gesture was interrupted/cancelled.
                                if (twoX) {
                                    onFastForwardChange(false)
                                    player.playbackParameters = PlaybackParameters(1f)
                                }
                            }
                        }
                    }
                }
            )

            // Centre play icon when paused-and-ready (not while buffering, not while 2x).
            if (isActive && isReady && !isPlaying && !fastForward) {
                Icon(
                    Icons.Filled.PlayArrow, null,
                    Modifier.align(Alignment.Center).size(72.dp),
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }

            // Thin seek bar at the bottom edge (top of the nav bar).
            if (!fastForward) {
                VideoProgressBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onStartSeek = { seeking = true },
                    onSeek = { ms -> player.seekTo(ms); positionMs = ms },
                    onEndSeek = { seeking = false },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // During long-press 2x everything but the video is hidden.
        if (!fastForward) {
            RightRail(aweme, onComments = onComments, onProfile = { onProfile(aweme.author.secUid) })
            BottomInfo(aweme, onProfile = { onProfile(aweme.author.secUid) })
        }
    }
}

/** Thin draggable progress line: tap to jump, drag to scrub. */
@Composable
private fun BoxScope.VideoProgressBar(
    positionMs: Long,
    durationMs: Long,
    onStartSeek: () -> Unit,
    onSeek: (Long) -> Unit,
    onEndSeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // Generous touch target; the visible line is only 2dp at its bottom edge.
    Box(
        modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent().changes.firstOrNull { it.changedToDown() }
                            ?: continue
                        onStartSeek()
                        val seekToChange = { x: Float ->
                            val frac = (x / size.width).coerceIn(0f, 1f)
                            onSeek((frac * durationMs).toLong())
                        }
                        seekToChange(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull() ?: continue
                            seekToChange(c.position.x)
                            if (!c.pressed) { onEndSeek(); break }
                        }
                    }
                }
            }
    ) {
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp)
                .background(Color.White.copy(alpha = 0.3f))
        )
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth(fraction).height(2.dp)
                .background(Color.White)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageCarousel(aweme: Aweme) {
    if (aweme.images.isEmpty()) return
    val state = rememberPagerState { aweme.images.size }
    HorizontalPager(state = state) { page ->
        AsyncImage(
            model = aweme.images[page],
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().background(Color.Black)
        )
    }
}

@Composable
private fun BoxScope.RightRail(aweme: Aweme, onComments: () -> Unit, onProfile: () -> Unit) {
    Column(
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier.clickable { onProfile() }
        ) {
            AsyncImage(
                model = aweme.author.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
            )
            Box(
                Modifier.align(Alignment.BottomCenter).size(20.dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Person, null, Modifier.size(14.dp), tint = Color.White) }
        }
        RailIcon(Icons.Filled.Favorite, fmtCount(aweme.stats.digg))
        RailIcon(Icons.Filled.Comment, fmtCount(aweme.stats.comment), onClick = onComments)
        RailIcon(Icons.Filled.Share, fmtCount(aweme.stats.share))
    }
}

@Composable
private fun RailIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .then(if (onClick != null) Modifier.background(Color.Black.copy(alpha = 0.25f)) else Modifier)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(30.dp), tint = Color.White)
        }
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun BoxScope.BottomInfo(aweme: Aweme, onProfile: () -> Unit) {
    Column(
        Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .padding(start = 12.dp, end = 72.dp, bottom = 20.dp)
    ) {
        Text(
            "@${aweme.author.nickname}",
            color = Color.White,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (aweme.desc.isNotBlank()) {
            Text(
                aweme.desc,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Row(
            Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.MusicNote, null, Modifier.size(14.dp), tint = Color.White)
            Spacer(Modifier.width(4.dp))
            Text(
                aweme.music.title.ifBlank { "原声" },
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
