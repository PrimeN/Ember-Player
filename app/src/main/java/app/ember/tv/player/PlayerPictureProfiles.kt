package app.ember.tv.player

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
            add(PlayerDialogAction("Use as default") {store(profile.state.applyTo(if(resumeKey.isNotBlank())PictureProfiles.bind(config,resumeKey,null) else config))})
            if(resumeKey.isNotBlank()&&!privatePlayback&&!live&&!trailer)
                add(PlayerDialogAction("Use for this media") {store(PictureProfiles.bind(config,resumeKey,profile.id))})
            add(PlayerDialogAction("Delete profile",destructive=true){store(PictureProfiles.remove(config,profile.id));managePictureProfiles()})
        },message="Includes picture, shader and HDR output settings.")
    }}.toMutableList()
    actions+=PlayerDialogAction("Save current settings") {
        playbackValueDialog("Save picture / shader profile","","Name this profile. An existing name replaces that profile.","Save") {name->
            runCatching {store(PictureProfiles.save(config,name,PictureState.from(PictureProfiles.effective(config,resumeKey))))}.exceptionOrNull()?.message
        }
    }
    if(bound!=null)actions+=PlayerDialogAction("Use global picture settings") {store(PictureProfiles.bind(config,resumeKey,null))}
    showPlayerActions("Picture / shader profiles",actions,message=bound?.let {"This media uses ${it.name}. Edits update that saved profile."} ?: "Save up to 16 profiles; optionally remember one for a media item.")
}
