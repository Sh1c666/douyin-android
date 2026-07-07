package com.mydouyin.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mydouyin.data.model.Aweme
import com.mydouyin.player.PlayerPool
import com.mydouyin.ui.comments.CommentsSheetContent
import com.mydouyin.ui.feed.VideoPage
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Full-screen vertical pager over an author's works — swipe up/down to play them
 * in order. Reuses [PlayerPool] and [VideoPage] (same UI as the main feed).
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> pool.setActive(page) }
    }

    LaunchedEffect(pagerState, hasMore, loadingMore) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (hasMore && !loadingMore && works.isNotEmpty() && page >= works.size - 2) {
                    onRequestMore()
                }
            }
    }

    var fastForward by remember { mutableStateOf(false) }
    var commentsFor by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = !fastForward,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val aweme = works.getOrNull(page) ?: return@VerticalPager
            VideoPage(
                aweme = aweme,
                index = page,
                isActive = pagerState.settledPage == page,
                pool = pool,
                fastForward = fastForward,
                onFastForwardChange = { fastForward = it },
                onComments = { commentsFor = aweme.id },
                onProfile = { onBack() }
            )
        }

        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
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
