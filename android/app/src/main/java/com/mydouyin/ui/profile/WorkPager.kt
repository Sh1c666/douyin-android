package com.mydouyin.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.mydouyin.data.model.Aweme
import com.mydouyin.player.PlayerPool
import com.mydouyin.player.PlayerSurface
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Full-screen vertical pager over an author's works — swipe up/down to play them
 * in order. Shown as an overlay from [ProfileScreen]; reuses [PlayerPool] the same
 * way the main feed does (central playback control), and asks for more pages when
 * the user nears the end.
 */
@Composable
fun WorkPager(
    works: List<Aweme>,
    startIndex: Int,
    hasMore: Boolean,
    loadingMore: Boolean,
    onRequestMore: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pool = remember { PlayerPool(context) }
    DisposableEffect(Unit) { onDispose { pool.release() } }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> pool.pauseAll()
                Lifecycle.Event.ON_START -> pool.resumeActive()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    BackHandler { onBack() }

    val start = startIndex.coerceIn(0, works.lastIndex.coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = start, pageCount = { works.size })

    // Central playback control: only the settled page plays.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> pool.setActive(page) }
    }

    // Load the next page of works when within 2 of the end.
    LaunchedEffect(pagerState, hasMore, loadingMore) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (hasMore && !loadingMore && works.isNotEmpty() && page >= works.size - 2) {
                    onRequestMore()
                }
            }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val aweme = works.getOrNull(page) ?: return@VerticalPager
            WorkVideoPage(
                aweme = aweme,
                index = page,
                isActive = pagerState.settledPage == page,
                pool = pool
            )
        }

        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
        }
    }
}

@Composable
private fun WorkVideoPage(aweme: Aweme, index: Int, isActive: Boolean, pool: PlayerPool) {
    val player = remember(index) { pool.playerFor(index) }
    LaunchedEffect(aweme.id) { pool.bind(index, aweme.video.url) }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var isReady by remember { mutableStateOf(player.playbackState == Player.STATE_READY) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                isReady = state == Player.STATE_READY
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (aweme.isImage) {
            AsyncImage(
                model = aweme.images.firstOrNull().orEmpty(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(onTap = { pool.toggle(index) })
                }
            )
            if (isActive && isReady && !isPlaying) {
                Icon(
                    Icons.Filled.PlayArrow, null,
                    Modifier.align(Alignment.Center).size(72.dp),
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            Text("@${aweme.author.nickname}", color = Color.White, fontSize = 15.sp)
            if (aweme.desc.isNotBlank()) {
                Text(
                    aweme.desc,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
