package app.ember.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.ember.tv.data.*
import app.ember.tv.player.*
import app.ember.tv.ui.*
import kotlinx.coroutines.*

class PlayerSettingsActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs=remember {Prefs(this)}
            var revision by remember {mutableIntStateOf(0)}
            val config=remember(revision){prefs.playbackConfig}
            val subtitles=remember(revision){prefs.subtitleConfig}
            val scope=rememberCoroutineScope()
            fun update(change:(PlaybackConfig)->PlaybackConfig){prefs.playbackConfig=change(prefs.playbackConfig);revision++}
            fun captions(change:(SubtitleConfig)->SubtitleConfig){prefs.subtitleConfig=change(prefs.subtitleConfig);revision++}
            PlayerTheme(prefs) {
                CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides Fg) {
                val unlocked by PrivateMedia.unlocked.collectAsState()
                val privacy by PrivateMedia.state.collectAsState()
                SideEffect {if(PrivateMedia.enabled)window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
                if((privacy.pin.isNotEmpty()||PrivateMedia.enabled)&&!unlocked){PrivatePinPrompt({finish()},{ });return@CompositionLocalProvider}
                LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal=36.dp),
                    contentPadding=PaddingValues(vertical=24.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
                    item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("Player settings",fontSize=26.sp);Chip("Back"){finish()}
                    }}
                    item {Section("Playback") {
                        PlayerChoice("Controls",PlayerInterfaces.options,PlayerInterfaces.resolve(config,"modern")){v->update {it.copy(playerInterface=v)}}
                        PlayerChoice("Playback engine",listOf("auto" to "Automatic","standard" to "Standard","compatibility" to "Compatibility"),config.engine){v->update {it.copy(engine=v)}}
                        PlayerChoice("Resume",listOf("ask" to "Ask","resume" to "Resume automatically","start" to "Start over"),config.resume){v->update {it.copy(resume=v)}}
                        PlayerChoice("Seeking",listOf("precise" to "Precise","fast" to "Fast keyframes"),config.seekMode){v->update {it.copy(seekMode=v)}}
                        PlayerChoice("Buffer",listOf("normal" to "Normal","large" to "Large","huge" to "Huge"),config.buffer){v->update {it.copy(buffer=v)}}
                        SwitchChip("Remember working decoders",config.rememberDecoderChoices){update {it.copy(rememberDecoderChoices=!it.rememberDecoderChoices)}}
                        Chip("Reset decoder memory"){DecoderHealth.clear(this@PlayerSettingsActivity)}
                        SwitchChip("Picture in picture",config.pip){update {it.copy(pip=!it.pip)}}
                        SwitchChip("Background audio",config.backgroundAudio){update {it.copy(backgroundAudio=!it.backgroundAudio)}}
                        Text("Keep sound playing when the screen is off or you switch apps. Picture in picture takes priority when enabled.",color=Mute,fontSize=12.sp)
                        SwitchChip("Ask before leaving playback",config.confirmExit){update {it.copy(confirmExit=!it.confirmExit)}}
                        SwitchChip("IntroDB markers",config.introDb){update {it.copy(introDb=!it.introDb)}}
                        PlayerChoice("Skip markers",listOf("button" to "Show skip button","auto" to "Skip automatically","off" to "Off"),config.skipIntro){v->update {it.copy(skipIntro=v)}}
                    }}
                    item {Section("Sleep timer") {SleepTimerSettings()}}
                    item {Section("Picture") {
                        PlayerChoice("Software video",listOf("fallback" to "When needed","off" to "Device decoders only","always" to "Always software"),config.ffmpegVideo){v->update {it.copy(ffmpegVideo=v)}}
                        PlayerChoice("Frame rate matching",listOf("off" to "Off","seamless" to "Seamless only","always" to "Match video"),config.frameRate){v->update {it.copy(frameRate=v)}}
                        SwitchChip("Match video resolution",config.matchResolution){update {it.copy(matchResolution=!it.matchResolution)}}
                        PlayerChoice("Dolby Vision",listOf("auto" to "Automatic","always" to "Use Dolby Vision","hdr10" to "HDR10 base layer when available"),config.dolbyVision){v->update {it.copy(dolbyVision=v)}}
                        PlayerChoice("Deinterlacing",listOf("off" to "Off","auto" to "Automatic","always" to "Always"),config.deinterlacing){v->update {it.copy(deinterlacing=v)}}
                    }}
                    item {Section("Touch and rotation") {
                        Text("Touch gestures apply on phones and tablets. Remote navigation stays available on TVs.",color=Mute,fontSize=12.sp)
                        SwitchChip("Touch gestures",config.touch.enabled){update {it.copy(touch=it.touch.copy(enabled=!it.touch.enabled))}}
                        PlayerChoice("Double-tap seek",listOf("5" to "5 seconds","10" to "10 seconds","15" to "15 seconds","30" to "30 seconds","60" to "60 seconds"),config.touch.seekSeconds.toString()){v->update {it.copy(touch=it.touch.copy(seekSeconds=v.toInt()))}}
                        SwitchChip("Swipe right side for volume",config.touch.volumeSwipe){update {it.copy(touch=it.touch.copy(volumeSwipe=!it.touch.volumeSwipe))}}
                        SwitchChip("Swipe left side for brightness",config.touch.brightnessSwipe){update {it.copy(touch=it.touch.copy(brightnessSwipe=!it.touch.brightnessSwipe))}}
                        SwitchChip("Pinch to zoom",config.touch.pinchZoom){update {it.copy(touch=it.touch.copy(pinchZoom=!it.touch.pinchZoom))}}
                        SwitchChip("Hold video for temporary 2× speed",config.touch.holdToSpeed){update {it.copy(touch=it.touch.copy(holdToSpeed=!it.touch.holdToSpeed))}}
                        PlayerChoice("Orientation",listOf("auto" to "Follow device setting","sensor" to "Automatic rotation","landscape" to "Landscape","portrait" to "Portrait"),config.touch.orientation){v->update {it.copy(touch=it.touch.copy(orientation=v))}}
                        Text("Tap to show controls. Double-tap the centre to pause; double-tap an edge to seek. Swipe horizontally to preview a seek, then release. Playback lock is in the player options; Back unlocks it.",color=Mute,fontSize=12.sp)
                    }}
                    item {Section("Sound") {
                        SwitchChip("HDMI passthrough",config.passthrough){update {it.copy(passthrough=!it.passthrough)}}
                        PlayerChoice("Audio decoding",listOf("on" to "Software when needed","prefer" to "Prefer software","off" to "Device decoders only"),config.ffmpeg){v->update {it.copy(ffmpeg=v)}}
                        PlayerChoice("Audio track",listOf("quality" to "Best quality","language" to "Preferred language","default" to "File default"),config.audioPick){v->update {it.copy(audioPick=v)}}
                        Text("Sound delay: ${config.audioDelayMs} ms",fontSize=14.sp)
                        ButtonRow {Chip("−25 ms"){update {it.copy(audioDelayMs=(it.audioDelayMs-25).coerceAtLeast(-2000))}};Chip("+25 ms"){update {it.copy(audioDelayMs=(it.audioDelayMs+25).coerceAtMost(2000))}}}
                        Text("Bluetooth delay: ${config.bluetoothDelayMs?.let {"$it ms"} ?: "Use sound delay"}",fontSize=14.sp)
                        ButtonRow {Chip("−25 ms"){update {it.copy(bluetoothDelayMs=((it.bluetoothDelayMs ?: it.audioDelayMs)-25).coerceAtLeast(-2000))}};Chip("+25 ms"){update {it.copy(bluetoothDelayMs=((it.bluetoothDelayMs ?: it.audioDelayMs)+25).coerceAtMost(2000))}};Chip("Use sound delay"){update {it.copy(bluetoothDelayMs=null)}}}
                        PlayerChoice("Audio preset",AudioEffects.presets,config.audioEffects.preset){v->update {it.copy(audioEffects=it.audioEffects.copy(preset=v))}}
                        EqualizerSettings(prefs,config.audioEffects){v->update {it.copy(audioEffects=v)}}
                        PlayerChoice("Bass",(0..6).map {it.toString() to "+$it dB"},config.audioEffects.bass.toString()){v->update {it.copy(audioEffects=it.audioEffects.copy(bass=v.toInt()))}}
                        PlayerChoice("Treble",(0..6).map {it.toString() to "+$it dB"},config.audioEffects.treble.toString()){v->update {it.copy(audioEffects=it.audioEffects.copy(treble=v.toInt()))}}
                        PlayerChoice("Stereo width",listOf(0,50,100,125,150,175,200).map {it.toString() to "$it%"},config.audioEffects.stereoWidth.toString()){v->update {it.copy(audioEffects=it.audioEffects.copy(stereoWidth=v.toInt()))}}
                        PlayerChoice("Channels",AudioEffects.channelChoices,config.audioEffects.channels){v->update {it.copy(audioEffects=it.audioEffects.copy(channels=v))}}
                        Text("Audio effects apply to decoded sound; passthrough stays untouched.",color=Mute,fontSize=12.sp)
                    }}
                    item {Section("Subtitles") {
                        PlayerChoice("At start",listOf("foreign" to "For foreign audio","always" to "Always","off" to "Off"),subtitles.autoSelect){v->captions {it.copy(autoSelect=v)}}
                        var languages by remember {mutableStateOf(subtitles.languages.joinToString(", "))}
                        TvTextField(languages,{languages=it.take(120)},Modifier.fillMaxWidth(),placeholder="Languages: en, fr, ja")
                        Chip("Save languages"){captions {it.copy(languages=languages.split(',').map(String::trim).filter {v->v.matches(Regex("[A-Za-z]{2,3}(-[A-Za-z]{2})?"))}.take(8))}}
                        PlayerChoice("Text size",listOf("small" to "Small","normal" to "Normal","large" to "Large","xlarge" to "Extra large"),subtitles.size){v->captions {it.copy(size=v)}}
                        PlayerChoice("Text background",listOf("outline" to "Outline","shadow" to "Shadow","box" to "Box"),subtitles.background){v->captions {it.copy(background=v)}}
                        SwitchChip("Preserve authored styling",subtitles.authoredStyles){captions {it.copy(authoredStyles=!it.authoredStyles)}}
                        SwitchChip("Use system caption style",subtitles.systemStyle){captions {it.copy(systemStyle=!it.systemStyle)}}
                        SubtitleAssistanceSettings(subtitles.assistance){value->captions {it.copy(assistance=value)}}
                        AdvancedSubtitleSettings(subtitles){value->captions {value}}
                        SwitchChip("Community subtitle search",subtitles.stremioOpenSubs){captions {it.copy(stremioOpenSubs=!it.stremioOpenSubs)}}
                    }}
                    item {Section("Appearance and performance") {
                        PlayerChoice("Performance",listOf("auto" to "Automatic","low" to "Low","balanced" to "Balanced","high" to "High"),prefs.devicePerformance){v->prefs.devicePerformance=v;revision++}
                        PlayerChoice("Colours",listOf("0" to "Ember","1" to "Shadow Ember","5" to "Modern Ember","6" to "Cinema Red"),prefs.accent.toString()){v->prefs.accent=v.toInt();revision++}
                        PlayerChoice("Display mode",listOf("night" to "Night","day" to "Day"),prefs.theme){v->prefs.theme=v;revision++}
                        SwitchChip("Glass surfaces",prefs.navigationGlass){prefs.navigationGlass=!prefs.navigationGlass;revision++}
                        Chip("Clear disposable caches"){scope.launch {val result=withContext(Dispatchers.IO){runCatching {CacheManagement.clear()}};android.widget.Toast.makeText(this@PlayerSettingsActivity,if(result.isSuccess)"Cache cleared" else "Some cached data could not be cleared",android.widget.Toast.LENGTH_LONG).show()}}
                    }}
                    item {Section("Privacy") {PrivacySettings()}}
                    item {Section("Backups") {PlayerBackupSettings {revision++}}}
                    item {Section("About") {
                        Text("Ember Player ${app.ember.tv.BuildConfig.VERSION_NAME}",fontSize=18.sp)
                        Text("An independent player for Android and Android TV. No service credentials are bundled. Codec and HDR playback depend on the device, display and selected engine.",color=Mute,fontSize=13.sp)
                        PlayerLegal()
                    }}
                }
                }
            }
        }
    }
}
@Composable
internal fun PlayerChoice(label:String,options:List<Pair<String,String>>,value:String,pick:(String)->Unit) {
    var open by remember {mutableStateOf(false)}
    Chip("$label · ${options.firstOrNull {it.first==value}?.second ?: value}",maxLines=1){open=true}
    if(open)OptionMenu(label,options.map {(id,name)->MenuOption(name,id==value){pick(id)}},{open=false})
}
@Composable
private fun PlayerLegal() {
    var chosen by remember {mutableStateOf<Pair<String,String>?>(null)}
    val context=androidx.compose.ui.platform.LocalContext.current
    val licenses=listOf("GPL v3 — mpv, FFmpeg and Nextlib" to "player-GPL-3.0.txt","Apache 2.0 — AndroidX Media3 and libraries" to "Apache-2.0.txt",
        "Fraunhofer MPEG-H" to "Fraunhofer-FDK-MPEG-H.txt","libmpv Android wrapper" to "libmpv-wrapper-MIT.txt",
        "libaums USB notices" to "libaums-NOTICE.txt","SQLCipher" to "SQLCipher-LICENSE.txt","SMBJ-RPC" to "SMBJ-RPC-BSD.txt","JSch" to "JSch-BSD.txt","Apache Commons notices" to "Commons-IO-Lang-NOTICE.txt","Apache Commons Net notices" to "Commons-Net-NOTICE.txt",
        "JSch compression notices" to "JSch-JZlib-BSD.txt","JSch password hashing notices" to "JSch-jBCrypt-ISC.txt","Android NDK notices" to "Android-NDK-29-LLVM-NOTICE.txt")
    licenses.forEach {entry->Chip(entry.first){chosen=entry}}
    Chip("Google Cast SDK terms") {runCatching {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://developers.google.com/cast/docs/terms")))}}
    Chip("Android SDK terms") {runCatching {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://developer.android.com/studio/terms.html")))}}
    chosen?.let {(title,file)->
        var text by remember(file){mutableStateOf("Loading license…")}
        LaunchedEffect(file){text=withContext(Dispatchers.IO){runCatching {context.assets.open("licenses/$file").bufferedReader().use {it.readText()}}.getOrDefault("License unavailable. See the source repository.")}}
        TvDialog({chosen=null},width=680.dp,scrollContent=true){DialogHeading(title);Text(text,fontSize=13.sp);Chip("Close"){chosen=null}}
    }
}
