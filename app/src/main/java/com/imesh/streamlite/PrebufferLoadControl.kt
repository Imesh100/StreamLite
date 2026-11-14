package com.imesh.streamlite

import android.content.Context
import android.net.ConnectivityManager
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator

/**
 * Wraps the normal load control. While the video is paused it can stop downloading ahead
 * (always, never, or only on mobile data) so a paused video does not use your data plan.
 * mode: 0 = always keep buffering, 1 = Wi-Fi only, 2 = never.
 */
class PrebufferLoadControl(
    private val d: LoadControl,
    context: Context,
    private val mode: () -> Int
) : LoadControl {
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun mayBufferWhilePaused(): Boolean = when (mode()) {
        0 -> true
        2 -> false
        else -> try { !cm.isActiveNetworkMetered } catch (e: Exception) { true }
    }

    // Every overload is forwarded untouched. ExoPlayer decides which one it calls, and the
    // interface's own defaults throw "not implemented", so a missing forward breaks playback.
    override fun onPrepared(playerId: PlayerId) = d.onPrepared(playerId)

    @Deprecated("Deprecated in LoadControl")
    override fun onPrepared() = d.onPrepared()

    override fun onTracksSelected(
        parameters: LoadControl.Parameters, trackGroups: TrackGroupArray, trackSelections: Array<ExoTrackSelection?>
    ) = d.onTracksSelected(parameters, trackGroups, trackSelections)

    override fun onTracksSelected(
        playerId: PlayerId, timeline: Timeline, mediaPeriodId: MediaSource.MediaPeriodId,
        renderers: Array<Renderer>, trackGroups: TrackGroupArray, trackSelections: Array<ExoTrackSelection?>
    ) = d.onTracksSelected(playerId, timeline, mediaPeriodId, renderers, trackGroups, trackSelections)

    @Deprecated("Deprecated in LoadControl")
    override fun onTracksSelected(
        timeline: Timeline, mediaPeriodId: MediaSource.MediaPeriodId,
        renderers: Array<Renderer>, trackGroups: TrackGroupArray, trackSelections: Array<ExoTrackSelection?>
    ) = d.onTracksSelected(timeline, mediaPeriodId, renderers, trackGroups, trackSelections)

    @Deprecated("Deprecated in LoadControl")
    override fun onTracksSelected(
        renderers: Array<Renderer>, trackGroups: TrackGroupArray, trackSelections: Array<ExoTrackSelection?>
    ) = d.onTracksSelected(renderers, trackGroups, trackSelections)

    override fun onStopped(playerId: PlayerId) = d.onStopped(playerId)

    @Deprecated("Deprecated in LoadControl")
    override fun onStopped() = d.onStopped()

    override fun onReleased(playerId: PlayerId) = d.onReleased(playerId)

    @Deprecated("Deprecated in LoadControl")
    override fun onReleased() = d.onReleased()

    override fun getAllocator(playerId: PlayerId): Allocator = d.getAllocator(playerId)

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = d.getBackBufferDurationUs(playerId)

    @Deprecated("Deprecated in LoadControl")
    override fun getBackBufferDurationUs(): Long = d.getBackBufferDurationUs()

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean = d.retainBackBufferFromKeyframe(playerId)

    @Deprecated("Deprecated in LoadControl")
    override fun retainBackBufferFromKeyframe(): Boolean = d.retainBackBufferFromKeyframe()

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        // Always allow a small cushion so resuming is instant; only the extra buffering is optional.
        if (!parameters.playWhenReady && parameters.bufferedDurationUs > 15_000_000L && !mayBufferWhilePaused()) {
            return false
        }
        return d.shouldContinueLoading(parameters)
    }

    @Deprecated("Deprecated in LoadControl")
    override fun shouldContinueLoading(playbackPositionUs: Long, bufferedDurationUs: Long, playbackSpeed: Float): Boolean =
        d.shouldContinueLoading(playbackPositionUs, bufferedDurationUs, playbackSpeed)

    override fun shouldContinuePreloading(
        playerId: PlayerId, timeline: Timeline, mediaPeriodId: MediaSource.MediaPeriodId, bufferedDurationUs: Long
    ): Boolean = d.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean = d.shouldStartPlayback(parameters)

    @Deprecated("Deprecated in LoadControl")
    override fun shouldStartPlayback(
        timeline: Timeline, mediaPeriodId: MediaSource.MediaPeriodId, bufferedDurationUs: Long,
        playbackSpeed: Float, rebuffering: Boolean, targetLiveOffsetUs: Long
    ): Boolean = d.shouldStartPlayback(timeline, mediaPeriodId, bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs)

    @Deprecated("Deprecated in LoadControl")
    override fun shouldStartPlayback(
        bufferedDurationUs: Long, playbackSpeed: Float, rebuffering: Boolean, targetLiveOffsetUs: Long
    ): Boolean = d.shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs)
}
