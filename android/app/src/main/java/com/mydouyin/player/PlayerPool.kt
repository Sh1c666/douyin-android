package com.mydouyin.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * A fixed-size pool of ExoPlayers for a VerticalPager feed.
 *
 * Each page index maps to slot `index % size`. As long as `size` is strictly
 * greater than the number of pages the pager ever has composed at once, the
 * modulo mapping never collides among live pages: scrolling off-screen simply
 * rebinds the slot's player to the new page's media. With beyondViewportPageCount = 1
 * the pager composes 3 pages at rest but can transiently hold 4 mid-scroll, so the
 * default `size = 5` leaves a safe margin. This caps memory at `size` decoders
 * regardless of how far the user scrolls.
 *
 * Playback is CENTRAL, not per-page: only the active (settled) page ever plays.
 * [setActive] is called whenever the pager settles, and it pauses every player
 * before enabling the one bound to the active index. [bind] mirrors the same
 * rule, so a freshly bound active page starts playing and a recycled slot never
 * keeps leaking its previous video's audio. This is what prevents the
 * "black screen + audio from the last video" race the per-page approach had.
 */
class PlayerPool(context: Context, private val size: Int = 5) {
    private val ctx = context.applicationContext
    private val players: List<ExoPlayer> = (0 until size).map {
        Players.new(ctx).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = false
            volume = 1f
        }
    }
    private val slotIndex = IntArray(size) { -1 } // which page each slot currently holds
    private var activeIndex = -1                  // the one page allowed to play
    private var released = false                  // set by release(); later calls become no-ops

    private fun slot(index: Int): Int = ((index % size) + size) % size
    fun playerFor(index: Int): ExoPlayer = players[slot(index)]

    /** Load [url] into the slot for [index] (only re-prepares when the slot changes page),
     *  then reapply the play rule so the active page plays and a recycled slot stops. */
    fun bind(index: Int, url: String?) {
        if (released) return
        val s = slot(index)
        val p = players[s]
        if (slotIndex[s] != index) {
            p.stop()
            p.clearMediaItems()
            if (!url.isNullOrEmpty()) {
                p.setMediaItem(MediaItem.fromUri(url))
                p.prepare()
            }
            // A recycled slot must never inherit the previous page's 2x long-press speed.
            p.playbackParameters = PlaybackParameters(1f)
            slotIndex[s] = index
        }
        p.playWhenReady = (index == activeIndex)
    }

    /** Pause everything, then play only the player bound to [index]. */
    fun setActive(index: Int) {
        if (released) return
        activeIndex = index
        for (s in 0 until size) {
            players[s].playWhenReady = (slotIndex[s] == index)
        }
    }

    /** Tap-to-toggle on the active page. */
    fun toggle(index: Int) {
        if (released) return
        val s = slot(index)
        if (slotIndex[s] == index) players[s].playWhenReady = !players[s].playWhenReady
    }

    fun pauseAll() { if (!released) players.forEach { it.playWhenReady = false } }

    /** Re-resume the active page (e.g. returning from background). */
    fun resumeActive() { if (!released && activeIndex >= 0) setActive(activeIndex) }

    fun setMuted(muted: Boolean) { if (!released) players.forEach { it.volume = if (muted) 0f else 1f } }

    fun release() {
        if (released) return
        released = true
        players.forEach { it.release() }
    }
}
