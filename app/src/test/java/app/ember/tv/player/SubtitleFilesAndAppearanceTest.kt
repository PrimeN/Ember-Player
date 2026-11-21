package app.ember.tv.player

import android.app.Application
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.SubtitleView
import app.ember.tv.data.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SubtitleFilesAndAppearanceTest {
    @Test fun olderSettingsKeepTheirAppearanceAndEncodingDefaults() {
        val config=Json.decodeFromString<SubtitleConfig>("""{"size":"large","color":"yellow"}""")
        assertEquals("auto",config.encoding)
        assertEquals(0L,config.defaultDelayMs)
        assertFalse(config.layout.needsCompatibility)
        assertEquals(1f,config.layout.scale)
        val updated=config.copy(layout=SubtitleLayout(foreground="#80FFAA00",alignment="right",lineSpacing=4f),encoding="windows-1251",defaultDelayMs=-500)
        assertEquals(updated,Json.decodeFromString<SubtitleConfig>(Json.encodeToString(updated)))
    }
    @Test fun colorsKeepAlphaAndInvalidValuesFallBack() {
        assertEquals(0xFF11AA22.toInt(),SubtitleLayout.color("#11aa22",0))
        assertEquals(0x8011AA22.toInt(),SubtitleLayout.color("#8011aa22",0))
        assertEquals("#8011AA22",SubtitleLayout.hex(0x8011AA22.toInt()))
        for(value in listOf("red","#12345","#GG0011","#001122\n","#123456789"))assertEquals(42,SubtitleLayout.color(value,42))
        assertEquals(0xFFFFE650.toInt(),SubtitleLayout().textColor("yellow"))
    }
    @Test fun layoutBoundsProtectRenderingAndOnlyKnownNativeOptionsAreSent() {
        val layout=SubtitleLayout(scalePercent=100000,bottomMarginPercent=-100,alignment="invalid",letterSpacing=Float.NaN,lineSpacing=1000f)
        assertEquals(2f,layout.scale);assertEquals(0f,layout.position(.1f))
        assertEquals(mapOf("sub-align-x" to "center","sub-spacing" to "0.0","sub-line-spacing" to "20.0"),layout.nativeOptions())
        assertTrue(SubtitleLayout(letterSpacing=1f).needsCompatibility)
        assertTrue(SubtitleLayout(outlineWidth=0f).needsCompatibility)
    }
    @Test fun byteOrderMarksOverrideExplicitEncodings() {
        val text="字幕 Привет"
        assertEquals(text,SubtitleEncodings.decode(byteArrayOf(0xFF.toByte(),0xFE.toByte())+text.toByteArray(Charsets.UTF_16LE),"en","windows-1252"))
        assertEquals(text,SubtitleEncodings.decode(byteArrayOf(0xEF.toByte(),0xBB.toByte(),0xBF.toByte())+text.toByteArray(),"en","Shift_JIS"))
    }
    @Test fun explicitLegacyEncodingsAndUnknownSettingsAreReadable() {
        val russian="Привет";val japanese="字幕"
        assertEquals(russian,SubtitleEncodings.decode(russian.toByteArray(charset("windows-1251")),"en","windows-1251"))
        assertEquals(japanese,SubtitleEncodings.decode(japanese.toByteArray(charset("Shift_JIS")),"en","Shift_JIS"))
        assertEquals(japanese,SubtitleEncodings.decode(japanese.toByteArray(),"ja","not-a-charset"))
    }
    @Test fun nearbyMatchingKeepsEpisodeNumbersDistinct() {
        assertTrue(NearbySubtitles.matches("Show.S01E01.mkv","show.s01e01.en.forced.srt"))
        assertTrue(NearbySubtitles.matches("Movie.mp4","Movie.ass"))
        assertFalse(NearbySubtitles.matches("Show.S01E01.mkv","Show.S01E010.srt"))
        assertFalse(NearbySubtitles.matches("Movie.mp4","Movie.mp3"))
        assertFalse(NearbySubtitles.matches(".mkv",".srt"))
    }
    @Test fun nearbyScanUsesTheGrantedParentAndNeverSuggestsOtherEpisodes() {
        val context:Application=RuntimeEnvironment.getApplication()
        val provider=SubtitleDocumentsProvider()
        provider.attachInfo(context,android.content.pm.ProviderInfo().apply {
            authority="subtitle.files";exported=true;grantUriPermissions=true
            readPermission="android.permission.MANAGE_DOCUMENTS";writePermission="android.permission.MANAGE_DOCUMENTS"
        })
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("subtitle.files",provider)
        val root="content://subtitle.files/tree/root/document/root"
        val media=QueueMedia("content://subtitle.files/tree/root/document/video","Episode",directory=root)
        context.contentResolver.query(Uri.parse(media.uri),arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),android.os.Bundle.EMPTY,null)!!.use {assertTrue(it.moveToFirst());assertEquals("Show.S01E01.mkv",it.getString(0))}
        assertEquals("Show.S01E01.mkv",MediaFiles.name(context,Uri.parse(media.uri)))
        context.contentResolver.query(Uri.parse("content://subtitle.files/tree/root/document/root/children"),null,android.os.Bundle.EMPTY,null)!!.use {assertEquals(5,it.count)}
        val found=NearbySubtitles.find(context,"subtitle-test",media)
        assertEquals(listOf("Show.S01E01.en.srt","Show.S01E01.fr.ass"),found.map {it.name})
        assertTrue(found.all {it.uris.single().startsWith("content://subtitle.files/tree/root/document/")})
        var checks=0
        try {
            NearbySubtitles.find(context,"subtitle-test",media){if(++checks>1)throw kotlinx.coroutines.CancellationException()}
            fail("Cancelled scan should stop")
        } catch(_:kotlinx.coroutines.CancellationException){}
    }
    @Test fun rememberedChoicesUseDigestsAndCanBeReplacedOrRemoved() {
        var raw:String?=null
        val store=ManualSubtitleChoices({raw},{raw=it;true})
        val first=ManualSubtitleFile("Movie.srt",listOf("content://files/subtitle"))
        val key="https://private.example/movie?token=secret"
        assertTrue(store.put(key,first));assertEquals(first,store.get(key))
        assertFalse(raw!!.contains(key));assertFalse(raw!!.contains("secret"))
        val pair=ManualSubtitleFile("Movie.idx",listOf("content://files/index","content://files/sub"))
        assertTrue(store.put(key,pair));assertEquals(pair,store.get(key))
        assertTrue(store.remove(key));assertNull(store.get(key))
    }
    @Test fun corruptOrUnsafeChoicesAreRejectedAndFailedWritesAreReported() {
        val store=ManualSubtitleChoices({"not json"},{false})
        assertNull(store.get("video"))
        assertFalse(store.put("video",ManualSubtitleFile("file.srt",listOf("content://files/sub"))))
        for(choice in listOf(ManualSubtitleFile("file.srt",listOf("https://example.invalid/sub")),
            ManualSubtitleFile("file.srt",network=NetworkQueueEntry("account","../private.srt",1)),
            ManualSubtitleFile("file.srt",listOf("content://files/sub"),NetworkQueueEntry("account","movie.srt",1))))
            assertFalse(ManualSubtitleChoices.valid(choice))
        assertTrue(ManualSubtitleChoices.valid(ManualSubtitleFile("file.srt",network=NetworkQueueEntry("account","folder/movie.srt",100))))
    }
    @Test fun rememberedChoiceStorageStaysBounded() {
        var raw:String?=null
        val store=ManualSubtitleChoices({raw},{raw=it;true})
        for(i in 0..259)assertTrue(store.put("video-$i",ManualSubtitleFile("file.srt",listOf("content://files/sub/$i"))))
        assertNull(store.get("video-0"));assertNotNull(store.get("video-259"))
        assertEquals(256,Json.decodeFromString<Map<String,ManualSubtitleFile>>(raw!!).size)
    }
    @Test fun nativeAssReceivesTheOriginalScriptAsASingleTimedSample() {
        val format = androidx.media3.common.Format.Builder().setSampleMimeType("text/x-ssa").build()
        val bytes = "[Script Info]\nPlayResX: 1920\n[Events]\nDialogue: preserved".toByteArray()
        val stream = AssScriptStream(format, bytes)
        val holder = androidx.media3.exoplayer.FormatHolder()
        val buffer = androidx.media3.decoder.DecoderInputBuffer(
            androidx.media3.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT
        )
        assertEquals(androidx.media3.common.C.RESULT_FORMAT_READ, stream.readData(holder, buffer, 0))
        assertEquals(format, holder.format)
        assertEquals(androidx.media3.common.C.RESULT_BUFFER_READ, stream.readData(holder, buffer, 0))
        assertEquals(0L, buffer.timeUs)
        buffer.flip()
        val actual = ByteArray(bytes.size)
        buffer.data!!.get(actual)
        assertArrayEquals(bytes, actual)
        buffer.clear()
        stream.readData(holder, buffer, 0)
        assertTrue(buffer.isEndOfStream)
        assertTrue(authoredAssEnabled(true, false))
        assertFalse(authoredAssEnabled(true, true))
    }

    @Test fun switchingBackendsMigratesExternalSubtitlesAndAnOffChoiceWinsPendingLoads() = runBlocking {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).get()
        val player = ExoPlayer.Builder(activity).build()
        var backend: Any? = null
        val owners = mutableListOf<SubtitleOwner>()
        val controller = SubtitleController(activity, { player }, SubtitleEngine(SubtitleView(activity)),
            SubtitleConfig(), {}, nativeBackend = { backend }, showOwner = { owners.add(it) },
            nativeSubtitle = { _, _, _ -> backend != null })
        val script = "1\n00:00:00,000 --> 00:00:02,000\nFirst\n"
        try {
            assertTrue(controller.loadManual(script, "srt"))
            assertEquals(SubtitleOwner.EXTERNAL, owners.last())
            assertTrue(controller.engine.active)
            backend = Any()
            controller.onBackendChanged()
            assertEquals(SubtitleOwner.NATIVE, owners.last())
            assertFalse(controller.engine.active)
            backend = null
            controller.onBackendChanged()
            assertEquals(SubtitleOwner.EXTERNAL, owners.last())
            assertTrue(controller.engine.active)

            val work = launch(start = CoroutineStart.UNDISPATCHED) {
                assertFalse(controller.loadManual(script, "srt"))
            }
            controller.selectOff()
            work.join()
            assertEquals(SubtitleOwner.OFF, owners.last())
            assertFalse(controller.engine.active)
        } finally {
            player.release()
        }
    }

    @Test fun explicitSelectionCancelsPendingManualWorkButAutomaticSelectionDoesNot() {
        val activity=Robolectric.buildActivity(ComponentActivity::class.java).get()
        val player=ExoPlayer.Builder(activity).build()
        try {
            var selections=0
            val controller=SubtitleController(activity,{player},SubtitleEngine(SubtitleView(activity)),SubtitleConfig(),{})
            controller.onManualSelection={selections++}
            controller.selectEmbedded(0,0,manual=false);assertEquals(0,selections)
            controller.selectOff();assertEquals(1,selections)
            controller.selectEmbedded(0,0);assertEquals(2,selections)
            controller.reserveManualSelection();assertEquals(2,selections)
        } finally {player.release()}
    }
    @Test fun playlistsPreserveSubtitleDirectoryAcrossSaveAndRestore() {
        var raw:String?=null
        val store=SavedPlaylists({raw},{raw=it;true})
        val media=QueueMedia("content://files/tree/root/document/video","Movie",directory="content://files/tree/root/document/root")
        assertTrue(store.save("Films",listOf(media)))
        assertEquals(media.directory,store.read().single().entries.single().directory)
        assertEquals("Movie",Json.decodeFromString<PlaylistEntry>("""{"uri":"content://files/video","title":"Movie"}""").title)
    }
}

class SubtitleDocumentsProvider:android.provider.DocumentsProvider() {
    private val names=mapOf("video" to "Show.S01E01.mkv","en" to "Show.S01E01.en.srt","fr" to "Show.S01E01.fr.ass","next" to "Show.S01E010.srt","notes" to "readme.txt")
    override fun onCreate()=true
    override fun queryRoots(projection:Array<out String>?)=android.database.MatrixCursor(arrayOf("root_id"))
    private fun rows(ids:List<String>,projection:Array<out String>?):android.database.Cursor {
        val columns=projection ?: arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return android.database.MatrixCursor(columns).apply {ids.forEach {id->addRow(columns.map {column->when(column) {
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID->id
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME->names[id]
            else->null
        }})}}
    }
    override fun queryDocument(documentId:String,projection:Array<out String>?)=rows(listOf(documentId),projection)
    override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,sortOrder:String?)=rows(names.keys.toList(),projection)
    override fun isChildDocument(parentDocumentId:String,documentId:String)=documentId in names
    override fun openDocument(documentId:String,mode:String,signal:android.os.CancellationSignal?):android.os.ParcelFileDescriptor=error("No content needed for listing")
}
