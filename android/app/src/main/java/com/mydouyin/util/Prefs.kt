package com.mydouyin.util

import android.content.Context

/** Stores the pasted Douyin cookie (the only secret the app needs). */
object Prefs {
    private const val NAME = "mydouyin"
    private const val KEY_COOKIE = "cookie"

    fun cookie(ctx: Context): String = sp(ctx).getString(KEY_COOKIE, "") ?: ""

    fun setCookie(ctx: Context, value: String) {
        sp(ctx).edit().putString(KEY_COOKIE, value.trim()).apply()
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
