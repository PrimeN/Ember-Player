package app.ember.player

import app.ember.tv.R

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.player.*
import app.ember.tv.ui.*

@Composable
internal fun PictureProfileSettings(config:PlaybackConfig,save:(PlaybackConfig)->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    var name by remember {mutableStateOf("")}
    var chosen by remember {mutableStateOf("")}
    var deleting by remember {mutableStateOf<String?>(null)}
    var notice by remember {mutableStateOf<PlayerFeedback?>(null)}
    val profiles=PictureProfiles.normalized(config).pictureProfiles
    Text(uiResources.getString(R.string.ui_save_upscaling_shader_color_debanding_smoothing_and_hdr_choices),style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    TvTextField(name,{name=it.take(32);notice=null},Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_profile_name),startEditing=!PlayerTouchControls.isTelevision(androidx.compose.ui.platform.LocalContext.current))
    PlayerAction(uiResources.getString(R.string.ui_save_current_picture_settings)) {
        runCatching {PictureProfiles.save(config,name,PictureState.from(config))}.onSuccess {save(it);notice=PlayerFeedback(uiResources.getString(R.string.ui_profile_saved),false)}.onFailure {notice=PlayerFeedback(uiResources.getString(PresetValidationException.resource(it)))}
    }
    if(profiles.isNotEmpty()) {
        val selected=profiles.firstOrNull {it.id==chosen} ?: profiles.first()
        PlayerChoice(uiResources.getString(R.string.ui_saved_profile),profiles.map {it.id to it.name},selected.id){chosen=it}
        ButtonRow {
            PlayerAction(uiResources.getString(R.string.ui_use_as_default)){save(selected.state.applyTo(config));notice=PlayerFeedback(uiResources.getString(R.string.ui_default_picture_settings_updated),false)}
            PlayerAction(uiResources.getString(R.string.ui_delete_profile),destructive=true){deleting=selected.id}
        }
    }
    deleting?.let {id->PlayerConfirmationDialog(uiResources.getString(R.string.ui_delete_picture_profile),uiResources.getString(R.string.ui_the_saved_profile_and_its_media_assignments_will_be),uiResources.getString(R.string.ui_delete_f6fdbe),{deleting=null}) {
        deleting=null;save(PictureProfiles.remove(config,id));notice=PlayerFeedback(uiResources.getString(R.string.ui_profile_and_its_media_assignments_removed),false)
    }}
    notice?.let {PlayerFeedbackText(it)}
}

@Composable
internal fun HdrOutputSettings(value:HdrOutput,save:(HdrOutput)->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val p=value.normalized()
    SwitchChip(uiResources.getString(R.string.ui_hdr_to_sdr_output),p.sdr){save(p.copy(sdr=!p.sdr,expand=false))}
    Text(uiResources.getString(R.string.ui_optional_compatibility_output_for_sdr_screens_automatic_engine_selection),style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    SwitchChip(uiResources.getString(R.string.ui_sdr_to_hdr_experimental),p.expand){save(p.copy(expand=!p.expand,sdr=false))}
    Text(uiResources.getString(R.string.ui_local_inverse_tone_mapping_off_by_default_requires_advanced),style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    if(p.expand)Text(uiResources.getString(R.string.ui_display ,HdrDisplayInfo.read(androidx.compose.ui.platform.LocalContext.current).summary),style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    if(p.expand)PlayerChoice(uiResources.getString(R.string.ui_expanded_hdr_peak),(listOf(300,400,600,800,1000,1500)+p.hdrPeak).distinct().sorted().map {it.toString() to uiResources.getString(R.string.ui_nits ,it.toString())},p.hdrPeak.toString()){save(p.copy(hdrPeak=it.toInt()))}
    if(p.sdr) {
        PlayerChoice(uiResources.getString(R.string.ui_tone_mapping),HdrOutput.tones,p.tone){save(p.copy(tone=it))}
        PlayerChoice(uiResources.getString(R.string.ui_gamut_mapping),HdrOutput.gamuts,p.gamut){save(p.copy(gamut=it))}
        PlayerChoice(uiResources.getString(R.string.ui_sdr_screen_peak),(listOf(50,80,100,120,160,203,250,300,400,500)+p.peak).distinct().sorted().map {it.toString() to uiResources.getString(R.string.ui_nits ,it.toString())},p.peak.toString()){save(p.copy(peak=it.toInt()))}
        SwitchChip(uiResources.getString(R.string.ui_analyze_scene_peaks),p.analyzePeak){save(p.copy(analyzePeak=!p.analyzePeak))}
        Text(uiResources.getString(R.string.ui_peak_analysis_adds_gpu_work_with_it_off_mapping),style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    }
}
