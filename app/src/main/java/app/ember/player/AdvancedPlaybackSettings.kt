package app.ember.player

import app.ember.tv.R

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.player.*
import app.ember.tv.data.*
import app.ember.tv.ui.*

@Composable
internal fun StreamSettings(config:PlaybackConfig,save:(PlaybackConfig)->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val p=config.stream.normalized()
    fun update(value:StreamConfig){save(config.copy(stream=value.normalized()))}
    timeoutFields.forEach {f->PlayerChoice(uiResources.getString(f.label),f.choices(p).map {it.toString() to "$it ${f.unit}"},f.read(p).toString()){update(f.write(p,it.toInt()))}}
    Text(uiResources.getString(R.string.ui_timeouts_apply_when_a_stream_is_reopened_these_http),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
    SwitchChip(uiResources.getString(R.string.ui_custom_buffering),p.custom){update(p.copy(custom=!p.custom))}
    if(p.custom) {
        bufferFields.forEach {f->PlayerChoice(uiResources.getString(f.label),f.choices(p).map {it.toString() to "$it ${f.unit}"},f.read(p).toString()){update(f.write(p,it.toInt()))}}
        SwitchChip(uiResources.getString(R.string.ui_wait_for_buffer_refill),p.pauseForBuffer){update(p.copy(pauseForBuffer=!p.pauseForBuffer))}
    }
    val effective=StreamConfig.plan(LocalContext.current,config)
    Text(uiResources.getString(R.string.ui_device_limit_mib_forward_mib_rewind_standard_uses_the ,effective.forwardMiB.toString(),effective.rewindMiB.toString()),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
}

@Composable
internal fun SubtitlePresetSettings(prefs:Prefs,config:SubtitleConfig,apply:(SubtitleConfig)->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    var open by remember {mutableStateOf(false)}
    var name by remember {mutableStateOf("")}
    var tick by remember {mutableIntStateOf(0)}
    var deleting by remember {mutableStateOf<String?>(null)}
    var notice by remember {mutableStateOf<PlayerFeedback?>(null)}
    val styles=remember(tick){prefs.subtitlePresets.take(12)}
    PlayerAction(if(open)uiResources.getString(R.string.ui_hide_saved_subtitle_styles) else uiResources.getString(R.string.ui_saved_subtitle_styles)){open=!open}
    if(!open)return
    TvTextField(name,{name=it.take(32)},Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_style_name),startEditing=!PlayerTouchControls.isTelevision(LocalContext.current))
    PlayerAction(uiResources.getString(R.string.ui_save_subtitle_style)) {runCatching {SubtitlePresets.save(prefs.subtitlePresets,name,config)}.onSuccess {prefs.subtitlePresets=it;tick++;notice=PlayerFeedback(uiResources.getString(R.string.ui_style_saved),false)}.onFailure {notice=PlayerFeedback(uiResources.getString(PresetValidationException.resource(it)))}}
    styles.forEach {style->ButtonRow {
        PlayerAction(uiResources.getString(R.string.ui_apply ,style.name)){apply(style.style.applyTo(config));notice=PlayerFeedback(uiResources.getString(R.string.ui_style_applied),false)}
        PlayerAction(uiResources.getString(R.string.ui_delete ,style.name),destructive=true){deleting=style.name}
    }}
    Text(uiResources.getString(R.string.ui_appearance_only_languages_subtitle_providers_and_speech_services_stay),style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    deleting?.let {name->PlayerConfirmationDialog(uiResources.getString(R.string.ui_delete_subtitle_style),uiResources.getString(R.string.ui_remove_from_saved_subtitle_styles_current_subtitle_settings_will ,name),uiResources.getString(R.string.ui_delete_f6fdbe),{deleting=null}) {
        deleting=null;prefs.subtitlePresets=prefs.subtitlePresets.filterNot {it.name==name};tick++
    }}
    notice?.let {PlayerFeedbackText(it)}
}
