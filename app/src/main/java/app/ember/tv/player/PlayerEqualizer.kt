package app.ember.tv.player

import app.ember.tv.R

import androidx.media3.common.util.UnstableApi

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerActivity.editEqualizer() {
    val effects = config.audioEffects
    showPlayerActions(resources.getString(R.string.ui_equalizer), AudioEffects.frequencies.indices.map { index ->
        PlayerDialogAction(resources.getString(R.string.ui_db ,AudioEffects.frequencyLabel(index),(effects.gains[index]).toString())) {
            showPlayerActions(AudioEffects.frequencyLabel(index), (-12..12).map { gain ->
                PlayerDialogAction(resources.getString(R.string.ui_db_276a0e ,if (gain > 0) "+" else "",gain.toString()), selected = gain == effects.gains[index]) {
                    changeAudioEffects(effects.band(index, gain)); editEqualizer()
                }
            }, cancel = { editEqualizer() })
        }
    } + prefs.audioPresets.map { preset ->
        PlayerDialogAction(resources.getString(R.string.ui_use ,preset.name)) { changeAudioEffects(preset.effects); editEqualizer() }
    } + PlayerDialogAction(resources.getString(R.string.ui_reset_sound_effects)) { changeAudioEffects(AudioEffects()); editEqualizer() },
        message = resources.getString(R.string.ui_decoded_sound_only_passthrough_keeps_the_original_audio))
}
