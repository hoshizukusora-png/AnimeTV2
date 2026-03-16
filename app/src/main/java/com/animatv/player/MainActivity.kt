package com.animatv.player

import android.annotation.SuppressLint
import android.content.*
import java.text.SimpleDateFormat
import java.util.*
import android.content.pm.ActivityInfo
import android.os.*
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.widget.TextViewCompat
import com.google.android.material.snackbar.Snackbar
import com.animatv.player.adapter.CategoryAdapter
import com.animatv.player.adapter.SidebarAdapter
import com.animatv.player.databinding.ActivityMainBinding
import com.animatv.player.dialog.SearchDialog
import com.animatv.player.dialog.SettingDialog
import com.animatv.player.extension.*
import com.animatv.player.extra.*
import com.animatv.player.extra.LocaleHelper
import com.animatv.player.model.*

open class MainActivity : AppCompatActivity() {
    private var doubleBackToExitPressedOnce = false
    private var isTelevision = UiMode().isTelevision()
    private val preferences = Preferences()
    private val helper = PlaylistHelper()
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CategoryAdapter
    private var sidebarAdapter: SidebarAdapter? = null
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 1000)
        }
    }

    // Category display names (tanpa emoji supaya aman di semua device)
    private val catNames = mapOf(
        "nasional"      to "[TV] NASIONAL",
        "berita"        to "[NWS] BERITA",
        "hiburan"       to "[ENT] HIBURAN",
        "olahraga"      to "[SPT] OLAHRAGA",
        "internasional" to "[INT] INTERNASIONAL",
        "jepang"        to "[JPN] JEPANG",
        "vision+"       to "[VIS] VISION+ DRM",
        "vision"        to "[VIS] VISION+ DRM",
        "indihome"      to "[IND] INDIHOME DRM",
        "custom"        to "[CST] CUSTOM",
        "favorit"       to "\u2605 FAVORIT",
        "favorite"      to "\u2605 FAVORIT"
    )

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when(intent.getStringExtra(MAIN_CALLBACK)) {
                UPDATE_PLAYLIST -> updatePlaylist(false)
                INSERT_FAVORITE -> adapter.insertOrUpdateFavorite()
                REMOVE_FAVORITE -> adapter.removeFavorite()
            }
        }
    }

    companion object {
        const val MAIN_CALLBACK = "MAIN_CALLBACK"
        const val UPDATE_PLAYLIST = "UPDATE_PLAYLIST"
        const val INSERT_FAVORITE = "REFRESH_FAVORITE"
        const val REMOVE_FAVORITE = "REMOVE_FAVORITE"
    }

    @SuppressLint("DefaultLocale")
    override fun attachBaseContext(base: android.content.Context) {
        val lang = LocaleHelper.getLanguageCode(base)
        super.attachBaseContext(LocaleHelper.setLocale(base, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        startClock()

        binding.buttonSearch.setOnClickListener { openSearch() }
        binding.buttonRefresh.setOnClickListener { updatePlaylist(false) }
        binding.buttonSettings.setOnClickListener { openSettings() }
        binding.buttonExit.setOnClickListener { finish() }
        binding.searchHint?.setOnClickListener { openSearch() }

        // Resolution buttons - apply via broadcast to PlayerActivity
        setupResolutionButtons()

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter(MAIN_CALLBACK))

        if (!Playlist.cached.isCategoriesEmpty()) setPlaylistToAdapter(Playlist.cached)
        else showAlertPlaylistError(getString(R.string.null_playlist))
    }

    private fun setLoadingPlaylist(show: Boolean) {
        if (show) {
            binding.loading.startShimmer()
            binding.loading.visibility = View.VISIBLE
        } else {
            binding.loading.stopShimmer()
            binding.loading.visibility = View.GONE
        }
    }

    private fun setupSidebar(playlistSet: Playlist) {
        val cats = playlistSet.categories
        sidebarAdapter = SidebarAdapter(cats) { cat, position ->
            // Tampilkan hanya channel dari kategori yang dipilih di konten tengah
            val key = cat.name?.lowercase()?.trim() ?: ""
            val matchedTitle = catNames.entries.firstOrNull { key.contains(it.key) }?.value
                ?: cat.name?.uppercase() ?: ""
            binding.textCurrentCat?.text = matchedTitle
            // Cari index kategori ini di full list (bukan di sidebar)
            val catIndex = playlistSet.categories.indexOf(cat)
            adapter.showCategory(if (catIndex >= 0) catIndex else position)
            // Scroll konten tengah ke atas saat ganti kategori
            binding.rvCategory.scrollToPosition(0)
        }
        binding.rvSidebar.layoutManager = LinearLayoutManager(this)
        binding.rvSidebar.adapter = sidebarAdapter
        // Default: tampilkan kategori pertama
        adapter.showCategory(0)
    }

    @SuppressLint("SetTextI18n")
    private fun updateStats(playlistSet: Playlist) {
        try {
            val total = playlistSet.categories.sumOf { it.channels?.size ?: 0 }
            val customCount = playlistSet.categories
                .filter { it.name?.lowercase()?.contains("custom") == true }
                .sumOf { it.channels?.size ?: 0 }
            binding.statTotal?.text = total.toString()
            binding.statCustom?.text = customCount.toString()

            val firstCat = playlistSet.categories.firstOrNull()
            if (firstCat != null) {
                val key = firstCat.name?.lowercase()?.trim() ?: ""
                val title = catNames.entries.firstOrNull { key.contains(it.key) }?.value
                    ?: firstCat.name?.uppercase() ?: ""
                binding.textCurrentCat?.text = title
            }
        } catch (e: Exception) { }
    }

    private fun setPlaylistToAdapter(playlistSet: Playlist) {
        if (preferences.sortCategory) playlistSet.sortCategories()
        if (preferences.sortChannel) playlistSet.sortChannels()
        playlistSet.trimChannelWithEmptyStreamUrl()

        val fav = helper.readFavorites().trimNotExistFrom(playlistSet)
        if (preferences.sortFavorite) fav.sort()
        if (fav?.channels?.isNotEmpty() == true)
            playlistSet.insertFavorite(fav.channels)
        else playlistSet.removeFavorite()

        adapter = CategoryAdapter(playlistSet.categories)
        binding.catAdapter = adapter

        Playlist.cached = playlistSet
        helper.writeCache(playlistSet)

        setupSidebar(playlistSet)
        updateStats(playlistSet)
        setLoadingPlaylist(false)
        Toast.makeText(applicationContext, R.string.playlist_updated, Toast.LENGTH_SHORT).show()

        if (preferences.playLastWatched && PlayerActivity.isFirst) {
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra(PlayData.VALUE, preferences.watched)
            this.startActivity(intent)
        }
    }

    private fun updatePlaylist(useCache: Boolean) {
        setLoadingPlaylist(true)
        binding.catAdapter?.clear()
        val playlistSet = Playlist()

        SourcesReader().set(preferences.sources, object : SourcesReader.Result {
            override fun onError(source: String, error: String) {
                val snackbar = Snackbar.make(binding.root, "[${error.uppercase()}] $source", Snackbar.LENGTH_INDEFINITE)
                snackbar.setAction(android.R.string.ok) { snackbar.dismiss() }
                snackbar.show()
            }

            override fun onResponse(playlist: Playlist?) {
                if (playlist != null) playlistSet.mergeWith(playlist)
                else Toast.makeText(applicationContext, R.string.playlist_cant_be_parsed, Toast.LENGTH_SHORT).show()
            }

            override fun onFinish() {
                if (!playlistSet.isCategoriesEmpty()) setPlaylistToAdapter(playlistSet)
                else showAlertPlaylistError(getString(R.string.null_playlist))
            }
        }).process(useCache)
    }

    private fun showAlertPlaylistError(message: String?) {
        val alert = AlertDialog.Builder(this).apply {
            setTitle(R.string.alert_title_playlist_error)
            setMessage(message)
            setCancelable(false)
            setNeutralButton(R.string.settings) { _, _ -> openSettings() }
            setPositiveButton(R.string.dialog_retry) { _, _ -> updatePlaylist(true) }
        }
        val cache = helper.readCache()
        if (cache != null) {
            alert.setNegativeButton(R.string.dialog_cached) { _, _ -> setPlaylistToAdapter(cache) }
        }
        alert.create().show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.setFullScreenFlags()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> openSettings()
            else -> return super.onKeyUp(keyCode, event)
        }
        return true
    }

    override fun onBackPressed() {
        if (isTelevision || doubleBackToExitPressedOnce) {
            super.onBackPressed()
            finish()
            return
        }
        doubleBackToExitPressedOnce = true
        Toast.makeText(this, getString(R.string.press_back_twice_exit_app), Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockRunnable)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    private fun startClock() {
        clockHandler.post(clockRunnable)
    }

    @SuppressLint("DefaultLocale")
    private fun updateClock() {
        val now = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
        binding.textClock?.text = timeFormat.format(now.time)
        binding.textDate?.text = dayFormat.format(now.time)
    }

    private fun openSettings() {
        SettingDialog().show(supportFragmentManager.beginTransaction(), null)
    }

    private fun openSearch() {
        SearchDialog().show(supportFragmentManager.beginTransaction(), null)
    }

    @SuppressLint("DefaultLocale")
    private fun setupResolutionButtons() {
        val btns = listOf(
            binding.btn360, binding.btn720, binding.btn1080, binding.btn4k, binding.btnAuto
        )
        val maxBitrates = listOf(800_000, 2_500_000, 5_000_000, 20_000_000, Int.MAX_VALUE)
        val maxHeights  = listOf(360, 720, 1080, 2160, Int.MAX_VALUE)

        // Restore saved selection (default 720p = index 1)
        val savedIdx = preferences.resolutionIndex
        btns.forEachIndexed { i, btn ->
            if (btn != null)
                TextViewCompat.setTextAppearance(btn,
                    if (i == savedIdx) R.style.ResBtnActive else R.style.ResBtn)
        }

        btns.forEachIndexed { idx, btn ->
            btn?.setOnClickListener {
                preferences.resolutionIndex = idx
                btns.forEachIndexed { i, b ->
                    if (b != null)
                        TextViewCompat.setTextAppearance(b,
                            if (i == idx) R.style.ResBtnActive else R.style.ResBtn)
                }
                // Broadcast to PlayerActivity if running
                LocalBroadcastManager.getInstance(this).sendBroadcast(
                    Intent(PlayerActivity.PLAYER_CALLBACK)
                        .putExtra(PlayerActivity.PLAYER_CALLBACK, PlayerActivity.RETRY_PLAYBACK)
                )
            }
        }
    }
}
