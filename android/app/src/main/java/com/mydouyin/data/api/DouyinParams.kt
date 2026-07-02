package com.mydouyin.data.api

import kotlin.random.Random

/** Builds the signed query-param maps for each Douyin endpoint.
 *  verifyFp / fp / a_bogus are added later by DouyinSignInterceptor, so they are
 *  intentionally absent here. webid + msToken are included (they are signed over). */
object DouyinParams {

    private const val TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789=_"

    private fun base(): LinkedHashMap<String, String> = linkedMapOf(
        "device_platform" to "webapp",
        "aid" to "6383",
        "channel" to "channel_pc_web",
        "update_version_code" to "170400",
        "pc_client_type" to "1",
        "version_code" to "170400",
        "version_name" to "17.4.0",
        "cookie_enabled" to "true",
        "screen_width" to "1707",
        "screen_height" to "960",
        "browser_language" to "zh-CN",
        "browser_platform" to "Win32",
        "browser_name" to "Edge",
        "browser_version" to "125.0.0.0",
        "browser_online" to "true",
        "engine_name" to "Blink",
        "engine_version" to "125.0.0.0",
        "os_name" to "Windows",
        "os_version" to "10",
        "cpu_core_num" to "32",
        "device_memory" to "8",
        "platform" to "PC",
        "downlink" to "10",
        "effective_type" to "4g",
        "round_trip_time" to "100",
    )

    private fun LinkedHashMap<String, String>.tail() = apply {
        put("webid", webid())
        put("msToken", msToken())
    }

    fun webid(): String = buildString { repeat(19) { append(Random.nextInt(10)) } }

    fun msToken(): String = buildString {
        repeat(128) { append(TOKEN_ALPHABET[Random.nextInt(TOKEN_ALPHABET.length)]) }
    }

    fun feed(count: Int = 20): Map<String, String> = base().apply {
        put("count", count.toString())
        put("feed_style", "1")
        put("filter_warn", "0")
        put("max_cursor", "0")
        put("refresh_cursor", "")
        put("refresh_index", "2")
        put("tag", "")
        put("type", "0")
    }.tail()

    fun detail(awemeId: String): Map<String, String> = base().apply {
        put("aweme_id", awemeId)
    }.tail()

    fun user(secUid: String): Map<String, String> = base().apply {
        put("publish_video_strategy_type", "2")
        put("source", "channel_pc_web")
        put("sec_user_id", secUid)
        put("personal_center_strategy", "1")
    }.tail()

    fun works(secUid: String, maxCursor: String): Map<String, String> = base().apply {
        put("sec_user_id", secUid)
        put("max_cursor", maxCursor)
        put("locate_query", "false")
        put("show_live_replay_strategy", "1")
        put("need_time_list", if (maxCursor == "0") "1" else "0")
        put("time_list_query", "0")
        put("whale_cut_token", "")
        put("cut_version", "1")
        put("count", "18")
        put("publish_video_strategy_type", "2")
    }.tail()

    fun comments(awemeId: String, cursor: String, count: String = "20"): Map<String, String> = base().apply {
        put("aweme_id", awemeId)
        put("cursor", cursor)
        put("count", count)
        put("item_type", "0")
        put("whale_cut_token", "")
        put("cut_version", "1")
        put("rcFT", "")
        put("round_trip_time", "0")
    }.tail()

    fun replies(awemeId: String, commentId: String, cursor: String, count: String = "20"): Map<String, String> = base().apply {
        put("item_id", awemeId)
        put("comment_id", commentId)
        put("cut_version", "1")
        put("cursor", cursor)
        put("count", count)
        put("item_type", "0")
        put("round_trip_time", "0")
    }.tail()

    /** Signed params for the webcast live-room enter API. Minimal, webcast-specific set
     *  (NOT the aweme webapp base()) — a_bogus/verifyFp/fp are added by the interceptor. */
    fun liveEnter(roomId: String): Map<String, String> = linkedMapOf(
        "aid" to "6383",
        "app_name" to "douyin_web",
        "live_id" to "1",
        "device_platform" to "web",
        "language" to "zh-CN",
        "room_id_str" to roomId,
        "web_rid" to roomId,
        "enter_from" to "web_live",
        "cookie_enabled" to "true",
        "is_need_double_stream" to "false",
        "insert_task_id" to "",
        "msToken" to msToken(),
    )
}
