package com.mydouyin.data.api

import com.mydouyin.data.model.*
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.QueryMap
import retrofit2.http.Url

/** Direct Douyin web API. The query string each method builds is signed on-device
 *  by DouyinSignInterceptor (which also appends a_bogus/verifyFp/fp). */
interface DouyinApi {
    @GET("/aweme/v1/web/tab/feed/")
    suspend fun feed(@QueryMap params: Map<String, String>): FeedRaw

    @GET("/aweme/v1/web/aweme/detail/")
    suspend fun detail(@QueryMap params: Map<String, String>): DetailRaw

    @GET("/aweme/v1/web/user/profile/other/")
    suspend fun user(@QueryMap params: Map<String, String>): ProfileRaw

    @GET("/aweme/v1/web/aweme/post/")
    suspend fun works(@QueryMap params: Map<String, String>): PostsRaw

    @GET("/aweme/v1/web/comment/list/")
    suspend fun comments(@QueryMap params: Map<String, String>): CommentsRaw

    @GET("/aweme/v1/web/comment/list/reply/")
    suspend fun replies(@QueryMap params: Map<String, String>): CommentsRaw

    /** Signed webcast live-room enter API (absolute URL on live.douyin.com). Returns the
     *  raw body so the response can be logged before parsing — the stream URL lives at
     *  data[].stream_url. Signed because DouyinSignInterceptor now also covers webcast. */
    @GET
    suspend fun liveEnter(@Url url: String, @QueryMap params: Map<String, String>): ResponseBody
}
