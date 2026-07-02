package com.mydouyin.ui.nav

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mydouyin.ui.feed.FeedScreen
import com.mydouyin.ui.live.LiveScreen
import com.mydouyin.ui.profile.ProfileScreen
import com.mydouyin.ui.settings.SettingsScreen

object Routes {
    const val FEED = "feed"
    const val LIVE = "live"
    const val SETTINGS = "settings"
    const val PROFILE = "profile/{secUid}"
    fun profile(secUid: String) = "profile/${Uri.encode(secUid)}"
}

private data class TopDest(val route: String, val label: String)

private val TOP_DEST = listOf(
    TopDest(Routes.FEED, "首页"),
    TopDest(Routes.LIVE, "直播"),
    TopDest(Routes.SETTINGS, "设置"),
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = current in TOP_DEST.map { it.route }

    // Long-press 2x state: hoisted here so the bottom bar can react to it.
    var fastForward by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (showBar) {
                // While long-pressing for 2x on the feed, the whole bar becomes the cue.
                val cue2x = fastForward && current == Routes.FEED
                // Short, text-only bottom bar — no icons.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(Color(0xFF101012)),
                    horizontalArrangement = if (cue2x) Arrangement.Center else Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (cue2x) {
                        Text(
                            text = "2倍速播放中",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        TOP_DEST.forEach { d ->
                            val selected = current == d.route
                            Text(
                                text = d.label,
                                color = if (selected) Color.White else Color(0xFF8E8E93),
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        nav.navigate(d.route) {
                                            popUpTo(Routes.FEED) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    .wrapContentSize(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.FEED,
            modifier = Modifier.padding(inner)
        ) {
            composable(Routes.FEED) {
                FeedScreen(
                    fastForward = fastForward,
                    onFastForwardChange = { fastForward = it },
                    onOpenProfile = { nav.navigate(Routes.profile(it)) }
                )
            }
            composable(Routes.LIVE) {
                LiveScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                Routes.PROFILE,
                arguments = listOf(navArgument("secUid") { type = NavType.StringType })
            ) { entry ->
                val secUid = Uri.decode(entry.arguments?.getString("secUid").orEmpty())
                ProfileScreen(
                    secUid = secUid,
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}
