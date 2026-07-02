package com.mydouyin.util

/**
 * Remembers the aweme the user was last watching on the feed, so an author's
 * profile can mark it ("刚刚看过") and offer to jump back to it. Plain volatile
 * fields — read once when a profile opens; no reactive UI depends on changes.
 */
object LastWatched {
    @Volatile var awemeId: String = ""
    @Volatile var secUid: String = ""

    fun set(id: String, secUid: String) {
        awemeId = id
        this.secUid = secUid
    }
}
