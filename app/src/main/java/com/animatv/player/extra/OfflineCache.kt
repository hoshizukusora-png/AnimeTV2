package com.animatv.player.extra

import android.content.Context
import com.animatv.player.App
import java.io.File

/**
 * OfflineCache - simpan playlist untuk diputar offline
 *
 * Cara kerja:
 * 1. Saat online: download playlist -> simpan ke file lokal
 * 2. Saat offline: baca dari file lokal yang tersimpan
 * 3. Cache tidak pernah dihapus otomatis - update hanya saat online
 */
object OfflineCache {

    private const val CACHE_DIR = "playlist_cache"
    private const val MAIN_CACHE = "channels_main.json"
    private const val CACHE_INFO = "cache_info.txt"

    private val cacheDir: File
        get() {
            val dir = File(App.context.filesDir, CACHE_DIR)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    // Simpan playlist ke cache
    fun savePlaylist(content: String, sourceUrl: String) {
        try {
            File(cacheDir, MAIN_CACHE).writeText(content)
            // Simpan info kapan terakhir update
            val info = "url=$sourceUrl\ntime=${System.currentTimeMillis()}\nsize=${content.length}"
            File(cacheDir, CACHE_INFO).writeText(info)
            android.util.Log.d("OfflineCache", "Playlist cached: ${content.length} bytes")
        } catch (e: Exception) {
            android.util.Log.e("OfflineCache", "Save failed: ${e.message}")
        }
    }

    // Baca playlist dari cache
    fun loadPlaylist(): String? {
        return try {
            val file = File(cacheDir, MAIN_CACHE)
            if (file.exists() && file.length() > 100) {
                file.readText()
            } else null
        } catch (e: Exception) { null }
    }

    // Cek apakah ada cache
    fun hasCache(): Boolean {
        return try {
            val file = File(cacheDir, MAIN_CACHE)
            file.exists() && file.length() > 100
        } catch (e: Exception) { false }
    }

    // Info cache terakhir
    fun getCacheInfo(): String {
        return try {
            val infoFile = File(cacheDir, CACHE_INFO)
            if (!infoFile.exists()) return "Belum ada cache"
            val lines = infoFile.readLines()
            val timeMs = lines.find { it.startsWith("time=") }
                ?.removePrefix("time=")?.toLongOrNull() ?: 0L
            val size = lines.find { it.startsWith("size=") }
                ?.removePrefix("size=") ?: "?"
            val date = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm")
                .format(java.util.Date(timeMs))
            "Cache: $date ($size bytes)"
        } catch (e: Exception) { "Info tidak tersedia" }
    }

    // Hapus cache
    fun clearCache() {
        try {
            File(cacheDir, MAIN_CACHE).delete()
            File(cacheDir, CACHE_INFO).delete()
        } catch (e: Exception) {}
    }
}
