package app.ember.tv.player

import app.ember.tv.R

import android.content.Intent
import android.widget.Toast
import androidx.media3.common.util.UnstableApi

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerActivity.backgroundRequest():BackgroundAudioRequest? {
    if(privatePlayback||app.ember.player.PrivateMedia.hidden(currentUrl.orEmpty()))return null
    if(!config.backgroundAudio || pickingSidecar || isFinishing || isInPictureInPictureMode)return null
    val p=player ?: return null
    if(!p.playWhenReady || ended)return null
    val source=playbackEngine?.snapshot()?.takeIf {it.urls.size==1} ?: return null
    val effective=config.copy(
        engine=if(playbackEngine?.compatibility!=null)"compatibility" else config.engine,
        passthrough=config.passthrough&&!codecs.pcm,
        audioDelayMs=if(!bluetoothOutput)(audioDelay.us/1000).toInt() else config.audioDelayMs,
        bluetoothDelayMs=if(bluetoothOutput)(audioDelay.us/1000).toInt() else config.bluetoothDelayMs,
    )
    return BackgroundAudioRequest(source.copy(audioLanguages=(listOfNotNull(p.audioFormat?.language)+source.audioLanguages).distinct()),effective,
        Intent(intent),queueId,watchProfileId,localActivityScope,p.currentPosition.coerceAtLeast(0),p.playbackParameters.speed,p.volume,
        boosts.getOrElse(boostLevel){0},BackgroundAudioTrack.from(p.audioFormat),p.mediaMetadata.artworkData?.takeIf {it.size<=512*1024})
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerActivity.stageBackgroundAudio() {
    if(backgroundStage!=null || !configInitialized)return
    val request=backgroundRequest() ?: return
    backgroundStage=runCatching {BackgroundAudioService.stage(this,request)}.getOrNull()
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerActivity.commitBackgroundAudio():Boolean {
    val request=if(configInitialized)backgroundRequest() else null
    var stage=backgroundStage
    if(request==null){stage?.let(BackgroundAudioService::cancelStage);backgroundStage=null;return false}
    if(stage==null) {
        stage=runCatching {BackgroundAudioService.stage(this,request)}.getOrNull()
        if(stage==null){Toast.makeText(this,resources.getString(R.string.ui_this_device_blocked_background_playback),Toast.LENGTH_SHORT).show();return false}
        backgroundStage=stage
    }
    // The compatibility backend allows one native player per process. Release video first.
    endPlayback(finishActivity=true)
    backgroundQueueTransferred=BackgroundAudioService.commit(stage,request)
    backgroundStage=null
    return backgroundQueueTransferred
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerActivity.queueItemAllowed(media:QueueMedia)=!app.ember.player.PrivateMedia.hidden(media.uri) || privatePlayback&&app.ember.player.PrivateMedia.unlocked.value
