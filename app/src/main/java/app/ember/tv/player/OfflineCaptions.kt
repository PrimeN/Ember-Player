package app.ember.tv.player

import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.annotation.RequiresApi
import app.ember.tv.data.SubtitleAssistance
import kotlinx.coroutines.*
import java.io.Closeable
import java.util.Locale
import kotlin.coroutines.resume

internal data class CaptionLanguagePair(val source:String,val target:String) {
    val label get()="${Locale.forLanguageTag(source).displayName} → ${Locale.forLanguageTag(target).displayName}"
}
internal interface CaptionTranslator:Closeable {fun translate(text:String,result:(String?)->Unit):Closeable}
internal interface CaptionSpeaker:Closeable {fun say(text:String);fun stop()}

/** Only installed resources are eligible. No HTTP client or download/install request is used. */
internal object OfflineCaptions {
    fun installedPairs(context:Context):List<CaptionLanguagePair> = if(Build.VERSION.SDK_INT>=31)runCatching {AndroidCaptionTranslator.pairs(context)}.getOrDefault(emptyList()) else emptyList()
    fun offline(voice:Voice)=!voice.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features.orEmpty()
    fun clean(text:String)=text.take(4096).filter {it=='\n'||it=='\t'||!it.isISOControl()}.trim().take(2000)
    fun translator(context:Context,config:SubtitleAssistance):CaptionTranslator? =
        if(config.translate&&Build.VERSION.SDK_INT>=31&&config.source.isNotBlank()&&config.target.isNotBlank())AndroidCaptionTranslator(context.applicationContext,CaptionLanguagePair(config.source,config.target)) else null
}

@RequiresApi(31)
private class AndroidCaptionTranslator(private val context:Context,private val pair:CaptionLanguagePair):CaptionTranslator {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var translator:android.view.translation.Translator?=null
    private var closed=false
    companion object {
        fun pairs(context:Context):List<CaptionLanguagePair> {
            val manager=context.getSystemService(android.view.translation.TranslationManager::class.java) ?: return emptyList()
            return manager.getOnDeviceTranslationCapabilities(android.view.translation.TranslationSpec.DATA_FORMAT_TEXT,android.view.translation.TranslationSpec.DATA_FORMAT_TEXT)
                .filter {it.state==android.view.translation.TranslationCapability.STATE_ON_DEVICE}
                .map {CaptionLanguagePair(it.sourceSpec.locale.toLanguageTag(),it.targetSpec.locale.toLanguageTag())}.distinct().take(100)
        }
    }
    private suspend fun ready():android.view.translation.Translator? {
        translator?.let {return it}
        if(closed || pair !in withContext(Dispatchers.IO){pairs(context)})return null
        val manager=context.getSystemService(android.view.translation.TranslationManager::class.java) ?: return null
        val result=suspendCancellableCoroutine<android.view.translation.Translator?> {continuation->
            val spec=android.view.translation.TranslationContext.Builder(
                android.view.translation.TranslationSpec(android.icu.util.ULocale.forLanguageTag(pair.source),android.view.translation.TranslationSpec.DATA_FORMAT_TEXT),
                android.view.translation.TranslationSpec(android.icu.util.ULocale.forLanguageTag(pair.target),android.view.translation.TranslationSpec.DATA_FORMAT_TEXT)).build()
            manager.createOnDeviceTranslator(spec,context.mainExecutor) {value->
                if(closed||!continuation.isActive)value?.destroy() else continuation.resume(value)
            }
        }
        if(closed){result?.destroy();return null}
        translator=result;return result
    }
    override fun translate(text:String,result:(String?)->Unit):Closeable {
        val job=scope.launch {
            val translated=try {withTimeoutOrNull(2500) {
                val engine=ready() ?: return@withTimeoutOrNull null
                suspendCancellableCoroutine<String?> {continuation->
                    val signal=CancellationSignal();continuation.invokeOnCancellation {signal.cancel()}
                    val request=android.view.translation.TranslationRequest.Builder().setFlags(android.view.translation.TranslationRequest.FLAG_TRANSLATION_RESULT)
                        .setTranslationRequestValues(listOf(android.view.translation.TranslationRequestValue.forText(text))).build()
                    engine.translate(request,signal,context.mainExecutor) {response->
                        if(continuation.isActive) {
                            val value=response.translationResponseValues[0]
                            continuation.resume(if(response.translationStatus==android.view.translation.TranslationResponse.TRANSLATION_STATUS_SUCCESS&&value?.statusCode==android.view.translation.TranslationResponseValue.STATUS_SUCCESS)value.text?.toString() else null)
                        }
                    }
                }
            }} catch(e:CancellationException){throw e} catch(_:Exception){null}
            if(!closed)result(translated)
        }
        return Closeable {job.cancel()}
    }
    override fun close(){closed=true;scope.cancel();runCatching {translator?.destroy()};translator=null}
}

internal class OfflineCaptionSpeaker(context:Context,private val config:SubtitleAssistance,private val available:(List<Voice>)->Unit={}):CaptionSpeaker {
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private var speech:TextToSpeech?=null
    private var voice:Voice?=null
    private var pending:String?=null
    private var closed=false
    init {
        speech=TextToSpeech(context.applicationContext) {status->handler.post {
            if(closed)return@post
            val engine=speech ?: return@post
            val voices=if(status==TextToSpeech.SUCCESS)runCatching {engine.voices.orEmpty().filter(OfflineCaptions::offline).sortedBy {it.name}.take(200)}.getOrDefault(emptyList()) else emptyList()
            val language=(if(config.translate)config.target else config.source).ifBlank {Locale.getDefault().toLanguageTag()}
            voice=voices.firstOrNull {it.name==config.voice} ?: voices.filter {it.locale.language==Locale.forLanguageTag(language).language}.maxByOrNull {it.quality}
            engine.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            available(voices)
            pending?.let {pending=null;say(it)}
        }}
    }
    override fun say(text:String) {
        if(closed)return
        val selected=voice ?: run {pending=OfflineCaptions.clean(text);return}
        val engine=speech ?: return
        if(engine.setVoice(selected)!=TextToSpeech.SUCCESS)return
        val params=android.os.Bundle().apply {putString(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS,"true")}
        engine.speak(OfflineCaptions.clean(text),TextToSpeech.QUEUE_FLUSH,params,"ember-caption")
    }
    override fun stop(){pending=null;speech?.stop()}
    override fun close(){closed=true;pending=null;speech?.stop();speech?.shutdown();speech=null;voice=null}
}

/** Late results cannot replace a new cue; text and translations stay in bounded memory only. */
internal class CaptionAssistant(private val config:SubtitleAssistance,private val translator:CaptionTranslator?,private val speaker:CaptionSpeaker?,private val unavailable:()->Unit={},private val display:(String)->Unit):Closeable {
    private var generation=0L
    private var request:Closeable?=null
    private var previous=""
    private var closed=false
    private var reported=false
    private fun missing(){if(!reported){reported=true;unavailable()}}
    private val cache=object:LinkedHashMap<String,String>(32,.75f,true){override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,String>?)=size>100}
    fun update(value:String,playing:Boolean) {
        if(closed)return
        val text=OfflineCaptions.clean(value)
        if(!playing){if(previous.isNotEmpty()){previous="";generation++;request?.close();request=null;speaker?.stop()};return}
        if(text==previous)return
        previous=text;val visit=++generation;request?.close();request=null;display("");speaker?.stop()
        if(text.isEmpty())return
        if(config.translate) {
            if(translator==null){missing();return}
            val cached=cache[text]
            if(cached!=null){display(cached);if(config.readAloud)speaker?.say(cached);return}
            request=translator.translate(text){translated->
                if(closed||visit!=generation)return@translate
                val valid=translated?.let(OfflineCaptions::clean)?.takeIf {it.isNotBlank()}
                if(valid!=null){cache[text]=valid;display(valid);if(config.readAloud)speaker?.say(valid)} else missing()
            }
        } else if(config.readAloud)speaker?.say(text)
    }
    override fun close(){if(closed)return;closed=true;generation++;listOfNotNull(request,translator,speaker).forEach {runCatching {it.close()}};cache.clear();display("")}
}
