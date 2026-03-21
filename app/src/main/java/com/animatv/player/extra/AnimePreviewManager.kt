package com.animatv.player.extra

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView

/**
 * AnimePreviewManager
 * Memutar video anime trailer/opening dari YouTube secara acak & tanpa suara
 * di panel kanan atas MainActivity.
 *
 * Cara kerja:
 * - Daftar YouTube video ID anime populer sudah di-hardcode (tidak butuh API key)
 * - Setiap VIDEO_DURATION_MS menit, ganti ke video acak berikutnya
 * - Video di-embed via YouTube IFrame dengan parameter: mute=1, autoplay=1, controls=0
 * - Tombol "▶ next" bisa skip manual
 */
object AnimePreviewManager {

    private const val VIDEO_DURATION_MS = 60_000L  // ganti video tiap 60 detik

    // Daftar YouTube video ID - opening/trailer anime populer
    // Format: Pair(videoId, judulAnime)
    private val ANIME_VIDEOS = listOf(
        Pair("BmCEMXZBfpA", "Sword Art Online OP"),
        Pair("sLCkHop6hHQ", "Attack on Titan OP"),
        Pair("0TDL8WMiJiE", "Demon Slayer OP"),
        Pair("HScFoHJDXAI", "My Hero Academia OP"),
        Pair("d5PZApANgAI", "Naruto Shippuden OP"),
        Pair("VDcLBrpOmk4", "One Piece OP"),
        Pair("N_oD2EOUgWE", "Fullmetal Alchemist OP"),
        Pair("UBPesJMdxJg", "Tokyo Ghoul OP"),
        Pair("OFfCGPDWoEk", "Fairy Tail OP"),
        Pair("6GKiHf0Wd6w", "Black Clover OP"),
        Pair("2SVFnDpLMYg", "Hunter x Hunter OP"),
        Pair("nq7LOrj6Lss", "Bleach OP"),
        Pair("cPnDCeTxuIk", "Re:Zero OP"),
        Pair("mvPDsxkRXBE", "Overlord OP"),
        Pair("1VUeUCCNkH0", "That Time I Got Reincarnated OP"),
        Pair("vr6x2FcHhB4", "Symphogear OP"),
        Pair("0-PGbsN0crY", "Kill la Kill OP"),
        Pair("2GocHBBDLSU", "No Game No Life OP"),
        Pair("sqf7bQjvFGI", "Steins;Gate OP"),
        Pair("eKqSZp5FMZY", "Code Geass OP"),
    )

    private val handler = Handler(Looper.getMainLooper())
    private var currentIndex = -1
    private var webView: WebView? = null
    private var labelView: TextView? = null
    private var nextBtn: TextView? = null
    private var isRunning = false

    private val autoNextRunnable = Runnable { playNext() }

    @SuppressLint("SetJavaScriptEnabled")
    fun setup(wv: WebView, label: TextView, next: TextView) {
        webView = wv
        labelView = label
        nextBtn = next

        // Setup WebView
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = WebViewClient()

        // Tombol next
        next.setOnClickListener { playNext() }

        // Mulai putar video acak pertama
        isRunning = true
        playRandom()
    }

    fun playNext() {
        handler.removeCallbacks(autoNextRunnable)
        playRandom()
    }

    private fun playRandom() {
        val videos = ANIME_VIDEOS
        var nextIndex: Int
        do {
            nextIndex = videos.indices.random()
        } while (nextIndex == currentIndex && videos.size > 1)
        currentIndex = nextIndex

        val (videoId, title) = videos[currentIndex]
        labelView?.text = "♪ $title"

        loadYouTubeEmbed(videoId)

        // Auto-ganti setelah VIDEO_DURATION_MS
        handler.removeCallbacks(autoNextRunnable)
        handler.postDelayed(autoNextRunnable, VIDEO_DURATION_MS)
    }

    private fun loadYouTubeEmbed(videoId: String) {
        val wv = webView ?: return

        // HTML embed YouTube dengan:
        // autoplay=1, mute=1, controls=0, loop=1, rel=0, modestbranding=1
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              * { margin:0; padding:0; background:#000; overflow:hidden; }
              body { width:100%; height:100%; background:#000; }
              iframe { position:absolute; top:0; left:0; width:100%; height:100%;
                       border:none; pointer-events:none; }
            </style>
            </head>
            <body>
            <iframe
              src="https://www.youtube.com/embed/${videoId}?autoplay=1&mute=1&controls=0&loop=1&playlist=${videoId}&rel=0&modestbranding=1&iv_load_policy=3&disablekb=1&playsinline=1"
              allow="autoplay; encrypted-media"
              allowfullscreen>
            </iframe>
            </body>
            </html>
        """.trimIndent()

        wv.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
    }

    fun pause() {
        handler.removeCallbacks(autoNextRunnable)
        webView?.loadUrl("about:blank")
    }

    fun resume() {
        if (isRunning) playRandom()
    }

    fun destroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        webView?.destroy()
        webView = null
    }
}
