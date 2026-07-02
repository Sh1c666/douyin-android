package com.mydouyin.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** Thin Compose wrapper around Media3 PlayerView. */
@Composable
fun PlayerSurface(
    player: Player,
    modifier: Modifier = Modifier,
    useController: Boolean = false,
    // FIT (not ZOOM): a 9:16 Douyin video fills a portrait phone edge-to-edge with no
    // cropping, and non-vertical clips letterbox instead of losing their edges.
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.useController = useController
                this.resizeMode = resizeMode
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { it.player = player }
    )
}
