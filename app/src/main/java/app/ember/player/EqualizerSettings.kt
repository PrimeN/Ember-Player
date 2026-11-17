package app.ember.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.data.Prefs
import app.ember.tv.player.*
import app.ember.tv.ui.*

@Composable
internal fun EqualizerSettings(prefs: Prefs, value: AudioEffects, changed: (AudioEffects) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var presets by remember { mutableStateOf(prefs.audioPresets) }
    var deleting by remember {mutableStateOf<String?>(null)}
    var notice by remember { mutableStateOf<PlayerFeedback?>(null) }
    Chip(if (expanded) "Hide equalizer bands" else "Adjust equalizer bands") { expanded = !expanded }
    if (expanded) {
        AudioEffects.frequencies.indices.forEach { index ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${AudioEffects.frequencyLabel(index)} · ${value.gains[index]} dB", style=androidx.tv.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Chip("−") { changed(value.band(index, value.gains[index] - 1)) }
                Chip("+") { changed(value.band(index, value.gains[index] + 1)) }
            }
        }
        Chip("Reset sound effects") { changed(AudioEffects()) }
        TvTextField(name, { name = it.take(48) }, Modifier.fillMaxWidth(), placeholder = "Preset name")
        Chip("Save sound preset") {
            val updated = AudioPresets.save(presets, name, value)
            if (updated == null) notice=PlayerFeedback("Enter a name. You can keep up to 12 sound presets.")
            else { prefs.audioPresets = updated; presets = updated; name = ""; notice=PlayerFeedback("Preset saved",false) }
        }
        notice?.let {PlayerFeedbackText(it)}
        presets.forEach { preset ->
            ButtonRow {
                Chip("Use ${preset.name}", maxLines = 1) { changed(preset.effects) }
                PlayerAction("Delete ${preset.name}",destructive=true){deleting=preset.name}
            }
        }
    }
    deleting?.let {name->PlayerConfirmationDialog("Delete sound preset?","Remove $name from saved sound presets? Current sound settings will be kept.","Delete",{deleting=null}) {
        deleting=null;presets=presets.filterNot {it.name==name};prefs.audioPresets=presets
    }}

}
