### #7 [open] background sound always stop playing after several minutes
by gmg2719 on 2026-11-17T08:25:36Z
labels: bug

### What happened?

after I put the app to background playing, it playing several minutes and stop playing, it seems stop playing after serveral connection failure to Failed to connect to 23.237.104.106:8080. My network connection is OK, I think the server is unstable, can you retry or reconnect the server immediately after connection failure. When connection is recovered, background playing should start immediately.


Some debug logs for you to help you resolve the issue:



StreamLite 1.1.0
Android 13 (API 33)
Device  nubia NX302J
ABI     arm64-v8a, armeabi-v7a, armeabi
Screen  720x1458 @320dpi
Memory  class 256 MB, large 512 MB
Settings buffer=256MB maxRes=Auto (best for screen) theme=0 reconnect=true resume=false autoPip=true
Data    today 54.2 MB, month 54.2 MB

--- Player ---
Speed now : 0.00 MB/s (0.0 Mbps)
Est. link : 2.6 Mbps
Data used : 10.3 MB
Stalls    : 10
Buffer    : 15.3 s  (limit 256 MB)
Audio     : MP4A-LATM 2ch 24kHz
Decoder   : -
Battery   : 91% (charging)
State     : playing
FFmpeg audio decoder: available
Compat  decoder=0 textureView=false

--- Decoders on this device ---
video/avc  c2.unisoc.avc.decoder  hardware  max 4096x4096  profiles [1, 2, 8, 16, 65536]
video/avc  OMX.sprd.h264.decoder  hardware  max 4096x4096  profiles [1, 2, 8, 16, 65536]
video/avc  c2.android.avc.decoder  software  max 1280x1280  profiles [1, 2, 8, 65536, 524288]
video/avc  OMX.google.h264.decoder  software  max 1280x1280  profiles [1, 2, 8, 65536, 524288]
video/hevc  c2.unisoc.hevc.decoder  hardware  max 4096x4096  profiles [1, 2, 4, 4096, 8192]
video/hevc  OMX.sprd.hevc.decoder  hardware  max 4096x4096  profiles [1, 2, 4, 4096, 8192]
video/hevc  c2.android.hevc.decoder  software  max 1280x1280  profiles [1, 4]
video/hevc  OMX.google.hevc.decoder  software  max 1280x1280  profiles [1, 4]
video/x-vnd.on2.vp9  c2.unisoc.vp9.decoder  hardware  max 4096x4096  profiles [1, 4, 4096, 16384]
video/x-vnd.on2.vp9  OMX.sprd.vp9.decoder  hardware  max 4096x4096  profiles [1, 4, 4096, 16384]
video/x-vnd.on2.vp9  c2.android.vp9.decoder  software  max 2048x2048  profiles [1, 4, 4096, 16384]
video/x-vnd.on2.vp9  OMX.google.vp9.decoder  software  max 2048x2048  profiles [1, 4, 4096, 16384]
video/x-vnd.on2.vp8  c2.unisoc.vp8.decoder  hardware  max 1920x1088  profiles [1]
video/x-vnd.on2.vp8  c2.android.vp8.decoder  software  max 2048x2048  profiles [1]
video/x-vnd.on2.vp8  OMX.google.vp8.decoder  software  max 2048x2048  profiles [1]
video/av01  c2.unisoc.av1.decoder  hardware  max 1920x1088  profiles [1, 2]
video/av01  OMX.sprd.av1.decoder  hardware  max 1920x1088  profiles [1, 2]
video/av01  c2.android.av1.decoder  software  max 2048x2048  profiles [1, 2]
video/mpeg2: NONE
video/mp4v-es  c2.unisoc.mpeg4.decoder  hardware  max 896x896  profiles [1, 32768]
video/mp4v-es  OMX.sprd.mpeg4.decoder  hardware  max 896x896  profiles [1, 32768]
video/mp4v-es  c2.android.mpeg4.decoder  software  max 1344x1344  profiles [1]
video/mp4v-es  OMX.google.mpeg4.decoder  software  max 1344x1344  profiles [1]
audio/mp4a-latm  c2.android.aac.decoder  software
audio/mp4a-latm  OMX.google.aac.decoder  software
audio/ac3: NONE
audio/eac3: NONE
audio/vnd.dts: NONE
audio/opus  c2.android.opus.decoder  software
audio/opus  OMX.google.opus.decoder  software
audio/vorbis  c2.android.vorbis.decoder  software
audio/vorbis  OMX.google.vorbis.decoder  software
(HEVC profile 1 = Main, 2 = Main10. VP9 profile 1 = 8-bit, 4 = 10-bit.)

--- Log ---
13:28:11.103  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 headers=0
13:28:11.205  playlist: 1 items, start 0
13:28:11.265  state buffering
13:28:12.482  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
13:28:12.950  state ready
13:28:36.910  background audio: mono.m3u8 at -439
13:28:46.001  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 headers=0
13:28:46.005  back from background audio at -1319
13:28:46.012  state buffering
13:28:46.113  error ERROR_CODE_PARSING_MANIFEST_MALFORMED cause=I Input does not start with the #EXTM3U header. {contentIsMalformed=true, dataType=4}
13:28:46.140  state idle
13:28:57.505  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 headers=0
13:28:57.508  state buffering
13:28:57.595  error ERROR_CODE_PARSING_MANIFEST_MALFORMED cause=I Input does not start with the #EXTM3U header. {contentIsMalformed=true, dataType=4}
13:28:57.618  state idle
13:29:14.656  state buffering
13:29:15.202  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
13:29:16.225  state ready
13:31:01.154  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_BOOMERANG/USA_BOOMERANG.m3u8 headers=0
13:31:01.195  playlist: 1 items, start 0
13:31:01.200  state buffering
13:31:02.272  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
13:31:02.531  state ready
13:31:11.099  background audio: mono.m3u8 at 2228
13:31:21.205  back from background audio at -2208
13:31:21.211  state buffering
13:31:21.767  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
13:31:22.289  state ready
13:31:55.644  background audio: mono.m3u8 at 3011
13:31:59.816  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_BOOMERANG/USA_BOOMERANG.m3u8 headers=0
13:31:59.822  back from background audio at -922
13:31:59.826  state buffering
13:31:59.920  error ERROR_CODE_PARSING_MANIFEST_MALFORMED cause=I Input does not start with the #EXTM3U header. {contentIsMalformed=true, dataType=4}
13:31:59.949  state idle
13:32:49.904  state buffering
13:32:50.955  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
13:32:52.206  state ready
13:33:46.911  background audio: mono.m3u8 at 8832
13:33:50.005  back from background audio at 8832
13:33:50.011  state buffering
13:33:50.203  boost failed: java.lang.RuntimeException: AudioEffect: set/get parameter error
13:33:50.571  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
13:33:51.561  state ready
13:46:49.607  state buffering
13:46:51.617  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
13:51:57.367  state buffering
13:51:58.455  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
13:51:59.258  state ready
14:10:14.866  state buffering
14:10:15.925  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
14:10:16.413  state ready
14:10:22.409  state buffering
14:10:23.436  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
14:10:24.210  state ready
14:22:50.785  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 headers=0
14:22:50.790  state buffering
14:22:50.867  error ERROR_CODE_IO_UNSPECIFIED cause=q Unexpected SecurityException: enforceReadPermissionInner Permission Denial: reading androidx.core.content.FileProvider uri content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 from pid=12724, uid=10338,mReadPermission:null,mMyUid:10204,userUri:content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 requires the provider be exported, or grantUriPermission()
14:22:50.868  state idle
14:22:52.868  retry 1
14:22:52.869  state buffering
14:22:52.888  error ERROR_CODE_IO_UNSPECIFIED cause=q Unexpected SecurityException: enforceReadPermissionInner Permission Denial: reading androidx.core.content.FileProvider uri content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 from pid=12724, uid=10338,mReadPermission:null,mMyUid:10204,userUri:content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 requires the provider be exported, or grantUriPermission()
14:22:52.890  state idle
14:22:56.893  retry 2
14:22:56.893  state buffering
14:22:56.905  error ERROR_CODE_IO_UNSPECIFIED cause=q Unexpected SecurityException: enforceReadPermissionInner Permission Denial: reading androidx.core.content.FileProvider uri content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 from pid=12724, uid=10338,mReadPermission:null,mMyUid:10204,userUri:content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 requires the provider be exported, or grantUriPermission()
14:22:56.906  state idle
14:44:23.242  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 headers=0
14:44:23.289  playlist: 1 items, start 0
14:44:23.294  state buffering
14:44:24.643  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
14:44:25.531  state ready
14:44:30.774  background audio: mono.m3u8 at 2867
14:56:02.957  background audio error ERROR_CODE_BEHIND_LIVE_WINDOW
14:57:07.499  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 headers=0
14:57:07.500  back from background audio at -4408
14:57:07.512  state buffering
14:57:07.581  error ERROR_CODE_PARSING_MANIFEST_MALFORMED cause=I Input does not start with the #EXTM3U header. {contentIsMalformed=true, dataType=4}
14:57:07.625  state idle
14:57:12.203  state buffering
14:57:12.224  error ERROR_CODE_PARSING_MANIFEST_MALFORMED cause=I Input does not start with the #EXTM3U header. {contentIsMalformed=true, dataType=4}
14:57:12.238  state idle
14:57:17.748  state buffering
14:57:18.912  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
14:57:19.481  state ready
14:57:34.039  error ERROR_CODE_IO_NETWORK_CONNECTION_FAILED cause=s java.io.IOException: java.util.concurrent.ExecutionException: java.net.ConnectException: Failed to connect to /23.237.104.106:8080
14:57:34.039  state idle
14:57:36.041  retry 1
14:57:36.042  state buffering
14:57:40.158  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
14:57:40.749  state ready
14:58:23.878  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 headers=0
14:58:23.917  playlist: 1 items, start 0
14:58:23.920  state buffering
14:58:25.301  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
14:58:26.153  state ready
14:58:44.210  state buffering
14:58:45.260  state ready
15:05:32.686  state buffering
15:05:34.348  state ready
15:11:30.984  error ERROR_CODE_BEHIND_LIVE_WINDOW cause=b null
15:11:30.985  state idle
15:11:30.986  state buffering
15:11:31.690  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
15:11:32.845  state ready
15:11:37.592  state buffering
15:11:39.127  state ready
15:12:19.879  state buffering
15:12:21.945  state ready
15:12:39.581  background audio: mono.m3u8 at 7924
15:12:50.961  background audio error ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
15:12:52.962  background audio retry 1
15:13:43.783  background audio error ERROR_CODE_BEHIND_LIVE_WINDOW
15:16:49.431  back from background audio at -2148
15:16:49.454  state buffering
15:16:50.836  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
15:16:51.798  state ready
15:16:59.150  background audio: mono.m3u8 at 1668
15:24:32.454  background audio error ERROR_CODE_BEHIND_LIVE_WINDOW
15:28:15.169  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 headers=0
15:28:15.170  back from background audio at -2677
15:28:15.174  state buffering
15:28:15.239  error ERROR_CODE_PARSING_MANIFEST_MALFORMED cause=I Input does not start with the #EXTM3U header. {contentIsMalformed=true, dataType=4}
15:28:15.271  state idle
15:28:27.025  state buffering
15:28:30.068  error ERROR_CODE_IO_NETWORK_CONNECTION_FAILED cause=s java.io.IOException: java.util.concurrent.ExecutionException: java.net.ConnectException: Failed to connect to /23.237.104.106:8080
15:28:30.070  state idle
15:28:32.071  retry 1
15:28:32.072  state buffering
15:28:35.093  error ERROR_CODE_IO_NETWORK_CONNECTION_FAILED cause=s java.io.IOException: java.util.concurrent.ExecutionException: java.net.ConnectException: Failed to connect to /23.237.104.106:8080
15:28:35.094  state idle
15:28:35.993  retry 2
15:28:35.993  state buffering
15:28:37.305  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
15:28:37.672  state ready
15:28:43.238  background audio: mono.m3u8 at -4947
15:28:46.478  back from background audio at 1165
15:28:46.495  state buffering
15:28:47.122  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
15:28:47.467  state ready
15:28:58.528  background audio: mono.m3u8 at 7933
15:29:05.539  back from background audio at 12983
15:29:05.560  state buffering
15:29:06.323  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
15:29:07.001  state ready
15:29:09.487  background audio: mono.m3u8 at 9108
15:29:12.526  back from background audio at 9764
15:29:12.533  state buffering
15:29:13.206  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
15:29:13.685  state ready
15:29:15.176  background audio: mono.m3u8 at 10922
15:29:21.722  back from background audio at 9496
15:29:21.737  state buffering
15:29:22.472  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
15:29:25.425  background audio: mono.m3u8 at -504
15:35:56.671  background audio error ERROR_CODE_BEHIND_LIVE_WINDOW
15:38:42.456  back from background audio at -880
15:38:42.483  state buffering
15:38:44.094  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
15:38:45.058  state ready
15:38:54.703  background audio: mono.m3u8 at 2512
15:38:58.161  back from background audio at -1849
15:38:58.170  state buffering
15:38:59.343  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
15:38:59.750  state ready
15:39:00.333  background audio: mono.m3u8 at -3729
15:39:48.665  background audio error ERROR_CODE_BEHIND_LIVE_WINDOW
15:41:25.125  back from background audio at -2589
15:41:25.152  state buffering
15:41:26.972  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
15:41:27.756  state ready

### Steps / sample link

_No response_

### Device and Android version

_No response_

### Debug report

```text

```

### #6 [open] support phone local storage m3u8 file play
by gmg2719 on 2026-11-17T06:13:39Z
labels: enhancement

### What would you like?

Please add support of local storage m3u8 file (I put the m3u8 link in a file with .m3u8) playing, just like vlc player, support folder browsing and click the m3u8 file to play.

### Why is it useful?

_No response_

### #5 [open] cannot resume play after bring it back from background audio
by gmg2719 on 2026-11-17T05:45:36Z
labels: bug

### What happened?
step1: choose m3u8 file on my phone ,play it with this app, it plays OK
step2: choose background sound play, it goes background, sound play Ok
step3: bring it to front,  it report error. error report are:

StreamLite 1.1.0
Android 13 (API 33)
Device  nubia NX302J
ABI     arm64-v8a, armeabi-v7a, armeabi
Screen  720x1458 @320dpi
Memory  class 256 MB, large 512 MB
Settings buffer=256MB maxRes=Auto (best for screen) theme=0 reconnect=true resume=false autoPip=true
Data    today 4.5 MB, month 4.5 MB

--- Player ---
Speed now : 0.00 MB/s (0.0 Mbps)
Est. link : 2.4 Mbps
Data used : 0 B
Stalls    : 0
Buffer    : 0.0 s  (limit 256 MB)
Decoder   : -
Battery   : 84% (charging)
State     : idle
FFmpeg audio decoder: available
Compat  decoder=0 textureView=false

--- Decoders on this device ---
video/avc  c2.unisoc.avc.decoder  hardware  max 4096x4096  profiles [1, 2, 8, 16, 65536]
video/avc  OMX.sprd.h264.decoder  hardware  max 4096x4096  profiles [1, 2, 8, 16, 65536]
video/avc  c2.android.avc.decoder  software  max 1280x1280  profiles [1, 2, 8, 65536, 524288]
video/avc  OMX.google.h264.decoder  software  max 1280x1280  profiles [1, 2, 8, 65536, 524288]
video/hevc  c2.unisoc.hevc.decoder  hardware  max 4096x4096  profiles [1, 2, 4, 4096, 8192]
video/hevc  OMX.sprd.hevc.decoder  hardware  max 4096x4096  profiles [1, 2, 4, 4096, 8192]
video/hevc  c2.android.hevc.decoder  software  max 1280x1280  profiles [1, 4]
video/hevc  OMX.google.hevc.decoder  software  max 1280x1280  profiles [1, 4]
video/x-vnd.on2.vp9  c2.unisoc.vp9.decoder  hardware  max 4096x4096  profiles [1, 4, 4096, 16384]
video/x-vnd.on2.vp9  OMX.sprd.vp9.decoder  hardware  max 4096x4096  profiles [1, 4, 4096, 16384]
video/x-vnd.on2.vp9  c2.android.vp9.decoder  software  max 2048x2048  profiles [1, 4, 4096, 16384]
video/x-vnd.on2.vp9  OMX.google.vp9.decoder  software  max 2048x2048  profiles [1, 4, 4096, 16384]
video/x-vnd.on2.vp8  c2.unisoc.vp8.decoder  hardware  max 1920x1088  profiles [1]
video/x-vnd.on2.vp8  c2.android.vp8.decoder  software  max 2048x2048  profiles [1]
video/x-vnd.on2.vp8  OMX.google.vp8.decoder  software  max 2048x2048  profiles [1]
video/av01  c2.unisoc.av1.decoder  hardware  max 1920x1088  profiles [1, 2]
video/av01  OMX.sprd.av1.decoder  hardware  max 1920x1088  profiles [1, 2]
video/av01  c2.android.av1.decoder  software  max 2048x2048  profiles [1, 2]
video/mpeg2: NONE
video/mp4v-es  c2.unisoc.mpeg4.decoder  hardware  max 896x896  profiles [1, 32768]
video/mp4v-es  OMX.sprd.mpeg4.decoder  hardware  max 896x896  profiles [1, 32768]
video/mp4v-es  c2.android.mpeg4.decoder  software  max 1344x1344  profiles [1]
video/mp4v-es  OMX.google.mpeg4.decoder  software  max 1344x1344  profiles [1]
audio/mp4a-latm  c2.android.aac.decoder  software
audio/mp4a-latm  OMX.google.aac.decoder  software
audio/ac3: NONE
audio/eac3: NONE
audio/vnd.dts: NONE
audio/opus  c2.android.opus.decoder  software
audio/opus  OMX.google.opus.decoder  software
audio/vorbis  c2.android.vorbis.decoder  software
audio/vorbis  OMX.google.vorbis.decoder  software
(HEVC profile 1 = Main, 2 = Main10. VP9 profile 1 = 8-bit, 4 = 10-bit.)

--- Log ---
13:28:11.103  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 headers=0
13:28:11.205  playlist: 1 items, start 0
13:28:11.265  state buffering
13:28:12.482  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
13:28:12.950  state ready
13:28:36.910  background audio: mono.m3u8 at -439
13:28:46.001  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 headers=0
13:28:46.005  back from background audio at -1319
13:28:46.012  state buffering
13:28:46.113  error ERROR_CODE_PARSING_MANIFEST_MALFORMED cause=I Input does not start with the #EXTM3U header. {contentIsMalformed=true, dataType=4}
13:28:46.140  state idle
13:28:57.505  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_MTV/USA_MTV.m3u8 headers=0
13:28:57.508  state buffering
13:28:57.595  error ERROR_CODE_PARSING_MANIFEST_MALFORMED cause=I Input does not start with the #EXTM3U header. {contentIsMalformed=true, dataType=4}
13:28:57.618  state idle
13:29:14.656  state buffering
13:29:15.202  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
13:29:16.225  state ready
13:31:01.154  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_BOOMERANG/USA_BOOMERANG.m3u8 headers=0
13:31:01.195  playlist: 1 items, start 0
13:31:01.200  state buffering
13:31:02.272  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
13:31:02.531  state ready
13:31:11.099  background audio: mono.m3u8 at 2228
13:31:21.205  back from background audio at -2208
13:31:21.211  state buffering
13:31:21.767  audio 2ch audio/mp4a-latm codecs=mp4a.40.2 -> handled
13:31:22.289  state ready
13:31:55.644  background audio: mono.m3u8 at 3011
13:31:59.816  open content://zte.com.cn.filer.fileprovider/external_files/2/USA_TV_LIST/USA_TV_AUDIO/USA_BOOMERANG/USA_BOOMERANG.m3u8 headers=0
13:31:59.822  back from background audio at -922
13:31:59.826  state buffering
13:31:59.920  error ERROR_CODE_PARSING_MANIFEST_MALFORMED cause=I Input does not start with the #EXTM3U header. {contentIsMalformed=true, dataType=4}
13:31:59.949  state idle


### Steps / sample link

_No response_

### Device and Android version

_No response_

### Debug report

```text

```

### #4 [closed] Add user defined reconnect retry times
by gmg2719 on 2026-11-17T01:35:52Z
labels: enhancement

### What would you like?

at this moment, the app will retry 12 times after network drop. I want to define my own retry time, for example 999, to ensure even long network drop, the app can resume playing after network resumes.

### Why is it useful?

_No response_

### #3 [closed] Please add support to play sound at background when I press Home button on my phone
by gmg2719 on 2026-11-17T01:32:17Z
labels: enhancement

### What would you like?

in VLC , I can listen to the sound while I don't want to watch the video. Please add this feature support. I want to listen the video sound while I do some other work on my phone.

### Why is it useful?

_No response_

--- comments on #7
--- comments on #6
--- comments on #5
[gmg2719] The m3u8 file I uploaded for you to debug the issue. please see attachement.

[USA_BOOMERANG.zip](https://github.com/user-attachments/files/34956066/USA_BOOMERANG.zip)

--- comments on #4
--- comments on #3
