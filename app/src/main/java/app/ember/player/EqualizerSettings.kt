package app.ember.player

import app.ember.tv.R

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
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    var expanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var presets by remember { mutableStateOf(prefs.audioPresets) }
    var deleting by remember {mutableStateOf<String?>(null)}
    var notice by remember { mutableStateOf<PlayerFeedback?>(null) }
    PlayerAction(if (expanded) uiResources.getString(R.string.ui_hide_equalizer_bands) else uiResources.getString(R.string.ui_adjust_equalizer_bands)) { expanded = !expanded }
    if (expanded) {
        AudioEffects.frequencies.indices.forEach { index ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiResources.getString(R.string.ui_db ,AudioEffects.frequencyLabel(index),(value.gains[index]).toString()), style=androidx.tv.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                PlayerAction("−") { changed(value.band(index, value.gains[index] - 1)) }
                PlayerAction("+") { changed(value.band(index, value.gains[index] + 1)) }
            }
        }
        PlayerAction(uiResources.getString(R.string.ui_reset_sound_effects)) { changed(AudioEffects()) }
        TvTextField(name, { name = it.take(48) }, Modifier.fillMaxWidth(), placeholder = uiResources.getString(R.string.ui_preset_name))
        PlayerAction(uiResources.getString(R.string.ui_save_sound_preset)) {
            val updated = AudioPresets.save(presets, name, value)
            if (updated == null) notice=PlayerFeedback(uiResources.getString(R.string.ui_enter_a_name_you_can_keep_up_to_12))
            else { prefs.audioPresets = updated; presets = updated; name = ""; notice=PlayerFeedback(uiResources.getString(R.string.ui_preset_saved),false) }
        }
        notice?.let {PlayerFeedbackText(it)}
        presets.forEach { preset ->
            ButtonRow {
                PlayerAction(uiResources.getString(R.string.ui_use ,preset.name), maxLines = 1) { changed(preset.effects) }
                PlayerAction(uiResources.getString(R.string.ui_delete ,preset.name),destructive=true){deleting=preset.name}
            }
        }
    }
    deleting?.let {name->PlayerConfirmationDialog(uiResources.getString(R.string.ui_delete_sound_preset),uiResources.getString(R.string.ui_remove_from_saved_sound_presets_current_sound_settings_will ,name),uiResources.getString(R.string.ui_delete_f6fdbe),{deleting=null}) {
        deleting=null;presets=presets.filterNot {it.name==name};prefs.audioPresets=presets
    }}

}
