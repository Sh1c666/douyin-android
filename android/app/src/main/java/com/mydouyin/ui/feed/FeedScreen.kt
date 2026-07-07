package com.mydouyin.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mydouyin.player.PlayerPool
import com.mydouyin.ui.comments.CommentsSheetContent
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    vm: FeedViewModel = viewModel(),
    fastForward: Boolean,
    onFastForwardChange: (Boolean) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    val pool = remember { PlayerPool(context) }
    DisposableEffect(Unit) { onDispose { pool.release() } }

    // Pause everything in the background, resume the active page on return.
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

    var muted by remember { mutableStateOf(false) }
    var commentsFor by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            items.isEmpty() && loading -> CenterLoading()
            items.isEmpty() && error != null -> ErrorState(error!!) { vm.refresh() }
            items.isNotEmpty() -> {
                val pagerState = rememberPagerState(pageCount = { items.size })
                // Central playback control: only the settled page plays.
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }
                        .distinctUntilChanged()
                        .collect { page ->
                            pool.setActive(page)
                            items.getOrNull(page)?.let {
                                com.mydouyin.util.LastWatched.set(it.id, it.author.secUid)
                            }
                        }
                }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }
                        .distinctUntilChanged()
                        .collect { page ->
                            if (page >= items.size - 4) vm.loadMore()
                        }
                }
                VerticalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !fastForward,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val isActive by remember(page) {
                        derivedStateOf { pagerState.settledPage == page }
                    }
                    VideoPage(
                        aweme = items[page],
                        index = page,
                        isActive = isActive,
                        pool = pool,
                        fastForward = fastForward,
                        onFastForwardChange = onFastForwardChange,
                        onComments = { commentsFor = items[page].id },
                        onProfile = onOpenProfile
                    )
                }

                if (!fastForward) {
                    IconButton(
                        onClick = { muted = !muted; pool.setMuted(!muted) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Icon(
                            if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = "静音",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    val cid = commentsFor
    if (cid != null) {
        ModalBottomSheet(
            onDismissRequest = { commentsFor = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CommentsSheetContent(awemeId = cid)
        }
    }
}

@Composable
private fun CenterLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun ErrorState(msg: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(msg, color = Color.White)
            TextButton(onClick = onRetry) { Text("重试", color = MaterialTheme.colorScheme.primary) }
        }
    }
}
