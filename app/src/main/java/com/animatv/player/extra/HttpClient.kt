package com.animatv.player.extra

import android.util.Log
import com.animatv.player.App
import com.animatv.player.R
import okhttp3.*
import java.io.File
import java.util.concurrent.TimeUnit

class HttpClient(private val useCache: Boolean) {

    private val tlsFactory by lazy {
        try {
            TLSSocketFactory().trustAllHttps()
        } catch (e: Exception) {
            Log.e("HttpClient", "TLSSocketFactory init failed: ${e.message}")
            null
        }
    }

    fun create(request: Request): Call {
        val cacheFile = File(App.context.cacheDir, "HttpClient")
        val cacheSize: Long = 10 * 1024 * 1024

        val builder = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            // Interceptor: inject API Key untuk server privat
            .addInterceptor(PrivateServerInterceptor())

        try {
            val tls = tlsFactory
            if (tls != null) {
                builder.sslSocketFactory(tls, tls.getTrustManager())
                builder.hostnameVerifier { _, _ -> true }
            }
        } catch (e: Exception) {
            Log.w("HttpClient", "Could not set SSL factory: ${e.message}")
        }

        if (useCache) {
            try {
                builder.cache(Cache(cacheFile, cacheSize))
            } catch (e: Exception) {
                Log.w("HttpClient", "Could not set cache: ${e.message}")
            }
        }

        return try {
            builder.build().newCall(request)
        } catch (e: Exception) {
            Log.e("HttpClient", "Build failed, using default client: ${e.message}")
            OkHttpClient().newCall(request)
        }
    }
}

/**
 * Interceptor yang menambahkan X-API-Key header
 * HANYA untuk request ke server privat SymphogearTV.
 * Request ke URL lain (GitHub API, dsb) tidak terpengaruh.
 */
class PrivateServerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()

        // Ambil base URL server dari strings.xml (tanpa path /channels.json)
        val playlistUrl = try {
            App.context.getString(R.string.iptv_playlist)
        } catch (e: Exception) { "" }

        // Hanya inject header kalau request menuju server privat kita
        val serverBase = playlistUrl
            .substringBefore("/channels.json")
            .trim()

        if (serverBase.isNotBlank() && url.startsWith(serverBase)) {
            // Gabungkan key dari dua bagian (obfuskasi sederhana)
            val k1 = try { App.context.getString(R.string.srv_k1) } catch (e: Exception) { "" }
            val k2 = try { App.context.getString(R.string.srv_k2) } catch (e: Exception) { "" }
            val apiKey = "$k1$k2"

            val newRequest = originalRequest.newBuilder()
                .addHeader("X-API-Key", apiKey)
                .build()
            return chain.proceed(newRequest)
        }

        return chain.proceed(originalRequest)
    }
}
