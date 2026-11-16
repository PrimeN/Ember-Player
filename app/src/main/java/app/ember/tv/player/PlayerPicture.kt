package app.ember.tv.player

import android.widget.Toast
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.applyPictureEnhancement(format:Format?=player?.videoFormat) {
    val engine=playbackEngine?:return
    val effective=renderConfig()
    val picture=effective.picture.copy(enabled=effective.picture.enabled&&!pictureBypassed)
    engine.compatibility?.let {it.enhancement(picture);it.hdrOutput(effective.hdrOutput);return}
    val hdr=format?.colorInfo?.colorTransfer in setOf(androidx.media3.common.C.COLOR_TRANSFER_ST2084,androidx.media3.common.C.COLOR_TRANSFER_HLG)
    val expansion=effective.hdrOutput.expand&&!hdr&&format!=null&&HdrDisplayInfo.read(this).pq&&RendererPolicy.normalized(effective.renderer)!="gpu"
    if(!compareOriginal && config.engine=="auto" && (effective.hdrOutput.sdr&&hdr || expansion) && engine.standard.currentTracks.groups.isNotEmpty() && format?.drmInitData==null && engine.switch())return
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
    changePictureState {it.copy(picture=value.normalized())}
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.recoverPicture(error:PlaybackException):Boolean {
    if(pictureBypassed || !pictureEffectsActive || !PictureProfiles.effective(config,resumeKey).picture.enabled || playbackEngine?.compatibility!=null ||
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
    val effective=PictureProfiles.effective(config,resumeKey)
    val p=effective.picture
    add(OptionRow("Compare picture processing",note="Compare original and saved processing; closing the comparison restores saved settings.",action={closeMenu();comparePicture()}))
    add(OptionRow("Compatibility renderer",RendererPolicy.choices.first {it.first==RendererPolicy.normalized(config.renderer)}.second,RendererPolicy.choices.map {it.second},RendererPolicy.choices.indexOfFirst {it.first==RendererPolicy.normalized(config.renderer)},note="Advanced enables inverse tone mapping. Renderer failures retry stable GPU output.",onPick={index->config=config.copy(renderer=RendererPolicy.choices[index].first);prefs.playbackConfig=config;playbackEngine?.compatibility?.renderer(config.renderer);changed()}))
    add(OptionRow("Network and buffering",action={closeMenu();editStreamSettings()}))
    val bound=PictureProfiles.selected(config,resumeKey)
    add(OptionRow("Saved picture / shader profiles",bound?.name ?: "Defaults",note=if(bound!=null)"Edits update this saved profile." else "Save and reuse picture, shader and HDR settings.",action={closeMenu();managePictureProfiles()}))
    val note="GPU effects use more power. HDR, Dolby Vision, protected and tunneled video keep their supported output path. Standard uses Lanczos; Compatibility can also use the bundled shader when enlarging video."
    add(OptionRow("Picture enhancement",toggle=p.enabled,note=if(pictureBypassed || playbackEngine?.compatibility?.enhancementBypassed==true)"Temporarily bypassed for this video. Toggle to retry." else note,
        action={changePicture(p.copy(enabled=!p.enabled))}))
    addAll(hdrOutputRows(effective.hdrOutput))
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun PlayerActivity.hdrOutputRows(value:HdrOutput):List<OptionRow> = buildList {
    val p=value.normalized()
    add(OptionRow("HDR to SDR output",toggle=p.sdr,note=if(playbackEngine?.compatibility?.toneMappingBypassed==true)"Temporarily bypassed. Toggle to retry." else "Compatibility only. Normal HDR output stays automatic when off. Separate from SDR picture enhancement.",action={changePictureState {it.copy(hdr=p.copy(sdr=!p.sdr,expand=false))}}))
    add(OptionRow("SDR to HDR · experimental",toggle=p.expand,note="Local inverse tone mapping. Requires an HDR10 display, Advanced renderer and a signaled SurfaceView. It cannot recover missing source detail.",action={changePictureState {it.copy(hdr=p.copy(expand=!p.expand,sdr=false))}}))
    if(p.expand){val peaks=(listOf(300,400,600,800,1000,1500)+p.hdrPeak).distinct().sorted();add(OptionRow("Expanded HDR peak","${p.hdrPeak} nits",peaks.map {"$it nits"},peaks.indexOf(p.hdrPeak).coerceAtLeast(0),onPick={index->changePictureState {it.copy(hdr=p.copy(hdrPeak=peaks[index]))}}))}
    if(p.sdr) {
        add(OptionRow("Tone mapping",HdrOutput.tones.first {it.first==p.tone}.second,HdrOutput.tones.map {it.second},HdrOutput.tones.indexOfFirst {it.first==p.tone},onPick={index->changePictureState {it.copy(hdr=p.copy(tone=HdrOutput.tones[index].first))}}))
        add(OptionRow("Gamut mapping",HdrOutput.gamuts.first {it.first==p.gamut}.second,HdrOutput.gamuts.map {it.second},HdrOutput.gamuts.indexOfFirst {it.first==p.gamut},onPick={index->changePictureState {it.copy(hdr=p.copy(gamut=HdrOutput.gamuts[index].first))}}))
        val peaks=(listOf(50,80,100,120,160,203,250,300,400,500)+p.peak).distinct().sorted()
        add(OptionRow("SDR screen peak","${p.peak} nits",peaks.map {"$it nits"},peaks.indexOf(p.peak),onPick={index->changePictureState {it.copy(hdr=p.copy(peak=peaks[index]))}}))
        add(OptionRow("Analyze scene peaks",toggle=p.analyzePeak,note="Extra GPU work; off uses available video metadata.",action={changePictureState {it.copy(hdr=p.copy(analyzePeak=!p.analyzePeak))}}))
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.renderConfig():PlaybackConfig {
    val selected=PictureProfiles.effective(config,resumeKey)
    return if(compareOriginal)selected.copy(picture=selected.picture.copy(enabled=false),hdrOutput=HdrOutput(),deband=false,interpolation=false) else selected
}
