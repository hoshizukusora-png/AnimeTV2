package com.animatv.player.tv

import android.view.KeyEvent

/**
 * ═══════════════════════════════════════════════════════════════════
 *  TV MAIN REMOTE  –  Sistem Remote TV untuk MainActivity
 * ═══════════════════════════════════════════════════════════════════
 *
 *  File ini BERDIRI SENDIRI. Tidak mengubah satu baris pun kode asli.
 *
 *  Cara integrasi (hanya 2 baris tambahan di MainActivity):
 *
 *    // 1. Deklarasi (di atas onCreate, setelah field lain):
 *    private val tvMainRemote by lazy { TvMainRemote(createTvMainHost()) }
 *
 *    // 2. Di dispatchKeyEvent yang SUDAH ADA, tambah di awal fungsi:
 *    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
 *        val handled = tvMainRemote.dispatchKeyEvent(event)
 *        if (handled) return true
 *        // ... sisa kode lama tidak berubah ...
 *    }
 *
 *    // 3. Buat helper private fun di MainActivity:
 *    private fun createTvMainHost(): TvRemoteContract.MainHost = ...
 *    (lihat komentar di bawah)
 * ═══════════════════════════════════════════════════════════════════
 */
class TvMainRemote(private val host: TvRemoteContract.MainHost) {

    /**
     * Dipanggil dari dispatchKeyEvent MainActivity.
     * Return true  = event dikonsumsi, jangan teruskan
     * Return false = teruskan ke kode lama
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val keyCode = event.keyCode

        when (keyCode) {
            // ── Search & Settings ──────────────────────────────────
            KeyEvent.KEYCODE_SEARCH -> {
                host.openSearch()
                return true
            }
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS -> {
                host.openSettings()
                return true
            }

            // ── BACK: tutup dropdown dulu ──────────────────────────
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> {
                if (host.isDropdownMenuOpen()) {
                    host.closeDropdown()
                    return true
                }
                // Teruskan ke kode lama (onBackPressed)
                return false
            }

            // ── DPAD_RIGHT dari sidebar → pindah ke channel grid ──
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (host.isDropdownMenuOpen()) return true  // block saat dropdown
                if (host.isFocusInSidebar()) {
                    host.focusChannelGrid()
                    return true
                }
                return false
            }

            // ── DPAD_LEFT dari channel grid → kembali ke sidebar ──
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (host.isDropdownMenuOpen()) {
                    host.closeDropdown()
                    return true
                }
                if (!host.isFocusInSidebar()) {
                    host.focusSidebar()
                    return true
                }
                return false
            }
        }

        return false
    }
}
