package com.imesh.streamlite

import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private class TrackOpt(
    val label: String,
    val group: Tracks.Group?,
    val index: Int,
    val action: (() -> Unit)? = null
)

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "url"
        private val RESIZE_NAMES = arrayOf(
            "Auto / Fit",
            "Full (stretch)",
            "Zoom (crop)",
            "Fit width",
            "Fit height"
        )
        private val RESIZE_MODES = intArrayOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
        )
        private val ORIENTATION_NAMES = arrayOf("Rotation: auto", "Rotation: landscape", "Rotation: portrait")
        private val FALLBACK_MIMES = arrayOf(MimeTypes.APPLICATION_M3U8, MimeTypes.APPLICATION_MPD)
    }

    private var player: ExoPlayer? = null
    private var bandwidthMeter: DefaultBandwidthMeter? = null
    private val handler = Handler(Looper.getMainLooper())
    private val audio by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    private lateinit var playerView: PlayerView
    private lateinit var gestureLayer: View
    private lateinit var controls: View
    private lateinit var titleText: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var posText: TextView
    private lateinit var durText: TextView
    private lateinit var statsText: TextView
    private lateinit var indicator: TextView
    private lateinit var spinner: ProgressBar

    private var url = ""
    private var mimeOverride: String? = null
    private var fallbackStage = 0
    private var subtitleUri: Uri? = null
    private var subtitleMime: String? = null

    private var resumePos = 0L
    private var resumePlay = true
    private var controlsVisible = true
    private var statsVisible = false
    private var userSeeking = false
    private var resizeIndex = 0
    private var orientationMode = 0

    private var decoderName = "-"
    private var droppedTotal = 0L
    private var lastStatT = 0L
    private var lastStatBytes = 0L

    // gesture state: 0 none, 1 seek, 2 brightness, 3 volume
    private var gestureMode = 0
    private var seekDeltaMs = 0L
    private var brightnessF = 0.5f
    private var volumeF = 0f

    private val subPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addSubtitle(uri)
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        volumeControlStream = AudioManager.STREAM_MUSIC

        url = intent.getStringExtra(EXTRA_URL) ?: intent.dataString ?: ""
        if (url.isBlank()) {
            finish()
            return
        }
        NetStats.bytes.set(0)

        playerView = findViewById(R.id.playerView)
        gestureLayer = findViewById(R.id.gestureLayer)
        controls = findViewById(R.id.controls)
        titleText = findViewById(R.id.title)
        btnPlay = findViewById(R.id.btnPlay)
        seekBar = findViewById(R.id.seekBar)
        posText = findViewById(R.id.posText)
        durText = findViewById(R.id.durText)
        statsText = findViewById(R.id.stats)
        indicator = findViewById(R.id.indicator)
        spinner = findViewById(R.id.spinner)

        titleText.text = Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() }
            ?: Uri.parse(url).host
            ?: url

        resizeIndex = Prefs.resizeIndex(this).coerceIn(0, RESIZE_MODES.size - 1)
        playerView.resizeMode = RESIZE_MODES[resizeIndex]

        setupControls()
        setupGestures()
        enterImmersive()
    }

    override fun onStart() {
        super.onStart()
        if (url.isNotBlank()) initPlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // ---------------------------------------------------------------- player

    private fun initPlayer() {
        if (player != null) return

        val meter = DefaultBandwidthMeter.Builder(this).build()
        bandwidthMeter = meter

        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setUserAgent("Mozilla/5.0 (Linux; Android) StreamLite/1.0")
        val dataSourceFactory = DefaultDataSource.Factory(this, http)
            .setTransferListener(CountingTransferListener(meter.transferListener))

        val trackSelector = DefaultTrackSelector(this)
        val heights = Prefs.RES_HEIGHTS
        val maxH = heights[Prefs.maxRes(this).coerceIn(0, heights.size - 1)]
        if (maxH > 0) {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setMaxVideoSize(maxH * 16 / 9, maxH)
                    .build()
            )
        }

        // Buffer limited by size in MB (user setting). Once the target is reached the
        // app stops downloading, which lets the radio idle and saves power.
        val targetBytes = Prefs.bufferMb(this) * 1024 * 1024
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30 * 60 * 1000, 30 * 60 * 1000, 2_500, 5_000)
            .setTargetBufferBytes(targetBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(20_000, false)
            .build()

        val p = ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableDecoderFallback(true))
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
                spinner.visibility = if (playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                updatePlayIcon()
                if (playbackState == Player.STATE_ENDED) showControls(true)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayIcon()
                scheduleHide()
            }

            override fun onPlayerError(error: PlaybackException) {
                handleError(error)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {}
        })

        p.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                this@PlayerActivity.decoderName = decoderName
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

        if (resumePos > 0) p.setMediaItem(buildItem(), resumePos) else p.setMediaItem(buildItem())
        p.playWhenReady = resumePlay
        p.prepare()

        updatePlayIcon()
        if (controlsVisible) {
            showControls(true)
        }
        if (statsVisible) {
            lastStatT = SystemClock.elapsedRealtime()
            lastStatBytes = NetStats.bytes.get()
            handler.post(statsRunnable)
        }
    }

    private fun releasePlayer() {
        val p = player ?: return
        resumePos = p.currentPosition
        resumePlay = p.playWhenReady
        handler.removeCallbacksAndMessages(null)
        playerView.player = null
        p.release()
        player = null
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
        val path = Uri.parse(u).path?.lowercase() ?: ""
        return when {
            path.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            path.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            path.contains(".ism") -> MimeTypes.APPLICATION_SS
            else -> null
        }
    }

    private fun handleError(e: PlaybackException) {
        val p = player ?: return
        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            p.seekToDefaultPosition()
            p.prepare()
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
        if (isFinishing) return
        AlertDialog.Builder(this)
            .setTitle("Can't play this link")
            .setMessage(e.errorCodeName + "\n" + (e.cause?.message ?: e.message ?: ""))
            .setCancelable(false)
            .setPositiveButton("Retry") { _, _ -> player?.prepare() }
            .setNegativeButton("Close") { _, _ -> finish() }
            .show()
    }

    // ---------------------------------------------------------------- controls

    private val hideRunnable = Runnable { showControls(false) }

    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    private val statsRunnable = object : Runnable {
        override fun run() {
            statsText.text = buildStats()
            handler.postDelayed(this, 1000)
        }
    }

    private val hideIndicator = Runnable { indicator.visibility = View.GONE }

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
        findViewById<View>(R.id.btnRew).setOnClickListener { seekRelative(-10_000); scheduleHide() }
        findViewById<View>(R.id.btnFwd).setOnClickListener { seekRelative(10_000); scheduleHide() }
        findViewById<View>(R.id.btnStats).setOnClickListener { toggleStats(); scheduleHide() }
        findViewById<View>(R.id.btnSub).setOnClickListener { showTrackDialog(C.TRACK_TYPE_TEXT, "Subtitles") }
        findViewById<View>(R.id.btnAudio).setOnClickListener { showTrackDialog(C.TRACK_TYPE_AUDIO, "Audio") }
        findViewById<View>(R.id.btnVideo).setOnClickListener { showTrackDialog(C.TRACK_TYPE_VIDEO, "Video quality") }
        findViewById<View>(R.id.btnSize).setOnClickListener { showSizeDialog() }
        findViewById<View>(R.id.btnRotate).setOnClickListener { cycleOrientation(); scheduleHide() }
        findViewById<View>(R.id.btnTune).setOnClickListener { showTuneDialog() }

        seekBar.max = 1000
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val d = player?.duration ?: return
                if (d > 0 && d != C.TIME_UNSET) posText.text = fmtTime(d * progress / 1000)
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
        controlsVisible = show
        controls.visibility = if (show) View.VISIBLE else View.GONE
        handler.removeCallbacks(progressRunnable)
        handler.removeCallbacks(hideRunnable)
        if (show) {
            updateProgress()
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
            posText.text = fmtTime(p.currentPosition)
        }
        durText.text = if (live) "LIVE" else fmtTime(dur)
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
        handler.postDelayed(hideIndicator, 900)
    }

    // ---------------------------------------------------------------- gestures

    private fun setupGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                showControls(!controlsVisible)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val back = e.x < gestureLayer.width / 2f
                seekRelative(if (back) -10_000 else 10_000)
                flash(if (back) "-10s" else "+10s")
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null) return false
                if (gestureMode == 0) {
                    gestureMode = when {
                        abs(distanceX) > abs(distanceY) -> 1
                        e1.x < gestureLayer.width / 2f -> 2
                        else -> 3
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
                if (gestureMode == 1 && seekDeltaMs != 0L) seekRelative(seekDeltaMs)
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

    // ---------------------------------------------------------------- dialogs

    private fun showTuneDialog() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(12), dp(20), dp(4))

        val bLabel = TextView(this)
        bLabel.text = "Brightness"
        val bSeek = SeekBar(this)
        bSeek.max = 100
        bSeek.progress = (currentBrightness() * 100).roundToInt()
        bSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) setBrightness(progress.coerceAtLeast(1) / 100f)
            }

            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })

        val vLabel = TextView(this)
        vLabel.text = "Volume"
        vLabel.setPadding(0, dp(12), 0, 0)
        val vSeek = SeekBar(this)
        vSeek.max = max
        vSeek.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        vSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) audio.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
            }

            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })

        root.addView(bLabel)
        root.addView(bSeek)
        root.addView(vLabel)
        root.addView(vSeek)

        AlertDialog.Builder(this)
            .setTitle("Brightness / Volume")
            .setView(root)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showSizeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Screen size")
            .setSingleChoiceItems(RESIZE_NAMES, resizeIndex) { d, which ->
                resizeIndex = which
                playerView.resizeMode = RESIZE_MODES[which]
                Prefs.setResizeIndex(this, which)
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cycleOrientation() {
        orientationMode = (orientationMode + 1) % 3
        requestedOrientation = when (orientationMode) {
            0 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        flash(ORIENTATION_NAMES[orientationMode])
    }

    private fun showTrackDialog(trackType: Int, title: String) {
        val p = player ?: return
        val isText = trackType == C.TRACK_TYPE_TEXT

        val opts = ArrayList<TrackOpt>()
        opts.add(TrackOpt(if (isText) "Off" else "Auto", null, -1))
        for (g in p.currentTracks.groups) {
            if (g.type != trackType) continue
            for (i in 0 until g.length) {
                if (g.isTrackSupported(i)) opts.add(TrackOpt(trackLabel(trackType, g.getTrackFormat(i)), g, i))
            }
        }
        if (isText) opts.add(TrackOpt("Load subtitle file...", null, -1) { subPicker.launch(arrayOf("*/*")) })

        if (opts.size == 1) {
            Toast.makeText(this, "Only one option in this stream", Toast.LENGTH_SHORT).show()
            return
        }

        val params = p.trackSelectionParameters
        val manual = params.overrides.values.any { it.type == trackType }
        val disabled = params.disabledTrackTypes.contains(trackType)
        var checked = 0
        if (!(isText && disabled) && (manual || isText)) {
            val idx = opts.indexOfFirst { it.group != null && it.group.isTrackSelected(it.index) }
            if (idx >= 0) checked = idx
        }

        val labels = opts.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, checked) { d, which ->
                val opt = opts[which]
                val act = opt.action
                if (act != null) {
                    act()
                } else {
                    val b = p.trackSelectionParameters.buildUpon()
                    val g = opt.group
                    if (g == null) {
                        b.clearOverridesOfType(trackType)
                        b.setTrackTypeDisabled(trackType, isText)
                    } else {
                        b.setTrackTypeDisabled(trackType, false)
                        b.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, opt.index))
                    }
                    p.trackSelectionParameters = b.build()
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                if (f.bitrate > 0) parts.add(fmt("%.1f Mbps", f.bitrate / 1_000_000.0))
            }
            C.TRACK_TYPE_AUDIO -> {
                lang?.let { parts.add(it) }
                f.label?.let { if (it != lang) parts.add(it) }
                codec(f)?.let { parts.add(it) }
                if (f.channelCount > 0) parts.add("${f.channelCount}ch")
                if (f.bitrate > 0) parts.add("${f.bitrate / 1000} kbps")
            }
            else -> {
                f.label?.let { parts.add(it) }
                lang?.let { if (!parts.contains(it)) parts.add(it) }
            }
        }
        return if (parts.isEmpty()) "Track" else parts.joinToString(" · ")
    }

    private fun codec(f: Format): String? = f.sampleMimeType?.substringAfter('/')?.uppercase(Locale.US)

    private fun addSubtitle(uri: Uri) {
        val queried = try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            }
        } catch (e: Exception) {
            null
        }
        val name = (queried ?: uri.lastPathSegment ?: "").lowercase(Locale.US)
        subtitleMime = when {
            name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            name.endsWith(".ttml") || name.endsWith(".xml") || name.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        subtitleUri = uri
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        val pos = p.currentPosition
        p.setMediaItem(buildItem(), pos)
        p.prepare()
        flash("Subtitle loaded")
    }

    // ---------------------------------------------------------------- stats

    private fun toggleStats() {
        statsVisible = !statsVisible
        statsText.visibility = if (statsVisible) View.VISIBLE else View.GONE
        handler.removeCallbacks(statsRunnable)
        if (statsVisible) {
            lastStatT = SystemClock.elapsedRealtime()
            lastStatBytes = NetStats.bytes.get()
            statsText.text = "Measuring..."
            handler.postDelayed(statsRunnable, 1000)
        }
    }

    private fun buildStats(): String {
        val p = player ?: return ""
        val now = SystemClock.elapsedRealtime()
        val total = NetStats.bytes.get()
        val dt = (now - lastStatT).coerceAtLeast(1)
        val speed = (total - lastStatBytes) * 1000 / dt // bytes per second
        lastStatT = now
        lastStatBytes = total

        val sb = StringBuilder()
        sb.append("Speed now : ").append(fmtSpeed(speed.toDouble())).append('\n')
        val est = bandwidthMeter?.bitrateEstimate ?: 0L
        if (est > 0) sb.append("Est. link : ").append(fmt("%.1f Mbps", est / 1e6)).append('\n')
        sb.append("Data used : ").append(fmtBytes(total)).append('\n')

        val bufMs = p.totalBufferedDuration
        val vBr = (p.videoFormat?.bitrate ?: 0).coerceAtLeast(0)
        val aBr = (p.audioFormat?.bitrate ?: 0).coerceAtLeast(0)
        val limitMb = Prefs.bufferMb(this)
        sb.append("Buffer    : ").append(fmt("%.1f s", bufMs / 1000.0))
        if (vBr + aBr > 0) {
            val mb = bufMs / 1000.0 * (vBr + aBr) / 8.0 / 1048576.0
            sb.append(fmt("  (~%.1f MB of %d MB)", mb, limitMb))
        } else {
            sb.append("  (limit ").append(limitMb).append(" MB)")
        }
        sb.append('\n')

        val vf = p.videoFormat
        val size = p.videoSize
        if (vf != null || size.width > 0) {
            sb.append("Video     : ").append(size.width).append('x').append(size.height)
            if (vf != null) {
                codec(vf)?.let { sb.append(' ').append(it) }
                if (vf.frameRate > 0) sb.append(' ').append(vf.frameRate.roundToInt()).append("fps")
            }
            if (vBr > 0) sb.append(' ').append(fmt("%.1f Mbps", vBr / 1e6))
            sb.append('\n')
        }
        val af = p.audioFormat
        if (af != null) {
            sb.append("Audio     : ").append(codec(af) ?: "?")
            if (af.channelCount > 0) sb.append(' ').append(af.channelCount).append("ch")
            if (af.sampleRate > 0) sb.append(' ').append(af.sampleRate / 1000).append("kHz")
            sb.append('\n')
        }
        sb.append("Decoder   : ").append(decoderName).append('\n')
        sb.append("Dropped   : ").append(droppedTotal).append(" frames\n")
        val state = when (p.playbackState) {
            Player.STATE_IDLE -> "idle"
            Player.STATE_BUFFERING -> "buffering"
            Player.STATE_READY -> if (p.playWhenReady) "playing" else "paused"
            Player.STATE_ENDED -> "ended"
            else -> "?"
        }
        sb.append("State     : ").append(state)
        return sb.toString()
    }

    // ---------------------------------------------------------------- helpers

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun fmt(format: String, vararg args: Any): String = String.format(Locale.US, format, *args)

    private fun fmtTime(ms: Long): String {
        if (ms < 0) return "0:00"
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) fmt("%d:%02d:%02d", h, m, sec) else fmt("%d:%02d", m, sec)
    }

    private fun fmtBytes(b: Long): String = when {
        b >= 1_073_741_824L -> fmt("%.2f GB", b / 1_073_741_824.0)
        b >= 1_048_576L -> fmt("%.1f MB", b / 1_048_576.0)
        else -> fmt("%.0f KB", b / 1024.0)
    }

    private fun fmtSpeed(bytesPerSec: Double): String =
        fmt("%.2f MB/s (%.1f Mbps)", bytesPerSec / 1_048_576.0, bytesPerSec * 8 / 1_000_000.0)
}
