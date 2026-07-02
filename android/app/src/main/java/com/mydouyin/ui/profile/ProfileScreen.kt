package com.mydouyin.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mydouyin.data.model.Aweme
import com.mydouyin.data.model.UserInfo
import com.mydouyin.data.repo.DouyinRepository
import com.mydouyin.util.LastWatched
import com.mydouyin.util.fmtCount
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    secUid: String,
    onBack: () -> Unit
) {
    val repo = remember { DouyinRepository() }
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<UserInfo?>(null) }
    var works by remember { mutableStateOf<List<Aweme>>(emptyList()) }
    var cursor by remember { mutableStateOf("0") }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var openedIndex by remember { mutableStateOf<Int?>(null) }

    suspend fun loadUser() {
        try { user = repo.user(secUid) } catch (_: Exception) {}
    }
    suspend fun loadWorks() {
        if (!hasMore || loadingMore) return
        loadingMore = true
        try {
            val page = repo.works(secUid, cursor)
            works = (works + page.list).distinctBy { it.id }
            cursor = page.maxCursor
            hasMore = page.hasMore == 1
        } catch (_: Exception) {
        } finally {
            loadingMore = false
            loading = false
        }
    }

    LaunchedEffect(secUid) {
        loading = true
        loadUser()
        loadWorks()
    }

    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= works.size + 1 - 3 && hasMore && !loadingMore
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) loadWorks() }

    // "刚刚看过": the video the user was just watching on the feed, if it's this author's.
    val lastWatchedId = remember(secUid) {
        if (LastWatched.secUid == secUid) LastWatched.awemeId else ""
    }
    val lastWatchedWorkIndex = remember(works, lastWatchedId) {
        if (lastWatchedId.isEmpty()) -1 else works.indexOfFirst { it.id == lastWatchedId }
    }
    var popupDismissed by remember(secUid) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileHeader(
                    user = user,
                    onBack = onBack
                )
            }
            if (works.isEmpty() && loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            itemsIndexed(works, key = { _, aw -> aw.id }) { index, aw ->
                WorkThumb(
                    aweme = aw,
                    isLastWatched = aw.id == lastWatchedId
                ) { openedIndex = index }
            }
        }

        // Floating "刚刚看过" chip — tap to jump the grid to that work.
        if (lastWatchedWorkIndex >= 0 && !popupDismissed && openedIndex == null) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 28.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Color.White)
                    .clickable {
                        scope.launch { gridState.scrollToItem(lastWatchedWorkIndex + 1) }
                        popupDismissed = true
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text("▶ 刚刚看过", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // Full-screen sequential work player, opened by tapping a thumbnail.
    val oi = openedIndex
    if (oi != null && works.isNotEmpty()) {
        WorkPager(
            works = works,
            startIndex = oi.coerceIn(0, works.lastIndex),
            hasMore = hasMore,
            loadingMore = loadingMore,
            onRequestMore = { scope.launch { loadWorks() } },
            onBack = { openedIndex = null }
        )
    }
}

@Composable
private fun ProfileHeader(user: UserInfo?, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = user?.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(76.dp).clip(CircleShape).background(Color.DarkGray)
            )
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    user?.nickname ?: "加载中…",
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Stat("粉丝", fmtCount(user?.followerCount ?: 0))
                    Stat("关注", fmtCount(user?.followingCount ?: 0))
                    Stat("作品", fmtCount(user?.awemeCount ?: 0))
                }
            }
        }
        if (!user?.signature.isNullOrBlank()) {
            Text(
                user!!.signature,
                color = Color(0xFFCCCCCC),
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1C1C1E)).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("作品", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Color(0xFF8E8E93), fontSize = 12.sp)
    }
}

@Composable
private fun WorkThumb(aweme: Aweme, isLastWatched: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.aspectRatio(9f / 16f).padding(1.dp)
            .clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = aweme.video.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isLastWatched) {
            Text(
                "刚刚看过",
                color = Color.White,
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        Box(Modifier.align(Alignment.BottomStart).padding(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fmtCount(aweme.stats.play.ifZero { aweme.stats.digg }),
                    color = Color.White, fontSize = 11.sp
                )
            }
        }
    }
}

private fun Int.ifZero(fallback: () -> Int): Int = if (this == 0) fallback() else this
