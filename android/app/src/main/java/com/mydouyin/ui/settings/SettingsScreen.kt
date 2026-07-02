package com.mydouyin.ui.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mydouyin.data.Session
import com.mydouyin.data.model.ConnResult
import com.mydouyin.data.repo.DouyinRepository
import com.mydouyin.util.Prefs
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { DouyinRepository() }

    var cookie by remember { mutableStateOf(Prefs.cookie(context)) }
    var saved by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ConnResult?>(null) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Cookie", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(
            "从浏览器（已登录抖音）F12 → Network → 任选 douyin.com 请求 → 复制 Cookie 值，整段粘进来。几天到两周需更新一次。",
            color = Color(0xFF8E8E93)
        )
        OutlinedTextField(
            value = cookie,
            onValueChange = { cookie = it; saved = false },
            modifier = Modifier.fillMaxWidth().height(160.dp),
            placeholder = { Text("粘贴 Cookie（k=v; k=v; ...）") },
            minLines = 5
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                if (text.isNotEmpty()) { cookie = text; saved = false }
            }) { Text("粘贴") }

            Button(onClick = {
                Prefs.setCookie(context, cookie)
                Session.setCookie(cookie)
                saved = true
                result = null
            }) { Text("保存") }

            Button(onClick = {
                scope.launch {
                    testing = true
                    result = null
                    try {
                        result = repo.testConnection()
                    } catch (e: Exception) {
                        result = ConnResult(ok = false, error = e.message)
                    } finally {
                        testing = false
                    }
                }
            }) { Text("测试") }
        }

        if (saved) Text("已保存", color = Color(0xFF34C759))
        if (testing) CircularProgressIndicator()

        result?.let { r ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    r.ok -> Text("✓ 签名+cookie 正常，取到 ${r.videoCount} 条视频", color = Color(0xFF34C759))
                    r.error != null -> Text("✗ 失败：${r.error}", color = Color(0xFFFF453A))
                    else -> Text("✗ 能连上但没取到视频，cookie 可能过期", color = Color(0xFFFF453A))
                }
                Text(
                    "登录态：${if (r.loggedIn) "已登录（cookie 含 sessionid）" else "未登录"}",
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "本应用直接调用抖音官方接口，签名在端上完成，不依赖任何本地后端。\n仅个人只读使用，勿传播/商用。",
            color = Color(0xFF8E8E93)
        )
    }
}
