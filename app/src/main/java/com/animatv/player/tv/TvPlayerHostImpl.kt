package com.animatv.player.tv

import android.widget.Toast
import com.animatv.player.PlayerActivity
import com.animatv.player.extra.UiMode

/**
 * ═══════════════════════════════════════════════════════════════════
 *  TV PLAYER HOST IMPLEMENTATION  –  Final
 * ═══════════════════════════════════════════════════════════════════
 *  Jembatan antara TvPlayerRemote dan private member PlayerActivity.
 *  Lambda Bridge Pattern: tidak ada reflection, tidak ubah kode lama.
 * ═══════════════════════════════════════════════════════════════════
 */
class TvPlayerHostImpl private constructor(
    private val activity:               PlayerActivity,
    private val getIsControllerVisible: () -> Boolean,
    private val getIsMiniPanelVisible:  () -> Boolean,
    private val getIsLocked:            () -> Boolean,
    private val getIsLive:              () -> Boolean,
    private val getIsPlaying:           () -> Boolean,
    private val getReverseNav:          () -> Boolean,
    private val doShowController:       () -> Unit,
    private val doHideController:       () -> Unit,
    private val doSwitchChannel:        (Int) -> Unit,
    private val doToggleMiniPanel:      () -> Unit,
    private val doOpenTrackSelector:    () -> Unit,
    private val doShowBtnLockOverlay:   () -> Unit,
    private val doJumpToChannel:        (Int) -> Unit,
    private val doPlay:                 () -> Unit,
    private val doPause:                () -> Unit,
    private val doSeekBack:             () -> Unit,
    private val doSeekForward:          () -> Unit,
) : TvRemoteContract.PlayerHost {

    companion object {
        private const val CH_NEXT  = 0
        private const val CH_PREV  = 1
        private const val CAT_UP   = 2
        private const val CAT_DOWN = 3

        fun create(
            activity:            PlayerActivity,
            isControllerVisible: () -> Boolean,
            isMiniPanelVisible:  () -> Boolean,
            isLocked:            () -> Boolean,
            isLive:              () -> Boolean,
            isPlaying:           () -> Boolean,
            reverseNav:          () -> Boolean,
            showController:      () -> Unit,
            hideController:      () -> Unit,
            switchChannel:       (Int) -> Unit,
            toggleMiniPanel:     () -> Unit,
            openTrackSelector:   () -> Unit,
            showBtnLockOverlay:  () -> Unit,
            jumpToChannel:       (Int) -> Unit,
            play:                () -> Unit,
            pause:               () -> Unit,
            seekBack:            () -> Unit,
            seekForward:         () -> Unit,
        ): TvPlayerHostImpl = TvPlayerHostImpl(
            activity               = activity,
            getIsControllerVisible = isControllerVisible,
            getIsMiniPanelVisible  = isMiniPanelVisible,
            getIsLocked            = isLocked,
            getIsLive              = isLive,
            getIsPlaying           = isPlaying,
            getReverseNav          = reverseNav,
            doShowController       = showController,
            doHideController       = hideController,
            doSwitchChannel        = switchChannel,
            doToggleMiniPanel      = toggleMiniPanel,
            doOpenTrackSelector    = openTrackSelector,
            doShowBtnLockOverlay   = showBtnLockOverlay,
            doJumpToChannel        = jumpToChannel,
            doPlay                 = play,
            doPause                = pause,
            doSeekBack             = seekBack,
            doSeekForward          = seekForward,
        )
    }

    override fun isControllerVisible(): Boolean = getIsControllerVisible()
    override fun isMiniPanelOpen():     Boolean = getIsMiniPanelVisible()
    override fun isPlayerLocked():      Boolean = getIsLocked()
    override fun isLiveContent():       Boolean = getIsLive()
    override fun isPlaying():           Boolean = getIsPlaying()
    override fun isTvDevice():          Boolean = UiMode().isTelevision()
    override fun isReverseNavigation(): Boolean = getReverseNav()

    override fun showController()    { doShowController() }
    override fun hideController()    { doHideController() }
    override fun play()              { doPlay() }
    override fun pause()             { doPause() }
    override fun seekBackward()      { doSeekBack() }
    override fun seekForward()       { doSeekForward() }
    override fun togglePlayPause()   { if (getIsPlaying()) doPause() else doPlay() }

    override fun switchToPrevChannel()  { doSwitchChannel(CH_PREV) }
    override fun switchToNextChannel()  { doSwitchChannel(CH_NEXT) }
    override fun switchToPrevCategory() { doSwitchChannel(CAT_UP) }
    override fun switchToNextCategory() { doSwitchChannel(CAT_DOWN) }

    override fun openMiniPanel()     { if (!getIsMiniPanelVisible()) doToggleMiniPanel() }
    override fun closeMiniPanel()    { if (getIsMiniPanelVisible())  doToggleMiniPanel() }
    override fun openTrackSelector() { doOpenTrackSelector() }
    override fun showLockOverlay()   { doShowBtnLockOverlay() }
    override fun exitPlayer()        { activity.finish() }
    override fun jumpToChannelIndex(index: Int) { doJumpToChannel(index) }
    override fun showToast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}
