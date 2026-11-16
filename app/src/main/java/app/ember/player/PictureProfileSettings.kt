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
    var notice by remember {mutableStateOf<String?>(null)}
    val profiles=PictureProfiles.normalized(config).pictureProfiles
    Text("Save upscaling, shader, colour, debanding, smoothing and HDR choices together. Using an existing name replaces that profile. Media-specific assignments are made while watching.",fontSize=12.sp,color=Mute)
    TvTextField(name,{name=it.take(32);notice=null},Modifier.fillMaxWidth(),placeholder="Profile name",startEditing=!PlayerTouchControls.isTelevision(androidx.compose.ui.platform.LocalContext.current))
    Chip("Save current picture settings") {
        runCatching {PictureProfiles.save(config,name,PictureState.from(config))}.onSuccess {save(it);notice="Profile saved"}.onFailure {notice=it.message}
    }
    if(profiles.isNotEmpty()) {
        val selected=profiles.firstOrNull {it.id==chosen} ?: profiles.first()
        PlayerChoice("Saved profile",profiles.map {it.id to it.name},selected.id){chosen=it}
        ButtonRow {
            Chip("Use as default"){save(selected.state.applyTo(config));notice="Default picture settings updated"}
            Chip("Delete profile"){save(PictureProfiles.remove(config,selected.id));notice="Profile and its media assignments removed"}
        }
    }
    notice?.let {Text(it,fontSize=12.sp,color=Mute)}
}

@Composable
internal fun HdrOutputSettings(value:HdrOutput,save:(HdrOutput)->Unit) {
    val p=value.normalized()
    SwitchChip("HDR to SDR output",p.sdr){save(p.copy(sdr=!p.sdr))}
    Text("Optional Compatibility output for SDR screens. Automatic engine selection can switch supported HDR video; explicit Standard, protected video and unsupported Compatibility audio keep their current path. The SDR picture-enhancement switch is separate.",fontSize=12.sp,color=Mute)
    if(p.sdr) {
        PlayerChoice("Tone mapping",HdrOutput.tones,p.tone){save(p.copy(tone=it))}
        PlayerChoice("Gamut mapping",HdrOutput.gamuts,p.gamut){save(p.copy(gamut=it))}
        PlayerChoice("SDR screen peak",(listOf(50,80,100,120,160,203,250,300,400,500)+p.peak).distinct().sorted().map {it.toString() to "$it nits"},p.peak.toString()){save(p.copy(peak=it.toInt()))}
        SwitchChip("Analyze scene peaks",p.analyzePeak){save(p.copy(analyzePeak=!p.analyzePeak))}
        Text("Peak analysis adds GPU work. With it off, mapping uses the video's available metadata.",fontSize=12.sp,color=Mute)
    }
}
