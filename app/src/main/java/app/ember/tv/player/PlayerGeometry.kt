package app.ember.tv.player

import app.ember.tv.R

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.currentZoom()=GeometryMemory.selected(config,resumeKey)?.zoom ?: config.zoom
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.persistGeometry() {
    if(GeometryMemory.selected(config,resumeKey)==null || privatePlayback)return
    config=GeometryMemory.set(config,resumeKey,VideoGeometry(currentZoom(),aspect,touchMagnification));prefs.playbackConfig=config
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.changeZoom(value:String) {
    if(GeometryMemory.selected(config,resumeKey)!=null)config=GeometryMemory.set(config,resumeKey,VideoGeometry(value,aspect,touchMagnification))
    else config=config.copy(zoom=value)
    prefs.playbackConfig=config;applyZoom();changed()
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.toggleGeometryMemory() {
    val current=GeometryMemory.selected(config,resumeKey)
    config=GeometryMemory.set(config,resumeKey,if(current==null)VideoGeometry(currentZoom(),aspect,touchMagnification) else null)
    prefs.playbackConfig=config
    if(current!=null){aspect=0;touchMagnification=1f;touchControls?.restoreMagnification(1f)}
    applyZoom();applyAspect();changed()
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.manageSubtitlePresets() {
    val current=prefs.subtitleConfig
    showPlayerActions(resources.getString(R.string.ui_subtitle_styles),prefs.subtitlePresets.take(12).map {preset->PlayerDialogAction(preset.name) {
        showPlayerActions(preset.name,listOf(PlayerDialogAction(resources.getString(R.string.ui_apply_style)){applySubLook(preset.style.applyTo(prefs.subtitleConfig));changed()},
            PlayerDialogAction(resources.getString(R.string.ui_delete_style),destructive=true){prefs.subtitlePresets=prefs.subtitlePresets.filterNot {it.name==preset.name};manageSubtitlePresets()}))
    }}+PlayerDialogAction(resources.getString(R.string.ui_save_current_style)) {
        playbackValueDialog(resources.getString(R.string.ui_save_subtitle_style),"",resources.getString(R.string.ui_appearance_only_language_and_provider_settings_stay_as_they),resources.getString(R.string.ui_save)){name->
            runCatching {prefs.subtitlePresets=SubtitlePresets.save(prefs.subtitlePresets,name,current)}.exceptionOrNull()?.message
        }
    })
}
