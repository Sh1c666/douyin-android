package com.mydouyin.player

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.mydouyin.sign.DOUYIN_UA

/** Central ExoPlayer builder: sends a browser User-Agent on all media requests so
 * Douyin's CDN (reached via the www.douyin.com/aweme/v1/play/ 302 redirect) doesn't
 * 403, and allows cross-protocol redirects just in case.
 *
 * The UA MUST match the one a_bogus signs over, so it comes from a single source of
 * truth — [DOUYIN_UA] in the sign package. */
object Players {
    fun new(context: Context): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(DOUYIN_UA)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(context, http)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
}
