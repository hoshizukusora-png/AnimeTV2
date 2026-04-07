package com.animatv.player.tv

/**
 * ═══════════════════════════════════════════════════════════════════
 *  TV REMOTE CONTRACT
 *  File ini hanya berisi interface/abstraksi.
 *  Tidak menyentuh kode asli sama sekali.
 *
 *  Cara kerja:
 *    PlayerActivity  ──implements──▶  TvRemoteContract.PlayerHost
 *    MainActivity    ──implements──▶  TvRemoteContract.MainHost
 *
 *    TvPlayerRemote  ──calls──▶  PlayerHost   (tidak perlu tahu detail PlayerActivity)
 *    TvMainRemote    ──calls──▶  MainHost     (tidak perlu tahu detail MainActivity)
 * ═══════════════════════════════════════════════════════════════════
 */
object TvRemoteContract {

    /** Kontrak yang harus dipenuhi PlayerActivity */
    interface PlayerHost {
        // ── State queries ──────────────────────────────────────────
        fun isControllerVisible(): Boolean
        fun isMiniPanelOpen(): Boolean
        fun isPlayerLocked(): Boolean
        fun isLiveContent(): Boolean
        fun isPlaying(): Boolean
        fun isTvDevice(): Boolean
        fun isReverseNavigation(): Boolean

        // ── Actions ────────────────────────────────────────────────
        fun showController()
        fun hideController()
        fun togglePlayPause()
        fun play()
        fun pause()
        fun seekBackward()
        fun seekForward()

        fun switchToPrevChannel()
        fun switchToNextChannel()
        fun switchToPrevCategory()
        fun switchToNextCategory()
        fun jumpToChannelIndex(index: Int)   // 0-based

        fun openMiniPanel()
        fun closeMiniPanel()

        fun openTrackSelector()
        fun showLockOverlay()
        fun exitPlayer()
        fun showToast(message: String)
    }

    /** Kontrak yang harus dipenuhi MainActivity */
    interface MainHost {
        fun isTvDevice(): Boolean
        fun isDropdownMenuOpen(): Boolean
        fun isFocusInSidebar(): Boolean

        fun openDropdown()
        fun closeDropdown()
        fun focusSidebar()
        fun focusChannelGrid()
        fun openSearch()
        fun openSettings()
        fun exitApp()
    }
}
