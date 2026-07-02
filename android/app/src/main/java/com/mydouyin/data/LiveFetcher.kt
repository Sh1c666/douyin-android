package com.mydouyin.data

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mydouyin.data.model.LiveInfo
import com.mydouyin.sign.DOUYIN_UA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Live room resolution + webcast enter-API response parsing.
 *
 *  The old approach scraped the live page's `_ROUTER_DATA` for a stream URL, but the
 *  current douyin page no longer embeds that (it ships `LIVE_SSR_DATA_ID` with a null
 *  `web_stream_url` unless logged in). Streams now come from the signed webcast
 *  `enter` endpoint — [parseEnter] normalises that JSON, and the signed HTTP call
 *  itself lives in [DouyinRepository] (it needs the on-device signer).
 *
 *  [resolveRoomId] turns a pasted room id / live.douyin.com link / v.douyin.com share
 *  short-link (or a blob of share text containing one) into a numeric room id. */
object LiveFetcher {
    private const val TAG = "LiveFetcher"

    /** raw → numeric room id, following share short-link redirects when needed.
     *  null if the input isn't a room id or a recognised live link. */
    suspend fun resolveRoomId(raw: String): String? = withContext(Dispatchers.IO) {
        val t = raw.trim()
        if (t.matches(Regex("\\d+"))) return@withContext t
        // Pull the first URL out of arbitrary pasted share text.
        val url = Regex("https?://[^\\s<\"']+").find(t)?.value ?: return@withContext null
        // Direct live.douyin.com/{id} or ?modal_id={id}.
        Regex("(?:live\\.douyin\\.com/|modal_id=)(\\d+)").find(url)
            ?.groupValues?.lastOrNull()?.let { return@withContext it }
        // Share short links (v.douyin.com / iesdouyin / webcast.amemv) → follow the 302 chain.
        val finalUrl = followRedirect(url)
        Regex("(?:live\\.douyin\\.com/|reflow/|modal_id=|room_id_str=|room_id=)(\\d+)").find(finalUrl)
            ?.groupValues?.lastOrNull()?.let { return@withContext it }
        null
    }

    private fun followRedirect(url: String): String = try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", DOUYIN_UA)
            .header("Referer", "https://www.douyin.com/")
            .build()
        // Net.liveClient follows redirects by default → request.url is the final destination.
        Net.liveClient.newCall(req).execute().use { it.request.url.toString() }
    } catch (e: Exception) {
        Log.w(TAG, "short-link redirect failed: ${e.message}")
        url
    }

    /** Parse the signed webcast enter response. Logs the raw body so a real-device test
     *  can reveal the exact shape — this endpoint can't be exercised from a dev machine
     *  without the on-device signer + cookie + a live room.
     *
     *  On failure it fills [LiveInfo.note] with the most likely cause (风控 / 房间无效 /
     *  未开播) so the UI can show something useful instead of a bare "未取到流地址". */
    fun parseEnter(raw: String, roomId: String): LiveInfo {
        Log.d(TAG, "enter resp (first 1500): ${raw.take(1500)}")
        val root: JsonElement = try {
            JsonParser.parseString(raw)
        } catch (e: Exception) {
            Log.w(TAG, "enter resp is not JSON: ${e.message}")
            return LiveInfo(roomId = roomId, note = "响应非 JSON（疑似风控验证页或登录失效）")
        }

        // Douyin status_code: 0 = ok, anything else = error / 风控 block.
        val statusCode = root.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("status_code")?.takeIf { it.isJsonPrimitive }?.asString
        if (statusCode != null && statusCode != "0") {
            Log.w(TAG, "enter rejected: status_code=$statusCode")
            return LiveInfo(roomId = roomId, note = "抖音拒绝请求 status_code=$statusCode（签名未过/风控）")
        }

        // stream_url sits inside the room object; walkFind returns that parent, then drill in.
        val streamParent = walkFind({ o -> o.has("stream_url") }, root) ?: JsonObject()
        val streamObj = streamParent.get("stream_url")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val flvObj = streamObj.get("flv_pull_url")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val flv = sequenceOf("FULL_HD1", "ORIGINATION", "BD1", "HD1", "SD1")
            .mapNotNull { flvObj.get(it)?.takeIf { it.isJsonPrimitive }?.asString }
            .firstOrNull()
            ?: flvObj.entrySet().firstOrNull()?.value?.takeIf { it.isJsonPrimitive }?.asString
            ?: ""
        val hls = streamObj.get("hls_pull_url")?.takeIf { it.isJsonPrimitive }?.asString ?: ""

        // Prefer the same object that carries stream_url (the room has both); fall back to a
        // tight title+status+owner signature so a common key like "title" can't match a sibling.
        val room = streamParent.takeIf { it.has("title") && it.has("owner") }
            ?: walkFind({ o -> o.has("title") && o.has("status") && o.has("owner") }, root)
            ?: JsonObject()
        val title = room.get("title")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        val ownerObj = room.get("owner")?.takeIf { it.isJsonObject }?.asJsonObject
        val nickname = ownerObj?.get("nickname")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        val status = room.get("status")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        val isLive = status == "2"   // webcast status: 2 = streaming

        val note = when {
            flv.isBlank() && hls.isBlank() && !isLive -> "主播未开播"
            flv.isBlank() && hls.isBlank() -> "未取到拉流地址（房间数据为空：房间号无效或风控）"
            else -> ""
        }
        Log.d(TAG, "enter parsed: status=$status isLive=$isLive hasHls=${hls.isNotBlank()} hasFlv=${flv.isNotBlank()} note=$note")
        return LiveInfo(
            roomId = roomId,
            title = title,
            nickname = nickname,
            status = status,
            isLive = isLive,
            flv = flv,
            hls = hls,
            note = note
        )
    }

    /** Depth-first search for the first object satisfying [walkKey]. */
    private fun walkFind(walkKey: (JsonObject) -> Boolean, root: JsonElement): JsonObject? {
        if (root.isJsonObject) {
            val o = root.asJsonObject
            if (walkKey(o)) return o
            for ((_, v) in o.entrySet()) walkFind(walkKey, v)?.let { return it }
        } else if (root.isJsonArray) {
            for (v in root.asJsonArray) walkFind(walkKey, v)?.let { return it }
        }
        return null
    }
}
