package com.mydouyin.data

/** Holds the pasted cookie + derived values, read by the sign interceptor. */
object Session {
    @Volatile var cookie: String = ""
        private set
    @Volatile var verifyFp: String = ""   // s_v_web_id from the cookie
        private set
    @Volatile var loggedIn: Boolean = false
        private set

    fun setCookie(c: String) {
        cookie = c.trim()
        verifyFp = Regex("s_v_web_id=([^;]+)").find(cookie)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        loggedIn = cookie.contains("sessionid=")
    }
}
