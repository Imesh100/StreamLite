package com.imesh.streamlite

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/** Decoder choice for devices with unusual hardware decoders (many TV boxes), plus a codec report. */
object Decoders {
    const val AUTO = 0
    const val SOFTWARE_FIRST = 1
    const val HARDWARE_ONLY = 2

    fun selector(mode: Int): MediaCodecSelector = when (mode) {
        SOFTWARE_FIRST -> MediaCodecSelector { mime, secure, tunneling ->
            MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
                .sortedBy { if (it.softwareOnly) 0 else 1 }
        }
        HARDWARE_ONLY -> MediaCodecSelector { mime, secure, tunneling ->
            MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling).filter { !it.softwareOnly }
        }
        else -> MediaCodecSelector.DEFAULT
    }

    private fun isSoftware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) {
            info.isSoftwareOnly
        } else {
            info.name.startsWith("OMX.google.") || info.name.startsWith("c2.android.")
        }

    /** Which decoders this device offers for the common formats. Helps explain "sound but no picture". */
    fun report(): String {
        val sb = StringBuilder()
        try {
            val all = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
            val mimes = arrayOf(
                "video/avc", "video/hevc", "video/x-vnd.on2.vp9", "video/x-vnd.on2.vp8", "video/av01",
                "video/mpeg2", "video/mp4v-es", "audio/mp4a-latm", "audio/ac3", "audio/eac3", "audio/vnd.dts",
                "audio/opus", "audio/vorbis"
            )
            for (mime in mimes) {
                val infos = all.filter { i -> i.supportedTypes.any { it.equals(mime, true) } }
                if (infos.isEmpty()) {
                    sb.append(mime).append(": NONE\n")
                    continue
                }
                for (i in infos) {
                    sb.append(mime).append("  ").append(i.name).append(if (isSoftware(i)) "  software" else "  hardware")
                    try {
                        val caps = i.getCapabilitiesForType(mime)
                        val v = caps.videoCapabilities
                        if (v != null) {
                            sb.append("  max ").append(v.supportedWidths.upper).append('x').append(v.supportedHeights.upper)
                            sb.append("  profiles ").append(caps.profileLevels.map { it.profile }.distinct().sorted())
                        }
                    } catch (e: Exception) {
                        sb.append("  (caps unavailable)")
                    }
                    sb.append('\n')
                }
            }
            sb.append("(HEVC profile 1 = Main, 2 = Main10. VP9 profile 1 = 8-bit, 4 = 10-bit.)\n")
        } catch (e: Exception) {
            sb.append("codec list failed: ").append(e).append('\n')
        }
        return sb.toString()
    }
}
