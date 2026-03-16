package com.animatv.player

import android.app.AlertDialog
import android.content.*
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.MediaDrm
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.upstream.DefaultAllocator
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.animatv.player.databinding.ActivityPlayerBinding
import com.animatv.player.databinding.CustomControlBinding
import com.animatv.player.dialog.TrackSelectionDialog
import com.animatv.player.extension.*
import com.animatv.player.extra.*
import com.animatv.player.extra.LocaleHelper
import com.animatv.player.model.Category
import com.animatv.player.model.Channel
import com.animatv.player.model.PlayData
import com.animatv.player.model.Playlist
import com.google.android.exoplayer2.util.MimeTypes
import java.net.URLDecoder
import java.util.*

class PlayerActivity : AppCompatActivity() {
    private var doubleBackToExitPressedOnce = false
    private var isTelevision = UiMode().isTelevision()
    private val preferences = Preferences()
    private val network = Network()
    private var category: Category? = null
    private var current: Channel? = null
    private var player: SimpleExoPlayer? = null
    private lateinit var mediaItem: MediaItem
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var bindingRoot: ActivityPlayerBinding
    private lateinit var bindingControl: CustomControlBinding
    private var handlerInfo: Handler? = null
    private var errorCounter = 0
    private var isLocked = false

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when(intent.getStringExtra(PLAYER_CALLBACK)) {
                RETRY_PLAYBACK -> retryPlayback(true)
                CLOSE_PLAYER -> finish()
            }
        }
    }

    companion object {
        var isFirst = true
        var isPipMode = false
        const val PLAYER_CALLBACK = "PLAYER_CALLBACK"
        const val RETRY_PLAYBACK = "RETRY_PLAYBACK"
        const val CLOSE_PLAYER = "CLOSE_PLAYER"
        private const val CHANNEL_NEXT = 0
        private const val CHANNEL_PREVIOUS = 1
        private const val CATEGORY_UP = 2
        private const val CATEGORY_DOWN = 3
    }

    override fun attachBaseContext(base: android.content.Context) {
        val lang = LocaleHelper.getLanguageCode(base)
        super.attachBaseContext(LocaleHelper.setLocale(base, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        bindingRoot = ActivityPlayerBinding.inflate(layoutInflater)
        bindingControl = CustomControlBinding.bind(bindingRoot.root.findViewById(R.id.custom_control))
        setContentView(bindingRoot.root)

        // set this is not first time
        isFirst = false

        // verify playlist
        if (Playlist.cached.isCategoriesEmpty()) {
            Log.e("PLAYER", getString(R.string.player_no_playlist))
            Toast.makeText(this, R.string.player_no_playlist, Toast.LENGTH_SHORT).show()
            this.finish()
            return
        }

        // get categories & channel to play
        try {
            val parcel: PlayData? = intent.getParcelableExtra(PlayData.VALUE)
            category = parcel.let { Playlist.cached.categories[it?.catId as Int] }
            current = parcel.let { category?.channels?.get(it?.chId as Int) }
        }
        catch (e: Exception) {
            Log.e("PLAYER", getString(R.string.player_playdata_error))
            Toast.makeText(this, R.string.player_playdata_error, Toast.LENGTH_SHORT).show()
            this.finish()
            return
        }

        // verify
        if (category == null || current == null) {
            Log.e("PLAYER", getString(R.string.player_no_channel))
            Toast.makeText(this, R.string.player_no_channel, Toast.LENGTH_SHORT).show()
            this.finish()
            return
        }

        // set listener
        bindingListener()

        // play the channel
        playChannel()

        // local broadcast receiver to update playlist
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter(PLAYER_CALLBACK))
    }

    private fun bindingListener() {
        bindingRoot.playerView.apply {
            setOnTouchListener(object : OnSwipeTouchListener() {
                override fun onSwipeDown() { switchChannel(CATEGORY_UP) }
                override fun onSwipeUp() { switchChannel(CATEGORY_DOWN) }
                override fun onSwipeLeft() { switchChannel(CHANNEL_NEXT) }
                override fun onSwipeRight() { switchChannel(CHANNEL_PREVIOUS) }
            })
            setControllerVisibilityListener {
                setChannelInformation (it == View.VISIBLE)
            }
        }
        bindingControl.trackSelection.setOnClickListener { showTrackSelector() }
        bindingControl.buttonExit.apply {
            visibility = if (isTelevision) View.GONE else View.VISIBLE
            setOnClickListener { finish() }
        }
        bindingControl.buttonPrevious.setOnClickListener { switchChannel(CHANNEL_PREVIOUS) }
        bindingControl.buttonRewind.setOnClickListener { player?.seekBack() }
        bindingControl.buttonForward.setOnClickListener { player?.seekForward() }
        bindingControl.buttonNext.setOnClickListener { switchChannel(CHANNEL_NEXT) }
        bindingControl.screenMode.setOnClickListener { showScreenMenu(it) }
        bindingControl.trackSelection.setOnClickListener { showTrackSelector() }
        bindingControl.buttonLock.apply {
            visibility = if (isTelevision) View.GONE else View.VISIBLE
            setOnClickListener {
                if (!isLocked) {
                    (it as ImageButton).setImageResource(R.drawable.ic_lock)
                    lockControl(true)
                }
            }
            setOnLongClickListener {
                val resId = if (isLocked) R.drawable.ic_lock_open else R.drawable.ic_lock
                (it as ImageButton).setImageResource(resId)
                lockControl(!isLocked); true
            }
        }
    }

    private fun setChannelInformation(visible: Boolean) {
        if (isLocked) return
        bindingRoot.layoutInfo.visibility =
            if (visible && !isPipMode) View.VISIBLE else View.INVISIBLE

        if (isPipMode) return
        if (visible == bindingRoot.playerView.isControllerVisible) return
        if (visible) bindingRoot.playerView.clearFocus()
        else return

        if (handlerInfo == null)
            handlerInfo = Handler(Looper.getMainLooper())

        handlerInfo?.removeCallbacksAndMessages(null)
        handlerInfo?.postDelayed({
                if (bindingRoot.playerView.isControllerVisible) return@postDelayed
                bindingRoot.layoutInfo.visibility = View.INVISIBLE
            },
            bindingRoot.playerView.controllerShowTimeoutMs.toLong()
        )
    }

    private fun lockControl(setLocked: Boolean) {
        isLocked = setLocked
        val visibility = if (setLocked) View.INVISIBLE else View.VISIBLE
        bindingRoot.layoutInfo.visibility = visibility
        bindingControl.buttonExit.visibility = visibility
        bindingControl.layoutControl.visibility = visibility
        bindingControl.screenMode.visibility = visibility
        bindingControl.trackSelection.visibility = visibility
        switchLiveOrVideo()
    }

    private fun switchLiveOrVideo() { switchLiveOrVideo(false) }
    private fun switchLiveOrVideo(reset: Boolean) {
        var visibility = when {
            reset -> View.GONE
            isLocked -> View.INVISIBLE
            player?.isCurrentWindowLive == true -> View.GONE
            else -> View.VISIBLE
        }
        bindingControl.layoutSeekbar.visibility = visibility
        bindingControl.spacerControl.visibility = visibility
        // override visibility if not seekable
        if (player?.isCurrentWindowSeekable == false) visibility = View.GONE
        bindingControl.buttonRewind.visibility = visibility
        bindingControl.buttonForward.visibility = visibility
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun isDrmWidevineSupported(): Boolean {
        if (MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID)) return true
        AlertDialog.Builder(this).apply {
            setTitle(R.string.player_playback_error)
            setMessage(R.string.device_not_support_widevine)
            setCancelable(false)
            setPositiveButton(getString(R.string.btn_next_channel)) { _,_ -> switchChannel(CHANNEL_NEXT) }
            setNegativeButton(R.string.btn_close) { _,_ -> finish() }
            create()
            show()
        }
        return false
    }

    private fun playChannel() {
        // reset view
        switchLiveOrVideo(true)

        // set category & channel name
        bindingRoot.categoryName.text = category?.name?.trim()
        bindingRoot.channelName.text = current?.name?.trim()

        // split streamurl with referer, user-agent
        var streamUrl = URLDecoder.decode(current?.streamUrl, "utf-8")
        var userAgent = streamUrl.findPattern(".*user-agent=(.+?)(\\|.*)?")
        val referer = streamUrl.findPattern(".*referer=(.+?)(\\|.*)?")

        // clean streamurl
        streamUrl = streamUrl.findPattern("(.+?)(\\|.*)?") ?: streamUrl

        // if null set User-Agent with existing resources
        if (userAgent == null) {
            val userAgents = listOf(*resources.getStringArray(R.array.user_agent))
            userAgent = userAgents.firstOrNull {
                current?.streamUrl?.contains(
                    it.substring(0, it.indexOf("/")).lowercase(Locale.getDefault())
                ) == true
            }
            if (userAgent.isNullOrEmpty()) {
                userAgent = userAgents[Random().nextInt(userAgents.size)]
            }
        }

        val drmLicense = Playlist.cached.drmLicenses.firstOrNull {
            current?.drmName?.equals(it.name) == true
        }?.url

        // Deteksi MimeType dari URL supaya ExoPlayer tahu format DASH vs HLS
        val mimeType = when {
            streamUrl.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            streamUrl.contains("/dash", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            streamUrl.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            streamUrl.contains("playlist.m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            streamUrl.contains("master.m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            streamUrl.contains("index.m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            else -> null
        }

        // HTTP factory dengan User-Agent dan Referer
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(userAgent)
        if (referer != null) httpDataSourceFactory.setDefaultRequestProperties(mapOf(Pair("referer", referer)))
        val dataSourceFactory = DefaultDataSourceFactory(this, httpDataSourceFactory)

        // Build DrmSessionManager dan MediaItem sesuai tipe DRM
        val isClearKey = current?.drmName?.startsWith("clearkey_") == true
        val isWidevine = current?.drmName?.startsWith("widevine_") == true
        val hasDrm = !current?.drmName.isNullOrBlank() && !drmLicense.isNullOrBlank()

        var drmSessionManager: com.google.android.exoplayer2.drm.DrmSessionManager =
            com.google.android.exoplayer2.drm.DrmSessionManager.DRM_UNSUPPORTED

        if (hasDrm && isClearKey) {
            // ClearKey: build JSON response langsung, pakai LocalMediaDrmCallback
            // Ini cara yang benar — tidak butuh network request untuk license
            try {
                fun hexToBytes(hex: String): ByteArray {
                    val len = hex.length
                    val data = ByteArray(len / 2)
                    for (i in 0 until len / 2)
                        data[i] = ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
                    return data
                }
                val pairs = drmLicense!!.split(",")
                val keysJson = StringBuilder("{\"keys\":[")
                pairs.forEachIndexed { i, pair ->
                    val kv = pair.trim().split(":")
                    if (kv.size == 2) {
                        val kidB64 = android.util.Base64.encodeToString(
                            hexToBytes(kv[0].trim()),
                            android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                        val keyB64 = android.util.Base64.encodeToString(
                            hexToBytes(kv[1].trim()),
                            android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                        if (i > 0) keysJson.append(",")
                        keysJson.append("{\"kty\":\"oct\",\"kid\":\"$kidB64\",\"k\":\"$keyB64\"}")
                    }
                }
                keysJson.append("],\"type\":\"temporary\"}")
                val licenseBytes = keysJson.toString().toByteArray(Charsets.UTF_8)
                // LocalMediaDrmCallback: langsung inject license JSON tanpa network
                val drmCallback = com.google.android.exoplayer2.drm.LocalMediaDrmCallback(licenseBytes)
                drmSessionManager = com.google.android.exoplayer2.drm.DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, com.google.android.exoplayer2.drm.FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(false)
                    .build(drmCallback)
                Log.d("DRM_DEBUG", "ClearKey DrmSessionManager built OK")
            } catch (e: Exception) {
                Log.e("DRM_DEBUG", "ClearKey build error: ${e.message}")
            }
        } else if (hasDrm && isWidevine) {
            if (!isDrmWidevineSupported()) return
            // Widevine: pakai HttpMediaDrmCallback dengan license server URL
            val drmCallback = com.google.android.exoplayer2.drm.HttpMediaDrmCallback(
                drmLicense, httpDataSourceFactory)
            drmSessionManager = com.google.android.exoplayer2.drm.DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, com.google.android.exoplayer2.drm.FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(true)
                .build(drmCallback)
            Log.d("DRM_DEBUG", "Widevine DrmSessionManager built, licUrl=$drmLicense")
        } else if (!current?.drmName.isNullOrBlank() && drmLicense == null) {
            Log.e("DRM_DEBUG", "DRM channel but license NOT FOUND!")
            Toast.makeText(applicationContext, "DRM license tidak ditemukan, coba refresh playlist", Toast.LENGTH_LONG).show()
        }

        // MediaItem — cukup set URI dan MimeType, DRM dihandle lewat DrmSessionManager
        mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .also { if (mimeType != null) it.setMimeType(mimeType) }
            .build()

        // MediaSourceFactory dengan DrmSessionManager yang sudah dikonfigurasi
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setDrmSessionManagerProvider { drmSessionManager }

        // create trackselector with resolution constraint from sidebar buttons
        trackSelector = DefaultTrackSelector(this).apply {
            val maxHeights = listOf(360, 720, 1080, 2160, Int.MAX_VALUE)
            val maxBitrates = listOf(800_000, 2_500_000, 5_000_000, 20_000_000, Int.MAX_VALUE)
            val idx = preferences.resolutionIndex.coerceIn(0, maxHeights.size - 1)
            parameters = ParametersBuilder(applicationContext)
                .setMaxVideoSize(Int.MAX_VALUE, maxHeights[idx])
                .setMaxVideoBitrate(maxBitrates[idx])
                .build()
        }

        // optimize prebuffer
        val loadControl: LoadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 16))
            .setBufferDurationsMs(32 * 1024, 64 * 1024, 1024, 1024)
            .setTargetBufferBytes(-1)
            .setPrioritizeTimeOverSizeThresholds(true).build()

        // enable extension renderer
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        // set player builder
        val playerBuilder = SimpleExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
        if (preferences.optimizePrebuffer)
            playerBuilder.setLoadControl(loadControl)

        // create player & set listener
        player = playerBuilder.build()
        player?.addListener(PlayerListener())

        // set player view
        bindingRoot.playerView.player = player
        bindingRoot.playerView.resizeMode = preferences.resizeMode
        bindingRoot.playerView.requestFocus()

        // play
        player?.playWhenReady = true
        player?.setMediaItem(mediaItem)
        player?.prepare()
    }

    private fun switchChannel(mode: Int): Boolean {
        if (isLocked) return true
        switchChannel(mode, false)
        bindingRoot.playerView.hideController()
        return true
    }

    private fun switchChannel(mode: Int, lastCh: Boolean) {
        val catId = Playlist.cached.categories.indexOf(category)
        val chId = category?.channels?.indexOf(current) as Int
        when(mode) {
            CATEGORY_UP -> {
                val previous = catId - 1
                if (previous > -1) {
                    category = Playlist.cached.categories[previous]
                    current = if (lastCh) category?.channels?.get(category?.channels?.size?.minus(1) ?: 0)
                    else category?.channels?.get(0)
                }
                else {
                    Toast.makeText(this, R.string.top_category, Toast.LENGTH_SHORT).show()
                    return
                }
            }
            CATEGORY_DOWN -> {
                val next = catId + 1
                if (next < Playlist.cached.categories.size) {
                    category = Playlist.cached.categories[next]
                    current = category?.channels?.get(0)
                }
                else {
                    Toast.makeText(this, R.string.bottom_category, Toast.LENGTH_SHORT).show()
                    return
                }
            }
            CHANNEL_PREVIOUS -> {
                val previous = chId - 1
                if (previous > -1) {
                    current = category?.channels?.get(previous)
                }
                else {
                    switchChannel(CATEGORY_UP, true)
                    return
                }
            }
            CHANNEL_NEXT -> {
                val next = chId + 1
                if (next < category?.channels?.size ?: 0) {
                    current = category?.channels?.get(next)
                }
                else {
                    switchChannel(CATEGORY_DOWN)
                    return
                }
            }
        }

        // reset player & play
        errorCounter = 0
        player?.playWhenReady = false
        player?.release()
        playChannel()
    }

    private fun retryPlayback(force: Boolean) {
        if (force) {
            player?.playWhenReady = true
            player?.setMediaItem(mediaItem)
            player?.prepare()
            return
        }

        AsyncSleep().task(object : AsyncSleep.Task {
            override fun onFinish() {
                retryPlayback(true)
            }
        }).start(1)
    }

    private inner class PlayerListener : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val trackHaveContent = TrackSelectionDialog.willHaveContent(trackSelector)
            bindingControl.trackSelection.visibility =
                if (trackHaveContent) View.VISIBLE else View.GONE
            when (state) {
                Player.STATE_READY -> {
                    errorCounter = 0
                    val catId = Playlist.cached.categories.indexOf(category)
                    val chId = category?.channels?.indexOf(current) as Int
                    preferences.watched = PlayData(catId, chId)
                    switchLiveOrVideo()
                }
                Player.STATE_ENDED -> retryPlayback(true)
                else -> { }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            if (isPlaying) setChannelInformation(true)
        }

        override fun onPlayerError(error: PlaybackException) {
            // if error more than 5 times, then show message dialog
            if (errorCounter < 5 && network.isConnected()) {
                errorCounter++
                Toast.makeText(applicationContext, error.message, Toast.LENGTH_SHORT).show()
                retryPlayback(false)
            }
            else {
                showMessage(
                    String.format(getString(R.string.player_error_message),
                        error.errorCode, error.errorCodeName, error.message), true
                )
            }
        }

        override fun onTracksChanged(trackGroups: com.google.android.exoplayer2.source.TrackGroupArray, trackSelections: com.google.android.exoplayer2.trackselection.TrackSelectionArray) {
            val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return
            val isVideoProblem = mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO) == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS
            val isAudioProblem = mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_AUDIO) == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS
            val problem = when {
                isVideoProblem && isAudioProblem -> "video & audio"
                isVideoProblem -> "video"
                else -> "audio"
            }
            val message = String.format(getString(R.string.error_unsupported), problem)
            if (isVideoProblem) showMessage(message, false)
            else if (isAudioProblem) Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showMessage(message: String, autoretry: Boolean) {
        val waitInSecond = 30
        val btnRetryText = if (autoretry) String.format(getString(R.string.btn_retry_count), waitInSecond) else getString(R.string.btn_retry)
        val builder = AlertDialog.Builder(this).apply {
            setTitle(R.string.player_playback_error)
            setMessage(message)
            setCancelable(false)
            setNegativeButton(getString(R.string.btn_next_channel)) { di,_ ->
                switchChannel(CHANNEL_NEXT)
                di.dismiss()
            }
            setPositiveButton(btnRetryText) { di,_ ->
                retryPlayback(true)
                di.dismiss()
            }
            setNeutralButton(R.string.btn_close) { di,_ ->
                di.dismiss()
                finish()
            }
            create()
        }
        val dialog = builder.show()

        if (!autoretry) return
        AsyncSleep().task(object : AsyncSleep.Task{
            override fun onCountDown(count: Int) {
                val text = if (count <= 0) getString(R.string.btn_retry)
                else String.format(getString(R.string.btn_retry_count), count)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = text
            }

            override fun onFinish() {
                dialog.dismiss()
                retryPlayback(true)
            }
        }).start(waitInSecond)
    }

    private fun showTrackSelector(): Boolean {
        TrackSelectionDialog.createForTrackSelector(trackSelector) { }
            .show(supportFragmentManager, "TrackSelection")
        return true
    }

    private fun showScreenMenu(view: View) {
        val timeout = bindingRoot.playerView.controllerShowTimeoutMs
        bindingRoot.playerView.controllerShowTimeoutMs = 0
        PopupMenu(this, view).apply {
            inflate(R.menu.screen_resize_mode)
            setOnMenuItemClickListener { m: MenuItem ->
                val mode = when(m.itemId) {
                    R.id.mode_fixed_width -> 1
                    R.id.mode_fixed_height -> 2
                    R.id.mode_fill -> 3
                    R.id.mode_zoom -> 4
                    else -> 0
                }
                if (bindingRoot.playerView.resizeMode != mode) {
                    bindingRoot.playerView.resizeMode = mode
                    preferences.resizeMode = mode
                }
                true
            }
            setOnDismissListener {
                bindingRoot.playerView.controllerShowTimeoutMs = timeout
            }
            show()
        }
    }

    override fun onResume() {
        super.onResume()
        player?.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }

    @Suppress("DEPRECATION")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == false) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = android.app.PictureInPictureParams.Builder().build()
                enterPictureInPictureMode(params)
            }
            else {
                enterPictureInPictureMode()
            }
        }
    }

    override fun onPictureInPictureModeChanged(pip: Boolean, config: Configuration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            super.onPictureInPictureModeChanged(pip, config)
        }
        isPipMode = pip
        setChannelInformation(!pip)
        bindingRoot.playerView.useController = !pip
        player?.playWhenReady = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.setFullScreenFlags()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!bindingRoot.playerView.isControllerVisible && keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            bindingRoot.playerView.showController()
            return true
        }
        if (isLocked) return true
        when(keyCode) {
            KeyEvent.KEYCODE_MENU -> return showTrackSelector()
            KeyEvent.KEYCODE_PAGE_UP -> return switchChannel(CATEGORY_UP)
            KeyEvent.KEYCODE_PAGE_DOWN -> return switchChannel(CATEGORY_DOWN)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> return switchChannel(CHANNEL_PREVIOUS)
            KeyEvent.KEYCODE_MEDIA_NEXT -> return switchChannel(CHANNEL_NEXT)
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player?.play(); return true; }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); return true; }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (player?.isPlaying == false) player?.play() else player?.pause()
                return true
            }
        }
        if (player?.isCurrentWindowLive == false) {
            when(keyCode) {
                KeyEvent.KEYCODE_MEDIA_REWIND -> { player?.seekBack(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { player?.seekForward(); return true }
            }
        }
        if (bindingRoot.playerView.isControllerVisible) return super.onKeyUp(keyCode, event)
        if (!preferences.reverseNavigation) {
            when (keyCode) {
                // UP/DOWN = ganti channel langsung tanpa keluar player
                KeyEvent.KEYCODE_DPAD_UP -> return switchChannel(CHANNEL_PREVIOUS)
                KeyEvent.KEYCODE_DPAD_DOWN -> return switchChannel(CHANNEL_NEXT)
                KeyEvent.KEYCODE_DPAD_LEFT -> return switchChannel(CATEGORY_UP)
                KeyEvent.KEYCODE_DPAD_RIGHT -> return switchChannel(CATEGORY_DOWN)
            }
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> return switchChannel(CHANNEL_NEXT)
                KeyEvent.KEYCODE_DPAD_DOWN -> return switchChannel(CHANNEL_PREVIOUS)
                KeyEvent.KEYCODE_DPAD_LEFT -> return switchChannel(CATEGORY_DOWN)
                KeyEvent.KEYCODE_DPAD_RIGHT -> return switchChannel(CATEGORY_UP)
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onBackPressed() {
        if (isLocked) return
        if (isTelevision || doubleBackToExitPressedOnce) {
            super.onBackPressed()
            finish(); return
        }
        doubleBackToExitPressedOnce = true
        Toast.makeText(this, getString(R.string.press_back_twice_exit_player), Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
    }

    override fun onDestroy() {
        player?.release()
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }
}
