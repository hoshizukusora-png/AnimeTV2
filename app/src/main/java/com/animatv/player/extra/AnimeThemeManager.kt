package com.animatv.player.extra

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.animatv.player.R
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

/**
 * AnimeThemeManager - mengelola semua fitur tema anime:
 * 1. Background Rotator - ganti background tiap X detik
 * 2. Quote of the Day - quote anime di halaman utama
 * 3. Sakura Effect - animasi kelopak sakura
 */
object AnimeThemeManager {

    // ===== DATA =====
    data class AnimeQuote(
        val quote: String,
        val anime: String,
        val character: String
    )

    // Background drawables untuk rotasi
    private val bgDrawables = listOf(
        R.drawable.bg_anime_1,
        R.drawable.bg_anime_2,
        R.drawable.bg_main
    )

    private var bgIndex = 0
    private val bgHandler = Handler(Looper.getMainLooper())
    private var bgRunnable: Runnable? = null

    // ===== 1. ANIME BACKGROUND ROTATOR =====

    fun startBackgroundRotator(
        context: Context,
        imageView: ImageView,
        intervalSeconds: Int = 30
    ) {
        stopBackgroundRotator()
        bgRunnable = object : Runnable {
            override fun run() {
                bgIndex = (bgIndex + 1) % bgDrawables.size
                imageView.animate()
                    .alpha(0f)
                    .setDuration(800)
                    .withEndAction {
                        val activity = context as? Activity
                        if (activity != null && !activity.isDestroyed && !activity.isFinishing) {
                            Glide.with(activity)
                                .load(bgDrawables[bgIndex])
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .into(imageView)
                            imageView.animate().alpha(0.18f).setDuration(800).start()
                        }
                    }.start()
                bgHandler.postDelayed(this, intervalSeconds * 1000L)
            }
        }
        bgHandler.postDelayed(bgRunnable!!, intervalSeconds * 1000L)
    }

    fun stopBackgroundRotator() {
        bgRunnable?.let { bgHandler.removeCallbacks(it) }
        bgRunnable = null
    }

    // ===== 2. QUOTE OF THE DAY =====

    fun getQuoteOfDay(context: Context): AnimeQuote? {
        return try {
            val json = context.assets.open("anime_quotes.json")
                .bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<AnimeQuote>>() {}.type
            val quotes: List<AnimeQuote> = Gson().fromJson(json, type)
            if (quotes.isEmpty()) return null
            // Pilih quote berdasarkan hari dalam tahun supaya setiap hari berbeda
            val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            quotes[dayOfYear % quotes.size]
        } catch (e: Exception) {
            null
        }
    }

    fun getRandomQuote(context: Context): AnimeQuote? {
        return try {
            val json = context.assets.open("anime_quotes.json")
                .bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<AnimeQuote>>() {}.type
            val quotes: List<AnimeQuote> = Gson().fromJson(json, type)
            if (quotes.isEmpty()) null
            else quotes[Random().nextInt(quotes.size)]
        } catch (e: Exception) {
            null
        }
    }

    // ===== 3. SAKURA EFFECT =====

    private val sakuraHandler = Handler(Looper.getMainLooper())
    private val sakuraRunnables = mutableListOf<Runnable>()
    private var isSakuraRunning = false

    fun startSakuraEffect(container: ViewGroup) {
        if (isSakuraRunning) return
        isSakuraRunning = true
        spawnSakuraPetal(container)
    }

    private fun spawnSakuraPetal(container: ViewGroup) {
        if (!isSakuraRunning) return

        val context = container.context
        val petal = View(context).apply {
            val size = (8 + Random().nextInt(12)).dpToPx(context)
            layoutParams = ViewGroup.LayoutParams(size, size)
            background = createSakuraDrawable(context)
            alpha = 0.6f + Random().nextFloat() * 0.4f
        }

        val screenWidth = container.width.takeIf { it > 0 } ?: 800
        val startX = Random().nextInt(screenWidth).toFloat()

        petal.x = startX
        petal.y = -50f

        container.addView(petal)

        val duration = (4000 + Random().nextInt(3000)).toLong()
        val endY = container.height.toFloat() + 100f
        val swayAmount = (50 + Random().nextInt(80)).toFloat()

        petal.animate()
            .translationY(endY)
            .translationX(startX + if (Random().nextBoolean()) swayAmount else -swayAmount)
            .rotation((180 + Random().nextInt(360)).toFloat())
            .setDuration(duration)
            .withEndAction {
                container.removeView(petal)
            }
            .start()

        // Spawn petal berikutnya
        val delay = (800 + Random().nextInt(1200)).toLong()
        val runnable = Runnable { spawnSakuraPetal(container) }
        sakuraRunnables.add(runnable)
        sakuraHandler.postDelayed(runnable, delay)
    }

    private fun createSakuraDrawable(context: Context): android.graphics.drawable.GradientDrawable {
        val drawable = android.graphics.drawable.GradientDrawable()
        // Bentuk oval = kelopak sakura
        drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
        // Warna pink/white sakura
        val colors = listOf(
            0xFFFFB7C5.toInt(), // pink muda
            0xFFFFD1DC.toInt(), // pink sangat muda
            0xFFFF91A4.toInt(), // pink medium
            0xFFFFFFFF.toInt()  // putih
        )
        drawable.setColor(colors[Random().nextInt(colors.size)])
        return drawable
    }

    fun stopSakuraEffect(container: ViewGroup) {
        isSakuraRunning = false
        sakuraRunnables.forEach { sakuraHandler.removeCallbacks(it) }
        sakuraRunnables.clear()
        // Hapus semua petal yang ada
        val toRemove = mutableListOf<View>()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child.tag == "sakura_petal") toRemove.add(child)
        }
        toRemove.forEach { container.removeView(it) }
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
