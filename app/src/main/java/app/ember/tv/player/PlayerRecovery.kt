package app.ember.tv.player

import app.ember.tv.R
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.delay
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode

@OptIn(UnstableApi::class)
internal fun PlayerActivity.mediaSourceFactory(factory: androidx.media3.datasource.DataSource.Factory)=DefaultMediaSourceFactory(factory)

@OptIn(UnstableApi::class)
internal fun PlayerActivity.describe(f: androidx.media3.common.Format?) = PlaybackErrors.describe(resources,f)

@OptIn(UnstableApi::class)
/**
 * Decoding failed: try the HDR10 picture of a Dolby Vision file, another sound track, or a
 * software decoder (up to 1080p), resuming where it stopped. False when nothing is left to try.
 */
internal fun PlayerActivity.recover(e: PlaybackException): Boolean {
    if(playbackEngine?.compatibility!=null)return false
    val failure=PlaybackFailureClassifier.classify(e)
    val decoding = failure.changesDecoder
    val p = player ?: return false
    val network = PlaybackErrors.connection(e.errorCode)
    if (network && ioRetries < 2) {
        ioRetries++
        lastRetryAt = android.os.SystemClock.elapsedRealtime()
        Toast.makeText(this, getString(R.string.pl_the_connection_dropped_trying_again), Toast.LENGTH_SHORT).show()
        val at = p.currentPosition
        val visit = playbackVisit
        val media = p.currentMediaItem
        val key = resumeKey
        cancelRecovery()
        val retry = Runnable {
            pendingRecovery = null
            if (player === p && playbackVisit == visit && resumeKey == key && p.currentMediaItem == media) {
                p.seekTo(at)
                // prepare retains playWhenReady, including a pause made during the retry delay.
                p.prepare()
            }
        }
        pendingRecovery = retry
        handler.postDelayed(retry, 1_500L * ioRetries)
        return true
    }
    val outputFailure=failure==PlaybackFailure.AUDIO_OUTPUT
    if ((!decoding && !outputFailure) || recoveries >= 6) return false
    val format=(e as? androidx.media3.exoplayer.ExoPlaybackException)?.rendererFormat
    val audio=outputFailure || format?.sampleMimeType?.let(MimeTypes::isAudio)==true
    // Session and saved decoder choices are explicit. Leave their error visible.
    val manual=if(audio)userAudioMode!=null || config.ffmpeg=="prefer" || config.ffmpeg=="off"
        else userVideoMode!=null || config.ffmpegVideo=="always" ||
            config.decoderRules.preference(currentUrl.orEmpty(),format?.sampleMimeType,codecs.sourceFilename) !in setOf("inherit","auto")
    val message: String
    if(audio && decoderRecovery.pcmFallback()) {
        codecs.pcm=true
        val was=p.playWhenReady;val at=p.currentPosition
        p.stop();p.seekTo(at);p.playWhenReady=was
        message=resources.getString(R.string.ui_trying_pcm_audio_output)
    } else {
        val safe=audio || (format ?: p.videoFormat)?.let(DecoderHealth::softwareSafe)==true
        val next=decoderRecovery.next(audio,if(audio)config.ffmpeg!="off" else config.ffmpegVideo!="off",safe,manual) ?: return false
        val route=PlaybackRoute(PlaybackCore.MEDIA3,if(audio)videoDecoder else next,
            if(audio)next else audioDecoder,if(codecs.pcm)"pcm" else "automatic")
        if(!decoderRecovery.attempt(route))return false
        val mode=if(next=="ffmpeg")DecoderMode.FFMPEG else DecoderMode.SOFTWARE
        if(audio)decoderManager.selectAudioDecoder(mode) else { decoderManager.selectVideoDecoder(mode);codecs.ffmpegVideo=mode==DecoderMode.FFMPEG }
        message=if(audio)resources.getString(R.string.ui_trying_another_audio_decoder) else resources.getString(R.string.ui_trying_another_video_decoder)
    }
    recoveries++
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    val at = p.currentPosition
    p.seekTo(at)
    p.prepare()
    return true
}

@OptIn(UnstableApi::class)
/**
 * Switches video to Nextlib's FFmpeg decoder: opens its gate and turns the TV's video decoders
 * off in the track selector, which makes it choose the video track again.
 */
internal fun PlayerActivity.useFfmpegVideo(p: ExoPlayer) {
    codecs.ffmpegVideo=true
    decoderManager.selectVideoDecoder(DecoderMode.FFMPEG)
}

@OptIn(UnstableApi::class)
internal fun PlayerActivity.cancelRecovery() {
    pendingRecovery?.let(handler::removeCallbacks)
    pendingRecovery = null
}
