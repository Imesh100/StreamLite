package com.imesh.streamlite

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import java.util.Locale

/**
 * Hand-over between the video player and the background audio service.
 * When the app goes to the background the player screen gives its position to the service, which keeps
 * playing the sound only (no video decoding, so it uses little power). When you come back, the video
 * continues from where the audio got to.
 */
object BackgroundAudio {
    class Handoff(val index: Int, val pos: Long, val playing: Boolean)

    @Volatile var active = false
    var items: List<PlaylistItem> = emptyList()
    var index = 0
    var playlistUrl = ""
    var headersRaw = ""
    var openUrl = ""
    var startPos = 0L
    private var last: Handoff? = null

    fun start(
        c: Context, items: List<PlaylistItem>, index: Int, playlistUrl: String,
        headersRaw: String, openUrl: String, pos: Long
    ): Boolean {
        if (items.isEmpty()) return false
        this.items = items
        this.index = index.coerceIn(0, items.lastIndex)
        this.playlistUrl = playlistUrl
        this.headersRaw = headersRaw
        this.openUrl = openUrl
        this.startPos = pos
        last = null
        active = true
        return try {
            ContextCompat.startForegroundService(c, Intent(c, AudioService::class.java))
            true
        } catch (e: Exception) {
            DebugLog.log("background audio could not start: $e")
            active = false
            false
        }
    }

    /** Stops the service if it is running and tells the player screen where the audio got to. */
    fun takeBack(c: Context): Handoff? {
        if (!active) {
            val h = last
            last = null
            return h
        }
        val s = AudioService.instance
        val h = Handoff(index, s?.position() ?: startPos, s?.isPlayingNow() ?: false)
        persist(c, h)
        active = false
        last = null
        c.stopService(Intent(c, AudioService::class.java))
        return h
    }

    /** Remember where we stopped so a freshly opened player screen resumes there. */
    fun persist(c: Context, h: Handoff) {
        if (playlistUrl.isNotBlank()) {
            Playlist.saveState(c, playlistUrl, h.index, h.pos)
        } else if (items.isNotEmpty()) {
            val e = Store.touch(c, items[0].url)
            e.pos = h.pos
            Store.update(c, e)
        }
    }

    fun finished(c: Context, pos: Long) {
        val h = Handoff(index, pos, false)
        persist(c, h)
        last = h
        active = false
    }
}

class AudioService : Service() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private var retryPending = false
    private var netCb: ConnectivityManager.NetworkCallback? = null
    private var startBytes = 0L
    private var started = false

    companion object {
        @Volatile var instance: AudioService? = null
        private const val CHANNEL = "playback"
        private const val ID = 71
        private const val ACT_TOGGLE = "com.imesh.streamlite.TOGGLE"
        private const val ACT_NEXT = "com.imesh.streamlite.NEXT"
        private const val ACT_PREV = "com.imesh.streamlite.PREV"
        private const val ACT_STOP = "com.imesh.streamlite.STOP"
    }

    fun position(): Long = player?.currentPosition ?: BackgroundAudio.startPos
    fun isPlayingNow(): Boolean = player?.playWhenReady == true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startBytes = NetStats.bytes.get()
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Background playback", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BackgroundAudio.active) {
            // started again after being stopped, nothing to play
            goForeground(buildNotification("StreamLite", false))
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACT_TOGGLE -> toggle()
            ACT_NEXT -> next()
            ACT_PREV -> prev()
            ACT_STOP -> {
                stopEverything()
            }
            else -> if (!started) begin()
        }
        return START_NOT_STICKY
    }

    private fun goForeground(n: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(ID, n)
            }
        } catch (e: Exception) {
            DebugLog.log("startForeground failed: $e")
        }
    }

    private fun title(): String = BackgroundAudio.items.getOrNull(BackgroundAudio.index)?.title ?: "StreamLite"

    private fun begin() {
        started = true
        goForeground(buildNotification(title(), true))
        setupSession()
        buildPlayer()
        registerNet()
        playIndex(BackgroundAudio.index, BackgroundAudio.startPos)
    }

    private fun buildPlayer() {
        var ua = "Mozilla/5.0 (Linux; Android) StreamLite/2.0"
        val props = HashMap<String, String>()
        for ((k, v) in Store.parseHeaders(BackgroundAudio.headersRaw)) {
            if (k.equals("user-agent", true)) ua = v else props[k] = v
        }
        val meter = DefaultBandwidthMeter.Builder(this).build()
        val http = OkHttpDataSource.Factory(HttpClients.shared).setUserAgent(ua)
        if (props.isNotEmpty()) http.setDefaultRequestProperties(props)
        val dsf = DefaultDataSource.Factory(this, http)
            .setTransferListener(CountingTransferListener(meter.transferListener))

        // Sound only: the video track is switched off, so no video decoder runs and less power is used.
        val ts = DefaultTrackSelector(this)
        val tb = ts.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        if (prefAudioLang.isNotBlank()) tb.setPreferredAudioLanguage(prefAudioLang)
        ts.setParameters(tb.build())

        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(60_000, 10 * 60_000, 2_500, 5_000)
            .setTargetBufferBytes(minOf(prefBufferMb, 32) * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val p = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsf))
            .setTrackSelector(ts)
            .setLoadControl(load)
            .setBandwidthMeter(meter)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    retryCount = 0
                    retryPending = false
                }
                if (playbackState == Player.STATE_ENDED) onEnded()
                refresh()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = refresh()
            override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
            override fun onPlayerError(error: PlaybackException) = onError(error)
        })
        player = p
    }

    private fun mimeFor(u: String): String? {
        val path = Uri.parse(u).path?.lowercase(Locale.US) ?: ""
        return when {
            path.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            path.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            path.contains(".ism") -> MimeTypes.APPLICATION_SS
            else -> null
        }
    }

    private fun playIndex(i: Int, pos: Long) {
        val item = BackgroundAudio.items.getOrNull(i) ?: return
        BackgroundAudio.index = i
        BackgroundAudio.startPos = pos
        retryCount = 0
        retryPending = false
        handler.removeCallbacksAndMessages(null)
        val b = MediaItem.Builder().setUri(item.url)
        mimeFor(item.url)?.let { b.setMimeType(it) }
        val p = player ?: return
        if (pos > 0) p.setMediaItem(b.build(), pos) else p.setMediaItem(b.build())
        p.playWhenReady = true
        p.prepare()
        DebugLog.log("background audio: ${item.title} at $pos")
        refresh()
    }

    private fun onEnded() {
        if (BackgroundAudio.index < BackgroundAudio.items.lastIndex) {
            playIndex(BackgroundAudio.index + 1, 0L)
        } else {
            DebugLog.log("background audio: finished")
            BackgroundAudio.finished(this, 0L)
            stopSelf()
        }
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

    private fun onError(e: PlaybackException) {
        DebugLog.log("background audio error ${e.errorCodeName}")
        val queued = BackgroundAudio.items.size > 1
        val limit = if (queued) 2 else prefMaxRetries
        if (prefReconnect && isNetworkError(e) && retryCount < limit) {
            retryCount++
            retryPending = true
            val delay = minOf(2 shl minOf(retryCount - 1, 3), 15) * 1000L
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ retryNow() }, delay)
            refresh()
            return
        }
        if (BackgroundAudio.index < BackgroundAudio.items.lastIndex) {
            playIndex(BackgroundAudio.index + 1, 0L)
            return
        }
        BackgroundAudio.finished(this, player?.currentPosition ?: 0L)
        stopSelf()
    }

    private fun retryNow() {
        if (!retryPending) return
        retryPending = false
        handler.removeCallbacksAndMessages(null)
        DebugLog.log("background audio retry $retryCount")
        player?.prepare()
    }

    private fun registerNet() {
        try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handler.post { if (retryPending) retryNow() }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            netCb = cb
        } catch (e: Exception) {
            DebugLog.log("net callback: $e")
        }
    }

    private fun toggle() {
        val p = player ?: return
        if (p.playWhenReady && p.playbackState != Player.STATE_ENDED) p.pause() else {
            if (p.playbackState == Player.STATE_ENDED) p.seekToDefaultPosition()
            p.play()
        }
    }

    private fun next() {
        if (BackgroundAudio.index < BackgroundAudio.items.lastIndex) playIndex(BackgroundAudio.index + 1, 0L)
    }

    private fun prev() {
        val p = player ?: return
        if (p.currentPosition > 5_000 || BackgroundAudio.index == 0) p.seekTo(0) else playIndex(BackgroundAudio.index - 1, 0L)
    }

    private fun stopEverything() {
        BackgroundAudio.finished(this, player?.currentPosition ?: 0L)
        stopSelf()
    }

    // ---------------------------------------------------------------- notification + session

    private fun setupSession() {
        try {
            val s = MediaSession(this, "StreamLite")
            s.setCallback(object : MediaSession.Callback() {
                override fun onPlay() { player?.play() }
                override fun onPause() { player?.pause() }
                override fun onSkipToNext() { next() }
                override fun onSkipToPrevious() { prev() }
                override fun onStop() { stopEverything() }
                override fun onSeekTo(pos: Long) { player?.seekTo(pos) }
            })
            s.isActive = true
            session = s
        } catch (e: Exception) {
            DebugLog.log("media session: $e")
        }
    }

    private fun refresh() {
        val p = player ?: return
        val playing = p.playWhenReady && p.playbackState != Player.STATE_ENDED
        try {
            session?.let { s ->
                val state = when {
                    retryPending || p.playbackState == Player.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING
                    playing -> PlaybackState.STATE_PLAYING
                    else -> PlaybackState.STATE_PAUSED
                }
                val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO
                s.setPlaybackState(
                    PlaybackState.Builder().setActions(actions)
                        .setState(state, p.currentPosition, if (playing) 1f else 0f).build()
                )
                s.setMetadata(
                    MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title())
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, if (p.duration > 0) p.duration else -1L).build()
                )
            }
            getSystemService(NotificationManager::class.java).notify(ID, buildNotification(title(), playing))
        } catch (e: Exception) {
            DebugLog.log("notification: $e")
        }
    }

    private fun action(icon: Int, label: String, act: String, code: Int): Notification.Action {
        val pi = PendingIntent.getService(
            this, code, Intent(this, AudioService::class.java).setAction(act),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build()
    }

    private fun buildNotification(title: String, playing: Boolean): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URL, BackgroundAudio.openUrl)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val multi = BackgroundAudio.items.size > 1
        var n = 0
        val compact = ArrayList<Int>()
        if (multi) { b.addAction(action(R.drawable.ic_skip_prev, "Previous", ACT_PREV, 1)); compact.add(n++) }
        b.addAction(
            action(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (playing) "Pause" else "Play", ACT_TOGGLE, 2
            )
        )
        compact.add(n++)
        if (multi) { b.addAction(action(R.drawable.ic_skip_next, "Next", ACT_NEXT, 3)); compact.add(n++) }
        b.addAction(action(R.drawable.ic_close, "Stop", ACT_STOP, 4))
        val style = Notification.MediaStyle().setShowActionsInCompactView(*compact.toIntArray())
        session?.let { style.setMediaSession(it.sessionToken) }
        b.setStyle(style)
            .setSmallIcon(R.drawable.ic_audio)
            .setContentTitle(title)
            .setContentText(if (retryPending) "Connection lost. Reconnecting…" else "Playing in the background")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setShowWhen(false)
        if (Build.VERSION.SDK_INT >= 21) b.setVisibility(Notification.VISIBILITY_PUBLIC)
        return b.build()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            netCb?.let { (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            // already gone
        }
        netCb = null
        DataUsage.add(this, NetStats.bytes.get() - startBytes)
        player?.release()
        player = null
        try {
            session?.isActive = false
            session?.release()
        } catch (e: Exception) {
            // ignore
        }
        session = null
        instance = null
        BackgroundAudio.active = false
        super.onDestroy()
    }
}
