package com.mydouyin

import android.app.Application
import com.mydouyin.data.Net
import com.mydouyin.data.Session
import com.mydouyin.util.Prefs

class DouyinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.setCookie(Prefs.cookie(this))
        Net.init(this)   // boots the on-device WebView signer
    }
}
