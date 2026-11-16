package app.ember.tv.player

import android.widget.Toast
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.applyPictureEnhancement(format:Format?=player?.videoFormat) {
    val engine=playbackEngine?:return
    val picture=config.picture.copy(enabled=config.picture.enabled&&!pictureBypassed)
    engine.compatibility?.let {it.enhancement(picture);return}
    val size=resources.displayMetrics
    val width=if(viewInitialized && view.width>0)view.width else size.widthPixels
    val height=if(viewInitialized && view.height>0)view.height else size.heightPixels
    val signature="${picture.normalized()}|${width}x${height}|${format?.width}x${format?.height}|${format?.rotationDegrees}|${StandardPictureEffects.supported(format)}|${config.tunneling}"
    if(signature==pictureSignature)return
    val effects=if(config.tunneling)emptyList() else StandardPictureEffects.effects(picture,width,height,format)
    engine.standard.setVideoEffects(effects)
    pictureEffectsActive=effects.isNotEmpty();pictureSignature=signature
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.changePicture(value:PictureEnhancement) {
    config=config.copy(picture=value.normalized());prefs.playbackConfig=config
    pictureBypassed=false
    applyPictureEnhancement();changed()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.recoverPicture(error:PlaybackException):Boolean {
    if(pictureBypassed || !pictureEffectsActive || !config.picture.enabled || playbackEngine?.compatibility!=null ||
        error.errorCode !in setOf(PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED,PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED))return false
    val p=player?:return false
    val at=p.currentPosition;val wanted=p.playWhenReady
    pictureBypassed=true
    applyPictureEnhancement()
    p.seekTo(at);p.prepare();p.playWhenReady=wanted
    Toast.makeText(this,"Picture enhancement unavailable for this video. Continuing without effects.",Toast.LENGTH_LONG).show()
    return true
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.pictureOptionRows():List<OptionRow> = buildList {
    val p=config.picture
    val note="GPU effects use more power. HDR, Dolby Vision, protected and tunneled video keep their supported output path. Standard uses Lanczos; Compatibility can also use the bundled shader when enlarging video."
    add(OptionRow("Picture enhancement",toggle=p.enabled,note=if(pictureBypassed || playbackEngine?.compatibility?.enhancementBypassed==true)"Temporarily bypassed for this video. Toggle to retry." else note,
        action={changePicture(p.copy(enabled=!p.enabled))}))
    if(p.enabled) {
        add(OptionRow("GPU upscaling",toggle=p.upscaling,action={changePicture(p.copy(upscaling=!p.upscaling))}))
        add(OptionRow("Fast upscaling shader",toggle=p.shader,note="Compatibility playback only; active above 1.2× in both dimensions.",action={changePicture(p.copy(shader=!p.shader))}))
        add(OptionRow("Picture preset",PictureEnhancement.presets.getOrNull(PictureEnhancement.presetIndex(p)) ?: "Custom",PictureEnhancement.presets,PictureEnhancement.presetIndex(p),onPick={changePicture(PictureEnhancement.preset(it,p))}))
        pictureAdjustments.forEach {adjustment->
            val values=adjustment.values(p)
            add(OptionRow(adjustment.label,adjustment.read(p.normalized()).toString(),values.map(Int::toString),values.indexOf(adjustment.read(p.normalized())),
                onPick={changePicture(adjustment.write(p,values[it]))}))
        }
        add(OptionRow("Reset picture adjustments",action={changePicture(p.copy(brightness=0,contrast=0,saturation=0,gamma=0,hue=0,sharpness=0))}))
    }
}
