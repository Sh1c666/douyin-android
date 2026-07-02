package com.mydouyin.sign

import okhttp3.Interceptor
import okhttp3.Response
import kotlinx.coroutines.runBlocking

/** The exact User-Agent get_ab() signs over — the HTTP UA MUST match, or a_bogus won't validate. */
const val DOUYIN_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/117.0"

/**
 * Signs every outgoing Douyin web-API request on-device: takes the query string
 * OkHttp already built, asks [signer] for an a_bogus over it, then appends
 * a_bogus / verifyFp / fp and the required headers (UA, referer, cookie).
 *
 * Because the signature is computed over the exact encoded query that will be
 * sent, and the tail params are added only after signing, sign == send — the
 * same property the Python backend relied on.
 */
class DouyinSignInterceptor(
    private val signer: AbogusSigner,
    private val cookie: () -> String,
    private val verifyFp: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val path = req.url.encodedPath
        // Endpoints that need on-device a_bogus signing: the Douyin web API, plus the
        // webcast live-room enter API (streams come from there now that the live page's
        // _ROUTER_DATA scrape is dead).
        val isAweme = path.contains("/aweme/v1/web/")
        val isWebcast = path.contains("/webcast/room/web/enter/")
        if (!isAweme && !isWebcast) {
            return chain.proceed(req)
        }

        val query = req.url.query ?: ""
        val ab = if (query.isNotEmpty()) runBlocking { signer.sign(query) } else ""

        val urlBuilder = req.url.newBuilder()
        if (ab.isNotEmpty()) urlBuilder.addQueryParameter("a_bogus", ab)
        val vfp = verifyFp()
        if (vfp.isNotEmpty()) {
            urlBuilder.addQueryParameter("verifyFp", vfp)
            urlBuilder.addQueryParameter("fp", vfp)
        }

        val awemeId = req.url.queryParameter("aweme_id")
        val secUid = req.url.queryParameter("sec_user_id")
        val roomId = req.url.queryParameter("room_id_str")
        val referer = when {
            isWebcast && !roomId.isNullOrEmpty() -> "https://live.douyin.com/$roomId"
            isWebcast -> "https://live.douyin.com/"
            !awemeId.isNullOrEmpty() -> "https://www.douyin.com/video/$awemeId"
            !secUid.isNullOrEmpty() -> "https://www.douyin.com/user/$secUid"
            else -> "https://www.douyin.com/"
        }

        val cookieStr = cookie()
        val builder = req.newBuilder()
            .url(urlBuilder.build())
            .header("User-Agent", DOUYIN_UA)
            .header("Referer", referer)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
        if (cookieStr.isNotEmpty()) builder.header("Cookie", cookieStr)

        return chain.proceed(builder.build())
    }
}
