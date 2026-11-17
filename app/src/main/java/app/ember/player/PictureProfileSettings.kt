package app.ember.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.player.*
import app.ember.tv.ui.*

@Composable
internal fun PictureProfileSettings(config:PlaybackConfig,save:(PlaybackConfig)->Unit) {
    var name by remember {mutableStateOf("")}
    var chosen by remember {mutableStateOf("")}
    var deleting by remember {mutableStateOf<String?>(null)}
    var notice by remember {mutableStateOf<PlayerFeedback?>(null)}
    val profiles=PictureProfiles.normalized(config).pictureProfiles
    Text("Save upscaling, shader, color, debanding, smoothing and HDR choices together. Using an existing name replaces that profile. Media-specific assignments are made while watching.",style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    TvTextField(name,{name=it.take(32);notice=null},Modifier.fillMaxWidth(),placeholder="Profile name",startEditing=!PlayerTouchControls.isTelevision(androidx.compose.ui.platform.LocalContext.current))
    Chip("Save current picture settings") {
        runCatching {PictureProfiles.save(config,name,PictureState.from(config))}.onSuccess {save(it);notice=PlayerFeedback("Profile saved",false)}.onFailure {notice=it.message?.let {PlayerFeedback(it)}}
    }
    if(profiles.isNotEmpty()) {
        val selected=profiles.firstOrNull {it.id==chosen} ?: profiles.first()
        PlayerChoice("Saved profile",profiles.map {it.id to it.name},selected.id){chosen=it}
        ButtonRow {
            Chip("Use as default"){save(selected.state.applyTo(config));notice=PlayerFeedback("Default picture settings updated",false)}
            PlayerAction("Delete profile",destructive=true){deleting=selected.id}
        }
    }
    deleting?.let {id->PlayerConfirmationDialog("Delete picture profile?","The saved profile and its media assignments will be removed. Current picture settings will be kept.","Delete",{deleting=null}) {
        deleting=null;save(PictureProfiles.remove(config,id));notice=PlayerFeedback("Profile and its media assignments removed",false)
    }}
    notice?.let {PlayerFeedbackText(it)}
}

@Composable
internal fun HdrOutputSettings(value:HdrOutput,save:(HdrOutput)->Unit) {
    val p=value.normalized()
    SwitchChip("HDR to SDR output",p.sdr){save(p.copy(sdr=!p.sdr,expand=false))}
    Text("Optional Compatibility output for SDR screens. Automatic engine selection can switch supported HDR video; explicit Standard, protected video and unsupported Compatibility audio keep their current path. The SDR picture-enhancement switch is separate.",style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    SwitchChip("SDR to HDR · experimental",p.expand){save(p.copy(expand=!p.expand,sdr=false))}
    Text("Local inverse tone mapping, off by default. Requires Advanced/Automatic renderer, a reported HDR10 display and confirmed SurfaceView color metadata. No AI model or online service. It can alter the intended look and increase banding or GPU load.",style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    if(p.expand)Text("Display: ${HdrDisplayInfo.read(androidx.compose.ui.platform.LocalContext.current).summary}",style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    if(p.expand)PlayerChoice("Expanded HDR peak",(listOf(300,400,600,800,1000,1500)+p.hdrPeak).distinct().sorted().map {it.toString() to "$it nits"},p.hdrPeak.toString()){save(p.copy(hdrPeak=it.toInt()))}
    if(p.sdr) {
        PlayerChoice("Tone mapping",HdrOutput.tones,p.tone){save(p.copy(tone=it))}
        PlayerChoice("Gamut mapping",HdrOutput.gamuts,p.gamut){save(p.copy(gamut=it))}
        PlayerChoice("SDR screen peak",(listOf(50,80,100,120,160,203,250,300,400,500)+p.peak).distinct().sorted().map {it.toString() to "$it nits"},p.peak.toString()){save(p.copy(peak=it.toInt()))}
        SwitchChip("Analyze scene peaks",p.analyzePeak){save(p.copy(analyzePeak=!p.analyzePeak))}
        Text("Peak analysis adds GPU work. With it off, mapping uses the video's available metadata.",style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    }
}
