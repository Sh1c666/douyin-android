package com.mydouyin.sign

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Generates the `a_bogus` request signature entirely on-device — no backend.
 *
 * A single hidden WebView loads the bundled `dy_ab_web.js` asset (the Douyin
 * signing engine, jsrsasign stripped). We call `window._dyGetAb(query, data)`
 * via evaluateJavascript. The WebView is created on the main thread and reused
 * for every signature, so each call costs only the JS execution (~few ms).
 *
 * Readiness is detected by polling rather than onPageFinished, which is more
 * reliable for a WebView that is never attached to the view hierarchy.
 */
class AbogusSigner(private val appContext: Context) {

    @Volatile private var webView: WebView? = null
    @Volatile private var ready = false

    /** Call on the main thread (Net.init posts this via the main looper). */
    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        if (webView != null) return
        val wv = WebView(appContext).apply {
            settings.javaScriptEnabled = true
            settings.databaseEnabled = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            webViewClient = WebViewClient()
        }
        val js = appContext.assets.open("dy_ab_web.js").bufferedReader().use { it.readText() }
        val html = "<!DOCTYPE html><html><head><meta charset='utf-8'></head><body><script>$js</script></body></html>"
        wv.loadDataWithBaseURL("https://www.douyin.com/", html, "text/html", "UTF-8", null)
        webView = wv
    }

    private suspend fun eval(js: String): String = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext ""
        suspendCancellableCoroutine { cont ->
            wv.evaluateJavascript(js) { result ->
                if (cont.isActive) cont.resume(parseResult(result))
            }
        }
    }

    private suspend fun ensureReady() {
        if (ready) return
        withTimeoutOrNull(10_000) {
            while (true) {
                val r = eval("(typeof window._dyGetAb==='function')?'1':'0'")
                if (r.contains("1")) { ready = true; return@withTimeoutOrNull }
                delay(150)
            }
        }
    }

    /** Compute a_bogus for the given (already URL-encoded) query string. */
    suspend fun sign(query: String, data: String = ""): String {
        ensureReady()
        return eval("try{window._dyGetAb(${jsStr(query)},${jsStr(data)})}catch(e){''}")
    }

    /** evaluateJavascript returns the JS value as a JSON-quoted token; recover the raw string. */
    private fun parseResult(result: String?): String {
        if (result.isNullOrBlank()) return ""
        val r = result.trim()
        return when {
            r.startsWith("\"") && r.endsWith("\"") -> r.substring(1, r.length - 1)
                .replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\/", "/")
            r == "null" -> ""
            else -> r
        }
    }

    private fun jsStr(s: String): String {
        val escaped = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "")
        return "'$escaped'"
    }
}
