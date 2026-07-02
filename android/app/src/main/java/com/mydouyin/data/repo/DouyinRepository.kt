package com.mydouyin.data.repo

import com.mydouyin.data.Mapper
import com.mydouyin.data.Net
import com.mydouyin.data.Session
import com.mydouyin.data.LiveFetcher
import com.mydouyin.data.api.DouyinParams
import com.mydouyin.data.model.*

/** Talks DIRECTLY to Douyin (no backend). Every call is signed on-device by
 *  DouyinSignInterceptor; responses are normalised by Mapper. */

private const val LIVE_ENTER_URL = "https://live.douyin.com/webcast/room/web/enter/"

class DouyinRepository {
    private val api get() = Net.api

    suspend fun feed(count: Int = 20): List<Aweme> = Mapper.feed(api.feed(DouyinParams.feed(count)))

    suspend fun detail(id: String): Aweme = Mapper.detail(api.detail(DouyinParams.detail(id)))

    suspend fun user(secUid: String): UserInfo = Mapper.user(api.user(DouyinParams.user(secUid)))

    suspend fun works(secUid: String, cursor: String = "0"): PostsPage =
        Mapper.posts(api.works(DouyinParams.works(secUid, cursor)))

    suspend fun comments(id: String, cursor: String = "0"): CommentsPage =
        Mapper.comments(api.comments(DouyinParams.comments(id, cursor)))

    suspend fun replies(id: String, cid: String, cursor: String = "0"): RepliesPage =
        Mapper.replies(api.replies(DouyinParams.replies(id, cid, cursor)))

    /** Streams come from the signed webcast enter API (the live page's _ROUTER_DATA is gone).
     *  The raw response is logged inside [LiveFetcher.parseEnter] for on-device diagnosis. */
    suspend fun live(rid: String): LiveInfo {
        val raw = api.liveEnter(LIVE_ENTER_URL, DouyinParams.liveEnter(rid)).string()
        return LiveFetcher.parseEnter(raw, rid)
    }

    /** Resolve a pasted room id / live.douyin.com link / v.douyin.com share short-link
     *  (or share text containing one) to a numeric room id. null if unrecognised. */
    suspend fun resolveRoomId(raw: String): String? = LiveFetcher.resolveRoomId(raw)

    /** Proves signing + cookie work end-to-end: issues a real feed request. */
    suspend fun testConnection(): ConnResult = try {
        val f = feed(4)
        ConnResult(ok = f.isNotEmpty(), loggedIn = Session.loggedIn, videoCount = f.size)
    } catch (e: Exception) {
        ConnResult(ok = false, loggedIn = Session.loggedIn, error = e.message)
    }
}
