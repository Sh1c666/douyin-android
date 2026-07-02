package com.mydouyin.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mydouyin.data.model.LiveInfo
import com.mydouyin.data.repo.DouyinRepository
import com.mydouyin.player.PlayerSurface
import kotlinx.coroutines.launch

@Composable
fun LiveScreen(initialRoomId: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { DouyinRepository() }
    var input by remember { mutableStateOf(initialRoomId ?: "") }
    var info by remember { mutableStateOf<LiveInfo?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val player = remember { com.mydouyin.player.Players.new(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun load(rid: String) {
        scope.launch {
            loading = true
            try {
                val li = repo.live(rid)
                info = li
                val url = li.hls.ifBlank { li.flv }
                if (url.isBlank()) {
                    status = li.note.ifBlank { if (!li.isLive) "主播未开播" else "未取到流地址" }
                } else {
                    player.setMediaItem(MediaItem.fromUri(url))
                    player.prepare()
                    player.playWhenReady = true
                    status = null
                }
            } catch (e: Exception) {
                status = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(initialRoomId) {
        val raw = initialRoomId ?: ""
        if (raw.isBlank()) return@LaunchedEffect
        input = raw
        val rid = repo.resolveRoomId(raw)
        if (rid != null) load(rid) else status = "无法识别房间号或链接"
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        info?.let {
            PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.TopStart).padding(start = 56.dp, top = 12.dp)) {
                Text(it.nickname, color = Color.White)
                Text(it.title, color = Color.White)
            }
        }

        Column(
            Modifier.align(Alignment.Center).padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("房间号 / 直播链接 / 分享文本") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                scope.launch {
                    status = "解析链接…"
                    val rid = repo.resolveRoomId(input)
                    if (rid == null) { status = "无法识别房间号或链接"; return@launch }
                    load(rid)
                }
            }) {
                Text("进入直播间")
            }
            status?.let { Text(it, color = Color.White) }
            if (loading) CircularProgressIndicator(color = Color.White)
        }

        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
        }
    }
}
