package app.ember.player

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
    val context=LocalContext.current
    var expanded by remember {mutableStateOf(false)}
    var pairs by remember {mutableStateOf<List<CaptionLanguagePair>>(emptyList())}
    var voices by remember {mutableStateOf<List<android.speech.tts.Voice>>(emptyList())}
    var voiceStatus by remember {mutableStateOf("Checking installed offline voices…")}
    Chip(if(expanded)"Hide offline assistance" else "Offline subtitle assistance"){expanded=!expanded}
    if(!expanded)return
    LaunchedEffect(Unit){pairs=withContext(Dispatchers.IO){OfflineCaptions.installedPairs(context)}}
    DisposableEffect(Unit) {
        val reader=OfflineCaptionSpeaker(context,config){voices=it;voiceStatus=if(it.isEmpty())"No installed offline voice is available." else "${it.size} installed offline voices"}
        onDispose {reader.close()}
    }
    Text("Uses only installed on-device language models and offline speech voices. No cloud translation, API keys or automatic model downloads. Bitmap subtitles have no text to translate or read.",color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
    if(pairs.isEmpty())Text("Translation is unavailable: no installed language pair is reported by this device. Android 12 or newer and an on-device translation service are required.",color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
    else PlayerChoice("Installed translation languages",pairs.map {"${it.source}|${it.target}" to it.label},"${config.source}|${config.target}"){value->
        val fields=value.split('|');save(config.copy(source=fields[0],target=fields[1]))
    }
    SwitchChip("Translate subtitles",config.translate){
        if(config.translate)save(config.copy(translate=false))
        else pairs.firstOrNull {it.source==config.source&&it.target==config.target}?.let {save(config.copy(translate=true))}
            ?: pairs.firstOrNull()?.let {save(config.copy(translate=true,source=it.source,target=it.target))}
    }
    Text(voiceStatus,color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
    if(voices.isNotEmpty())PlayerChoice("Offline voice",listOf("" to "Match subtitle language")+voices.map {it.name to "${it.locale.displayName} · ${it.name}"},config.voice){save(config.copy(voice=it))}
    SwitchChip("Read subtitles aloud",config.readAloud){if(config.readAloud||voices.isNotEmpty())save(config.copy(readAloud=!config.readAloud))}
    Text("Original subtitles stay visible if assistance is unavailable. Subtitles are read only during playback and speech stops when you pause or leave.",color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
}
