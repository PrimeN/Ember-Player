package app.ember.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import app.ember.tv.data.*
import app.ember.tv.player.*
import app.ember.tv.ui.*
import kotlinx.coroutines.*

class PlayerSettingsActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false)
        setContent {
            val prefs=remember {Prefs(this)}
            var revision by remember {mutableIntStateOf(0)}
            val config=remember(revision){prefs.playbackConfig}
            val subtitles=remember(revision){prefs.subtitleConfig}
            val scope=rememberCoroutineScope()
            fun update(change:(PlaybackConfig)->PlaybackConfig){prefs.playbackConfig=change(prefs.playbackConfig);revision++}
            fun captions(change:(SubtitleConfig)->SubtitleConfig){prefs.subtitleConfig=change(prefs.subtitleConfig);revision++}
            PlayerTheme(prefs) {
                val unlocked by PrivateMedia.unlocked.collectAsState()
                val privacy by PrivateMedia.state.collectAsState()
                SideEffect {if(PrivateMedia.enabled)window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
                if((privacy.pin.isNotEmpty()||PrivateMedia.enabled)&&!unlocked)PrivatePinPrompt({finish()},{ })
                else PlayerSettingsBrowser({finish()}) {id->SettingsContent(id,prefs,config,subtitles,::update,::captions,{revision++},scope)}
            }
        }
    }
}

@Composable
private fun PlayerSettingsActivity.SettingsContent(id:String,prefs:Prefs,config:PlaybackConfig,subtitles:SubtitleConfig,
    update:((PlaybackConfig)->PlaybackConfig)->Unit,captions:((SubtitleConfig)->SubtitleConfig)->Unit,refresh:()->Unit,scope:CoroutineScope) {
    when(id) {
        "playback"->PlayerSection("Playback") {
            PlayerChoice("Controls",PlayerInterfaces.options,PlayerInterfaces.resolve(config,"modern")){v->update {it.copy(playerInterface=v)}}
            PlayerChoice("Playback engine",listOf("auto" to "Automatic","standard" to "Standard","compatibility" to "Compatibility"),config.engine){v->update {it.copy(engine=v)}}
            PlayerChoice("Resume",listOf("ask" to "Ask","resume" to "Resume automatically","start" to "Start over"),config.resume){v->update {it.copy(resume=v)}}
            PlayerChoice("Seeking",listOf("precise" to "Precise","fast" to "Fast keyframes"),config.seekMode){v->update {it.copy(seekMode=v)}}
            PlayerChoice("Buffer",listOf("normal" to "Normal","large" to "Large","huge" to "Huge"),config.buffer){v->update {it.copy(buffer=v,stream=it.stream.copy(custom=false))}}
            SwitchChip("Remember working decoders",config.rememberDecoderChoices){update {it.copy(rememberDecoderChoices=!it.rememberDecoderChoices)}}
            Chip("Reset decoder memory"){DecoderHealth.clear(this@SettingsContent)}
            SwitchChip("Picture in picture",config.pip){update {it.copy(pip=!it.pip)}}
            SwitchChip("Background audio",config.backgroundAudio){update {it.copy(backgroundAudio=!it.backgroundAudio)}}
            Text("Keep sound playing when the screen is off or you switch apps. Picture in picture takes priority when enabled.",color=Mute,fontSize=12.sp)
            SwitchChip("Ask before leaving playback",config.confirmExit){update {it.copy(confirmExit=!it.confirmExit)}}
            SwitchChip("IntroDB markers",config.introDb){update {it.copy(introDb=!it.introDb)}}
            PlayerChoice("Skip markers",listOf("button" to "Show skip button","auto" to "Skip automatically","off" to "Off"),config.skipIntro){v->update {it.copy(skipIntro=v)}}
        }
        "network"->PlayerSection("Network and buffering") {
            StreamSettings(config){value->update {value}}
        }
        "sleep"->PlayerSection("Sleep timer") {
            SleepTimerSettings()
        }
        "enhancement"->PlayerSection("Picture enhancement") {
            val picture=config.picture
            SwitchChip("Picture enhancement",picture.enabled){update {it.copy(picture=it.picture.copy(enabled=!it.picture.enabled))}}
            Text("Enabled by default. GPU processing uses more power; turn it off for longer battery life or if playback stutters. HDR, Dolby Vision, protected and tunneled video keep their supported output path.",color=Mute,fontSize=12.sp)
            SwitchChip("GPU upscaling",picture.upscaling){update {it.copy(picture=it.picture.copy(upscaling=!it.picture.upscaling))}}
            SwitchChip("Fast upscaling shader",picture.shader){update {it.copy(picture=it.picture.copy(shader=!it.picture.shader))}}
            Text("Standard uses Lanczos when enlarging video. Compatibility can also use a bundled lightweight shader above 1.2× in both dimensions. No downloads are needed.",color=Mute,fontSize=12.sp)
            PlayerChoice("Picture preset",PictureEnhancement.presets.mapIndexed {i,v->i.toString() to v},PictureEnhancement.presetIndex(picture).takeIf {it>=0}?.toString() ?: "Custom"){v->update {it.copy(picture=PictureEnhancement.preset(v.toInt(),it.picture))}}
            pictureAdjustments.forEach {adjustment->
                PlayerChoice(adjustment.label,adjustment.values(picture).map {it.toString() to it.toString()},adjustment.read(picture.normalized()).toString()){v->
                    update {it.copy(picture=adjustment.write(it.picture,v.toInt()).normalized())}
                }
            }
            Chip("Reset picture adjustments"){update {it.copy(picture=it.picture.copy(brightness=0,contrast=0,saturation=0,gamma=0,hue=0,sharpness=0))}}
        }
        "profiles"->PlayerSection("Saved picture / shader profiles") {
            PictureProfileSettings(config){value->update {value}}
        }
        "hdr"->PlayerSection("HDR output") {
            PlayerChoice("Compatibility renderer",RendererPolicy.choices,RendererPolicy.normalized(config.renderer)){v->update {it.copy(renderer=v)}}
            HdrOutputSettings(config.hdrOutput){value->update {it.copy(hdrOutput=value)}}
        }
        "decoding"->PlayerSection("Picture") {
            PlayerChoice("Software video",listOf("fallback" to "When needed","off" to "Device decoders only","always" to "Always software"),config.ffmpegVideo){v->update {it.copy(ffmpegVideo=v)}}
            PlayerChoice("Compatibility decoder profile",NativeDecoderProfile.choices,NativeDecoderProfile.normalized(config.compatibilityDecoder)){v->update {it.copy(compatibilityDecoder=v)}}
            SwitchChip("Automatic Hi10P fallback",config.hi10pFallback){update {it.copy(hi10pFallback=!it.hi10pFallback)}}
            Text("Compatibility only. Automatic detects NVIDIA Shield 10-bit H.264 and uses full-quality software decoding. Lighter software skips some deblocking for lower CPU load; explicit track rules and manual choices take priority.",color=Mute,fontSize=12.sp)
            PlayerChoice("Frame rate matching",listOf("off" to "Off","seamless" to "Seamless only","always" to "Match video"),config.frameRate){v->update {it.copy(frameRate=v)}}
            SwitchChip("Match video resolution",config.matchResolution){update {it.copy(matchResolution=!it.matchResolution)}}
            PlayerChoice("Dolby Vision",listOf("auto" to "Automatic","always" to "Use Dolby Vision","hdr10" to "HDR10 base layer when available"),config.dolbyVision){v->update {it.copy(dolbyVision=v)}}
            PlayerChoice("Deinterlacing",listOf("off" to "Off","auto" to "Automatic","always" to "Always"),config.deinterlacing){v->update {it.copy(deinterlacing=v)}}
        }
        "touch"->PlayerSection("Touch and rotation") {
            Text("Touch gestures apply on phones and tablets. Remote navigation stays available on TVs.",color=Mute,fontSize=12.sp)
            SwitchChip("Touch gestures",config.touch.enabled){update {it.copy(touch=it.touch.copy(enabled=!it.touch.enabled))}}
            PlayerChoice("Double-tap seek",listOf("5" to "5 seconds","10" to "10 seconds","15" to "15 seconds","30" to "30 seconds","60" to "60 seconds"),config.touch.seekSeconds.toString()){v->update {it.copy(touch=it.touch.copy(seekSeconds=v.toInt()))}}
            PlayerChoice("Left double-tap",TouchActions.options,config.touch.leftDoubleTap){v->update {it.copy(touch=it.touch.copy(leftDoubleTap=v))}}
            PlayerChoice("Centre double-tap",TouchActions.options,config.touch.centerDoubleTap){v->update {it.copy(touch=it.touch.copy(centerDoubleTap=v))}}
            PlayerChoice("Right double-tap",TouchActions.options,config.touch.rightDoubleTap){v->update {it.copy(touch=it.touch.copy(rightDoubleTap=v))}}
            PlayerChoice("Edge tap region",listOf(20,25,30,35,40,45).map {it.toString() to "$it% each side"},config.touch.seekAreaPercent.toString()){v->update {it.copy(touch=it.touch.copy(seekAreaPercent=v.toInt()))}}
            SwitchChip("Horizontal swipe to seek",config.touch.horizontalSeek){update {it.copy(touch=it.touch.copy(horizontalSeek=!it.touch.horizontalSeek))}}
            SwitchChip("Swap brightness and volume sides",config.touch.swapBrightnessVolume){update {it.copy(touch=it.touch.copy(swapBrightnessVolume=!it.touch.swapBrightnessVolume))}}
            SwitchChip("Volume swipe",config.touch.volumeSwipe){update {it.copy(touch=it.touch.copy(volumeSwipe=!it.touch.volumeSwipe))}}
            SwitchChip("Brightness swipe",config.touch.brightnessSwipe){update {it.copy(touch=it.touch.copy(brightnessSwipe=!it.touch.brightnessSwipe))}}
            SwitchChip("Pinch to zoom",config.touch.pinchZoom){update {it.copy(touch=it.touch.copy(pinchZoom=!it.touch.pinchZoom))}}
            SwitchChip("Hold video for temporary speed",config.touch.holdToSpeed){update {it.copy(touch=it.touch.copy(holdToSpeed=!it.touch.holdToSpeed))}}
            PlayerChoice("Temporary hold speed",listOf("1.25" to "1.25×","1.5" to "1.5×","2.0" to "2×","3.0" to "3×","4.0" to "4×"),config.touch.holdSpeed.toString()){v->update {it.copy(touch=it.touch.copy(holdSpeed=v.toFloat()))}}
            SwitchChip("Subtitle touch gestures",config.touch.subtitleGestures){update {it.copy(touch=it.touch.copy(subtitleGestures=!it.touch.subtitleGestures))}}
            Text("With subtitles selected and controls hidden, start in the lower third: swipe sideways for timing, up/down for position, or pinch for text size. System captions keep their own style.",color=Mute,fontSize=12.sp)
            PlayerChoice("Orientation",listOf("auto" to "Follow device setting","sensor" to "Automatic rotation","landscape" to "Landscape","portrait" to "Portrait"),config.touch.orientation){v->update {it.copy(touch=it.touch.copy(orientation=v))}}
            Text("Tap to show controls. Double-tap the centre to pause; double-tap an edge to seek. Swipe horizontally to preview a seek, then release. Playback lock is in the player options; Back unlocks it.",color=Mute,fontSize=12.sp)
        }
        "audio"->PlayerSection("Sound") {
            PlayerChoice("Night listening",NightListening.choices,config.audioEffects.compression){v->update {it.copy(audioEffects=it.audioEffects.copy(compression=v))}}
            Text("Dynamic compression reduces loud effects relative to dialogue. Decoded audio only; passthrough stays untouched.",color=Mute,fontSize=12.sp)
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
        }
        "subtitles"->PlayerSection("Subtitles") {
            PlayerChoice("At start",listOf("foreign" to "For foreign audio","always" to "Always","off" to "Off"),subtitles.autoSelect){v->captions {it.copy(autoSelect=v)}}
            var languages by remember {mutableStateOf(subtitles.languages.joinToString(", "))}
            TvTextField(languages,{languages=it.take(120)},Modifier.fillMaxWidth(),placeholder="Languages: en, fr, ja")
            Chip("Save languages"){captions {it.copy(languages=languages.split(',').map(String::trim).filter {v->v.matches(Regex("[A-Za-z]{2,3}(-[A-Za-z]{2})?"))}.take(8))}}
            PlayerChoice("Text size",listOf("small" to "Small","normal" to "Normal","large" to "Large","xlarge" to "Extra large"),subtitles.size){v->captions {it.copy(size=v)}}
            PlayerChoice("Text background",listOf("outline" to "Outline","shadow" to "Shadow","box" to "Box"),subtitles.background){v->captions {it.copy(background=v)}}
            SwitchChip("Preserve authored styling",subtitles.authoredStyles){captions {it.copy(authoredStyles=!it.authoredStyles)}}
            SwitchChip("Use system caption style",subtitles.systemStyle){captions {it.copy(systemStyle=!it.systemStyle)}}
            SubtitleAssistanceSettings(subtitles.assistance){value->captions {it.copy(assistance=value)}}
            SubtitlePresetSettings(prefs,subtitles){value->captions {value}}
            AdvancedSubtitleSettings(subtitles){value->captions {value}}
            SwitchChip("Community subtitle search",subtitles.stremioOpenSubs){captions {it.copy(stremioOpenSubs=!it.stremioOpenSubs)}}
        }
        "appearance"->PlayerSection("Appearance and performance") {
            PlayerChoice("Performance",listOf("auto" to "Automatic","low" to "Low","balanced" to "Balanced","high" to "High"),prefs.devicePerformance){v->prefs.devicePerformance=v;refresh()}
            PlayerPalettePicker(prefs.accent){value->prefs.accent=value;refresh()}
            PlayerChoice("Display mode",listOf("night" to "Night","day" to "Day"),prefs.theme){v->prefs.theme=v;refresh()}
            SwitchChip("Glass surfaces",prefs.navigationGlass){prefs.navigationGlass=!prefs.navigationGlass;refresh()}
        }
        "privacy"->PlayerSection("Privacy") {
            PrivacySettings()
        }
        "backups"->PlayerSection("Backups") {
            PlayerBackupSettings {refresh()}
        }
        "about"->PlayerSection("About") {
            PlayerBrandLockup()
            Text("An independent player for Android and Android TV. No service credentials are bundled. Codec and HDR playback depend on the device, display and selected engine.",color=Mute,fontSize=13.sp)
            PlayerLegal()
        }
        "cache"->PlayerSection("Cache") {
            Text("Disposable artwork, thumbnails and temporary data can be recreated. Saved positions, settings and connection details are kept.",color=Mute,fontSize=13.sp)
            Chip("Clear disposable caches"){scope.launch {val result=withContext(Dispatchers.IO){runCatching {CacheManagement.clear()}};android.widget.Toast.makeText(this@SettingsContent,if(result.isSuccess)"Cache cleared" else "Some cached data could not be cleared",android.widget.Toast.LENGTH_LONG).show()}}
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
        "IAMF decoder notices" to "IAMF-NOTICES.txt",
        "libaums USB notices" to "libaums-NOTICE.txt","SQLCipher" to "SQLCipher-LICENSE.txt","SMBJ-RPC" to "SMBJ-RPC-BSD.txt","JSch" to "JSch-BSD.txt","Apache Commons notices" to "Commons-IO-Lang-NOTICE.txt","Apache Commons Net notices" to "Commons-Net-NOTICE.txt",
        "Fast upscaling shader — MIT" to "Anime4K-MIT.txt","JSch compression notices" to "JSch-JZlib-BSD.txt","JSch password hashing notices" to "JSch-jBCrypt-ISC.txt","Android NDK notices" to "Android-NDK-29-LLVM-NOTICE.txt")
    licenses.forEach {entry->Chip(entry.first){chosen=entry}}
    Chip("Google Cast SDK terms") {runCatching {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://developers.google.com/cast/docs/terms")))}}
    Chip("Android SDK terms") {runCatching {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://developer.android.com/studio/terms.html")))}}
    chosen?.let {(title,file)->
        var text by remember(file){mutableStateOf("Loading license…")}
        LaunchedEffect(file){text=withContext(Dispatchers.IO){runCatching {context.assets.open("licenses/$file").bufferedReader().use {it.readText()}}.getOrDefault("License unavailable. See the source repository.")}}
        TvDialog({chosen=null},width=680.dp,scrollContent=true){DialogHeading(title);Text(text,fontSize=13.sp);Chip("Close"){chosen=null}}
    }
}
