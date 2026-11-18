package app.ember.tv.player

import app.ember.tv.R

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.applyEffectivePicture() {
    val effective=renderConfig()
    pictureBypassed=false
    playbackEngine?.compatibility?.picture(effective.deband,effective.interpolation,effective.scaler,effective.deinterlacing)
    applyPictureEnhancement();changed()
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.changePictureState(change:(PictureState)->PictureState) {
    config=PictureProfiles.edit(config,resumeKey,change);prefs.playbackConfig=config
    applyEffectivePicture()
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.managePictureProfiles() {
    fun store(value:PlaybackConfig){config=value;prefs.playbackConfig=config;applyEffectivePicture()}
    val safe=PictureProfiles.normalized(config)
    val bound=PictureProfiles.selected(safe,resumeKey)
    val actions=safe.pictureProfiles.map {profile->PlayerDialogAction(profile.name,selected=profile.id==bound?.id) {
        showPlayerActions(profile.name,buildList {
            add(PlayerDialogAction(resources.getString(R.string.ui_use_as_default)) {store(profile.state.applyTo(if(resumeKey.isNotBlank())PictureProfiles.bind(config,resumeKey,null) else config))})
            if(resumeKey.isNotBlank()&&!privatePlayback)
                add(PlayerDialogAction(resources.getString(R.string.ui_use_for_this_media)) {store(PictureProfiles.bind(config,resumeKey,profile.id))})
            add(PlayerDialogAction(resources.getString(R.string.ui_delete_profile),destructive=true){store(PictureProfiles.remove(config,profile.id));managePictureProfiles()})
        },message=resources.getString(R.string.ui_includes_picture_shader_and_hdr_output_settings))
    }}.toMutableList()
    actions+=PlayerDialogAction(resources.getString(R.string.ui_save_current_settings)) {
        playbackValueDialog(resources.getString(R.string.ui_save_picture_shader_profile),"",resources.getString(R.string.ui_name_this_profile_an_existing_name_replaces_that_profile),resources.getString(R.string.ui_save)) {name->
            runCatching {store(PictureProfiles.save(config,name,PictureState.from(PictureProfiles.effective(config,resumeKey))))}.exceptionOrNull()?.let {resources.getString(PresetValidationException.resource(it))}
        }
    }
    if(bound!=null)actions+=PlayerDialogAction(resources.getString(R.string.ui_use_global_picture_settings)) {store(PictureProfiles.bind(config,resumeKey,null))}
    showPlayerActions(resources.getString(R.string.ui_picture_shader_profiles),actions,message=bound?.let {resources.getString(R.string.ui_this_media_uses_edits_update_that_saved_profile ,it.name)} ?: resources.getString(R.string.ui_save_up_to_16_profiles_optionally_remember_one_for))
}
