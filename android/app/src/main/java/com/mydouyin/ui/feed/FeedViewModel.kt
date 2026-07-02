package com.mydouyin.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydouyin.data.model.Aweme
import com.mydouyin.data.repo.DouyinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedViewModel : ViewModel() {
    private val repo = DouyinRepository()

    private val _items = MutableStateFlow<List<Aweme>>(emptyList())
    val items: StateFlow<List<Aweme>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val seen = HashSet<String>()
    private var fetching = false

    init { refresh() }

    fun refresh() {
        seen.clear()
        _items.value = emptyList()
        loadMore()
    }

    fun loadMore() {
        if (fetching) return
        fetching = true
        _loading.value = true
        viewModelScope.launch {
            try {
                _error.value = null
                val batch = repo.feed(20)
                val fresh = batch.filter {
                    it.id.isNotEmpty() && seen.add(it.id) &&
                        (it.video.url.isNotEmpty() || it.isImage)
                }
                if (fresh.isEmpty() && _items.value.isEmpty()) {
                    _error.value = "没有拿到视频，请到「设置」粘贴/更新 Cookie"
                }
                _items.value = _items.value + fresh
            } catch (e: Exception) {
                _error.value = e.message ?: "网络错误"
            } finally {
                fetching = false
                _loading.value = false
            }
        }
    }
}
