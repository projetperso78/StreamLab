package app.cleanroom.streamlab

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import com.google.common.util.concurrent.ListenableFuture
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import app.cleanroom.streamlab.player.AutoLibrary
import app.cleanroom.streamlab.player.PlaybackService

/** Parked/passenger TV browser: preview on the left, complete channel list on the right. */
class PassengerPlayerActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var splitView: LinearLayout
    private lateinit var previewHost: FrameLayout
    private lateinit var previewTitle: TextView
    private lateinit var status: TextView
    private lateinit var channelList: LinearLayout
    private lateinit var playerContainer: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var fullScreenBack: Button
    private lateinit var nowPlaying: TextView

    private var browser: MediaBrowser? = null
    private var playbackController: MediaController? = null
    private var browserFuture: ListenableFuture<MediaBrowser>? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var channels: List<MediaItem> = emptyList()
    private var selectedIndex = 0
    private var fullScreen = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(StreamLabLocale.wrap(newBase))
    }

    private val playbackListener = object : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            status.text = getString(R.string.pass_tv_unavailable)
        }
        override fun onPlaybackStateChanged(playbackState: Int) = updateStatus()
        override fun onIsPlayingChanged(isPlaying: Boolean) { CarScreen.apply(this@PassengerPlayerActivity, isPlaying); updateStatus() }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = channels.indexOfFirst { it.mediaId == mediaItem?.mediaId }
            if (index >= 0) {
                selectedIndex = index
                renderSelection()
            }
            updateStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.pass_tv)
        buildUi()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullScreen) showSplitView() else finish()
            }
        })
        connect()
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(8, 12, 15)) }
        splitView = LinearLayout(this).apply {
            orientation = if (resources.configuration.screenWidthDp < 700) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val portraitLayout = resources.configuration.screenWidthDp < 700

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, dp(12), 0)
        }
        left.addView(TextView(this).apply {
            text = getString(R.string.pass_tv_preview_label)
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.START
            setPadding(dp(4), 0, 0, dp(8))
        }, LinearLayout.LayoutParams(-1, -2))

        previewHost = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.pass_tv_fullscreen_open)
            setOnClickListener { showFullScreen() }
        }
        left.addView(previewHost, LinearLayout.LayoutParams(-1, 0, 1f))

        previewTitle = TextView(this).apply {
            text = getString(R.string.pass_tv_loading)
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(4), dp(10), dp(4), dp(2))
        }
        left.addView(previewTitle, LinearLayout.LayoutParams(-1, -2))

        status = TextView(this).apply {
            text = getString(R.string.pass_tv_connecting)
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(dp(4), dp(2), dp(4), 0)
        }
        left.addView(status, LinearLayout.LayoutParams(-1, -2))

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        right.addView(TextView(this).apply {
            text = getString(R.string.pass_tv_channels_label)
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(4), 0, dp(4), dp(8))
        }, LinearLayout.LayoutParams(-1, -2))

        channelList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        right.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(channelList, FrameLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, 0, 1f))

        if (portraitLayout) {
            left.setPadding(0, 0, 0, dp(12))
            right.setPadding(0, dp(12), 0, 0)
            splitView.addView(left, LinearLayout.LayoutParams(-1, 0, 0.56f))
            splitView.addView(right, LinearLayout.LayoutParams(-1, 0, 0.44f))
        } else {
            splitView.addView(left, LinearLayout.LayoutParams(0, -1, 0.46f))
            splitView.addView(right, LinearLayout.LayoutParams(0, -1, 0.54f))
        }
        root.addView(splitView, FrameLayout.LayoutParams(-1, -1))

        playerContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (!fullScreen) showFullScreen() else playerView.showController()
            }
        }
        playerView = layoutInflater.inflate(R.layout.passenger_player_view, playerContainer, false) as PlayerView
        playerView.controllerAutoShow = false
        playerView.useController = false
        playerView.setOnClickListener {
            if (!fullScreen) showFullScreen() else playerView.showController()
        }
        playerContainer.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        fullScreenBack = Button(this).apply {
            text = getString(R.string.pass_tv_back_channels)
            isAllCaps = false
            textSize = 15f
            visibility = View.GONE
            setOnClickListener { showSplitView() }
        }
        playerContainer.addView(fullScreenBack, FrameLayout.LayoutParams(dp(150), dp(58), Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(12)
            topMargin = dp(12)
        })

        nowPlaying = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setPadding(dp(14), dp(8), dp(14), dp(8))
            visibility = View.GONE
        }
        playerContainer.addView(nowPlaying, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = dp(12)
            bottomMargin = dp(12)
        })

        previewHost.addView(playerContainer, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        // Car screens put Android Auto's rail and bars over the app: keep everything inside the
        // safe area so names and buttons are never cut (car app quality AR-1).
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
    }

    private fun connect() {
        val token = try {
            SessionToken(
                this,
                ComponentName(CarServicesActivity.MAIN_APP_PACKAGE, PlaybackService::class.java.name),
            )
        } catch (_: IllegalArgumentException) {
            previewTitle.text = getString(R.string.pass_tv)
            status.text = getString(R.string.pass_tv_main_unavailable)
            return
        }
        val executor = ContextCompat.getMainExecutor(this)

        // Build with applicationContext so an Activity destroy cannot auto-unbind
        // before Media3 finishes asynchronous release of its service connection.
        val browserFuture = MediaBrowser.Builder(applicationContext, token).buildAsync()
        this.browserFuture = browserFuture
        browserFuture.addListener({
            if (isDestroyed || isFinishing) return@addListener
            runCatching { browserFuture.get() }
                .onSuccess {
                    browser = it
                    loadTv(it)
                }
                .onFailure { status.text = getString(R.string.pass_tv_catalog_error, it.javaClass.simpleName) }
        }, executor)

        val controllerFuture = MediaController.Builder(applicationContext, token).buildAsync()
        this.controllerFuture = controllerFuture
        controllerFuture.addListener({
            if (isDestroyed || isFinishing) return@addListener
            runCatching { controllerFuture.get() }
                .onSuccess { controller ->
                    playbackController = controller
                    controller.addListener(playbackListener)
                    playerView.player = controller
                    status.text = getString(R.string.pass_tv_connected)
                    if (channels.isNotEmpty()) {
                        val playingId = controller.currentMediaItem?.mediaId
                        channels.indexOfFirst { it.mediaId == playingId }.takeIf { it >= 0 }?.let { selectedIndex = it }
                        startPreviewOrResume()
                    }
                }
                .onFailure { status.text = getString(R.string.pass_tv_player_error, it.javaClass.simpleName) }
        }, executor)
    }

    private fun loadTv(connected: MediaBrowser, page: Int = 0, accumulated: List<MediaItem> = emptyList()) {
        val future = connected.getChildren(AutoLibrary.TV, page, 200, null)
        future.addListener({
            runCatching { future.get() }
                .onSuccess { result ->
                    if (isDestroyed || isFinishing) return@onSuccess
                    require(result.resultCode == 0) { "Catalogue inaccessible" }
                    val batch = result.value.orEmpty()
                    val merged = (accumulated + batch).distinctBy { it.mediaId }
                    if (batch.size == 200 && merged.size > accumulated.size) {
                        loadTv(connected, page + 1, merged)
                        return@onSuccess
                    }
                    channels = merged
                    if (channels.isEmpty()) {
                        previewTitle.text = getString(R.string.pass_tv_no_channels)
                        status.text = getString(R.string.pass_tv_catalog_empty)
                        return@onSuccess
                    }
                    // Back from driving: pick up the channel that kept playing as sound.
                    val playingId = playbackController?.currentMediaItem?.mediaId
                    selectedIndex = channels.indexOfFirst { it.mediaId == playingId }.takeIf { it >= 0 } ?: 0
                    renderChannelList()
                    renderSelection()
                    if (playbackController != null) startPreviewOrResume()
                }
                .onFailure { status.text = getString(R.string.pass_tv_catalog_error, it.javaClass.simpleName) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun renderChannelList() {
        channelList.removeAllViews()
        channels.forEachIndexed { index, item ->
            val title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { getString(R.string.pass_tv_channel_number, index + 1) }
            channelList.addView(Button(this).apply {
                text = title
                isAllCaps = false
                textSize = 15f
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                minHeight = dp(54)
                contentDescription = getString(R.string.pass_tv_select_channel, title)
                setOnClickListener {
                    selectedIndex = index
                    renderSelection()
                    playPreview(index)
                }
            }, LinearLayout.LayoutParams(-1, dp(58)).apply {
                bottomMargin = dp(5)
            })
        }
        refreshSelectedButton()
    }

    private fun refreshSelectedButton() {
        for (i in 0 until channelList.childCount) {
            val button = channelList.getChildAt(i) as? Button ?: continue
            val selected = i == selectedIndex
            button.alpha = if (selected) 1f else 0.78f
            button.backgroundTintList = ColorStateList.valueOf(
                if (selected) Color.rgb(72, 92, 104) else Color.rgb(76, 76, 80)
            )
        }
    }

    private fun renderSelection() {
        val item = channels.getOrNull(selectedIndex) ?: return
        val title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { getString(R.string.pass_tv_channel_default) }
        previewTitle.text = title
        status.text = "${selectedIndex + 1} / ${channels.size}"
        refreshSelectedButton()
    }

    private fun playPreview(index: Int) {
        val item = channels.getOrNull(index) ?: return
        val controller = playbackController ?: return
        selectedIndex = index
        renderSelection()
        if (controller.currentMediaItem?.mediaId != item.mediaId || controller.playbackState == Player.STATE_IDLE) {
            controller.setMediaItem(MediaItem.Builder().setMediaId(item.mediaId).build())
            controller.prepare()
        }
        controller.play()
    }

    /** Plays the selected channel; reopened after driving, goes straight back to full screen. */
    private fun startPreviewOrResume() {
        playPreview(selectedIndex)
        if (intent.getBooleanExtra(EXTRA_RESUME_FULL_SCREEN, false) && !fullScreen) {
            intent.removeExtra(EXTRA_RESUME_FULL_SCREEN)
            showFullScreen()
        }
    }

    private fun showFullScreen() {
        if (channels.isEmpty()) return
        playPreview(selectedIndex)
        fullScreen = true
        splitView.visibility = View.GONE
        (playerContainer.parent as? ViewGroup)?.removeView(playerContainer)
        root.addView(playerContainer, FrameLayout.LayoutParams(-1, -1))
        fullScreenBack.visibility = View.VISIBLE
        playerView.useController = true
        nowPlaying.text = previewTitle.text
        nowPlaying.visibility = View.VISIBLE
        playerView.showController()
    }

    private fun showSplitView() {
        if (!fullScreen) return
        fullScreen = false
        fullScreenBack.visibility = View.GONE
        playerView.useController = false
        nowPlaying.visibility = View.GONE
        (playerContainer.parent as? ViewGroup)?.removeView(playerContainer)
        previewHost.addView(playerContainer, FrameLayout.LayoutParams(-1, -1))
        splitView.visibility = View.VISIBLE
        renderSelection()
    }

    private fun moveSelection(delta: Int) {
        if (channels.isEmpty()) return
        selectedIndex = (selectedIndex + delta + channels.size) % channels.size
        playPreview(selectedIndex)
    }

    private fun updateStatus() {
        val controller = playbackController ?: return
        val state = when (controller.playbackState) {
            Player.STATE_BUFFERING -> getString(R.string.pass_tv_state_buffering)
            Player.STATE_READY -> getString(if (controller.isPlaying) R.string.pass_tv_state_playing else R.string.pass_tv_state_ready)
            Player.STATE_ENDED -> getString(R.string.pass_tv_state_ended)
            else -> getString(R.string.pass_tv_state_idle)
        }
        if (!fullScreen) status.text = getString(R.string.pass_tv_position, selectedIndex + 1, channels.size, state)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (!fullScreen) moveSelection(-1) else playerView.showController()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!fullScreen) moveSelection(1) else playerView.showController()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (fullScreen) moveSelection(-1) else return super.dispatchKeyEvent(event)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (fullScreen) moveSelection(1) else return super.dispatchKeyEvent(event)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!fullScreen) showFullScreen() else playerView.showController()
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onPause() {
        // Leaving the player on purpose (Back) stops it. When Android Auto covers the screen
        // because the car moves, only the picture goes: the sound keeps playing through the
        // playback service, with play/pause/next on the car's media card.
        if (isFinishing) playbackController?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        // releaseFuture cancels a pending connection or releases a completed one.
        // Never call controller.release() again: both paths would race to unbind.
        playerView.player = null
        playbackController?.removeListener(playbackListener)
        controllerFuture?.let(MediaController::releaseFuture)
        browserFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        browserFuture = null
        playbackController = null
        browser = null
        super.onDestroy()
    }

    companion object {
        /** Set by the passenger home when a channel kept playing as sound while driving. */
        const val EXTRA_RESUME_FULL_SCREEN = "resume_full_screen"
    }
}
