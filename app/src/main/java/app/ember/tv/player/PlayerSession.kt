package app.ember.tv.player

import app.ember.tv.R

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import app.ember.tv.player.Kind
import java.util.Locale
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode

@OptIn(UnstableApi::class)
/** Loads a source into the player and starts it. Used for the first episode and every next one. */
internal fun PlayerActivity.start(url: String, title: String, release: String?) {
    clips.cancel()
    touchControls?.cancel()
    closeCaptionAssistance();captionConfig=null;embeddedCaptionText=""
    manualSubtitleJob?.cancel();manualSubtitleJob=null
    nearbySubtitleJob?.cancel();nearbySubtitleJob=null
    cancelRecovery()
    playbackVisit++
    compareOriginal=false;pictureBypassed=false;pictureSignature=null;pictureEffectsActive=false
    val p = player ?: return
    playbackEngine?.standard?.setVideoEffects(emptyList())
    httpFactory.setConnectTimeoutMs(config.stream.normalized().connectSeconds*1000).setReadTimeoutMs(config.stream.normalized().readSeconds*1000)
    hdr10PlusMetadata = false
    decoderStableSince=0;decoderLearned=false
    repeatRange.clear()
    val restoredEdition=intent.getIntExtra(BackgroundAudioService.FORCE_EDITION,-1).takeIf {it in 0..8190}
    intent.removeExtra(BackgroundAudioService.FORCE_EDITION)
    playbackEngine?.configure(EngineSource(
        urls=listOf(url),title=title,key=resumeKey,
        audio=audioUrl,headers=sourceHeaders,filename=release,edition=restoredEdition,
        audioLanguages=listOfNotNull(showKey()?.let(memory::get)?.audio,java.util.Locale.getDefault().language).distinct(),
        subtitleLanguages=app.ember.tv.data.Subtitles.wanted(prefs.subtitleConfig)))
    val geometry=GeometryMemory.selected(config,resumeKey)
    aspect=geometry?.aspect ?: 0;touchMagnification=geometry?.magnification ?: 1f
    touchControls?.restoreMagnification(touchMagnification)
    applyZoom();applyAspect()
    usbWatch?.close();usbWatch=UsbMedia.watch(this,url){android.widget.Toast.makeText(this,resources.getString(R.string.ui_usb_drive_disconnected),android.widget.Toast.LENGTH_LONG).show();endPlayback()}
    privatePlayback=app.ember.player.PrivateMedia.hidden(url)
    if(privatePlayback){
        if(!app.ember.player.PrivateMedia.unlocked.value){endPlayback();return}
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);platformSession?.release();platformSession=null
    }
    currentUrl = url
    triedAsHls = false
    if (subsInitialized)subs.config=subConfigFor(prefs.subtitleConfig)
    if (subsInitialized) {
        subs.startSearch(
            url,
            app.ember.tv.data.Subtitles.Query(
                imdb = item.imdbId,
                movie = item.kind == Kind.MOVIE,
                season = item.season,
                episode = item.episode,
                release = release ?: title,
                hash = null,
                size = null,
            ),
            headers=sourceHeaders,
        )
    }
    subsTouched=false
    if(subsInitialized)restoreManualSubtitle()
    codecs.pcm=false
    codecs.sourceUrl=url;codecs.sourceFilename=release;codecs.manualVideo=userVideoMode!=null
    decoderManager.selectVideoDecoder(userVideoMode ?: if(config.ffmpegVideo=="always")DecoderMode.FFMPEG else DecoderMode.AUTO)
    decoderManager.selectAudioDecoder(userAudioMode ?: DecoderMode.AUTO)
    codecs.ffmpegVideo=decoderManager.videoMode==DecoderMode.FFMPEG
    codecs.preferSoftware=false
    audioPicked = false
    modeApplied = false
    dropped = 0
    loadSpeed = 0
    recoveries = 0
    ioRetries = 0
    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
    val media = MediaItem.Builder()
        .setUri(url)
        // Live links often have no extension; tell the player when it is HLS or DASH.
        .apply { PlaybackErrors.mimeHint(url)?.let(::setMimeType) }
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()
    val audio = audioUrl
    if (audio != null) {
        // Video and audio come as two streams; play them together.
        val factory = mediaSourceFactory(DefaultDataSource.Factory(this, httpFactory))
        val audioItem = MediaItem.fromUri(audio)
        p.setMediaSource(MergingMediaSource(factory.createMediaSource(media), factory.createMediaSource(audioItem)))
    } else {
        p.setMediaItem(media)
    }
    p.prepare()
    userVideoMode?.let { mode -> playbackEngine?.compatibility?.softwareVideo(mode == DecoderMode.FFMPEG || mode == DecoderMode.SOFTWARE) }
    p.playWhenReady = true
    loadMarkers()
}

@OptIn(UnstableApi::class)
/**
 * "Stop watching?" with Keep watching focused. The video keeps playing behind it; Back again
 * (or Stop) leaves, so a double press still exits quickly.
 */
internal fun PlayerActivity.askToLeave() {
    if (leaveDialog?.isShowing == true) return
    val p = player ?: return
    val name = controls.title.ifBlank { if (itemInitialized) item.title else "" }
    val where = playbackDuration(p).takeIf { it > 0 }?.let { resources.getString(R.string.ui_of ,time(p.currentPosition),time(it)) }
    leaveDialog = showPlayerActions(resources.getString(R.string.ui_stop_watching), listOf(
        PlayerDialogAction(resources.getString(R.string.ui_keep_watching), Icons.Outlined.PlayArrow) {},
        PlayerDialogAction(resources.getString(R.string.ui_stop), Icons.Outlined.StopCircle, destructive = true) { endPlayback() },
    ), message = listOfNotNull(name.takeIf { it.isNotBlank() }, where).joinToString(" · ").ifBlank { null },
        cancel = { endPlayback() })
}

@OptIn(UnstableApi::class)
/** Save the position, release playback resources and normally close the screen. */
internal fun PlayerActivity.endPlayback(finishActivity: Boolean = true) {
    clips.cancel()
    closeCaptionAssistance();captionConfig=null;embeddedCaptionText=""
    usbWatch?.close();usbWatch=null
    manualSubtitleJob?.cancel();manualSubtitleJob=null
    nearbySubtitleJob?.cancel();nearbySubtitleJob=null
    cancelRecovery()
    playbackVisit++
    handler.removeCallbacks(saver)
    handler.removeCallbacks(infoUpdater)
    handler.removeCallbacks(hideInfo)
    handler.removeCallbacks(subtitleTicker)
    handler.removeCallbacks(markerTicker)
    handler.removeCallbacks(controlsTicker)
    handler.removeCallbacks(hideControls)
    handler.removeCallbacks(statsTicker)
    outputRoute?.close(); outputRoute=null
    loudness?.let { runCatching { it.release() } }
    loudness = null
    val p = player ?: return
    rememberTracks()
    if (!ended) {
        savePosition()
    }
    platformSession?.release(); platformSession = null
    decoderManager.detach()
    p.release()
    player = null
    if (finishActivity) finish()
}

@OptIn(UnstableApi::class)
// ---- resume and progress ----
internal fun PlayerActivity.resume(p: ExoPlayer): Boolean {
    val forced=intent.getLongExtra(BackgroundAudioService.FORCE_POSITION,-1L)
    if(forced>=0) {intent.removeExtra(BackgroundAudioService.FORCE_POSITION);p.seekTo(forced.coerceAtMost(p.duration.takeIf {it>0} ?: Long.MAX_VALUE));return true}
    if (startOver) return true
    val saved = positions.resume(resumeKey)
    val duration = playbackDuration(p).takeIf {it>0}
    if(duration==null) {
        if(playbackEngine?.compatibility!=null && saved!=null){p.seekTo(saved.first);return true}
        return false
    }
    when {
        saved != null && PositionStore.canResume(saved.first,duration) -> p.seekTo(saved.first)
        resumeFraction in 0.02f..0.95f -> p.seekTo((duration * resumeFraction).toLong())
    }
    return true
}

@OptIn(UnstableApi::class)
internal fun PlayerActivity.playbackDuration(p: ExoPlayer): Long = p.duration.takeIf {it>0} ?: 0L

@OptIn(UnstableApi::class)
internal fun PlayerActivity.progress(): Float {
    val p = player ?: return 0f
    val d = playbackDuration(p)
    return if (d > 0) (p.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
}

@OptIn(UnstableApi::class)
internal fun PlayerActivity.savePosition() {
    if (privatePlayback || !app.ember.player.PrivateMedia.historyAllowed(currentUrl.orEmpty())) return
    val p = player ?: return
    if (ended) return
    val d = playbackDuration(p)
    if (d <= 0) return
    val pos = p.currentPosition
    positions.put(resumeKey, pos, d)
}

@OptIn(UnstableApi::class)
internal fun PlayerActivity.contentPlaybackAllowed():Boolean = watchProfileId==prefs.currentProfile
