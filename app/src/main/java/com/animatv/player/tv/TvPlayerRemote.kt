package com.animatv.player.tv

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * ═══════════════════════════════════════════════════════════════════
 *  TV PLAYER REMOTE  –  Sistem Remote TV untuk PlayerActivity
 * ═══════════════════════════════════════════════════════════════════
 *
 *  File ini BERDIRI SENDIRI. Tidak mengubah satu baris pun kode asli.
 *
 *  Cara integrasi (hanya 3 baris di PlayerActivity):
 *
 *    // 1. Deklarasi (di atas onCreate):
 *    private val tvRemote by lazy { TvPlayerRemote(this) }
 *
 *    // 2. Override dispatchKeyEvent (tambahkan di PlayerActivity):
 *    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
 *        val handled = tvRemote.dispatchKeyEvent(event)
 *        return if (handled) true else super.dispatchKeyEvent(event)
 *    }
 *
 *    // 3. Ganti onKeyUp (atau tambahkan di awal onKeyUp yang ada):
 *    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
 *        val handled = tvRemote.onKeyUp(keyCode, event)
 *        return if (handled) true else super.onKeyUp(keyCode, event)
 *    }
 *
 *  Kemudian implementasikan interface TvRemoteContract.PlayerHost
 *  di PlayerActivity (lihat TvPlayerHostImpl.kt).
 * ═══════════════════════════════════════════════════════════════════
 */
class TvPlayerRemote(private val host: TvRemoteContract.PlayerHost) {

    // ── Buffer angka untuk channel jumping (tekan 1,2 → ch12) ─────
    private val numBuffer = StringBuilder()
    private val numHandler = Handler(Looper.getMainLooper())
    private val numRunnable = Runnable {
        val idx = numBuffer.toString().toIntOrNull()
        numBuffer.clear()
        if (idx != null && idx > 0) {
            host.jumpToChannelIndex(idx - 1)  // konversi 1-based ke 0-based
        }
    }

    // ── State internal ─────────────────────────────────────────────
    private var doubleBackArmed = false
    private val doubleBackHandler = Handler(Looper.getMainLooper())

    // ─────────────────────────────────────────────────────────────────
    //  dispatchKeyEvent  –  dipanggil SEBELUM view hierarchy
    //  Return true  = event dikonsumsi di sini, stop propagasi
    //  Return false = teruskan ke super (view hierarchy normal)
    // ─────────────────────────────────────────────────────────────────
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isDown  = event.action == KeyEvent.ACTION_DOWN

        // ── Mini panel terbuka: kuasai semua navigasi ─────────────
        if (host.isMiniPanelOpen()) {
            when (keyCode) {
                // Tutup panel dengan BACK, ESCAPE, atau DPAD_RIGHT
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE,
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isDown) host.closeMiniPanel()
                    return true
                }
                // DPAD UP/DOWN → scroll RecyclerView (biarkan super handle)
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> return false

                // ENTER → pilih item yang difokus (biarkan super handle)
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> return false

                // Semua tombol lain dikonsumsi (tidak bocor ke player)
                else -> return true
            }
        }

        // ── Tombol angka → buffer channel jump ────────────────────
        if (isDown && !host.isPlayerLocked()) {
            val digit = keyCodeToDigit(keyCode)
            if (digit >= 0) {
                handleDigit(digit)
                return true
            }
        }

        // ── Tombol DPAD saat controller tampil ───────────────────
        // Biarkan ExoPlayer navigasi antar tombol controller (false = teruskan)
        // Tapi jangan bocorkan kalau panel terbuka (sudah ditangani di atas)
        if (host.isControllerVisible() && !host.isMiniPanelOpen()) {
            when (keyCode) {
                // Biarkan ExoPlayer handle navigasi D-pad di controller
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> return false
            }
        }

        return false  // default: teruskan ke super
    }

    // ─────────────────────────────────────────────────────────────────
    //  onKeyUp  –  dipanggil setelah dispatchKeyEvent
    //  Return true  = event dikonsumsi
    //  Return false = teruskan ke super
    // ─────────────────────────────────────────────────────────────────
    fun onKeyUp(keyCode: Int, @Suppress("UNUSED_PARAMETER") event: KeyEvent): Boolean {

        // ── Jika terkunci: hanya izinkan tombol buka kunci ────────
        if (host.isPlayerLocked()) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                host.showLockOverlay()
            }
            return true  // konsumsi semua, jangan sampai bocor
        }

        // ── Mini panel: sudah ditangani di dispatchKeyEvent ───────
        // (tinggal handle BACK di sini jika panel masih buka)
        if (host.isMiniPanelOpen()) {
            return true
        }

        // ── Tombol media khusus (selalu aktif) ────────────────────
        when (keyCode) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS -> {
                host.openTrackSelector()
                return true
            }
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                host.switchToPrevCategory()
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                host.switchToNextCategory()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { host.switchToPrevChannel(); return true }
            KeyEvent.KEYCODE_MEDIA_NEXT     -> { host.switchToNextChannel(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY     -> { host.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE    -> { host.pause(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { host.togglePlayPause(); return true }
            KeyEvent.KEYCODE_MEDIA_STOP     -> { host.pause(); return true }
        }

        // ── Seek (hanya untuk konten non-live) ────────────────────
        if (!host.isLiveContent()) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    if (!host.isControllerVisible()) host.showController()
                    if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) host.seekBackward()
                    else host.seekForward()
                    return true
                }
            }
        }

        // ── DPAD_CENTER / ENTER ───────────────────────────────────
        //   Controller tersembunyi → tampilkan controller
        //   Controller tampil → biarkan ExoPlayer navigasi (sudah di dispatchKeyEvent)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (!host.isControllerVisible()) {
                    host.showController()
                    return true
                }
                // Controller tampil → teruskan ke super (ExoPlayer handle)
                return false
            }
        }

        // ── BACK ──────────────────────────────────────────────────
        when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> {
                // Prioritas 1: tutup controller
                if (host.isControllerVisible()) {
                    host.hideController()
                    return true
                }
                // Prioritas 2: keluar player
                return handleBackExit()
            }
        }

        // ── Controller tampil: DPAD ditangani ExoPlayer ──────────
        if (host.isControllerVisible()) {
            return false  // biarkan super (ExoPlayer navigasi tombol controller)
        }

        // ── Controller tersembunyi: DPAD = ganti channel/kategori ─
        val reverse = host.isReverseNavigation()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (reverse) host.switchToNextChannel() else host.switchToPrevChannel()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (reverse) host.switchToPrevChannel() else host.switchToNextChannel()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (reverse) host.switchToNextCategory() else host.switchToPrevCategory()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (reverse) host.switchToPrevCategory() else host.switchToNextCategory()
                return true
            }
        }

        return false  // tidak dikonsumsi
    }

    // ─────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────

    /** Konversi keyCode angka ke digit 0-9, return -1 jika bukan angka */
    private fun keyCodeToDigit(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> 0
        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6
        KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 7
        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 8
        KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 9
        else -> -1
    }

    private fun handleDigit(digit: Int) {
        numHandler.removeCallbacks(numRunnable)
        numBuffer.append(digit)
        host.showToast("CH: $numBuffer")
        numHandler.postDelayed(numRunnable, 1500L)
    }

    private fun handleBackExit(): Boolean {
        if (host.isTvDevice() || doubleBackArmed) {
            host.exitPlayer()
            return true
        }
        doubleBackArmed = true
        host.showToast("Tekan BACK lagi untuk keluar")
        doubleBackHandler.removeCallbacksAndMessages(null)
        doubleBackHandler.postDelayed({ doubleBackArmed = false }, 2000L)
        return true
    }

    /** Bersihkan handler saat activity destroy */
    fun onDestroy() {
        numHandler.removeCallbacksAndMessages(null)
        doubleBackHandler.removeCallbacksAndMessages(null)
    }
}
