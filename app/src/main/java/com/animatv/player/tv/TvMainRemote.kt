package com.animatv.player.tv

import android.view.KeyEvent

/**
 * ═══════════════════════════════════════════════════════════════════
 *  TV MAIN REMOTE  –  v2 Final
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Hanya menangani hal yang TIDAK ditangani kode lama:
 *  - Fokus awal saat playlist dimuat
 *  - Navigasi topbar (Search/Refresh/Settings/Exit) via DPAD_UP dari sidebar
 *  - DPAD_DOWN dari topbar kembali ke sidebar
 *
 *  Kode lama di dispatchKeyEvent MainActivity tetap menangani:
 *  - DPAD_RIGHT (sidebar → channel grid)
 *  - DPAD_LEFT  (channel grid → sidebar)
 *  - DPAD_UP    (sidebar item 0 → dropdown header)
 *  - DPAD_DOWN  (dropdown header → sidebar)
 *  - ENTER/OK   (performClick)
 *  - BACK       (tutup dropdown / onBackPressed)
 *  - MENU/SEARCH
 *
 *  Dengan demikian TIDAK ADA tumpang tindih.
 * ═══════════════════════════════════════════════════════════════════
 */
class TvMainRemote(private val host: TvRemoteContract.MainHost) {

    /**
     * Dipanggil dari dispatchKeyEvent MainActivity.
     * Return true  = dikonsumsi, stop propagasi
     * Return false = teruskan ke kode lama
     *
     * PENTING: Fungsi ini hanya mengonsumsi event yang benar-benar
     * tidak ditangani kode lama, agar tidak terjadi konflik.
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Hanya tangani ACTION_DOWN
        if (event.action != KeyEvent.ACTION_DOWN) return false

        when (event.keyCode) {

            // DPAD_UP dari topbar buttons → kembali ke sidebar
            // (Kode lama menangani UP dari sidebar ke dropdown,
            //  tapi tidak menangani UP dari topbar ke sidebar)
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (host.isFocusInTopbar()) {
                    host.focusSidebar()
                    return true
                }
                return false
            }

            // DPAD_DOWN dari topbar buttons → turun ke sidebar
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (host.isFocusInTopbar()) {
                    host.focusSidebar()
                    return true
                }
                return false
            }

            // HOME button → fokus kembali ke sidebar
            KeyEvent.KEYCODE_HOME -> {
                host.focusSidebar()
                return true
            }
        }

        return false
    }

    /** Dipanggil setelah playlist dimuat untuk set fokus awal */
    fun onPlaylistReady() {
        if (host.isTvDevice()) {
            host.focusSidebarDelayed()
        }
    }
}
