package com.imesh.streamlite

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Rational
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

private class TrackOpt(val label: String, val group: Tracks.Group?, val index: Int)

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "url"
        private const val ACTION_PIP = "com.imesh.streamlite.PIP_TOGGLE"
        private const val MAX_RETRIES = 12
        private val RESIZE_NAMES = listOf("Fit (auto)", "Fill (stretch)", "Zoom (crop)", "Fit width", "Fit height")
        private val RESIZE_MODES = intArrayOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
        )
        private val SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        private val SPEED_LABELS = listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x")
        private val ORIENT_NAMES = listOf(
            "Auto-rotate (follows your phone)",
            "Landscape",
            "Landscape (reversed)",
            "Portrait",
            "Match the video"
        )
        private val FALLBACK_MIMES = arrayOf(MimeTypes.APPLICATION_M3U8, MimeTypes.APPLICATION_MPD)
    }

    private var player: ExoPlayer? = null
    private var bandwidthMeter: DefaultBandwidthMeter? = null
    private var loudness: LoudnessEnhancer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val audio by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    private lateinit var playerView: PlayerView
    private lateinit var gestureLayer: View
    private lateinit var controls: View
    private lateinit var titleText: TextView
    private lateinit var clockText: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var btnLock: ImageButton
    private lateinit var lockBtn: ImageButton
    private lateinit var btnSpeed: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var posText: TextView
    private lateinit var durText: TextView
    private lateinit var statsText: TextView
    private lateinit var indicator: TextView
    private lateinit var banner: TextView
    private lateinit var spinner: View

    private lateinit var entry: Entry
    private var headers: Map<String, String> = emptyMap()
    private var url = ""
    private var mimeOverride: String? = null
    private var fallbackStage = 0

    // subtitles
    private var subRaw: String? = null
    private var subKind = SubtitleTools.KIND_SRT
    private var subDelayMs = 0L
    private var subtitleUri: Uri? = null
    private var subtitleMime: String? = null
    private var autoSubsTried = false
    private var firstFrameDrawn = false

    // playback state
    private var resumePos = 0L
    private var resumePlay = true
    private var resumeLabel = 0L
    private var firstReady = true
    private var controlsVisible = true
    private var statsVisible = false
    private var userSeeking = false
    private var locked = false
    private var inPip = false
    private var pipReceiverOn = false
    private var resizeIndex = 0
    private var orient = 0
    private var speedIndex = 2
    private var boostPct = 0
    private var longPress = false
    private var audioLangSel = ""
    private var textLangSel = ""
    private var decoderModeNow = 0
    private var sleepAtMs = 0L
    private var stallCount = 0
    private var lastState = Player.STATE_IDLE

    // playlist
    private var queue: List<PlaylistItem> = emptyList()
    private var queueIndex = 0
    private var playlistUrl = ""
    private var playlistEntry: Entry? = null
    private var playlistPending = false
    private var triedPlaylist = false
    private var started = false
    private var bgOneShot = false
    private var savedResize = -1
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton

    // auto-reconnect
    private var retryCount = 0
    private var retryLeft = 0
    private var retryPending = false
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    // stats
    private var decoderName = "-"
    private var droppedTotal = 0L
    private var lastStatT = 0L
    private var lastStatBytes = 0L
    private var lastFlushed = 0L
    private var battStart = -1
    private var battStartT = 0L
    private val frameCount = AtomicInteger(0)
    private val frameListener = VideoFrameMetadataListener { _, _, _, _ -> frameCount.incrementAndGet() }

    // gestures: 0 none, 1 seek, 2 brightness, 3 volume, 4 ignored (middle zone)
    private var gestureMode = 0
    private var seekDeltaMs = 0L
    private var brightnessF = 0.5f
    private var volumeF = 0f

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // The sound plays either way; without the permission the notification is just hidden.
        bgOneShot = true
        moveTaskToBack(true)
    }

    private val subPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadSubtitleUri(uri)
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        volumeControlStream = AudioManager.STREAM_MUSIC

        url = intent.getStringExtra(EXTRA_URL) ?: intent.dataString ?: ""
        if (url.isBlank()) {
            finish()
            return
        }
        NetStats.bytes.set(0)
        entry = Store.touch(this, url)
        headers = Store.parseHeaders(entry.headers)
        decoderModeNow = prefDecoderMode
        applyPerVideo()

        val surfaceView = findViewById<PlayerView>(R.id.playerView)
        val textureView = findViewById<PlayerView>(R.id.playerViewTexture)
        playerView = if (prefTextureView) textureView else surfaceView
        (if (prefTextureView) surfaceView else textureView).visibility = View.GONE
        playerView.visibility = View.VISIBLE
        gestureLayer = findViewById(R.id.gestureLayer)
        controls = findViewById(R.id.controls)
        titleText = findViewById(R.id.title)
        clockText = findViewById(R.id.clock)
        btnPlay = findViewById(R.id.btnPlay)
        btnLock = findViewById(R.id.btnLock)
        lockBtn = findViewById(R.id.lockBtn)
        btnSpeed = findViewById(R.id.btnSpeed)
        seekBar = findViewById(R.id.seekBar)
        posText = findViewById(R.id.posText)
        durText = findViewById(R.id.durText)
        statsText = findViewById(R.id.stats)
        indicator = findViewById(R.id.indicator)
        banner = findViewById(R.id.banner)
        spinner = findViewById(R.id.spinner)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)

        titleText.text = Store.displayName(entry)
        resizeIndex = prefResizeIndex.coerceIn(0, RESIZE_MODES.size - 1)
        if (savedResize in RESIZE_MODES.indices) resizeIndex = savedResize
        playerView.resizeMode = RESIZE_MODES[resizeIndex]
        applySubtitleStyle()

        if (prefResume && entry.pos > 5000 && entry.dur > 0) {
            resumePos = entry.pos
            resumeLabel = entry.pos
        }
        orient = if (prefFollowVideo) 4 else 0

        setupControls()
        btnSpeed.text = SPEED_LABELS[speedIndex]
        setupGestures()
        btnPrev.setOnClickListener { prevItem() }
        btnNext.setOnClickListener { nextItem() }
        if (Playlist.isPlaylist(url, intent.type)) {
            playlistUrl = url
            playlistEntry = entry
            loadPlaylist(url)
        }
        ViewCompat.setOnApplyWindowInsetsListener(controls) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            insets
        }
        enterImmersive()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (locked) {
                    showLockButton()
                    flash("Locked. Hold the lock icon to unlock")
                } else {
                    finish()
                }
            }
        })
        DebugLog.log("open ${Fmt.cleanUrl(url)} headers=${headers.size}")
    }

    override fun onStart() {
        super.onStart()
        started = true
        bgOneShot = false
        val back = BackgroundAudio.takeBack(this)
        if (back != null) {
            DebugLog.log("back from background audio at ${back.pos}")
            resumePlay = back.playing
            if (queue.isNotEmpty() && back.index in queue.indices && back.index != queueIndex) {
                playIndex(back.index, back.pos)
                return
            }
            resumePos = back.pos
        }
        if (url.isNotBlank() && !playlistPending) initPlayer()
    }

    override fun onStop() {
        super.onStop()
        started = false
        val wasPip = Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode
        if (!wasPip && !isFinishing && (prefBackgroundAudio || bgOneShot)) handoffToBackground()
        releasePlayer()
        if (wasPip) finishAndRemoveTask()
    }

    override fun onDestroy() {
        unregisterPipReceiver()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    /** TV remote and keyboard: OK / arrows / media keys. With controls visible the arrows move focus. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!locked && !inPip) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                    togglePlay()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    nextItem()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    prevItem()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    player?.play()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    player?.pause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    seekRelative(30_000)
                    flash("+30s")
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    seekRelative(-30_000)
                    flash("-30s")
                    return true
                }
            }
            if (!controlsVisible) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        seekRelative(-10_000)
                        flash("-10s")
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        seekRelative(10_000)
                        flash("+10s")
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        showControls(true)
                        btnPlay.requestFocus()
                        return true
                    }
                }
            } else {
                scheduleHide()
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Mouse and air-mouse support: moving the pointer shows the controls, the wheel changes volume. */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (!locked && !inPip && ev.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_HOVER_MOVE -> if (!controlsVisible) showControls(true) else scheduleHide()
                MotionEvent.ACTION_SCROLL -> {
                    val dy = ev.getAxisValue(MotionEvent.AXIS_VSCROLL)
                    if (dy != 0f) {
                        audio.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            if (dy > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0
                        )
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        flash("Volume " + (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max) + "%")
                        return true
                    }
                }
            }
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.playWhenReady && p.playbackState != Player.STATE_ENDED) {
            p.pause()
        } else {
            if (p.playbackState == Player.STATE_ENDED) p.seekToDefaultPosition()
            p.play()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (prefAutoPip && !locked && player?.isPlaying == true) enterPip(false)
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // ---------------------------------------------------------------- player setup

    private fun initPlayer() {
        if (player != null) return

        val meter = DefaultBandwidthMeter.Builder(this).build()
        bandwidthMeter = meter

        var ua = "Mozilla/5.0 (Linux; Android) StreamLite/2.0"
        val props = HashMap<String, String>()
        for ((k, v) in headers) {
            if (k.equals("user-agent", true)) ua = v else props[k] = v
        }
        val http = OkHttpDataSource.Factory(HttpClients.shared)
            .setUserAgent(ua)
        if (props.isNotEmpty()) http.setDefaultRequestProperties(props)
        val dataSourceFactory = DefaultDataSource.Factory(this, http)
            .setTransferListener(CountingTransferListener(meter.transferListener))

        val trackSelector = DefaultTrackSelector(this)
        val tb = trackSelector.buildUponParameters()
        val maxH = RES_HEIGHTS[prefMaxRes.coerceIn(0, RES_HEIGHTS.size - 1)]
        if (maxH > 0) tb.setMaxVideoSize(maxH * 16 / 9, maxH)
        val aLang = audioLangSel.ifBlank { prefAudioLang }
        val tLang = textLangSel.ifBlank { prefTextLang }
        if (aLang.isNotBlank()) tb.setPreferredAudioLanguage(aLang)
        if (tLang.isNotBlank()) tb.setPreferredTextLanguage(tLang)
        trackSelector.setParameters(tb.build())

        // The buffer is limited by size in MB (your setting). Once it is full the app stops
        // downloading, which lets the radio idle and saves power.
        // Never ask for more buffer than half of what this device allows an app to use.
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val capMb = (am.largeMemoryClass / 2).coerceAtLeast(16)
        val bufferMb = minOf(prefBufferMb, capMb)
        if (bufferMb < prefBufferMb) DebugLog.log("buffer limited to $bufferMb MB (device memory)")
        val targetBytes = bufferMb * 1024 * 1024
        val baseLoad = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30 * 60 * 1000, 30 * 60 * 1000, 2_500, 5_000)
            .setTargetBufferBytes(targetBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(20_000, false)
            .build()
        // "Always" uses the stock load control untouched; the wrapper is only needed to stop buffering while paused.
        val loadControl: androidx.media3.exoplayer.LoadControl =
            if (prefPrebuffer == 0) baseLoad else PrebufferLoadControl(baseLoad, this) { prefPrebuffer }

        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(Decoders.selector(decoderModeNow))
            // Use the built-in software decoders for audio the device itself cannot decode (E-AC3, DTS...)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val p = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setBandwidthMeter(meter)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build()

        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                DebugLog.log("state ${stateName(playbackState)}")
                if (playbackState == Player.STATE_BUFFERING && lastState == Player.STATE_READY && player?.playWhenReady == true) {
                    stallCount++
                }
                lastState = playbackState
                spinner.visibility = if (playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                if (playbackState == Player.STATE_READY) onReady()
                if (playbackState == Player.STATE_ENDED) {
                    if (queue.isNotEmpty() && queueIndex < queue.lastIndex) playIndex(queueIndex + 1, 0L) else showControls(true)
                }
                updatePlayIcon()
                updateKeepOn()
                if (inPip) updatePip()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updatePlayIcon()
                updateKeepOn()
                scheduleHide()
                if (inPip) updatePip()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateKeepOn()
                scheduleHide()
            }

            override fun onPlayerError(error: PlaybackException) {
                handleError(error)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (orient == 4) applyOrientation()
                if (inPip) updatePip()
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupBoost(audioSessionId)
            }

            override fun onTracksChanged(tracks: Tracks) {
                logTracks(tracks)
                checkSupport(tracks)
            }

            override fun onRenderedFirstFrame() {
                firstFrameDrawn = true
                DebugLog.log("first video frame drawn")
            }
        })

        p.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                this@PlayerActivity.decoderName = decoderName
                DebugLog.log("decoder $decoderName")
            }

            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long
            ) {
                droppedTotal += droppedFrames
            }
        })

        player = p
        playerView.player = p
        spinner.visibility = View.VISIBLE
        p.setPlaybackSpeed(SPEEDS[speedIndex])
        if (statsVisible) {
            p.setVideoFrameMetadataListener(frameListener)
            lastStatT = SystemClock.elapsedRealtime()
            lastStatBytes = NetStats.bytes.get()
            handler.post(statsRunnable)
        }

        if (resumePos > 0) p.setMediaItem(buildItem(), resumePos) else p.setMediaItem(buildItem())
        p.playWhenReady = resumePlay
        p.prepare()

        val b = batteryInfo()
        battStart = b.first
        battStartT = SystemClock.elapsedRealtime()
        lastFlushed = NetStats.bytes.get()
        registerNet()
        handler.postDelayed(saveTick, 20_000)
        scheduleSleep()

        updatePlayIcon()
        if (controlsVisible && !locked) showControls(true)
        if (orient != 0) applyOrientation()
    }

    private val sleepRunnable = Runnable {
        sleepAtMs = 0
        player?.pause()
        flash("Sleep timer: paused")
    }

    private fun scheduleSleep() {
        handler.removeCallbacks(sleepRunnable)
        if (sleepAtMs <= 0) return
        val left = sleepAtMs - SystemClock.elapsedRealtime()
        if (left <= 0) sleepRunnable.run() else handler.postDelayed(sleepRunnable, left)
    }

    private fun showSleepSheet() {
        val labels = listOf("Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "90 minutes")
        val mins = intArrayOf(0, 15, 30, 45, 60, 90)
        val cur = if (sleepAtMs <= 0) 0 else -1
        Sheets.choose(this, "Sleep timer", labels, cur) { i ->
            sleepAtMs = if (mins[i] == 0) 0 else SystemClock.elapsedRealtime() + mins[i] * 60_000L
            scheduleSleep()
            flash(if (mins[i] == 0) "Sleep timer off" else "Pausing in ${mins[i]} min")
        }
    }

    private fun showJumpDialog() {
        val p = player ?: return
        val input = android.widget.EditText(this).apply {
            hint = "e.g. 1:23:45 or 12:30"
            inputType = android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_TIME
            setSingleLine()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Jump to time")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val parts = input.text.toString().trim().split(":").map { it.trim().toLongOrNull() ?: -1L }
                if (parts.isEmpty() || parts.size > 3 || parts.any { it < 0 }) {
                    flash("Use mm:ss or hh:mm:ss")
                } else {
                    var sec = 0L
                    for (x in parts) sec = sec * 60 + x
                    val dur = p.duration
                    val max = if (dur != C.TIME_UNSET && dur > 0) dur else Long.MAX_VALUE
                    p.seekTo((sec * 1000).coerceIn(0L, max))
                    updateProgress()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------- playlist

    private fun applyPerVideo() {
        speedIndex = 2
        savedResize = -1
        subDelayMs = 0
        boostPct = 0
        audioLangSel = ""
        textLangSel = ""
        if (prefRememberVideo) {
            PerVideo.load(this, url)?.let { v ->
                if (v.speed in SPEEDS.indices) speedIndex = v.speed
                savedResize = v.resize
                subDelayMs = v.subDelayMs
                boostPct = v.boost
                audioLangSel = v.audioLang
                textLangSel = v.textLang
            }
        }
    }

    private fun loadPlaylist(src: String) {
        playlistPending = true
        spinner.visibility = View.VISIBLE
        val hdr = headers
        Thread {
            val items = try { Playlist.load(this, src, hdr) } catch (e: Exception) {
                DebugLog.log("playlist load failed: $e")
                emptyList()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                playlistPending = false
                if (items.isEmpty()) {
                    spinner.visibility = View.GONE
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Can't open this playlist")
                        .setMessage("No playable links were found, or the playlist could not be downloaded.")
                        .setCancelable(false)
                        .setPositiveButton("Close") { _, _ -> finish() }
                        .show()
                } else {
                    startQueue(items)
                }
            }
        }.start()
    }

    private fun startQueue(items: List<PlaylistItem>) {
        queue = items
        val st = Playlist.state(this, playlistUrl)
        val start = if (prefResume) st.first.coerceIn(0, items.lastIndex) else 0
        val pos = if (prefResume && st.first == start) st.second else 0L
        val multi = if (items.size > 1) View.VISIBLE else View.GONE
        btnPrev.visibility = multi
        btnNext.visibility = multi
        DebugLog.log("playlist: ${items.size} items, start $start")
        playIndex(start, pos)
    }

    private fun playIndex(i: Int, resume: Long) {
        if (i !in queue.indices) return
        saveVideoSettings()
        val item = queue[i]
        queueIndex = i
        url = item.url
        entry = Entry(url = item.url, title = item.title, headers = playlistEntry?.headers ?: "")
        headers = Store.parseHeaders(entry.headers)
        subRaw = null
        subtitleUri = null
        subtitleMime = null
        autoSubsTried = false
        mimeOverride = null
        fallbackStage = 0
        retryCount = 0
        retryPending = false
        firstReady = true
        firstFrameDrawn = false
        handler.removeCallbacks(noPictureCheck)
        handler.removeCallbacks(retryTick)
        applyPerVideo()
        resizeIndex = if (savedResize in RESIZE_MODES.indices) savedResize else prefResizeIndex.coerceIn(0, RESIZE_MODES.size - 1)
        playerView.resizeMode = RESIZE_MODES[resizeIndex]
        btnSpeed.text = SPEED_LABELS[speedIndex]
        titleText.text = item.title + "  (" + (i + 1) + "/" + queue.size + ")"
        resumePos = resume
        resumeLabel = resume
        val p = player
        if (p != null) {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .clearOverrides()
                .setPreferredAudioLanguage((audioLangSel.ifBlank { prefAudioLang }).ifBlank { null })
                .setPreferredTextLanguage((textLangSel.ifBlank { prefTextLang }).ifBlank { null })
                .build()
            if (resume > 0) p.setMediaItem(buildItem(), resume) else p.setMediaItem(buildItem())
            p.playWhenReady = true
            p.prepare()
            p.setPlaybackSpeed(SPEEDS[speedIndex])
            applyBoost()
            spinner.visibility = View.VISIBLE
        } else if (started) {
            initPlayer()
        }
    }

    private fun nextItem() {
        if (queue.isEmpty()) return
        if (queueIndex < queue.lastIndex) playIndex(queueIndex + 1, 0L) else flash("Last item")
    }

    private fun prevItem() {
        if (queue.isEmpty()) return
        val p = player
        if (p != null && p.currentPosition > 5_000) {
            p.seekTo(0)
        } else if (queueIndex > 0) {
            playIndex(queueIndex - 1, 0L)
        } else {
            flash("First item")
        }
    }

    private fun showPlaylistSheet() {
        if (queue.isEmpty()) return
        // very long lists (IPTV) only show the items around the current one
        val from = (queueIndex - 100).coerceAtLeast(0)
        val to = (queueIndex + 200).coerceAtMost(queue.size)
        val labels = (from until to).map { "${it + 1}. ${queue[it].title}" }
        val title = if (queue.size > to - from) "Playlist (${queue.size}, showing nearby)" else "Playlist (${queue.size})"
        Sheets.choose(this, title, labels, queueIndex - from) { idx -> playIndex(from + idx, 0L) }
    }

    /** Gives the playing video to the background audio service so the sound carries on. */
    private fun handoffToBackground(): Boolean {
        val p = player ?: return false
        if (!p.playWhenReady || p.playbackState == Player.STATE_ENDED || p.playbackState == Player.STATE_IDLE) return false
        val multi = queue.isNotEmpty()
        val items = if (multi) queue else listOf(PlaylistItem(Store.displayName(entry), url))
        return BackgroundAudio.start(
            this, items, if (multi) queueIndex else 0, if (multi) playlistUrl else "",
            entry.headers, if (multi) playlistUrl else url, p.currentPosition
        )
    }

    private fun startBackgroundNow() {
        if (player == null) return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            bgOneShot = true
            moveTaskToBack(true)
        }
    }

    private fun saveVideoSettings() {
        if (!prefRememberVideo || url.isBlank()) return
        PerVideo.save(
            this, url,
            PerVideo.Settings(speedIndex, resizeIndex, subDelayMs, boostPct, audioLangSel, textLangSel),
            prefResizeIndex.coerceIn(0, RESIZE_MODES.size - 1)
        )
    }

    private fun releasePlayer() {
        val p = player ?: return
        savePosition(p)
        saveVideoSettings()
        if (queue.isNotEmpty()) {
            Playlist.saveState(this, playlistUrl, queueIndex, if (p.playbackState == Player.STATE_ENDED) 0L else p.currentPosition)
        }
        flushUsage()
        resumePos = p.currentPosition
        resumePlay = p.playWhenReady
        handler.removeCallbacksAndMessages(null)
        unregisterNet()
        try {
            loudness?.release()
        } catch (e: Exception) {
            DebugLog.log("boost release: $e")
        }
        loudness = null
        playerView.player = null
        p.release()
        player = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun buildItem(): MediaItem {
        val b = MediaItem.Builder().setUri(url)
        val mime = mimeOverride ?: guessMime(url)
        if (mime != null) b.setMimeType(mime)
        val sub = subtitleUri
        if (sub != null) {
            b.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(sub)
                        .setMimeType(subtitleMime ?: MimeTypes.APPLICATION_SUBRIP)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
        }
        return b.build()
    }

    private fun guessMime(u: String): String? {
        val path = Uri.parse(u).path?.lowercase(Locale.US) ?: ""
        return when {
            path.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            path.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            path.contains(".ism") -> MimeTypes.APPLICATION_SS
            else -> null
        }
    }

    private fun supportName(s: Int) = when (s) {
        C.FORMAT_HANDLED -> "handled"
        C.FORMAT_EXCEEDS_CAPABILITIES -> "exceeds device limits"
        C.FORMAT_UNSUPPORTED_DRM -> "drm unsupported"
        C.FORMAT_UNSUPPORTED_SUBTYPE -> "profile/level unsupported"
        else -> "no decoder"
    }

    private fun logTracks(tracks: Tracks) {
        for (g in tracks.groups) {
            if (g.type != C.TRACK_TYPE_VIDEO && g.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until g.length) {
                val f = g.getTrackFormat(i)
                val kind = if (g.type == C.TRACK_TYPE_VIDEO) "video ${f.width}x${f.height}" else "audio ${f.channelCount}ch"
                DebugLog.log("$kind ${f.sampleMimeType} codecs=${f.codecs} -> ${supportName(g.getTrackSupport(i))}")
            }
        }
    }

    /** Tells the user plainly when the device cannot decode the picture or the sound. */
    private fun checkSupport(tracks: Tracks) {
        val msgs = ArrayList<String>()
        for (type in intArrayOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)) {
            val groups = tracks.groups.filter { it.type == type }
            if (groups.isEmpty()) continue
            val anyHandled = groups.any { g -> (0 until g.length).any { g.getTrackSupport(it) == C.FORMAT_HANDLED } }
            if (anyHandled) continue
            val g = groups[0]
            val f = g.getTrackFormat(0)
            val codecName = f.sampleMimeType?.substringAfter('/')?.uppercase(Locale.US) ?: "this format"
            msgs.add(
                if (type == C.TRACK_TYPE_VIDEO) {
                    "No picture: this device can't decode $codecName ${f.width}x${f.height} (${supportName(g.getTrackSupport(0))})."
                } else {
                    "No sound: this device can't decode $codecName audio (${supportName(g.getTrackSupport(0))})."
                }
            )
        }
        if (msgs.isEmpty()) return
        notice(msgs.joinToString("\n") + "\nTry Settings > Compatibility, or pick another audio or video track.")
    }

    private val hideNotice = Runnable { if (!retryPending) banner.visibility = View.GONE }

    private fun notice(text: String) {
        DebugLog.log("notice: $text")
        banner.text = text
        banner.visibility = View.VISIBLE
        handler.removeCallbacks(hideNotice)
        handler.postDelayed(hideNotice, 12_000)
    }

    /** Playing with a video track but nothing drawn after a few seconds: black-picture case. */
    private val noPictureCheck = Runnable {
        val p = player ?: return@Runnable
        if (!firstFrameDrawn && p.videoFormat != null && p.playbackState == Player.STATE_READY) {
            notice("Sound is playing but no picture is drawn. Try Settings > Compatibility: Software first, or TextureView.")
        }
    }

    private fun stateName(s: Int) = when (s) {
        Player.STATE_IDLE -> "idle"
        Player.STATE_BUFFERING -> "buffering"
        Player.STATE_READY -> "ready"
        Player.STATE_ENDED -> "ended"
        else -> "?"
    }

    private fun onReady() {
        retryPending = false
        retryCount = 0
        handler.removeCallbacks(retryTick)
        banner.visibility = View.GONE
        if (firstReady) {
            firstReady = false
            if (resumeLabel > 0) flash("Resumed at ${Fmt.time(resumeLabel)}")
            autoFindSubs()
            handler.postDelayed(noPictureCheck, 5000)
        }
    }

    /** Keep the screen awake only while the video is actually playing or loading. */
    private fun updateKeepOn() {
        val p = player
        val on = p != null && p.playWhenReady &&
            (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING)
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ---------------------------------------------------------------- resume + data usage

    private val saveTick = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (p.isPlaying) {
                savePosition(p)
                flushUsage()
            }
            handler.postDelayed(this, 20_000)
        }
    }

    private fun savePosition(p: ExoPlayer) {
        if (p.isCurrentMediaItemLive) return
        val d = p.duration
        if (d == C.TIME_UNSET || d <= 0) return
        val pos = p.currentPosition
        entry.pos = if (pos > d - 10_000 || pos < 3_000) 0 else pos
        entry.dur = d
        Store.update(this, entry)
    }

    private fun flushUsage() {
        val now = NetStats.bytes.get()
        DataUsage.add(this, now - lastFlushed)
        lastFlushed = now
    }

    // ---------------------------------------------------------------- errors + auto-reconnect

    private val retryTick = object : Runnable {
        override fun run() {
            if (!retryPending) return
            if (retryLeft <= 0) {
                doRetry()
                return
            }
            banner.text = "Connection lost. Retrying in ${retryLeft}s ($retryCount/${prefMaxRetries}). Tap to retry now"
            banner.visibility = View.VISIBLE
            retryLeft--
            handler.postDelayed(this, 1000)
        }
    }

    private fun scheduleRetry() {
        retryCount++
        retryLeft = minOf(2 shl minOf(retryCount - 1, 3), 15)
        retryPending = true
        spinner.visibility = View.GONE
        handler.removeCallbacks(retryTick)
        retryTick.run()
    }

    private fun doRetry() {
        retryPending = false
        banner.text = "Reconnecting…"
        banner.visibility = View.VISIBLE
        DebugLog.log("retry $retryCount")
        player?.prepare()
    }

    private fun friendlyError(e: PlaybackException): String {
        val c = e.cause
        if (c is HttpDataSource.InvalidResponseCodeException) {
            return when (c.responseCode) {
                401 -> "The server wants a login (401). Add the right headers to this link."
                403 -> "The server refused access (403). The link may have expired, or it needs custom headers such as a referer or cookie."
                404 -> "Nothing was found at this link (404). Check the address."
                410 -> "This link has expired (410). Get a fresh link and paste it again."
                429 -> "The server says too many requests (429). Wait a bit and retry."
                else -> "The server answered with an error (${c.responseCode})."
            }
        }
        if (e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
            e.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
        ) return "This file format isn't supported, or the link isn't a video."
        if (isNetworkError(e)) return "Can't reach the server. Check your connection."
        return "Playback failed."
    }

    private fun isNetworkError(e: PlaybackException): Boolean {
        when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> return true
        }
        val c = e.cause
        if (c is HttpDataSource.InvalidResponseCodeException) return c.responseCode >= 500 || c.responseCode == 429
        return false
    }

    private fun registerNet() {
        if (netCallback != null) return
        try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handler.post {
                        if (retryPending) {
                            handler.removeCallbacks(retryTick)
                            doRetry()
                        }
                    }
                }
            }
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb
            )
            netCallback = cb
        } catch (e: Exception) {
            DebugLog.log("network callback failed: $e")
        }
    }

    private fun unregisterNet() {
        val cb = netCallback ?: return
        try {
            (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(cb)
        } catch (e: Exception) {
            DebugLog.log("network callback remove failed: $e")
        }
        netCallback = null
    }

    private fun handleError(e: PlaybackException, skipPlaylistCheck: Boolean = false) {
        DebugLog.log("error ${e.errorCodeName} cause=${e.cause?.javaClass?.simpleName} ${e.cause?.message}")
        val p = player ?: return
        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            p.seekToDefaultPosition()
            p.prepare()
            return
        }
        // A .m3u8 that is really a list of links: load it as a playlist.
        if (!skipPlaylistCheck && queue.isEmpty() && !triedPlaylist && !playlistPending && url.startsWith("http") &&
            (e.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
                e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
        ) {
            triedPlaylist = true
            val src = url
            val hdr = headers
            Thread {
                val items = try { Playlist.load(this, src, hdr) } catch (x: Exception) { emptyList() }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (items.size > 1 || (items.size == 1 && items[0].url != src)) {
                        playlistUrl = src
                        playlistEntry = entry
                        startQueue(items)
                    } else {
                        handleError(e, true)
                    }
                }
            }.start()
            return
        }
        // Link has no recognisable extension: try it as HLS, then as DASH.
        if (e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED &&
            guessMime(url) == null && fallbackStage < FALLBACK_MIMES.size
        ) {
            mimeOverride = FALLBACK_MIMES[fallbackStage]
            fallbackStage++
            p.setMediaItem(buildItem())
            p.prepare()
            return
        }
        val decoderError = when (e.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> true
            else -> false
        }
        if (decoderError && decoderModeNow != Decoders.SOFTWARE_FIRST) {
            DebugLog.log("decoder error, retrying with software decoders first")
            decoderModeNow = Decoders.SOFTWARE_FIRST
            releasePlayer()
            initPlayer()
            flash("Switched to software decoder")
            return
        }
        if (prefReconnect && isNetworkError(e) && retryCount < (if (queue.isNotEmpty()) 2 else prefMaxRetries)) {
            scheduleRetry()
            return
        }
        if (queue.isNotEmpty() && queueIndex < queue.lastIndex) {
            notice("Can't play this item. Skipping to the next one.")
            playIndex(queueIndex + 1, 0L)
            return
        }
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Can't play this link")
            .setMessage(friendlyError(e) + "\n\n" + e.errorCodeName + "\n" + (e.cause?.message ?: e.message ?: ""))
            .setCancelable(false)
            .setPositiveButton("Retry") { _, _ ->
                retryCount = 0
                player?.prepare()
            }
            .setNeutralButton("Copy report") { _, _ ->
                copyReport()
                finish()
            }
            .setNegativeButton("Close") { _, _ -> finish() }
            .show()
    }

    // ---------------------------------------------------------------- controls

    private val hideRunnable = Runnable { showControls(false) }
    private val hideIndicator = Runnable { indicator.visibility = View.GONE }
    private val hideLockBtn = Runnable { if (locked) lockBtn.visibility = View.GONE }

    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            updateClock()
            handler.postDelayed(this, 500)
        }
    }

    private val statsRunnable = object : Runnable {
        override fun run() {
            statsText.text = buildStats()
            handler.postDelayed(this, 1000)
        }
    }

    private fun setupControls() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        btnPlay.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (p.playWhenReady && p.playbackState != Player.STATE_ENDED) {
                p.pause()
            } else {
                if (p.playbackState == Player.STATE_ENDED) p.seekToDefaultPosition()
                p.play()
            }
            scheduleHide()
        }
        findViewById<View>(R.id.btnRew).setOnClickListener {
            seekRelative(-10_000)
            scheduleHide()
        }
        findViewById<View>(R.id.btnFwd).setOnClickListener {
            seekRelative(10_000)
            scheduleHide()
        }
        findViewById<View>(R.id.btnPip).setOnClickListener { enterPip(true) }
        findViewById<View>(R.id.btnSub).setOnClickListener { showSubtitleSheet() }
        findViewById<View>(R.id.btnAudio).setOnClickListener { showTrackSheet(C.TRACK_TYPE_AUDIO, "Audio track", "Auto") }
        findViewById<View>(R.id.btnMore).setOnClickListener { showMoreSheet() }
        btnLock.setOnClickListener { setLocked(true) }
        btnSpeed.setOnClickListener { showSpeedSheet() }
        posText.setOnClickListener { showJumpDialog() }
        findViewById<View>(R.id.btnSize).setOnClickListener { showSizeSheet() }
        findViewById<View>(R.id.btnRotate).apply {
            setOnClickListener { rotateTap() }
            setOnLongClickListener {
                showRotateMenu()
                true
            }
        }
        findViewById<View>(R.id.btnStats).setOnClickListener {
            toggleStats()
            scheduleHide()
        }
        findViewById<View>(R.id.btnTune).setOnClickListener { showTuneSheet() }
        lockBtn.setOnClickListener {
            showLockButton()
            flash("Hold to unlock")
        }
        lockBtn.setOnLongClickListener {
            setLocked(false)
            true
        }
        banner.setOnClickListener {
            if (retryPending) {
                handler.removeCallbacks(retryTick)
                doRetry()
            }
        }

        seekBar.max = 1000
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val d = player?.duration ?: return
                if (d > 0 && d != C.TIME_UNSET) posText.text = Fmt.time(d * progress / 1000)
            }

            override fun onStartTrackingTouch(s: SeekBar) {
                userSeeking = true
                handler.removeCallbacks(hideRunnable)
            }

            override fun onStopTrackingTouch(s: SeekBar) {
                userSeeking = false
                val p = player
                if (p != null && p.duration > 0 && p.duration != C.TIME_UNSET) {
                    p.seekTo(p.duration * s.progress / 1000)
                }
                scheduleHide()
            }
        })
    }

    private fun showControls(show: Boolean) {
        val visible = show && !locked && !inPip
        controlsVisible = visible
        controls.visibility = if (visible) View.VISIBLE else View.GONE
        handler.removeCallbacks(progressRunnable)
        handler.removeCallbacks(hideRunnable)
        if (visible) {
            updateProgress()
            updateClock()
            handler.postDelayed(progressRunnable, 500)
            scheduleHide()
        }
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        val p = player
        if (controlsVisible && p != null && p.isPlaying && !userSeeking) {
            handler.postDelayed(hideRunnable, 4000)
        }
    }

    private fun updatePlayIcon() {
        val p = player
        val playing = p != null && p.playWhenReady && p.playbackState != Player.STATE_ENDED
        btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun updateProgress() {
        val p = player ?: return
        val dur = p.duration
        val live = p.isCurrentMediaItemLive || dur == C.TIME_UNSET || dur <= 0
        seekBar.isEnabled = !live
        if (!userSeeking) {
            if (live) {
                seekBar.progress = 0
                seekBar.secondaryProgress = 0
            } else {
                seekBar.progress = (p.currentPosition * 1000 / dur).toInt().coerceIn(0, 1000)
                seekBar.secondaryProgress = (p.bufferedPosition * 1000 / dur).toInt().coerceIn(0, 1000)
            }
            posText.text = Fmt.time(p.currentPosition)
        }
        durText.text = if (live) "LIVE" else Fmt.time(dur)
    }

    private fun updateClock() {
        val b = batteryInfo()
        val time = DateFormat.getTimeFormat(this).format(Date())
        var text = if (b.first >= 0) "$time  ·  ${b.first}%" + (if (b.second) " (charging)" else "") else time
        val p = player
        if (p != null && p.playWhenReady && !p.isCurrentMediaItemLive && p.duration != C.TIME_UNSET && p.duration > 0) {
            val leftMs = ((p.duration - p.currentPosition) / p.playbackParameters.speed).toLong()
            if (leftMs > 0) {
                text += "  ·  ends " + DateFormat.getTimeFormat(this).format(Date(System.currentTimeMillis() + leftMs))
            }
        }
        clockText.text = text
    }

    private fun seekRelative(ms: Long) {
        val p = player ?: return
        val dur = p.duration
        val max = if (dur != C.TIME_UNSET && dur > 0) dur else Long.MAX_VALUE
        p.seekTo((p.currentPosition + ms).coerceIn(0L, max))
        updateProgress()
    }

    private fun flash(text: String) {
        indicator.text = text
        indicator.visibility = View.VISIBLE
        handler.removeCallbacks(hideIndicator)
        handler.postDelayed(hideIndicator, 1100)
    }

    // ---------------------------------------------------------------- lock

    private fun setLocked(l: Boolean) {
        locked = l
        if (l) {
            showControls(false)
            showLockButton()
            flash("Controls locked")
        } else {
            lockBtn.visibility = View.GONE
            handler.removeCallbacks(hideLockBtn)
            showControls(true)
            flash("Unlocked")
        }
        btnLock.setImageResource(if (l) R.drawable.ic_lock else R.drawable.ic_lock_open)
    }

    private fun showLockButton() {
        lockBtn.visibility = View.VISIBLE
        handler.removeCallbacks(hideLockBtn)
        handler.postDelayed(hideLockBtn, 2500)
    }

    // ---------------------------------------------------------------- gestures

    private fun setupGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (locked) {
                    showLockButton()
                } else if (e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
                    // mouse or air mouse: a click on the picture plays / pauses
                    togglePlay()
                    showControls(true)
                } else {
                    showControls(!controlsVisible)
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (locked || gestureMode != 0 || player == null) return
                gestureMode = 4
                longPress = true
                player?.setPlaybackSpeed(2f)
                flash("2\u00d7 speed")
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (locked) return true
                val back = e.x < gestureLayer.width / 2f
                seekRelative(if (back) -10_000 else 10_000)
                flash(if (back) "-10s" else "+10s")
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (locked || e1 == null) return false
                if (gestureMode == 0) {
                    gestureMode = when {
                        abs(distanceX) > abs(distanceY) -> 1
                        // keep the middle 30% free of vertical swipes so a stray touch doesn't change anything
                        e1.x < gestureLayer.width * 0.35f -> 2
                        e1.x > gestureLayer.width * 0.65f -> 3
                        else -> 4
                    }
                    brightnessF = currentBrightness()
                    volumeF = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                }
                val h = gestureLayer.height.coerceAtLeast(1).toFloat()
                when (gestureMode) {
                    1 -> {
                        seekDeltaMs = ((e2.x - e1.x) / gestureLayer.width.coerceAtLeast(1) * 120_000).toLong()
                        val s = seekDeltaMs / 1000
                        flash((if (s >= 0) "+" else "") + s + "s")
                    }
                    2 -> {
                        brightnessF = (brightnessF + distanceY / h * 1.5f).coerceIn(0.02f, 1f)
                        setBrightness(brightnessF)
                        flash("Brightness " + (brightnessF * 100).roundToInt() + "%")
                    }
                    3 -> {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        volumeF = (volumeF + distanceY / h * max * 1.5f).coerceIn(0f, max.toFloat())
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volumeF.roundToInt(), 0)
                        flash("Volume " + (volumeF / max * 100).roundToInt() + "%")
                    }
                }
                return true
            }
        })

        gestureLayer.setOnTouchListener { _, ev ->
            detector.onTouchEvent(ev)
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (longPress) {
                    longPress = false
                    player?.setPlaybackSpeed(SPEEDS[speedIndex])
                    indicator.visibility = View.GONE
                }
                if (gestureMode == 1 && seekDeltaMs != 0L && !locked) seekRelative(seekDeltaMs)
                gestureMode = 0
                seekDeltaMs = 0
            }
            true
        }
    }

    private fun systemBrightness(): Float = try {
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (e: Exception) {
        0.5f
    }

    private fun currentBrightness(): Float {
        val b = window.attributes.screenBrightness
        return if (b >= 0f) b else systemBrightness()
    }

    private fun setBrightness(v: Float) {
        val lp = window.attributes
        lp.screenBrightness = v.coerceIn(0.01f, 1f)
        window.attributes = lp
    }

    // ---------------------------------------------------------------- rotation

    private fun applyOrientation() {
        requestedOrientation = when (orient) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            3 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            4 -> {
                val vs = player?.videoSize
                when {
                    vs == null || vs.width <= 0 -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    vs.height > vs.width -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    /** Tap: rotate to the orientation that fits this video best; tap again to flip to the other. */
    private fun rotateTap() {
        val landscapeNow = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val vs = player?.videoSize
        val wide = vs == null || vs.width <= 0 || vs.width >= vs.height
        orient = if (landscapeNow != wide) {
            if (wide) 1 else 3
        } else {
            if (landscapeNow) 3 else 1
        }
        applyOrientation()
        resizeIndex = 0
        playerView.resizeMode = RESIZE_MODES[0]
        flash(if (orient == 1) "Landscape, fit to screen" else "Portrait, fit to screen")
        scheduleHide()
    }

    private fun showRotateMenu() {
        Sheets.choose(this, "Screen rotation", ORIENT_NAMES, orient) { i ->
            orient = i
            applyOrientation()
            flash(ORIENT_NAMES[i])
        }
    }

    // ---------------------------------------------------------------- picture in picture

    private fun pipSupported(): Boolean =
        Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun enterPip(manual: Boolean) {
        if (!pipSupported()) {
            if (manual) Toast.makeText(this, "Picture-in-picture is not available on this device", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            enterPictureInPictureMode(buildPipParams())
        } catch (e: Exception) {
            DebugLog.log("pip failed: $e")
            if (manual) Toast.makeText(this, "Could not start picture-in-picture", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(26)
    private fun buildPipParams(): PictureInPictureParams {
        val vs = player?.videoSize
        var w = vs?.width ?: 0
        var h = vs?.height ?: 0
        if (w <= 0 || h <= 0) {
            w = 16
            h = 9
        }
        val r = w.toFloat() / h
        if (r > 2.39f) {
            w = 239
            h = 100
        } else if (r < 0.42f) {
            w = 42
            h = 100
        }
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(w, h))
            .setActions(listOf(pipAction()))
            .build()
    }

    @RequiresApi(26)
    private fun pipAction(): RemoteAction {
        val playing = player?.playWhenReady == true
        val pi = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_PIP).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val icon = Icon.createWithResource(this, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        val t = if (playing) "Pause" else "Play"
        return RemoteAction(icon, t, t, pi)
    }

    private fun updatePip() {
        if (!pipSupported()) return
        try {
            setPictureInPictureParams(buildPipParams())
        } catch (e: Exception) {
            DebugLog.log("pip update failed: $e")
        }
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PIP) return
            player?.let { if (it.playWhenReady) it.pause() else it.play() }
            updatePip()
        }
    }

    private fun registerPipReceiver() {
        if (pipReceiverOn) return
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED)
        pipReceiverOn = true
    }

    private fun unregisterPipReceiver() {
        if (!pipReceiverOn) return
        try {
            unregisterReceiver(pipReceiver)
        } catch (e: Exception) {
            DebugLog.log("pip receiver: $e")
        }
        pipReceiverOn = false
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (inPip) {
            showControls(false)
            statsText.visibility = View.GONE
            indicator.visibility = View.GONE
            registerPipReceiver()
            updatePip()
        } else {
            unregisterPipReceiver()
            statsText.visibility = if (statsVisible) View.VISIBLE else View.GONE
            if (!locked) showControls(true)
        }
    }

    // ---------------------------------------------------------------- subtitles

    private fun applySubtitleStyle() {
        val sv = playerView.subtitleView ?: return
        val color = SUB_COLORS[prefSubColor.coerceIn(0, SUB_COLORS.size - 1)]
        val style = when (prefSubStyle) {
            0 -> CaptionStyleCompat(color, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null)
            1 -> CaptionStyleCompat(color, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.BLACK, null)
            else -> CaptionStyleCompat(color, 0xB3000000.toInt(), Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, Color.BLACK, null)
        }
        sv.setApplyEmbeddedStyles(false)
        sv.setApplyEmbeddedFontSizes(false)
        sv.setStyle(style)
        sv.setFractionalTextSize(0.0533f * prefSubSize / 100f)
        sv.setBottomPaddingFraction(0.08f + prefSubPos / 100f)
    }

    private fun loadSubtitleUri(uri: Uri) {
        val queried: String? = try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            }
        } catch (e: Exception) {
            null
        }
        val name = queried ?: uri.lastPathSegment ?: ""
        val text = try {
            contentResolver.openInputStream(uri)?.use { SubtitleTools.readCapped(it, SubtitleTools.MAX_SUBTITLE_BYTES) }?.let { SubtitleTools.decode(it) }
        } catch (e: Exception) {
            null
        }
        if (text == null) {
            Toast.makeText(this, "Could not read that subtitle file", Toast.LENGTH_SHORT).show()
            return
        }
        loadSubtitleText(text, name)
    }

    private fun loadSubtitleText(text: String, name: String) {
        subRaw = text
        subKind = SubtitleTools.kindOf(name, text)
        subDelayMs = 0
        applySubtitleFile()
    }

    /** Writes the (optionally delayed) subtitle to a cache file and reloads the media item. */
    private fun applySubtitleFile() {
        val raw = subRaw ?: return
        val ext = when (subKind) {
            SubtitleTools.KIND_VTT -> "vtt"
            SubtitleTools.KIND_ASS -> "ass"
            else -> "srt"
        }
        val content = if (subKind == SubtitleTools.KIND_ASS) raw else SubtitleTools.shift(raw, subDelayMs, subKind == SubtitleTools.KIND_VTT)
        try {
            val f = File(cacheDir, "sub_loaded.$ext")
            f.writeText(content)
            subtitleUri = Uri.fromFile(f)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not prepare the subtitle", Toast.LENGTH_SHORT).show()
            return
        }
        subtitleMime = when (subKind) {
            SubtitleTools.KIND_VTT -> MimeTypes.TEXT_VTT
            SubtitleTools.KIND_ASS -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        val pos = p.currentPosition
        p.setMediaItem(buildItem(), pos)
        p.prepare()
        flash("Subtitle loaded")
    }

    private fun autoFindSubs() {
        if (!prefAutoSubs || subRaw != null || autoSubsTried) return
        autoSubsTried = true
        val hdr = headers
        Thread {
            val found = SubtitleTools.findNextToVideo(url, hdr)
            if (found != null) {
                runOnUiThread {
                    if (!isDestroyed && player != null && subRaw == null) {
                        loadSubtitleText(found.first, found.second)
                        flash("Subtitle found")
                    }
                }
            }
        }.start()
    }

    private fun searchSubtitlesOnline() {
        val q = Uri.encode(Store.displayName(entry).substringBeforeLast('.') + " subtitles srt")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$q")))
        } catch (e: Exception) {
            Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSubtitleSheet() {
        val p = player ?: return
        val opts = trackOptions(C.TRACK_TYPE_TEXT, "Off")
        val labels = opts.map { it.label }
        var dialog: BottomSheetDialog? = null
        val tracks = Sheets.optionRows(this, labels, checkedIndex(C.TRACK_TYPE_TEXT, opts)) { i ->
            selectTrack(C.TRACK_TYPE_TEXT, opts[i])
            dialog?.dismiss()
        }
        val actions = Sheets.optionRows(this, listOf("Load a subtitle file…", "Search subtitles online"), -1) { i ->
            dialog?.dismiss()
            if (i == 0) subPicker.launch(arrayOf("*/*")) else searchSubtitlesOnline()
        }
        val size = Sheets.stepper(this, "Size", "$prefSubSize%") { dir ->
            prefSubSize = (prefSubSize + dir * 10).coerceIn(50, 250)
            applySubtitleStyle()
            "$prefSubSize%"
        }
        val delay = Sheets.stepper(this, "Delay (loaded files)", delayText()) { dir ->
            if (subRaw == null || subKind == SubtitleTools.KIND_ASS) {
                Toast.makeText(this, "Delay works for .srt and .vtt files loaded here", Toast.LENGTH_SHORT).show()
            } else {
                subDelayMs += dir * 500L
                applySubtitleFile()
            }
            delayText()
        }
        dialog = Sheets.show(this, "Subtitles", tracks, actions, size, delay)
        if (p.currentTracks.groups.none { it.type == C.TRACK_TYPE_TEXT }) {
            DebugLog.log("subtitle sheet: stream has no text tracks")
        }
    }

    private fun delayText(): String = Fmt.f("%+.1fs", subDelayMs / 1000.0)

    // ---------------------------------------------------------------- tracks

    private fun trackOptions(type: Int, off: String): List<TrackOpt> {
        val p = player ?: return emptyList()
        val l = ArrayList<TrackOpt>()
        l.add(TrackOpt(off, null, -1))
        for (g in p.currentTracks.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                if (g.isTrackSupported(i)) l.add(TrackOpt(trackLabel(type, g.getTrackFormat(i)), g, i))
            }
        }
        return l
    }

    private fun checkedIndex(type: Int, opts: List<TrackOpt>): Int {
        val p = player ?: return 0
        val params = p.trackSelectionParameters
        if (params.disabledTrackTypes.contains(type)) return 0
        val manual = params.overrides.values.any { it.type == type }
        if (!manual && type != C.TRACK_TYPE_TEXT) return 0
        val idx = opts.indexOfFirst { it.group != null && it.group.isTrackSelected(it.index) }
        return if (idx >= 0) idx else 0
    }

    private fun selectTrack(type: Int, opt: TrackOpt) {
        val p = player ?: return
        val b = p.trackSelectionParameters.buildUpon()
        val g = opt.group
        if (g == null) {
            b.clearOverridesOfType(type)
            b.setTrackTypeDisabled(type, type == C.TRACK_TYPE_TEXT)
        } else {
            b.setTrackTypeDisabled(type, false)
            b.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, opt.index))
        }
        p.trackSelectionParameters = b.build()
        val lang = g?.getTrackFormat(opt.index)?.language?.takeIf { it != "und" } ?: ""
        if (type == C.TRACK_TYPE_AUDIO) audioLangSel = lang
        if (type == C.TRACK_TYPE_TEXT) textLangSel = lang
        DebugLog.log("track select type=$type ${opt.label}")
    }

    private fun showTrackSheet(type: Int, title: String, autoLabel: String) {
        val opts = trackOptions(type, autoLabel)
        if (opts.size <= 1) {
            Toast.makeText(this, "Only one option in this stream", Toast.LENGTH_SHORT).show()
            return
        }
        Sheets.choose(this, title, opts.map { it.label }, checkedIndex(type, opts)) { i ->
            selectTrack(type, opts[i])
        }
    }

    private fun trackLabel(type: Int, f: Format): String {
        val parts = ArrayList<String>()
        val lang = f.language
            ?.takeIf { it != "und" }
            ?.let { Locale.forLanguageTag(it).displayLanguage }
            ?.takeIf { it.isNotBlank() }
        when (type) {
            C.TRACK_TYPE_VIDEO -> {
                if (f.height > 0) parts.add("${f.height}p")
                if (f.frameRate > 0) parts.add("${f.frameRate.roundToInt()} fps")
                codec(f)?.let { parts.add(it) }
                if (f.bitrate > 0) parts.add(Fmt.f("%.1f Mbps", f.bitrate / 1_000_000.0))
            }
            C.TRACK_TYPE_AUDIO -> {
                val ln = lang ?: f.label?.takeIf { it.isNotBlank() && it.length <= 24 }
                ln?.let { parts.add(it) }
                audioCodecName(f)?.let { parts.add(it) }
                channelName(f.channelCount)?.let { parts.add(it) }
            }
            else -> {
                f.label?.let { parts.add(it) }
                lang?.let { if (!parts.contains(it)) parts.add(it) }
            }
        }
        return if (parts.isEmpty()) "Track" else parts.joinToString(" · ")
    }

    private fun channelName(n: Int): String? = when {
        n <= 0 -> null
        n == 1 -> "Mono"
        n == 2 -> "Stereo"
        n == 6 -> "5.1"
        n == 8 -> "7.1"
        else -> "${n}ch"
    }

    private fun audioCodecName(f: Format): String? = when (f.sampleMimeType) {
        null -> null
        "audio/ac3" -> "Dolby Digital"
        "audio/eac3" -> "Dolby Digital+"
        "audio/eac3-joc" -> "Dolby Atmos"
        "audio/true-hd" -> "Dolby TrueHD"
        "audio/vnd.dts" -> "DTS"
        "audio/vnd.dts.hd" -> "DTS-HD"
        "audio/vnd.dts.uhd;profile=p2" -> "DTS:X"
        "audio/mp4a-latm" -> "AAC"
        "audio/mpeg" -> "MP3"
        "audio/mpeg-L2" -> "MP2"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/flac" -> "FLAC"
        else -> codec(f)
    }

    private fun codec(f: Format): String? = f.sampleMimeType?.substringAfter('/')?.uppercase(Locale.US)

    // ---------------------------------------------------------------- sheets

    private fun showSpeedSheet() {
        Sheets.choose(this, "Playback speed", SPEED_LABELS, speedIndex) { i ->
            speedIndex = i
            player?.setPlaybackSpeed(SPEEDS[i])
            btnSpeed.text = SPEED_LABELS[i]
        }
    }

    private fun showSizeSheet() {
        Sheets.choose(this, "Screen size", RESIZE_NAMES, resizeIndex) { i ->
            resizeIndex = i
            playerView.resizeMode = RESIZE_MODES[i]
            prefResizeIndex = i
        }
    }

    private fun showMoreSheet() {
        val labels = listOf(
            "Video quality", "Start from the beginning", "Copy debug report", "Share link",
            "Sleep timer", "Jump to time", "Play audio in background"
        ) + (if (queue.isNotEmpty()) listOf("Playlist (${queue.size})") else emptyList())
        Sheets.choose(this, "More", labels, -1) { i ->
            when (i) {
                0 -> showTrackSheet(C.TRACK_TYPE_VIDEO, "Video quality", "Auto")
                1 -> {
                    player?.seekTo(0)
                    flash("Back to the start")
                }
                2 -> copyReport()
                3 -> startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url),
                        "Share link"
                    )
                )
                4 -> showSleepSheet()
                5 -> showJumpDialog()
                6 -> startBackgroundNow()
                7 -> showPlaylistSheet()
            }
        }
    }

    private fun copyReport() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("StreamLite debug", DebugLog.report(this, buildStats())))
        Toast.makeText(this, "Debug report copied", Toast.LENGTH_SHORT).show()
    }

    private fun showTuneSheet() {
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val bright = Sheets.sliderRow(
            this, "Brightness", 1f, 100f, 1f, (currentBrightness() * 100).roundToInt().toFloat(),
            { "${it.toInt()}%" }
        ) { setBrightness(it / 100f) }
        val vol = Sheets.sliderRow(
            this, "Volume", 0f, maxVol.toFloat(), 1f, audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat(),
            { "${(it / maxVol * 100).roundToInt()}%" }
        ) { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) }
        val boost = Sheets.sliderRow(
            this, "Volume boost (careful, can distort)", 0f, 100f, 5f, boostPct.toFloat(),
            { "+" + Fmt.f("%.0f", it * 12 / 100) + " dB" }
        ) {
            boostPct = it.toInt()
            applyBoost()
        }
        Sheets.show(this, "Brightness and volume", bright, vol, boost)
    }

    // ---------------------------------------------------------------- volume boost

    private fun setupBoost(sessionId: Int) {
        try {
            loudness?.release()
            loudness = if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != 0) LoudnessEnhancer(sessionId) else null
            applyBoost()
        } catch (e: Exception) {
            DebugLog.log("boost unavailable: $e")
            loudness = null
        }
    }

    private fun applyBoost() {
        val le = loudness ?: return
        try {
            le.setTargetGain(boostPct * 12)
            le.setEnabled(boostPct > 0)
        } catch (e: Exception) {
            DebugLog.log("boost failed: $e")
        }
    }

    // ---------------------------------------------------------------- stats

    private fun toggleStats() {
        statsVisible = !statsVisible
        statsText.visibility = if (statsVisible) View.VISIBLE else View.GONE
        handler.removeCallbacks(statsRunnable)
        val p = player
        if (statsVisible) {
            p?.setVideoFrameMetadataListener(frameListener)
            frameCount.set(0)
            lastStatT = SystemClock.elapsedRealtime()
            lastStatBytes = NetStats.bytes.get()
            statsText.text = "Measuring…"
            handler.postDelayed(statsRunnable, 1000)
        } else {
            p?.clearVideoFrameMetadataListener(frameListener)
        }
    }

    private fun batteryInfo(): Pair<Int, Boolean> {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return Pair(if (level < 0 || scale <= 0) -1 else level * 100 / scale, plugged != 0)
    }

    private fun buildStats(): String {
        val p = player ?: return ""
        val now = SystemClock.elapsedRealtime()
        val total = NetStats.bytes.get()
        val dt = (now - lastStatT).coerceAtLeast(1)
        val speed = (total - lastStatBytes) * 1000.0 / dt
        val fps = frameCount.getAndSet(0) * 1000.0 / dt
        lastStatT = now
        lastStatBytes = total

        val sb = StringBuilder()
        sb.append("Speed now : ").append(Fmt.speed(speed)).append('\n')
        val est = bandwidthMeter?.bitrateEstimate ?: 0L
        if (est > 0) sb.append("Est. link : ").append(Fmt.f("%.1f Mbps", est / 1e6)).append('\n')
        sb.append("Data used : ").append(Fmt.bytes(total)).append('\n')
        sb.append("Stalls    : ").append(stallCount).append('\n')

        val bufMs = p.totalBufferedDuration
        val vf = p.videoFormat
        val af = p.audioFormat
        val vBr = (vf?.bitrate ?: 0).coerceAtLeast(0)
        val aBr = (af?.bitrate ?: 0).coerceAtLeast(0)
        sb.append("Buffer    : ").append(Fmt.f("%.1f s", bufMs / 1000.0))
        if (vBr + aBr > 0) {
            val mb = bufMs / 1000.0 * (vBr + aBr) / 8.0 / 1048576.0
            sb.append(Fmt.f("  (~%.1f of %d MB)", mb, prefBufferMb))
        } else {
            sb.append("  (limit ").append(prefBufferMb).append(" MB)")
        }
        sb.append('\n')

        val size = p.videoSize
        if (vf != null || size.width > 0) {
            sb.append("Video     : ").append(size.width).append('x').append(size.height)
            if (vf != null) {
                codec(vf)?.let { sb.append(' ').append(it) }
                if (vBr > 0) sb.append(' ').append(Fmt.f("%.1f Mbps", vBr / 1e6))
            }
            sb.append('\n')
            if (statsVisible) {
                sb.append("FPS       : ").append(Fmt.f("%.1f", fps))
                if (vf != null && vf.frameRate > 0) sb.append(Fmt.f(" (source %.0f)", vf.frameRate.toDouble()))
                sb.append("  dropped ").append(droppedTotal).append('\n')
            } else {
                sb.append("FPS       : (open Stats to measure)  dropped ").append(droppedTotal).append('\n')
            }
        }
        if (af != null) {
            sb.append("Audio     : ").append(codec(af) ?: "?")
            if (af.channelCount > 0) sb.append(' ').append(af.channelCount).append("ch")
            if (af.sampleRate > 0) sb.append(' ').append(af.sampleRate / 1000).append("kHz")
            sb.append('\n')
        }
        sb.append("Decoder   : ").append(decoderName).append('\n')

        val b = batteryInfo()
        val hours = (now - battStartT) / 3_600_000.0
        val drop = battStart - b.first
        val batt = when {
            b.first < 0 -> "n/a"
            b.second -> "${b.first}% (charging)"
            hours >= 0.03 && drop >= 1 -> Fmt.f("%d%% (~%.1f%%/h)", b.first, drop / hours)
            else -> "${b.first}% (measuring…)"
        }
        sb.append("Battery   : ").append(batt).append('\n')
        sb.append("State     : ").append(
            when (p.playbackState) {
                Player.STATE_IDLE -> "idle"
                Player.STATE_BUFFERING -> "buffering"
                Player.STATE_READY -> if (p.playWhenReady) "playing" else "paused"
                Player.STATE_ENDED -> "ended"
                else -> "?"
            }
        )
        return sb.toString()
    }
}
