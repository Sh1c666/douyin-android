package com.mydouyin.data

import com.mydouyin.data.model.*

/** Maps raw Douyin JSON into the small stable schema the UI consumes.
 *  Mirrors backend douyin/client.py _normalise_aweme + the comment/user mappers. */
object Mapper {

    private fun UrlList?.first(): String = this?.urlList?.firstOrNull().orEmpty()

    private fun pickPlay(video: VideoRaw?): String {
        val urls = video?.playAddr?.urlList ?: return ""
        // direct douyinvod.com URLs 403; the www.douyin.com/aweme/v1/play/?video_id=
        // redirect works (ExoPlayer follows the 302 to a fresh tokenised CDN URL).
        return urls.firstOrNull { it.contains("aweme/v1/play") || it.contains("/play/?video_id") }
            ?: urls.firstOrNull().orEmpty()
    }

    fun aweme(a: AwemeRaw?): Aweme? {
        if (a == null) return null
        val video = a.video
        val playUrl = pickPlay(video)
        val images = (a.images ?: emptyList()).mapNotNull { it.urlList?.lastOrNull() }
        val isImage = images.isNotEmpty() && playUrl.isEmpty()
        val stats = a.statistics ?: a.awemeStatistics
        val author = a.author
        val music = a.music
        return Aweme(
            id = a.awemeId.orEmpty(),
            desc = a.desc?.trim().orEmpty(),
            createTime = a.createTime ?: 0L,
            author = Author(
                secUid = author?.secUid.orEmpty(),
                nickname = author?.nickname.orEmpty(),
                avatar = author?.avatarThumb.first(),
                uid = author?.uid.orEmpty()
            ),
            video = VideoInfo(
                url = playUrl,
                cover = video?.cover.first(),
                dynamicCover = video?.dynamicCover.first(),
                duration = ((video?.duration ?: 0L) / 1000L).toInt(),
                width = video?.width ?: 0,
                height = video?.height ?: 0
            ),
            music = Music(
                title = music?.title.orEmpty(),
                author = music?.author.orEmpty(),
                cover = (music?.coverThumb ?: music?.cover).first(),
                url = music?.playUrl.first()
            ),
            stats = Stats(
                digg = stats?.diggCount ?: 0,
                comment = stats?.commentCount ?: 0,
                share = stats?.shareCount ?: 0,
                collect = stats?.collectCount ?: 0,
                play = stats?.playCount ?: 0
            ),
            isImage = isImage,
            images = images,
            shareUrl = if (a.awemeId != null) "https://www.douyin.com/video/${a.awemeId}" else ""
        )
    }

    fun feed(raw: FeedRaw?): List<Aweme> =
        (raw?.awemeList ?: emptyList()).mapNotNull { aweme(it) }
            .filter { it.video.url.isNotEmpty() || it.isImage }

    fun posts(raw: PostsRaw?): PostsPage = PostsPage(
        hasMore = raw?.hasMore ?: 0,
        maxCursor = raw?.maxCursor?.toString() ?: "0",
        list = (raw?.awemeList ?: emptyList()).mapNotNull { aweme(it) }
    )

    fun search(raw: SearchRaw?, offset: String): PostsPage {
        // Each hit is {type, aweme_info} (some inline cards use aweme_inline_info).
        val list = (raw?.data ?: emptyList())
            .mapNotNull { it.awemeInfo ?: it.awemeInlineInfo }
            .mapNotNull { aweme(it) }
            .filter { it.video.url.isNotEmpty() || it.isImage }
        // Search paginates by offset (server returns no cursor), so advance by result count.
        val nextOffset = ((offset.toLongOrNull() ?: 0L) + list.size).toString()
        val statusCode = raw?.statusCode
        val note = when {
            statusCode != null && statusCode != 0 ->
                "搜索被拒 status_code=$statusCode（签名未过/风控）"
            raw?.data.isNullOrEmpty() ->
                "无结果（也可能被风控返空，换个词或稍后再试）"
            else -> ""
        }
        return PostsPage(
            hasMore = raw?.hasMore ?: 0,
            maxCursor = nextOffset,
            list = list,
            note = note
        )
    }

    fun detail(raw: DetailRaw?): Aweme = aweme(raw?.awemeDetail) ?: Aweme()

    fun comments(raw: CommentsRaw?): CommentsPage {
        val list = (raw?.comments ?: emptyList()).map { c ->
            Comment(
                cid = c.cid.orEmpty(),
                text = c.text.orEmpty(),
                createTime = c.createTime ?: 0L,
                diggCount = c.diggCount ?: 0,
                replyCommentTotal = c.replyCommentTotal ?: 0,
                user = CommentUser(
                    secUid = c.user?.secUid.orEmpty(),
                    nickname = c.user?.nickname.orEmpty(),
                    avatar = c.user?.avatarThumb.first()
                )
            )
        }
        return CommentsPage(
            hasMore = raw?.hasMore ?: 0,
            cursor = raw?.cursor?.toString() ?: "0",
            total = raw?.total ?: 0,
            list = list
        )
    }

    fun replies(raw: CommentsRaw?): RepliesPage = RepliesPage(
        hasMore = raw?.hasMore ?: 0,
        cursor = raw?.cursor?.toString() ?: "0",
        list = (raw?.comments ?: emptyList()).map { c ->
            Comment(
                cid = c.cid.orEmpty(),
                text = c.text.orEmpty(),
                createTime = c.createTime ?: 0L,
                diggCount = c.diggCount ?: 0,
                replyCommentTotal = 0,
                user = CommentUser(
                    secUid = c.user?.secUid.orEmpty(),
                    nickname = c.user?.nickname.orEmpty(),
                    avatar = c.user?.avatarThumb.first()
                )
            )
        }
    )

    fun user(raw: ProfileRaw?): UserInfo {
        // /user/profile/other/ nests the user fields directly under "user".
        // (The old code read raw.user.user — a path that doesn't exist in the real
        // response — so every field came back empty and follower/like counts were 0.)
        val u = raw?.user ?: return UserInfo()
        return UserInfo(
            secUid = u.secUid.orEmpty(),
            nickname = u.nickname.orEmpty(),
            signature = u.signature.orEmpty(),
            avatar = u.avatarThumb.first(),
            uid = u.uid.orEmpty(),
            followerCount = u.followerCount ?: 0,
            followingCount = u.followingCount ?: 0,
            awemeCount = u.awemeCount ?: 0,
            totalFavorited = u.totalFavorited ?: 0,
            isLive = (u.roomId?.toString() ?: "0") !in setOf("0", "", "null"),
            roomId = (u.roomId?.toString() ?: "").let { if (it == "null") "" else it }
        )
    }
}
