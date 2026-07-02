package com.mydouyin.data

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.google.gson.GsonBuilder
import com.mydouyin.data.api.DouyinApi
import com.mydouyin.data.api.DouyinParams
import com.mydouyin.data.model.*
import com.mydouyin.sign.AbogusSigner
import com.mydouyin.sign.DOUYIN_UA
import com.mydouyin.sign.DouyinSignInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Wiring: a single on-device signer and one signed Retrofit client for the Douyin
 *  web API. No backend involved. */
object Net {

    lateinit var signer: AbogusSigner
        private set

    val api: DouyinApi by lazy { buildApi() }

    fun init(app: Application) {
        signer = AbogusSigner(app)
        // WebView must be created on the main thread.
        Handler(Looper.getMainLooper()).post { signer.start() }
    }

    private fun buildApi(): DouyinApi {
        val logging = HttpLoggingInterceptor().apply {
            // release 包不打请求日志（URL 含 a_bogus，虽非长期密钥但避免污染 logcat）
            level = if (com.mydouyin.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }
        val ok = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(DouyinSignInterceptor(signer, { Session.cookie }, { Session.verifyFp }))
            .addInterceptor(logging)
            .build()
        val gson = GsonBuilder().setLenient().create()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.douyin.com/")
            .client(ok)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        return retrofit.create(DouyinApi::class.java)
    }
}
