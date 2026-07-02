package com.mydouyin.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.mydouyin.data.model.Comment
import com.mydouyin.data.repo.DouyinRepository
import com.mydouyin.util.fmtCount
import kotlinx.coroutines.launch

@Composable
fun CommentsSheetContent(awemeId: String) {
    val scope = rememberCoroutineScope()
    val repo = remember { DouyinRepository() }

    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var cursor by remember { mutableStateOf("0") }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val replies = remember { mutableStateMapOf<String, List<Comment>>() }
    val loadingReplies = remember { mutableStateMapOf<String, Boolean>() }

    suspend fun loadMore() {
        if (loading && cursor != "0") return
        loading = true
        if (cursor == "0") error = null
        try {
            val page = repo.comments(awemeId, cursor)
            comments = (comments + page.list).distinctBy { it.cid }
            cursor = page.cursor
            hasMore = page.hasMore == 1
            total = page.total
        } catch (e: Exception) {
            if (comments.isEmpty()) error = e.message ?: "加载评论失败"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(awemeId) { loadMore() }

    val listState = rememberLazyListState()
    val reachedEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= comments.lastIndex && hasMore && !loading
        }
    }
    LaunchedEffect(reachedEnd) { if (reachedEnd) loadMore() }

    Column(Modifier.fillMaxWidth().height(420.dp)) {
        Text(
            if (total > 0) "$total 条评论" else "评论",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(12.dp)
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            items(comments, key = { it.cid }) { c ->
                CommentRow(
                    comment = c,
                    replies = replies[c.cid],
                    loadingReplies = loadingReplies[c.cid] == true,
                    onToggleReplies = {
                        if (replies[c.cid] != null) {
                            replies.remove(c.cid)
                        } else {
                            loadingReplies[c.cid] = true
                            scope.launch {
                                try {
                                    val r = repo.replies(awemeId, c.cid)
                                    replies[c.cid] = r.list
                                } catch (_: Exception) {
                                } finally {
                                    loadingReplies[c.cid] = false
                                }
                            }
                        }
                    }
                )
            }
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                    }
                }
            } else if (comments.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        Text(
                            error ?: "暂无评论",
                            color = if (error != null) Color(0xFFFF453A) else Color(0xFF8E8E93),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    replies: List<Comment>?,
    loadingReplies: Boolean,
    onToggleReplies: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row {
            AsyncImage(
                model = comment.user.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.DarkGray)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(comment.user.nickname, color = Color(0xFF8E8E93), fontSize = 13.sp)
                Text(
                    comment.text.ifBlank { "[表情/图片]" },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(relativeTime(comment.createTime), color = Color(0xFF8E8E93), fontSize = 12.sp)
                    if (comment.replyCommentTotal > 0) {
                        Spacer(Modifier.width(12.dp))
                        val label = if (loadingReplies) "加载中…" else "展开 ${comment.replyCommentTotal} 条回复"
                        Text(
                            label,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable(onClick = onToggleReplies)
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.FavoriteBorder, null, Modifier.size(18.dp), tint = Color(0xFF8E8E93))
                Text(fmtCount(comment.diggCount), color = Color(0xFF8E8E93), fontSize = 11.sp)
            }
        }

        if (replies != null && replies.isNotEmpty()) {
            Column(Modifier.padding(start = 44.dp, top = 6.dp)) {
                replies.forEach { r ->
                    Text(
                        "${r.user.nickname}：${r.text.ifBlank { "[表情]" }}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
        }
    }
}

private fun relativeTime(seconds: Long): String {
    if (seconds <= 0) return ""
    val diff = System.currentTimeMillis() / 1000 - seconds
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60}分钟前"
        diff < 86400 -> "${diff / 3600}小时前"
        diff < 2592000 -> "${diff / 86400}天前"
        else -> "${diff / 2592000}个月前"
    }
}
