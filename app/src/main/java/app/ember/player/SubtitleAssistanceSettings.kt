package app.ember.player

import app.ember.tv.R

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.data.SubtitleAssistance
import app.ember.tv.player.*
import app.ember.tv.ui.*
import kotlinx.coroutines.*

@Composable
internal fun SubtitleAssistanceSettings(config:SubtitleAssistance,save:(SubtitleAssistance)->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val context=LocalContext.current
    var expanded by remember {mutableStateOf(false)}
    var pairs by remember {mutableStateOf<List<CaptionLanguagePair>>(emptyList())}
    var voices by remember {mutableStateOf<List<android.speech.tts.Voice>>(emptyList())}
    var voiceStatus by remember {mutableStateOf(uiResources.getString(R.string.ui_checking_installed_offline_voices))}
    PlayerAction(if(expanded)uiResources.getString(R.string.ui_hide_offline_assistance) else uiResources.getString(R.string.ui_offline_subtitle_assistance)){expanded=!expanded}
    if(!expanded)return
    LaunchedEffect(Unit){pairs=withContext(Dispatchers.IO){OfflineCaptions.installedPairs(context)}}
    DisposableEffect(Unit) {
        val reader=OfflineCaptionSpeaker(context,config){voices=it;voiceStatus=if(it.isEmpty())uiResources.getString(R.string.ui_no_installed_offline_voice_is_available) else uiResources.getString(R.string.ui_installed_offline_voices ,it.size.toString())}
        onDispose {reader.close()}
    }
    Text(uiResources.getString(R.string.ui_uses_only_installed_on_device_language_models_and_offline),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
    if(pairs.isEmpty())Text(uiResources.getString(R.string.ui_translation_is_unavailable_no_installed_language_pair_is_reported),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
    else PlayerChoice(uiResources.getString(R.string.ui_installed_translation_languages),pairs.map {"${it.source}|${it.target}" to it.label},"${config.source}|${config.target}"){value->
        val fields=value.split('|');save(config.copy(source=fields[0],target=fields[1]))
    }
    SwitchChip(uiResources.getString(R.string.ui_translate_subtitles),config.translate){
        if(config.translate)save(config.copy(translate=false))
        else pairs.firstOrNull {it.source==config.source&&it.target==config.target}?.let {save(config.copy(translate=true))}
            ?: pairs.firstOrNull()?.let {save(config.copy(translate=true,source=it.source,target=it.target))}
    }
    Text(voiceStatus,color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
    if(voices.isNotEmpty())PlayerChoice(uiResources.getString(R.string.ui_offline_voice),listOf("" to uiResources.getString(R.string.ui_match_subtitle_language))+voices.map {it.name to "${it.locale.displayName} · ${it.name}"},config.voice){save(config.copy(voice=it))}
    SwitchChip(uiResources.getString(R.string.ui_read_subtitles_aloud),config.readAloud){if(config.readAloud||voices.isNotEmpty())save(config.copy(readAloud=!config.readAloud))}
    Text(uiResources.getString(R.string.ui_original_subtitles_stay_visible_if_assistance_is_unavailable_subtitles),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
}
