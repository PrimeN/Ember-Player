package app.ember.player

import app.ember.tv.R

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
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    when(id) {
        "playback"->PlayerSection(uiResources.getString(PlayerUiLabels.PLAYBACK)) {
            PlayerChoice(uiResources.getString(R.string.ui_controls),PlayerInterfaces.options,PlayerInterfaces.resolve(config,"modern")){v->update {it.copy(playerInterface=v)}}
            PlayerChoice(uiResources.getString(R.string.ui_playback_engine),listOf("auto" to uiResources.getString(R.string.ui_automatic),"standard" to uiResources.getString(R.string.ui_standard),"compatibility" to uiResources.getString(R.string.ui_compatibility)),config.engine){v->update {it.copy(engine=v)}}
            PlayerChoice(uiResources.getString(R.string.ui_resume),listOf("ask" to uiResources.getString(R.string.ui_ask),"resume" to uiResources.getString(R.string.ui_resume_automatically),"start" to uiResources.getString(R.string.ui_start_over)),config.resume){v->update {it.copy(resume=v)}}
            PlayerChoice(uiResources.getString(R.string.ui_seeking),listOf("precise" to uiResources.getString(R.string.ui_precise),"fast" to uiResources.getString(R.string.ui_fast_keyframes)),config.seekMode){v->update {it.copy(seekMode=v)}}
            PlayerChoice(uiResources.getString(R.string.ui_buffer),listOf("normal" to uiResources.getString(R.string.ui_normal),"large" to uiResources.getString(R.string.ui_large),"huge" to uiResources.getString(R.string.ui_huge)),config.buffer){v->update {it.copy(buffer=v,stream=it.stream.copy(custom=false))}}
            SwitchChip(uiResources.getString(R.string.ui_remember_working_decoders),config.rememberDecoderChoices){update {it.copy(rememberDecoderChoices=!it.rememberDecoderChoices)}}
            Chip(uiResources.getString(R.string.ui_reset_decoder_memory)){DecoderHealth.clear(this@SettingsContent)}
            SwitchChip(uiResources.getString(R.string.ui_picture_in_picture),config.pip){update {it.copy(pip=!it.pip)}}
            SwitchChip(uiResources.getString(R.string.ui_background_audio),config.backgroundAudio){update {it.copy(backgroundAudio=!it.backgroundAudio)}}
            Text(uiResources.getString(R.string.ui_keep_sound_playing_when_the_screen_is_off_or),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            SwitchChip(uiResources.getString(R.string.ui_ask_before_leaving_playback),config.confirmExit){update {it.copy(confirmExit=!it.confirmExit)}}
            SwitchChip(uiResources.getString(R.string.ui_introdb_markers),config.introDb){update {it.copy(introDb=!it.introDb)}}
            PlayerChoice(uiResources.getString(R.string.ui_skip_markers),listOf("button" to uiResources.getString(R.string.ui_show_skip_button),"auto" to uiResources.getString(R.string.ui_skip_automatically),"off" to uiResources.getString(R.string.ui_off)),config.skipIntro){v->update {it.copy(skipIntro=v)}}
        }
        "network"->PlayerSection(uiResources.getString(R.string.ui_network_and_buffering)) {
            StreamSettings(config){value->update {value}}
        }
        "sleep"->PlayerSection(uiResources.getString(R.string.ui_sleep_timer)) {
            SleepTimerSettings()
        }
        "enhancement"->PlayerSection(uiResources.getString(R.string.ui_picture_enhancement)) {
            val picture=config.picture
            SwitchChip(uiResources.getString(R.string.ui_picture_enhancement),picture.enabled){update {it.copy(picture=it.picture.copy(enabled=!it.picture.enabled))}}
            Text(uiResources.getString(R.string.ui_enabled_by_default_gpu_processing_uses_more_power_turn),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            SwitchChip(uiResources.getString(R.string.ui_gpu_upscaling),picture.upscaling){update {it.copy(picture=it.picture.copy(upscaling=!it.picture.upscaling))}}
            SwitchChip(uiResources.getString(R.string.ui_fast_upscaling_shader),picture.shader){update {it.copy(picture=it.picture.copy(shader=!it.picture.shader))}}
            Text(uiResources.getString(R.string.ui_standard_uses_lanczos_when_enlarging_video_compatibility_can_also),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            PlayerChoice(uiResources.getString(R.string.ui_picture_preset),PictureEnhancement.presets.mapIndexed {i,v->i.toString() to uiResources.getString(v)},PictureEnhancement.presetIndex(picture).takeIf {it>=0}?.toString() ?: uiResources.getString(R.string.ui_custom)){v->update {it.copy(picture=PictureEnhancement.preset(v.toInt(),it.picture))}}
            pictureAdjustments.forEach {adjustment->
                PlayerChoice(uiResources.getString(adjustment.label),adjustment.values(picture).map {it.toString() to it.toString()},adjustment.read(picture.normalized()).toString()){v->
                    update {it.copy(picture=adjustment.write(it.picture,v.toInt()).normalized())}
                }
            }
            Chip(uiResources.getString(R.string.ui_reset_picture_adjustments)){update {it.copy(picture=it.picture.copy(brightness=0,contrast=0,saturation=0,gamma=0,hue=0,sharpness=0))}}
        }
        "profiles"->PlayerSection(uiResources.getString(R.string.ui_saved_picture_shader_profiles)) {
            PictureProfileSettings(config){value->update {value}}
        }
        "hdr"->PlayerSection(uiResources.getString(R.string.ui_hdr_output)) {
            PlayerChoice(uiResources.getString(R.string.ui_compatibility_renderer),RendererPolicy.choices,RendererPolicy.normalized(config.renderer)){v->update {it.copy(renderer=v)}}
            HdrOutputSettings(config.hdrOutput){value->update {it.copy(hdrOutput=value)}}
        }
        "decoding"->PlayerSection(uiResources.getString(R.string.ui_video_decoding)) {
            PlayerChoice(uiResources.getString(R.string.ui_software_video),listOf("fallback" to uiResources.getString(R.string.ui_when_needed),"off" to uiResources.getString(R.string.ui_device_decoders_only),"always" to uiResources.getString(R.string.ui_always_software)),config.ffmpegVideo){v->update {it.copy(ffmpegVideo=v)}}
            PlayerChoice(uiResources.getString(R.string.ui_compatibility_decoder_profile),NativeDecoderProfile.choices,NativeDecoderProfile.normalized(config.compatibilityDecoder)){v->update {it.copy(compatibilityDecoder=v)}}
            SwitchChip(uiResources.getString(R.string.ui_automatic_hi10p_fallback),config.hi10pFallback){update {it.copy(hi10pFallback=!it.hi10pFallback)}}
            Text(uiResources.getString(R.string.ui_compatibility_only_automatic_detects_nvidia_shield_10_bit_h),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            PlayerChoice(uiResources.getString(R.string.ui_frame_rate_matching),listOf("off" to uiResources.getString(R.string.ui_off),"seamless" to uiResources.getString(R.string.ui_seamless_only),"always" to uiResources.getString(R.string.ui_match_video)),config.frameRate){v->update {it.copy(frameRate=v)}}
            SwitchChip(uiResources.getString(R.string.ui_match_video_resolution),config.matchResolution){update {it.copy(matchResolution=!it.matchResolution)}}
            PlayerChoice(uiResources.getString(R.string.ui_dolby_vision),listOf("auto" to uiResources.getString(R.string.ui_automatic),"always" to uiResources.getString(R.string.ui_use_dolby_vision),"hdr10" to uiResources.getString(R.string.ui_hdr10_base_layer_when_available)),config.dolbyVision){v->update {it.copy(dolbyVision=v)}}
            PlayerChoice(uiResources.getString(R.string.ui_deinterlacing),listOf("off" to uiResources.getString(R.string.ui_off),"auto" to uiResources.getString(R.string.ui_automatic),"always" to uiResources.getString(R.string.ui_always)),config.deinterlacing){v->update {it.copy(deinterlacing=v)}}
        }
        "touch"->PlayerSection(uiResources.getString(R.string.ui_touch_and_rotation)) {
            Text(uiResources.getString(R.string.ui_touch_gestures_apply_on_phones_and_tablets_remote_navigation),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            SwitchChip(uiResources.getString(R.string.ui_touch_gestures),config.touch.enabled){update {it.copy(touch=it.touch.copy(enabled=!it.touch.enabled))}}
            PlayerChoice(uiResources.getString(R.string.ui_double_tap_seek),listOf("5" to uiResources.getString(R.string.ui_5_seconds),"10" to uiResources.getString(R.string.ui_10_seconds),"15" to uiResources.getString(R.string.ui_15_seconds),"30" to uiResources.getString(R.string.ui_30_seconds),"60" to uiResources.getString(R.string.ui_60_seconds)),config.touch.seekSeconds.toString()){v->update {it.copy(touch=it.touch.copy(seekSeconds=v.toInt()))}}
            PlayerChoice(uiResources.getString(R.string.ui_left_double_tap),TouchActions.options,config.touch.leftDoubleTap){v->update {it.copy(touch=it.touch.copy(leftDoubleTap=v))}}
            PlayerChoice(uiResources.getString(R.string.ui_center_double_tap),TouchActions.options,config.touch.centerDoubleTap){v->update {it.copy(touch=it.touch.copy(centerDoubleTap=v))}}
            PlayerChoice(uiResources.getString(R.string.ui_right_double_tap),TouchActions.options,config.touch.rightDoubleTap){v->update {it.copy(touch=it.touch.copy(rightDoubleTap=v))}}
            PlayerChoice(uiResources.getString(R.string.ui_edge_tap_region),listOf(20,25,30,35,40,45).map {it.toString() to uiResources.getString(R.string.ui_each_side ,it.toString())},config.touch.seekAreaPercent.toString()){v->update {it.copy(touch=it.touch.copy(seekAreaPercent=v.toInt()))}}
            SwitchChip(uiResources.getString(R.string.ui_horizontal_swipe_to_seek),config.touch.horizontalSeek){update {it.copy(touch=it.touch.copy(horizontalSeek=!it.touch.horizontalSeek))}}
            SwitchChip(uiResources.getString(R.string.ui_swap_brightness_and_volume_sides),config.touch.swapBrightnessVolume){update {it.copy(touch=it.touch.copy(swapBrightnessVolume=!it.touch.swapBrightnessVolume))}}
            SwitchChip(uiResources.getString(R.string.ui_volume_swipe),config.touch.volumeSwipe){update {it.copy(touch=it.touch.copy(volumeSwipe=!it.touch.volumeSwipe))}}
            SwitchChip(uiResources.getString(R.string.ui_brightness_swipe),config.touch.brightnessSwipe){update {it.copy(touch=it.touch.copy(brightnessSwipe=!it.touch.brightnessSwipe))}}
            SwitchChip(uiResources.getString(R.string.ui_pinch_to_zoom),config.touch.pinchZoom){update {it.copy(touch=it.touch.copy(pinchZoom=!it.touch.pinchZoom))}}
            SwitchChip(uiResources.getString(R.string.ui_hold_video_for_temporary_speed),config.touch.holdToSpeed){update {it.copy(touch=it.touch.copy(holdToSpeed=!it.touch.holdToSpeed))}}
            PlayerChoice(uiResources.getString(R.string.ui_temporary_hold_speed),listOf("1.25" to "1.25×","1.5" to "1.5×","2.0" to "2×","3.0" to "3×","4.0" to "4×"),config.touch.holdSpeed.toString()){v->update {it.copy(touch=it.touch.copy(holdSpeed=v.toFloat()))}}
            SwitchChip(uiResources.getString(R.string.ui_subtitle_touch_gestures),config.touch.subtitleGestures){update {it.copy(touch=it.touch.copy(subtitleGestures=!it.touch.subtitleGestures))}}
            Text(uiResources.getString(R.string.ui_with_subtitles_selected_and_controls_hidden_start_in_the),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            PlayerChoice(uiResources.getString(R.string.ui_orientation),listOf("auto" to uiResources.getString(R.string.ui_follow_device_setting),"sensor" to uiResources.getString(R.string.ui_automatic_rotation),"landscape" to uiResources.getString(R.string.ui_landscape),"portrait" to uiResources.getString(R.string.ui_portrait)),config.touch.orientation){v->update {it.copy(touch=it.touch.copy(orientation=v))}}
            Text(uiResources.getString(R.string.ui_tap_to_show_controls_double_tap_the_center_to),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
        }
        "audio"->PlayerSection(uiResources.getString(PlayerUiLabels.AUDIO)) {
            PlayerChoice(uiResources.getString(R.string.ui_night_listening),NightListening.choices,config.audioEffects.compression){v->update {it.copy(audioEffects=it.audioEffects.copy(compression=v))}}
            Text(uiResources.getString(R.string.ui_dynamic_compression_reduces_loud_effects_relative_to_dialogue_decoded),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            SwitchChip(uiResources.getString(R.string.ui_hdmi_passthrough),config.passthrough){update {it.copy(passthrough=!it.passthrough)}}
            PlayerChoice(uiResources.getString(R.string.ui_audio_decoding),listOf("on" to uiResources.getString(R.string.ui_software_when_needed),"prefer" to uiResources.getString(R.string.ui_prefer_software),"off" to uiResources.getString(R.string.ui_device_decoders_only)),config.ffmpeg){v->update {it.copy(ffmpeg=v)}}
            PlayerChoice(uiResources.getString(R.string.ui_audio_track),listOf("quality" to uiResources.getString(R.string.ui_best_quality),"language" to uiResources.getString(R.string.ui_preferred_language),"default" to uiResources.getString(R.string.ui_file_default)),config.audioPick){v->update {it.copy(audioPick=v)}}
            Text(uiResources.getString(R.string.ui_sound_delay_ms ,config.audioDelayMs.toString()),style=androidx.tv.material3.MaterialTheme.typography.bodyMedium)
            ButtonRow {Chip(uiResources.getString(R.string.ui_25_ms)){update {it.copy(audioDelayMs=(it.audioDelayMs-25).coerceAtLeast(-2000))}};Chip(uiResources.getString(R.string.ui_25_ms_ca896a)){update {it.copy(audioDelayMs=(it.audioDelayMs+25).coerceAtMost(2000))}}}
            Text(uiResources.getString(R.string.ui_bluetooth_delay ,config.bluetoothDelayMs?.let {uiResources.getString(R.string.ui_ms ,it.toString())} ?: uiResources.getString(R.string.ui_use_sound_delay)),style=androidx.tv.material3.MaterialTheme.typography.bodyMedium)
            ButtonRow {Chip(uiResources.getString(R.string.ui_25_ms)){update {it.copy(bluetoothDelayMs=((it.bluetoothDelayMs ?: it.audioDelayMs)-25).coerceAtLeast(-2000))}};Chip(uiResources.getString(R.string.ui_25_ms_ca896a)){update {it.copy(bluetoothDelayMs=((it.bluetoothDelayMs ?: it.audioDelayMs)+25).coerceAtMost(2000))}};Chip(uiResources.getString(R.string.ui_use_sound_delay)){update {it.copy(bluetoothDelayMs=null)}}}
            PlayerChoice(uiResources.getString(R.string.ui_audio_preset),AudioEffects.presets,config.audioEffects.preset){v->update {it.copy(audioEffects=it.audioEffects.copy(preset=v))}}
            EqualizerSettings(prefs,config.audioEffects){v->update {it.copy(audioEffects=v)}}
            PlayerChoice(uiResources.getString(R.string.ui_bass),(0..6).map {it.toString() to uiResources.getString(R.string.ui_db_024cae ,it.toString())},config.audioEffects.bass.toString()){v->update {it.copy(audioEffects=it.audioEffects.copy(bass=v.toInt()))}}
            PlayerChoice(uiResources.getString(R.string.ui_treble),(0..6).map {it.toString() to uiResources.getString(R.string.ui_db_024cae ,it.toString())},config.audioEffects.treble.toString()){v->update {it.copy(audioEffects=it.audioEffects.copy(treble=v.toInt()))}}
            PlayerChoice(uiResources.getString(R.string.ui_stereo_width),listOf(0,50,100,125,150,175,200).map {it.toString() to "$it%"},config.audioEffects.stereoWidth.toString()){v->update {it.copy(audioEffects=it.audioEffects.copy(stereoWidth=v.toInt()))}}
            PlayerChoice(uiResources.getString(R.string.ui_channels),AudioEffects.channelChoices,config.audioEffects.channels){v->update {it.copy(audioEffects=it.audioEffects.copy(channels=v))}}
            Text(uiResources.getString(R.string.ui_audio_effects_apply_to_decoded_sound_passthrough_stays_untouched),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
        }
        "subtitles"->PlayerSection(uiResources.getString(PlayerUiLabels.SUBTITLES)) {
            PlayerChoice(uiResources.getString(R.string.ui_at_start),listOf("foreign" to uiResources.getString(R.string.ui_for_foreign_audio),"always" to uiResources.getString(R.string.ui_always),"off" to uiResources.getString(R.string.ui_off)),subtitles.autoSelect){v->captions {it.copy(autoSelect=v)}}
            var languages by remember {mutableStateOf(subtitles.languages.joinToString(", "))}
            TvTextField(languages,{value->
                languages=value.take(120)
                SubtitleLanguages.parse(languages)?.let {codes->captions {it.copy(languages=codes)}}
            },Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_languages_en_fr_ja))
            if(SubtitleLanguages.parse(languages)==null)PlayerErrorText(uiResources.getString(R.string.ui_enter_up_to_8_language_codes_separated_by_commas))
            PlayerChoice(uiResources.getString(R.string.ui_text_size),listOf("small" to uiResources.getString(R.string.ui_small),"normal" to uiResources.getString(R.string.ui_normal),"large" to uiResources.getString(R.string.ui_large),"xlarge" to uiResources.getString(R.string.ui_extra_large)),subtitles.size){v->captions {it.copy(size=v)}}
            PlayerChoice(uiResources.getString(R.string.ui_text_background),listOf("outline" to uiResources.getString(R.string.ui_outline),"shadow" to uiResources.getString(R.string.ui_shadow),"box" to uiResources.getString(R.string.ui_box)),subtitles.background){v->captions {it.copy(background=v)}}
            SwitchChip(uiResources.getString(R.string.ui_preserve_authored_styling),subtitles.authoredStyles){captions {it.copy(authoredStyles=!it.authoredStyles)}}
            SwitchChip(uiResources.getString(R.string.ui_use_system_caption_style),subtitles.systemStyle){captions {it.copy(systemStyle=!it.systemStyle)}}
            SubtitleAssistanceSettings(subtitles.assistance){value->captions {it.copy(assistance=value)}}
            SubtitlePresetSettings(prefs,subtitles){value->captions {value}}
            AdvancedSubtitleSettings(subtitles){value->captions {value}}
            SwitchChip(uiResources.getString(R.string.ui_community_subtitle_search),subtitles.stremioOpenSubs){captions {it.copy(stremioOpenSubs=!it.stremioOpenSubs)}}
        }
        "appearance"->PlayerSection(uiResources.getString(R.string.ui_appearance_and_performance)) {
            PlayerChoice(uiResources.getString(R.string.ui_performance),listOf("auto" to uiResources.getString(R.string.ui_automatic),"low" to uiResources.getString(R.string.ui_low),"balanced" to uiResources.getString(R.string.ui_balanced),"high" to uiResources.getString(R.string.ui_high)),prefs.devicePerformance){v->prefs.devicePerformance=v;refresh()}
            PlayerPalettePicker(prefs.accent){value->prefs.accent=value;refresh()}
            PlayerChoice(uiResources.getString(R.string.ui_theme),listOf("system" to uiResources.getString(R.string.ui_follow_system),"night" to uiResources.getString(R.string.ui_dark),"day" to uiResources.getString(R.string.ui_light)),prefs.theme){v->prefs.theme=v;refresh()}
            SwitchChip(uiResources.getString(R.string.ui_glass_surfaces),prefs.navigationGlass){prefs.navigationGlass=!prefs.navigationGlass;refresh()}
        }
        "privacy"->PlayerSection(uiResources.getString(PlayerUiLabels.PRIVATE_MEDIA)) {
            PrivacySettings()
        }
        "backups"->PlayerSection(uiResources.getString(R.string.ui_backups)) {
            PlayerBackupSettings {refresh()}
        }
        "about"->PlayerSection(uiResources.getString(R.string.ui_about)) {
            PlayerBrandLockup()
            Text(uiResources.getString(R.string.ui_an_independent_player_for_android_and_android_tv_no),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            PlayerLegal()
        }
        "cache"->PlayerSection(uiResources.getString(R.string.ui_cache)) {
            Text(uiResources.getString(R.string.ui_disposable_artwork_thumbnails_and_temporary_data_can_be_recreated),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            Chip(uiResources.getString(R.string.ui_clear_disposable_caches)){scope.launch {val result=withContext(Dispatchers.IO){runCatching {CacheManagement.clear()}};android.widget.Toast.makeText(this@SettingsContent,if(result.isSuccess)uiResources.getString(R.string.ui_cache_cleared) else uiResources.getString(R.string.ui_some_cached_data_could_not_be_cleared),android.widget.Toast.LENGTH_LONG).show()}}
        }
    }
}
@Composable
internal fun PlayerChoice(label:String,options:List<Pair<String,String>>,value:String,pick:(String)->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current
    var open by remember {mutableStateOf(false)}
    Chip(uiResources.getString(R.string.ui_label_value ,label,options.firstOrNull {it.first==value}?.second ?: value),maxLines=1){open=true}
    if(open)OptionMenu(label,options.map {(id,name)->MenuOption(name,id==value){pick(id)}},{open=false})
}
@Composable
private fun PlayerLegal() {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    var chosen by remember {mutableStateOf<Pair<String,String>?>(null)}
    val context=androidx.compose.ui.platform.LocalContext.current
    val licenses=listOf(uiResources.getString(R.string.ui_gpl_v3_mpv_ffmpeg_and_nextlib) to "player-GPL-3.0.txt",uiResources.getString(R.string.ui_apache_2_0_androidx_media3_and_libraries) to "Apache-2.0.txt",
        uiResources.getString(R.string.ui_fraunhofer_mpeg_h) to "Fraunhofer-FDK-MPEG-H.txt",uiResources.getString(R.string.ui_libmpv_android_wrapper) to "libmpv-wrapper-MIT.txt",
        uiResources.getString(R.string.ui_iamf_decoder_notices) to "IAMF-NOTICES.txt",
        uiResources.getString(R.string.ui_libaums_usb_notices) to "libaums-NOTICE.txt","SQLCipher" to "SQLCipher-LICENSE.txt","SMBJ-RPC" to "SMBJ-RPC-BSD.txt","JSch" to "JSch-BSD.txt",uiResources.getString(R.string.ui_apache_commons_notices) to "Commons-IO-Lang-NOTICE.txt",uiResources.getString(R.string.ui_apache_commons_net_notices) to "Commons-Net-NOTICE.txt",
        uiResources.getString(R.string.ui_fast_upscaling_shader_mit) to "Anime4K-MIT.txt",uiResources.getString(R.string.ui_jsch_compression_notices) to "JSch-JZlib-BSD.txt",uiResources.getString(R.string.ui_jsch_password_hashing_notices) to "JSch-jBCrypt-ISC.txt",uiResources.getString(R.string.ui_android_ndk_notices) to "Android-NDK-29-LLVM-NOTICE.txt")
    licenses.forEach {entry->Chip(entry.first){chosen=entry}}
    Chip(uiResources.getString(R.string.ui_google_cast_sdk_terms)) {runCatching {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://developers.google.com/cast/docs/terms")))}}
    Chip(uiResources.getString(R.string.ui_android_sdk_terms)) {runCatching {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://developer.android.com/studio/terms.html")))}}
    chosen?.let {(title,file)->
        var text by remember(file){mutableStateOf(uiResources.getString(R.string.ui_loading_license))}
        LaunchedEffect(file){text=withContext(Dispatchers.IO){runCatching {context.assets.open("licenses/$file").bufferedReader().use {it.readText()}}.getOrDefault(uiResources.getString(R.string.ui_license_unavailable_see_the_source_repository))}}
        TvDialog({chosen=null},width=680.dp,scrollContent=true){DialogHeading(title);Text(text,style=androidx.tv.material3.MaterialTheme.typography.bodySmall);PlayerAction(uiResources.getString(R.string.ui_close)){chosen=null}}
    }
}

@JvmName("PlayerChoiceResourceOptions")
@Composable
internal fun PlayerChoice(label:String,options:List<Pair<String,Int>>,value:String,pick:(String)->Unit) {
    val resources=androidx.compose.ui.platform.LocalResources.current
    PlayerChoice(label,options.map {it.first to resources.getString(it.second)},value,pick)
}
