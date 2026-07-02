package com.mydouyin.data.model

import com.google.gson.annotations.SerializedName

/** Raw Douyin web response shapes (only the fields we read). All nullable:
 *  Gson bypasses Kotlin defaults for missing fields, so nullability prevents NPE. */

data class UrlList(
    @SerializedName("url_list") val urlList: List<String>? = null,
    val uri: String? = null
)

data class AuthorRaw(
    @SerializedName("sec_uid") val secUid: String? = null,
    val nickname: String? = null,
    @SerializedName("avatar_thumb") val avatarThumb: UrlList? = null,
    val uid: String? = null
)

data class VideoRaw(
    @SerializedName("play_addr") val playAddr: UrlList? = null,
    val cover: UrlList? = null,
    @SerializedName("dynamic_cover") val dynamicCover: UrlList? = null,
    val duration: Long? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class MusicRaw(
    val title: String? = null,
    val author: String? = null,
    @SerializedName("cover_thumb") val coverThumb: UrlList? = null,
    val cover: UrlList? = null,
    @SerializedName("play_url") val playUrl: UrlList? = null
)

data class StatsRaw(
    @SerializedName("digg_count") val diggCount: Int? = null,
    @SerializedName("comment_count") val commentCount: Int? = null,
    @SerializedName("share_count") val shareCount: Int? = null,
    @SerializedName("collect_count") val collectCount: Int? = null,
    @SerializedName("play_count") val playCount: Int? = null
)

data class AwemeRaw(
    @SerializedName("aweme_id") val awemeId: String? = null,
    val desc: String? = null,
    @SerializedName("create_time") val createTime: Long? = null,
    val author: AuthorRaw? = null,
    val video: VideoRaw? = null,
    val music: MusicRaw? = null,
    val statistics: StatsRaw? = null,
    @SerializedName("aweme_statistics") val awemeStatistics: StatsRaw? = null,
    val images: List<UrlList>? = null
)

data class FeedRaw(
    @SerializedName("aweme_list") val awemeList: List<AwemeRaw>? = null,
    @SerializedName("status_code") val statusCode: Int? = null
)

data class PostsRaw(
    @SerializedName("aweme_list") val awemeList: List<AwemeRaw>? = null,
    @SerializedName("has_more") val hasMore: Int? = null,
    @SerializedName("max_cursor") val maxCursor: Any? = null
)

data class DetailRaw(
    @SerializedName("aweme_detail") val awemeDetail: AwemeRaw? = null,
    @SerializedName("status_code") val statusCode: Int? = null
)

data class CommentUserRaw(
    @SerializedName("sec_uid") val secUid: String? = null,
    val nickname: String? = null,
    @SerializedName("avatar_thumb") val avatarThumb: UrlList? = null
)

data class CommentRaw(
    val cid: String? = null,
    val text: String? = null,
    @SerializedName("create_time") val createTime: Long? = null,
    @SerializedName("digg_count") val diggCount: Int? = null,
    @SerializedName("reply_comment_total") val replyCommentTotal: Int? = null,
    val user: CommentUserRaw? = null
)

data class CommentsRaw(
    val comments: List<CommentRaw>? = null,
    @SerializedName("has_more") val hasMore: Int? = null,
    val cursor: Any? = null,
    val total: Int? = null
)

data class ProfileUserRaw(
    @SerializedName("sec_uid") val secUid: String? = null,
    val nickname: String? = null,
    val signature: String? = null,
    @SerializedName("avatar_thumb") val avatarThumb: UrlList? = null,
    val uid: String? = null,
    @SerializedName("follower_count") val followerCount: Int? = null,
    @SerializedName("following_count") val followingCount: Int? = null,
    @SerializedName("aweme_count") val awemeCount: Int? = null,
    @SerializedName("total_favorited") val totalFavorited: Int? = null,
    @SerializedName("room_id") val roomId: Any? = null
)

// /aweme/v1/web/user/profile/other/ returns the user object directly under "user"
// (i.e. {"user": {sec_uid, follower_count, ...}}), NOT nested under "user"."user".
data class ProfileRaw(val user: ProfileUserRaw? = null)

// /aweme/v1/web/general/search/single/ wraps each hit as {type, aweme_info} (or
// aweme_inline_info for some inline cards) inside the top-level "data" array.
data class SearchItemRaw(
    val type: Int? = null,
    @SerializedName("aweme_info") val awemeInfo: AwemeRaw? = null,
    @SerializedName("aweme_inline_info") val awemeInlineInfo: AwemeRaw? = null
)

data class SearchRaw(
    val data: List<SearchItemRaw>? = null,
    @SerializedName("has_more") val hasMore: Int? = null,
    @SerializedName("status_code") val statusCode: Int? = null
)
