package app.ember.player

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
    val p=config.stream.normalized()
    fun update(value:StreamConfig){save(config.copy(stream=value.normalized()))}
    timeoutFields.forEach {f->PlayerChoice(f.label,f.choices(p).map {it.toString() to "$it ${f.unit}"},f.read(p).toString()){update(f.write(p,it.toInt()))}}
    Text("Timeouts apply when a stream is reopened. These HTTP limits are shared by both playback engines and background audio.",color=Mute,fontSize=12.sp)
    SwitchChip("Custom buffering",p.custom){update(p.copy(custom=!p.custom))}
    if(p.custom) {
        bufferFields.forEach {f->PlayerChoice(f.label,f.choices(p).map {it.toString() to "$it ${f.unit}"},f.read(p).toString()){update(f.write(p,it.toInt()))}}
        SwitchChip("Wait for buffer refill",p.pauseForBuffer){update(p.copy(pauseForBuffer=!p.pauseForBuffer))}
    }
    val effective=StreamConfig.plan(LocalContext.current,config)
    Text("Device limit: ${effective.forwardMiB} MiB forward + ${effective.rewindMiB} MiB rewind. Standard uses the forward cap; byte-limited rewind is Compatibility-only. Standard applies buffer changes on prepare; Compatibility applies them while playing. Larger buffers cannot fix a persistently slow connection.",color=Mute,fontSize=12.sp)
}

@Composable
internal fun SubtitlePresetSettings(prefs:Prefs,config:SubtitleConfig,apply:(SubtitleConfig)->Unit) {
    var open by remember {mutableStateOf(false)}
    var name by remember {mutableStateOf("")}
    var tick by remember {mutableIntStateOf(0)}
    var notice by remember {mutableStateOf<String?>(null)}
    val styles=remember(tick){prefs.subtitlePresets.take(12)}
    Chip(if(open)"Hide saved subtitle styles" else "Saved subtitle styles"){open=!open}
    if(!open)return
    TvTextField(name,{name=it.take(32)},Modifier.fillMaxWidth(),placeholder="Style name",startEditing=!PlayerTouchControls.isTelevision(LocalContext.current))
    Chip("Save subtitle style") {runCatching {SubtitlePresets.save(prefs.subtitlePresets,name,config)}.onSuccess {prefs.subtitlePresets=it;tick++;notice="Style saved"}.onFailure {notice=it.message}}
    styles.forEach {style->ButtonRow {
        Chip("Apply ${style.name}"){apply(style.style.applyTo(config));notice="Style applied"}
        Chip("Delete ${style.name}"){prefs.subtitlePresets=prefs.subtitlePresets.filterNot {it.name==style.name};tick++}
    }}
    Text("Appearance only. Languages, subtitle providers and speech services stay unchanged. Custom font files may need to be reimported after restoring a backup.",fontSize=12.sp,color=Mute)
    notice?.let {Text(it,fontSize=12.sp,color=Mute)}
}
