package com.mydouyin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mydouyin.ui.nav.AppRoot
import com.mydouyin.ui.theme.MyDouyinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyDouyinTheme {
                AppRoot()
            }
        }
    }
}
