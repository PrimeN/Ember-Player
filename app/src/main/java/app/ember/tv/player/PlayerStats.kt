package app.ember.tv.player

import app.ember.tv.R
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale

// PlayerActivity's playback statistics (Settings > Player > Stats).
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.rate(bits: Long): String = when {
    bits <= 0 -> "—"
    bits >= 1_000_000_000 -> String.format(Locale.US, resources.getString(R.string.ui_2f_gbps), bits / 1e9)
    bits >= 1_000_000 -> String.format(Locale.US, resources.getString(R.string.ui_1f_mbps), bits / 1e6)
    else -> String.format(Locale.US, resources.getString(R.string.ui_d_kbps), bits / 1000)
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.containerName(p: ExoPlayer): String {
    val mime = p.currentMediaItem?.localConfiguration?.mimeType ?: p.videoFormat?.containerMimeType ?: p.audioFormat?.containerMimeType
    val path = p.currentMediaItem?.localConfiguration?.uri?.lastPathSegment.orEmpty().lowercase()
    return when {
        mime == MimeTypes.APPLICATION_M3U8 || path.endsWith(".m3u8") -> "HLS"
        mime == MimeTypes.APPLICATION_MPD || path.endsWith(".mpd") -> "DASH"
        mime == MimeTypes.VIDEO_MATROSKA || path.endsWith(".mkv") -> resources.getString(R.string.ui_matroska_mkv)
        mime == MimeTypes.VIDEO_WEBM || path.endsWith(".webm") -> "WebM"
        mime == MimeTypes.VIDEO_MP2T || path.endsWith(".ts") -> "MPEG-TS"
        path.endsWith(".mov") -> getString(R.string.pl_quicktime_mov)
        mime == MimeTypes.VIDEO_MP4 || path.endsWith(".mp4") || path.endsWith(".m4v") -> "MP4"
        mime != null -> mime.substringAfter('/')
        else -> "—"
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.videoLine(f: Format?): String {
    f ?: return getString(R.string.ui_none)
    val codec = PlaybackFormats.video(f.sampleMimeType, f.codecs)
    val hdr = resources.getString(PlaybackFormats.hdr(f.sampleMimeType, f.codecs, f.colorInfo?.colorTransfer,
        hdr10PlusMetadata && playbackEngine?.compatibility == null, playbackEngine?.compatibility != null))
    val bitrate = listOf(f.bitrate, f.averageBitrate, f.peakBitrate).firstOrNull { it > 0 }?.let { rate(it.toLong()) }
    val fps = f.frameRate.takeIf { it > 0 }?.let { String.format(Locale.US, "%.3f", it).trimEnd('0').trimEnd('.') + resources.getString(R.string.ui_fps) }
    val depth = f.colorInfo?.lumaBitdepth?.takeIf { it > 0 }?.let { "$it-bit" }
    return listOfNotNull(codec, hdr, if (f.width > 0) "${f.width}×${f.height}" else null, bitrate, fps, depth).joinToString(", ")
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.audioLine(f: Format?): String {
    f ?: return getString(R.string.ui_none)
    val ch = when (f.channelCount) {
        1 -> "1.0"; 2 -> "2.0"; 6 -> "5.1"; 8 -> "7.1"
        else -> if (f.channelCount > 0) resources.getString(R.string.ui_ch ,f.channelCount.toString()) else null
    }
    val hz = f.sampleRate.takeIf { it > 0 }?.let { resources.getString(R.string.ui_hz_648ee5 ,it.toString()) }
    val bitrate = listOf(f.bitrate, f.averageBitrate, f.peakBitrate).firstOrNull { it > 0 }?.let { rate(it.toLong()) }
    val out = getString(if (passthrough()) R.string.pl_passthrough else R.string.pl_decoded)
    return listOfNotNull(Playback.audioName(f), hz, ch, bitrate, out).joinToString(", ")
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.updateStats() {
    val p = player
    if (!advanced || p == null || config.hud == "off") {
        controls.stats = emptyList()
        return
    }
    val out = ArrayList<Pair<String, String>>()
    val full = config.hud == "full"
    out += resources.getString(R.string.ui_engine) to if(playbackEngine?.compatibility!=null)resources.getString(R.string.ui_compatibility) else resources.getString(R.string.ui_standard)
    playbackEngine?.compatibility?.diagnostics()?.forEach {(k,v)->if(k!=R.string.ui_engine)out+=resources.getString(k) to v}
    out += resources.getString(R.string.ui_source_hdr_f0e76b) to HdrDiagnostics.source(resources,p.videoFormat)
    out += resources.getString(R.string.ui_requested_output_ef90cd) to HdrDiagnostics.requested(resources,PictureProfiles.effective(config,resumeKey).hdrOutput)
    out += resources.getString(R.string.ui_picture_comparison) to if(compareOriginal)resources.getString(R.string.ui_original_preview) else resources.getString(R.string.ui_saved_processing)
    if(playbackEngine?.compatibility==null){out+=resources.getString(R.string.ui_dolby_vision_rpu_fel) to resources.getString(R.string.ui_not_independently_verified);out+=resources.getString(R.string.ui_active_output) to resources.getString(R.string.ui_device_renderer_active_hdr_signal_not_independently_verified)}
    if (full) out += getString(R.string.pl_container) to containerName(p)
    out += getString(R.string.pl_video) to videoLine(p.videoFormat)
    out += getString(R.string.pl_audio) to audioLine(p.audioFormat)
    if (markersFromIntroDb && markers.isNotEmpty()) out += resources.getString(R.string.ui_skip_markers) to resources.getString(R.string.ui_introdb_community_timings)
    if (full) {
        @Suppress("DEPRECATION") val types = runCatching { windowManager.defaultDisplay.hdrCapabilities?.supportedHdrTypes }.getOrNull() ?: intArrayOf()
        val supported = types.toList().mapNotNull { when(it) {1->resources.getString(R.string.ui_dolby_vision);2->"HDR10";3->"HLG";4->"HDR10+";else->null} }
        if(out.none {it.first==resources.getString(R.string.ui_display_hdr_capabilities)})out += resources.getString(R.string.ui_display_hdr_capabilities) to supported.joinToString(", ").ifEmpty { resources.getString(R.string.ui_not_reported) }
        if(playbackEngine?.compatibility==null)out += resources.getString(R.string.ui_hdr_output) to resources.getString(R.string.ui_display_capability_does_not_confirm_the_active_output_signal)
    }
    val ahead = ((p.bufferedPosition - p.currentPosition) / 1000).coerceAtLeast(0)
    val health = when {
        p.playbackState == Player.STATE_BUFFERING -> getString(R.string.pl_buffering)
        ahead >= 15 -> getString(R.string.pl_healthy)
        ahead >= 5 -> getString(R.string.pl_fair)
        else -> getString(R.string.pl_low)
    }
    out += getString(R.string.pl_buffer_status) to getString(R.string.pl_buffer_value, health, ahead)
    if (full) {
        out += getString(R.string.pl_network_throughput) to rate(bandwidth)
        out += getString(R.string.pl_stream_speed) to rate(loadSpeed)
        out += getString(R.string.pl_decoders) to listOfNotNull(videoDecoder, audioDecoder ?: if (passthrough()) getString(R.string.pl_passthrough) else null).joinToString(" · ").ifBlank { "—" }
        out += getString(R.string.pl_dropped_frames) to dropped.toString()
        val display = if (android.os.Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
        display?.mode?.let { m ->
            out += getString(R.string.pl_display) to "${m.physicalWidth}×${m.physicalHeight} @ " + String.format(Locale.US, "%.3f", m.refreshRate).trimEnd('0').trimEnd('.') + resources.getString(R.string.ui_hz_717dec)
        }
        val mem = android.app.ActivityManager.MemoryInfo().also { (getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(it) }
        out += getString(R.string.pl_device_info) to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / " + String.format(Locale.US, resources.getString(R.string.ui_2f_gb), mem.totalMem / 1e9)
        runCatching {
            val st = android.os.StatFs(filesDir.path)
            out += getString(R.string.pl_free_disk_space) to String.format(Locale.US, resources.getString(R.string.ui_2f_gib_2f_gib), st.availableBytes / 1_073_741_824.0, st.totalBytes / 1_073_741_824.0)
        }
    }
    controls.stats = out
}
