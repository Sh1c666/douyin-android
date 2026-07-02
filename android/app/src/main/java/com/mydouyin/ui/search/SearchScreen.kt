package com.mydouyin.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mydouyin.data.model.Aweme
import com.mydouyin.data.repo.DouyinRepository
import com.mydouyin.ui.profile.WorkPager
import com.mydouyin.util.fmtCount
import kotlinx.coroutines.launch

/** 搜索页：顶部搜索框 + 三列封面网格 + 点开复用 [WorkPager] 全屏顺序播放。
 *
 *  走签名 /aweme/v1/web/general/search/single/（与 cv-cat/DouYin_Spider 一致）。该端点
 *  常被抖音 verify_check 风控返空 —— 空/风控结果会在网格区上方明文提示，便于真机诊断。 */
@Composable
fun SearchScreen() {
    val repo = remember { DouyinRepository() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Aweme>>(emptyList()) }
    var offset by remember { mutableStateOf("0") }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }   // 风控 / 空结果 / 错误
    var activeQuery by remember { mutableStateOf("") }       // 当前展示结果对应的查询词
    var searched by remember { mutableStateOf(false) }       // 是否已发起过搜索（区分初始空态）
    var openedIndex by remember { mutableStateOf<Int?>(null) }

    suspend fun doSearch(q: String, off: String, append: Boolean) {
        if (append) loadingMore = true else loading = true
        note = null
        try {
            val page = repo.search(q, off)
            results = if (append) (results + page.list).distinctBy { it.id } else page.list
            offset = page.maxCursor
            hasMore = page.hasMore == 1
            if (!append && results.isEmpty()) note = page.note.ifBlank { "无结果" }
        } catch (e: Exception) {
            note = e.message ?: "搜索失败"
            if (!append) results = emptyList()
        } finally {
            loading = false
            loadingMore = false
        }
    }

    fun submit() {
        val q = query.trim()
        if (q.isEmpty()) return
        activeQuery = q
        offset = "0"
        hasMore = false
        searched = true
        scope.launch { doSearch(q, "0", append = false) }
    }

    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= results.size - 4 && hasMore && !loadingMore && results.isNotEmpty()
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd) doSearch(activeQuery, offset, append = true)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            // 搜索栏
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索抖音视频", color = Color(0xFF8E8E93)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    )
                )
                Button(onClick = { submit() }, enabled = !loading) { Text("搜索") }
            }

            // 结果网格
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(results, key = { _, a -> a.id }) { _, aw ->
                    SearchThumb(aweme = aw) {
                        val idx = results.indexOfFirst { it.id == aw.id }
                        if (idx >= 0) openedIndex = idx
                    }
                }
                if (loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        // 覆盖层：加载 / 提示 / 初始引导（互斥）
        if (loading && results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (results.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    note ?: if (searched) "无结果" else "输入关键词搜索视频",
                    color = Color(0xFF8E8E93),
                    fontSize = 14.sp
                )
            }
        }
    }

    // 点开封面 → 全屏顺序播放，复用主页/作品同款 PlayerPool。
    val oi = openedIndex
    if (oi != null && results.isNotEmpty()) {
        WorkPager(
            works = results,
            startIndex = oi.coerceIn(0, results.lastIndex),
            hasMore = hasMore,
            loadingMore = loadingMore,
            onRequestMore = { scope.launch { doSearch(activeQuery, offset, append = true) } },
            onBack = { openedIndex = null }
        )
    }
}

@Composable
private fun SearchThumb(aweme: Aweme, onClick: () -> Unit) {
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
        Box(Modifier.align(Alignment.BottomStart).padding(6.dp)) {
            Text(fmtCount(aweme.stats.play.ifZero { aweme.stats.digg }), color = Color.White, fontSize = 11.sp)
        }
    }
}

private fun Int.ifZero(fallback: () -> Int): Int = if (this == 0) fallback() else this
