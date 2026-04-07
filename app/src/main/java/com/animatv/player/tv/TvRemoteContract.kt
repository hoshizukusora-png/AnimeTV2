package com.animatv.player.tv

/**
 * ═══════════════════════════════════════════════════════════════════
 *  TV REMOTE CONTRACT  –  v2 Final
 * ═══════════════════════════════════════════════════════════════════
 */
object TvRemoteContract {

    /** Kontrak yang harus dipenuhi PlayerActivity */
    interface PlayerHost {
        fun isControllerVisible(): Boolean
        fun isMiniPanelOpen():     Boolean
        fun isPlayerLocked():      Boolean
        fun isLiveContent():       Boolean
        fun isPlaying():           Boolean
        fun isTvDevice():          Boolean
        fun isReverseNavigation(): Boolean

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
        fun jumpToChannelIndex(index: Int)

        fun openMiniPanel()
        fun closeMiniPanel()
        fun openTrackSelector()
        fun showLockOverlay()
        fun exitPlayer()
        fun showToast(message: String)
    }

    /** Kontrak yang harus dipenuhi MainActivity */
    interface MainHost {
        fun isTvDevice():          Boolean
        fun isDropdownMenuOpen():  Boolean
        fun isFocusInSidebar():    Boolean
        fun isFocusInTopbar():     Boolean   // ← baru: fokus di tombol Search/Refresh/Settings/Exit

        fun openDropdown()
        fun closeDropdown()
        fun focusSidebar()
        fun focusSidebarDelayed()  // ← baru: fokus ke sidebar + scroll ke child pertama dengan post{}
        fun focusChannelGrid()
        fun focusTopbar()          // ← baru: fokus ke tombol Search di topbar
        fun openSearch()
        fun openSettings()
        fun exitApp()
    }
}
