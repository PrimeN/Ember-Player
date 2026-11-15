package app.ember.tv.player

import androidx.media3.common.util.UnstableApi

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerActivity.editEqualizer() {
    val effects = config.audioEffects
    showPlayerActions("Equalizer", AudioEffects.frequencies.indices.map { index ->
        PlayerDialogAction("${AudioEffects.frequencyLabel(index)} · ${effects.gains[index]} dB") {
            showPlayerActions(AudioEffects.frequencyLabel(index), (-12..12).map { gain ->
                PlayerDialogAction("${if (gain > 0) "+" else ""}$gain dB", selected = gain == effects.gains[index]) {
                    changeAudioEffects(effects.band(index, gain)); editEqualizer()
                }
            }, cancel = { editEqualizer() })
        }
    } + prefs.audioPresets.map { preset ->
        PlayerDialogAction("Use ${preset.name}") { changeAudioEffects(preset.effects); editEqualizer() }
    } + PlayerDialogAction("Reset sound effects") { changeAudioEffects(AudioEffects()); editEqualizer() },
        message = "Decoded sound only. Passthrough keeps the original audio.")
}
