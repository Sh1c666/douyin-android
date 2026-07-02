package com.mydouyin.data.repo

import com.mydouyin.data.Mapper
import com.mydouyin.data.Net
import com.mydouyin.data.Session
import com.mydouyin.data.api.DouyinParams
import com.mydouyin.data.model.*

/** Talks DIRECTLY to Douyin (no backend). Every call is signed on-device by
 *  DouyinSignInterceptor; responses are normalised by Mapper. */

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

    /** General video search via /aweme/v1/web/general/search/single/ (signed).
     *  [offset] is the pagination offset (incremented by result count). NOTE: this
     *  endpoint is sometimes hit by Douyin 风控 (verify_check) and returns empty —
     *  Mapper fills PostsPage.note with the reason so the UI can show it. */
    suspend fun search(keyword: String, offset: String = "0"): PostsPage =
        Mapper.search(api.search(DouyinParams.search(keyword, offset)), offset)

    /** Proves signing + cookie work end-to-end: issues a real feed request. */
    suspend fun testConnection(): ConnResult = try {
        val f = feed(4)
        ConnResult(ok = f.isNotEmpty(), loggedIn = Session.loggedIn, videoCount = f.size)
    } catch (e: Exception) {
        ConnResult(ok = false, loggedIn = Session.loggedIn, error = e.message)
    }
}
