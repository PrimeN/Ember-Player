package app.ember.tv.player

import app.ember.tv.R

import android.widget.Toast
import app.ember.player.localized
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
    Toast.makeText(this,resources.getString(R.string.ui_picture_enhancement_unavailable_for_this_video_continuing_without_effects),Toast.LENGTH_LONG).show()
    return true
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.pictureOptionRows():List<OptionRow> = buildList {
    val effective=PictureProfiles.effective(config,resumeKey)
    val p=effective.picture
    add(OptionRow(resources.getString(R.string.ui_compare_picture_processing),note=resources.getString(R.string.ui_compare_original_and_saved_processing_closing_the_comparison_restores),action={closeMenu();comparePicture()}))
    add(OptionRow(resources.getString(R.string.ui_compatibility_renderer),RendererPolicy.choices.localized(resources).first {it.first==RendererPolicy.normalized(config.renderer)}.second,RendererPolicy.choices.localized(resources).map {it.second},RendererPolicy.choices.localized(resources).indexOfFirst {it.first==RendererPolicy.normalized(config.renderer)},note=resources.getString(R.string.ui_advanced_enables_inverse_tone_mapping_renderer_failures_retry_stable),onPick={index->config=config.copy(renderer=RendererPolicy.choices.localized(resources)[index].first);prefs.playbackConfig=config;playbackEngine?.compatibility?.renderer(config.renderer);changed()}))
    add(OptionRow(resources.getString(R.string.ui_network_and_buffering),action={closeMenu();editStreamSettings()}))
    val bound=PictureProfiles.selected(config,resumeKey)
    add(OptionRow(resources.getString(R.string.ui_saved_picture_shader_profiles),bound?.name ?: resources.getString(R.string.ui_defaults),note=if(bound!=null)resources.getString(R.string.ui_edits_update_this_saved_profile) else resources.getString(R.string.ui_save_and_reuse_picture_shader_and_hdr_settings),action={closeMenu();managePictureProfiles()}))
    val note=resources.getString(R.string.ui_gpu_effects_use_more_power_hdr_dolby_vision_protected)
    add(OptionRow(resources.getString(R.string.ui_picture_enhancement),toggle=p.enabled,note=if(pictureBypassed || playbackEngine?.compatibility?.enhancementBypassed==true)resources.getString(R.string.ui_temporarily_bypassed_for_this_video_toggle_to_retry) else note,
        action={changePicture(p.copy(enabled=!p.enabled))}))
    addAll(hdrOutputRows(effective.hdrOutput))
    if(p.enabled) {
        add(OptionRow(resources.getString(R.string.ui_gpu_upscaling),toggle=p.upscaling,action={changePicture(p.copy(upscaling=!p.upscaling))}))
        add(OptionRow(resources.getString(R.string.ui_fast_upscaling_shader),toggle=p.shader,note=resources.getString(R.string.ui_compatibility_playback_only_active_above_1_2_in_both),action={changePicture(p.copy(shader=!p.shader))}))
        add(OptionRow(resources.getString(R.string.ui_picture_preset),PictureEnhancement.presets.getOrNull(PictureEnhancement.presetIndex(p))?.let(resources::getString) ?: resources.getString(R.string.ui_custom),PictureEnhancement.presets.map(resources::getString),PictureEnhancement.presetIndex(p),onPick={changePicture(PictureEnhancement.preset(it,p))}))
        pictureAdjustments.forEach {adjustment->
            val values=adjustment.values(p)
            add(OptionRow(resources.getString(adjustment.label),adjustment.read(p.normalized()).toString(),values.map(Int::toString),values.indexOf(adjustment.read(p.normalized())),
                onPick={changePicture(adjustment.write(p,values[it]))}))
        }
        add(OptionRow(resources.getString(R.string.ui_reset_picture_adjustments),action={changePicture(p.copy(brightness=0,contrast=0,saturation=0,gamma=0,hue=0,sharpness=0))}))
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun PlayerActivity.hdrOutputRows(value:HdrOutput):List<OptionRow> = buildList {
    val p=value.normalized()
    add(OptionRow(resources.getString(R.string.ui_hdr_to_sdr_output),toggle=p.sdr,note=if(playbackEngine?.compatibility?.toneMappingBypassed==true)resources.getString(R.string.ui_temporarily_bypassed_toggle_to_retry) else resources.getString(R.string.ui_compatibility_only_normal_hdr_output_stays_automatic_when_off),action={changePictureState {it.copy(hdr=p.copy(sdr=!p.sdr,expand=false))}}))
    add(OptionRow(resources.getString(R.string.ui_sdr_to_hdr_experimental),toggle=p.expand,note=resources.getString(R.string.ui_local_inverse_tone_mapping_requires_an_hdr10_display_advanced),action={changePictureState {it.copy(hdr=p.copy(expand=!p.expand,sdr=false))}}))
    if(p.expand){val peaks=(listOf(300,400,600,800,1000,1500)+p.hdrPeak).distinct().sorted();add(OptionRow(resources.getString(R.string.ui_expanded_hdr_peak),resources.getString(R.string.ui_nits ,p.hdrPeak.toString()),peaks.map {resources.getString(R.string.ui_nits ,it.toString())},peaks.indexOf(p.hdrPeak).coerceAtLeast(0),onPick={index->changePictureState {it.copy(hdr=p.copy(hdrPeak=peaks[index]))}}))}
    if(p.sdr) {
        add(OptionRow(resources.getString(R.string.ui_tone_mapping),HdrOutput.tones.localized(resources).first {it.first==p.tone}.second,HdrOutput.tones.localized(resources).map {it.second},HdrOutput.tones.localized(resources).indexOfFirst {it.first==p.tone},onPick={index->changePictureState {it.copy(hdr=p.copy(tone=HdrOutput.tones.localized(resources)[index].first))}}))
        add(OptionRow(resources.getString(R.string.ui_gamut_mapping),HdrOutput.gamuts.localized(resources).first {it.first==p.gamut}.second,HdrOutput.gamuts.localized(resources).map {it.second},HdrOutput.gamuts.localized(resources).indexOfFirst {it.first==p.gamut},onPick={index->changePictureState {it.copy(hdr=p.copy(gamut=HdrOutput.gamuts.localized(resources)[index].first))}}))
        val peaks=(listOf(50,80,100,120,160,203,250,300,400,500)+p.peak).distinct().sorted()
        add(OptionRow(resources.getString(R.string.ui_sdr_screen_peak),resources.getString(R.string.ui_nits ,p.peak.toString()),peaks.map {resources.getString(R.string.ui_nits ,it.toString())},peaks.indexOf(p.peak),onPick={index->changePictureState {it.copy(hdr=p.copy(peak=peaks[index]))}}))
        add(OptionRow(resources.getString(R.string.ui_analyze_scene_peaks),toggle=p.analyzePeak,note=resources.getString(R.string.ui_extra_gpu_work_off_uses_available_video_metadata),action={changePictureState {it.copy(hdr=p.copy(analyzePeak=!p.analyzePeak))}}))
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.renderConfig():PlaybackConfig {
    val selected=PictureProfiles.effective(config,resumeKey)
    return if(compareOriginal)selected.copy(picture=selected.picture.copy(enabled=false),hdrOutput=HdrOutput(),deband=false,interpolation=false) else selected
}
