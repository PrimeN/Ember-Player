package app.ember.tv.player

import app.ember.tv.R

import app.ember.player.localized

import androidx.media3.common.util.UnstableApi

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerActivity.navigateSeek(target: Long) {
    playbackEngine?.navigate(target,SeekPreference.fast(config.seekMode))
    controls.position=player?.currentPosition ?: controls.position
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerActivity.changeAudioEffects(value: AudioEffects) {
    config=config.copy(audioEffects=value)
    prefs.playbackConfig=config
    audioEffectState.value=value
    if(playbackEngine?.compatibility?.audioEffects(value)==false)
        android.widget.Toast.makeText(this,resources.getString(R.string.ui_audio_effects_are_unavailable_for_this_output),android.widget.Toast.LENGTH_SHORT).show()
    changed()
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerActivity.editPlayerShortcuts() {
    fun save(values: List<String>) {config=config.copy(quickShortcuts=PlayerShortcuts.normalized(values));prefs.playbackConfig=config;changed()}
    val current=PlayerShortcuts.normalized(config.quickShortcuts)
    val entries=current.map { id->PlayerShortcuts.choices.localized(resources).first {it.first==id} } + PlayerShortcuts.choices.localized(resources).filter {current.size<8 && it.first !in current}
    showPlayerActions(resources.getString(R.string.ui_player_shortcuts),entries.map { (id,label) ->
        PlayerDialogAction(if(id in current) "${current.indexOf(id)+1}. $label" else resources.getString(R.string.ui_add ,label),selected=id in current) {
            val index=current.indexOf(id)
            if(index<0) {if(current.size<8)save(current+id);editPlayerShortcuts()}
            else showPlayerActions(label,buildList {
                if(index>0)add(PlayerDialogAction(resources.getString(R.string.ui_move_earlier_07dd6f)) {val list=current.toMutableList();java.util.Collections.swap(list,index,index-1);save(list);editPlayerShortcuts()})
                if(index<current.lastIndex)add(PlayerDialogAction(resources.getString(R.string.ui_move_later_bb4c84)) {val list=current.toMutableList();java.util.Collections.swap(list,index,index+1);save(list);editPlayerShortcuts()})
                add(PlayerDialogAction(resources.getString(R.string.ui_remove_shortcut),destructive=true) {save(current-id);editPlayerShortcuts()})
            })
        }
    }+PlayerDialogAction(resources.getString(R.string.ui_reset_shortcuts)) {save(PlayerShortcuts.defaults);editPlayerShortcuts()},message=resources.getString(R.string.ui_choose_up_to_eight_controls_select_an_existing_shortcut))
}
