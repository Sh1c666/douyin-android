package com.mydouyin.data.model

import com.google.gson.annotations.SerializedName

/** Mirrors the JSON produced by the FastAPI backend (douyin/client.py). */

data class Author(
    @SerializedName("sec_uid") val secUid: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val uid: String = ""
)

data class VideoInfo(
    val url: String = "",
    val cover: String = "",
    @SerializedName("dynamic_cover") val dynamicCover: String = "",
    val duration: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)

data class Music(
    val title: String = "",
    val author: String = "",
    val cover: String = "",
    val url: String = ""
)

data class Stats(
    val digg: Int = 0,
    val comment: Int = 0,
    val share: Int = 0,
    val collect: Int = 0,
    val play: Int = 0
)

data class Aweme(
    val id: String = "",
    val desc: String = "",
    @SerializedName("create_time") val createTime: Long = 0,
    val author: Author = Author(),
    val video: VideoInfo = VideoInfo(),
    val music: Music = Music(),
    val stats: Stats = Stats(),
    @SerializedName("is_image") val isImage: Boolean = false,
    val images: List<String> = emptyList(),
    @SerializedName("share_url") val shareUrl: String = ""
)

data class UserInfo(
    @SerializedName("sec_uid") val secUid: String = "",
    val nickname: String = "",
    val signature: String = "",
    val avatar: String = "",
    val uid: String = "",
    @SerializedName("follower_count") val followerCount: Int = 0,
    @SerializedName("following_count") val followingCount: Int = 0,
    @SerializedName("aweme_count") val awemeCount: Int = 0,
    @SerializedName("total_favorited") val totalFavorited: Int = 0,
    @SerializedName("is_live") val isLive: Boolean = false,
    @SerializedName("room_id") val roomId: String = ""
)

data class PostsPage(
    @SerializedName("has_more") val hasMore: Int = 0,
    @SerializedName("max_cursor") val maxCursor: String = "0",
    val list: List<Aweme> = emptyList(),
    /** Human-readable reason when the page came back empty (风控/无结果…).
     *  Empty when there's nothing to flag. */
    val note: String = ""
)

data class CommentUser(
    @SerializedName("sec_uid") val secUid: String = "",
    val nickname: String = "",
    val avatar: String = ""
)

data class Comment(
    val cid: String = "",
    val text: String = "",
    @SerializedName("create_time") val createTime: Long = 0,
    @SerializedName("digg_count") val diggCount: Int = 0,
    @SerializedName("reply_comment_total") val replyCommentTotal: Int = 0,
    val user: CommentUser = CommentUser()
)

data class CommentsPage(
    @SerializedName("has_more") val hasMore: Int = 0,
    val cursor: String = "0",
    val total: Int = 0,
    val list: List<Comment> = emptyList()
)

data class RepliesPage(
    @SerializedName("has_more") val hasMore: Int = 0,
    val cursor: String = "0",
    val list: List<Comment> = emptyList()
)

/** Result of the in-app "test connection" check: does a real feed request succeed. */
data class ConnResult(
    val ok: Boolean = false,
    val loggedIn: Boolean = false,
    val videoCount: Int = 0,
    val error: String? = null
)
