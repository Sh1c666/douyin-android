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

/** Wiring: a single on-device signer, one signed Retrofit client for the Douyin
 *  web API, and a plain client for fetching live room HTML. No backend involved. */
object Net {

    lateinit var signer: AbogusSigner
        private set

    val api: DouyinApi by lazy { buildApi() }

    val liveClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun init(app: Application) {
        signer = AbogusSigner(app)
        // WebView must be created on the main thread.
        Handler(Looper.getMainLooper()).post { signer.start() }
    }

    private fun buildApi(): DouyinApi {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
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
