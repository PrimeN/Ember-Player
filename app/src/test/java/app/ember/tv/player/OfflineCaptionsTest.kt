package app.ember.tv.player

import app.ember.tv.data.SubtitleAssistance
import android.app.Application
import android.speech.tts.Voice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.Closeable
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class OfflineCaptionsTest {
    private class Translator:CaptionTranslator {
        val pending=mutableListOf<(String?)->Unit>();var cancelled=0;var closed=0
        override fun translate(text:String,result:(String?)->Unit):Closeable {pending+=result;return Closeable {cancelled++}}
        override fun close(){closed++}
    }
    private class Speaker:CaptionSpeaker {
        val spoken=mutableListOf<String>();var stopped=0;var closed=0
        override fun say(text:String){spoken+=text}
        override fun stop(){stopped++}
        override fun close(){closed++}
    }
    @Test fun lateResultsCannotReplaceNewCuesOrSpeakAfterPause() {
        val translator=Translator();val speaker=Speaker();var displayed=""
        val assistant=CaptionAssistant(SubtitleAssistance(translate=true,readAloud=true),translator,speaker){displayed=it}
        assistant.update("First",true);assistant.update("Second",true)
        translator.pending[0]("Late first");assertEquals("",displayed);assertTrue(speaker.spoken.isEmpty())
        translator.pending[1]("Translated second");assertEquals("Translated second",displayed)
        assertEquals(listOf("Translated second"),speaker.spoken)
        assistant.update("Third",true);assistant.update("Third",false)
        translator.pending[2]("Late third");assertFalse(speaker.spoken.contains("Late third"));assertTrue(translator.cancelled>=2)
        assistant.close();assertEquals(1,translator.closed);assertEquals(1,speaker.closed)
    }
    @Test fun unavailableTranslationKeepsOriginalCaptionsIndependentAndDoesNotSpeakWrongLanguage() {
        val translator=Translator();val speaker=Speaker();var overlay=""
        val assistant=CaptionAssistant(SubtitleAssistance(translate=true,readAloud=true),translator,speaker){overlay=it}
        assistant.update("Original",true);translator.pending.single()(null)
        assertEquals("",overlay);assertTrue(speaker.spoken.isEmpty())
        assistant.close()
    }
    @Test fun repeatedCuesAreCachedAndBlankCuesClearSpeech() {
        val translator=Translator();var shown=""
        val assistant=CaptionAssistant(SubtitleAssistance(translate=true),translator,null){shown=it}
        assistant.update("Hello",true);translator.pending.single()("Bonjour")
        assistant.update("",true);assertEquals("",shown)
        assistant.update("Hello",true);assertEquals("Bonjour",shown);assertEquals(1,translator.pending.size)
        assistant.close();translator.pending.single()("late");assertEquals("",shown)
    }
    @Test fun readAloudWithoutTranslationDoesNotCreateTranslationRequests() {
        val speaker=Speaker()
        val assistant=CaptionAssistant(SubtitleAssistance(readAloud=true),null,speaker){}
        assistant.update("Hello",true);assistant.update("Hello",true)
        assertEquals(listOf("Hello"),speaker.spoken)
        assistant.update("Hello",false);assertTrue(speaker.stopped>0)
        assistant.close()
    }
    @Test fun voicesRequiringNetworkOrMissingDataAreNeverEligible() {
        fun voice(network:Boolean,features:Set<String>)=Voice("Voice",Locale.ENGLISH,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,network,features)
        assertTrue(OfflineCaptions.offline(voice(false,emptySet())))
        assertFalse(OfflineCaptions.offline(voice(true,emptySet())))
        assertFalse(OfflineCaptions.offline(voice(false,setOf(android.speech.tts.TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))))
        assertEquals("text\nnext",OfflineCaptions.clean("\u0000text\nnext\u0001"))
        assertEquals(2000,OfflineCaptions.clean("x".repeat(4000)).length)
    }
    @Test fun missingTranslatorDoesNotReadUntranslatedTextWithTheTargetVoice(){
        val speaker=Speaker();var unavailable=0
        val assistant=CaptionAssistant(SubtitleAssistance(translate=true,readAloud=true),null,speaker,{unavailable++}){}
        assistant.update("Hello",true);assistant.update("Next",true)
        assertEquals(1,unavailable);assertTrue(speaker.spoken.isEmpty());assistant.close()
    }
    @Test fun assistanceIsOptInAndOlderConfigurationsStillLoad() {
        val old=kotlinx.serialization.json.Json.decodeFromString<app.ember.tv.data.SubtitleConfig>("{}")
        assertFalse(old.assistance.translate);assertFalse(old.assistance.readAloud)
    }
}
