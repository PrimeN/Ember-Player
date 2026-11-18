package app.ember.player

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import app.ember.tv.data.Prefs
import app.ember.tv.player.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class,qualifiers="w960dp-h720dp-land-mdpi")
class PlayerPlaylistUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var storage:PlayerUiStoreFixture
    @Before fun setup(){storage=PlayerUiStoreFixture(context)}
    @After fun cleanup(){storage.close()}
    @Test fun aLibraryCardCanAppendToAnExistingPlaylist() {
        val store=SavedPlaylists(context)
        assertTrue(store.save("Watch next",listOf(QueueMedia("content://videos/1","One"))))
        val media=QueueMedia("content://videos/2","Two")
        compose.setContent {PlayerTheme(Prefs(context)){PlayerMediaCard(PlayerLibraryMedia(media),Modifier.width(240.dp),0){}}}
        compose.onNodeWithContentDescription("Add Two to playlist").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.waitUntil(10_000){compose.onAllNodesWithText("Watch next").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Watch next").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.waitUntil(10_000){store.read().single().entries.size==2}
        compose.runOnIdle {assertEquals(listOf("One","Two"),store.read().single().entries.map {it.title})}
        compose.onNodeWithText("Add to playlist").assertDoesNotExist()
    }
    @Test @Config(qualifiers="w960dp-h720dp-land-television-mdpi")
    fun reorderingWithRemoteActionsKeepsTheResumeItemAndCanBeClosed() {
        val store=SavedPlaylists(context)
        assertTrue(store.save("Order",listOf(QueueMedia("content://videos/1","One"),QueueMedia("content://videos/2","Two")),1))
        var shown by mutableStateOf(true)
        val initial=store.read().single()
        compose.setContent {PlayerTheme(Prefs(context)){if(shown)PlaylistEditor(context,initial){shown=false}}}
        compose.onNodeWithContentDescription("Move Two earlier").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.waitUntil(10_000){store.read().single().entries.first().title=="Two"}
        compose.runOnIdle {assertEquals(0,store.read().single().index)}
        compose.onNodeWithText("1. Two").assertExists()
        compose.onNodeWithText("Done").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Order").assertDoesNotExist()
    }
    @Test @Config(qualifiers="w960dp-h720dp-land-television-mdpi")
    fun movingWhileSavingKeepsTheRemoteFocusOnTheSameEntry() {
        val raw=java.util.concurrent.atomic.AtomicReference<String?>(null)
        val blocking=java.util.concurrent.atomic.AtomicBoolean(false)
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        val store=SavedPlaylists({raw.get()},{value->
            if(blocking.get()){entered.countDown();check(release.await(10,java.util.concurrent.TimeUnit.SECONDS))}
            raw.set(value);true
        })
        assertTrue(store.save("Focus",(1..20).map {QueueMedia("content://videos/$it",listOf("One","Two","Three","Four").getOrNull(it-1) ?: "Clip $it")},2))
        val initial=store.read().single()
        compose.setContent {PlayerTheme(Prefs(context)){PlaylistEditor(context,initial,store=store){}}}
        val earlier=compose.onNodeWithContentDescription("Move Three earlier")
        earlier.performSemanticsAction(SemanticsActions.RequestFocus){it()};earlier.assertIsFocused()
        fun scroll()=compose.onNode(hasScrollToNodeAction()).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        val before=scroll()
        blocking.set(true)
        try {
            earlier.performSemanticsAction(SemanticsActions.OnClick){it()}
            compose.waitUntil(10_000){entered.count==0L}
            earlier.assertIsEnabled().assertIsFocused()
        }finally {blocking.set(false);release.countDown()}
        compose.waitUntil(10_000){compose.onAllNodesWithText("2. Three").fetchSemanticsNodes().isNotEmpty()}
        earlier.assertIsFocused()
        assertEquals(before,scroll(),0.01f)
        earlier.performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.waitUntil(10_000){compose.onAllNodesWithText("1. Three").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Move Three later").assertIsFocused()
        compose.runOnIdle {assertEquals(0,store.read().single().index)}
    }
    @Test @Config(qualifiers="w960dp-h720dp-land-television-mdpi")
    fun movingAcrossPagesRestoresRemoteFocusToTheMovedEntry() {
        val store=SavedPlaylists(context)
        assertTrue(store.save("Pages",(0..100).map {QueueMedia("content://videos/$it","Clip $it")}))
        val initial=store.read().single()
        compose.setContent {PlayerTheme(Prefs(context)){PlaylistEditor(context,initial){}}}
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Move Clip 99 later"))
        compose.onNodeWithContentDescription("Move Clip 99 later").performSemanticsAction(SemanticsActions.RequestFocus){it()}
        compose.onNodeWithContentDescription("Move Clip 99 later").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.waitUntil(10_000){compose.onAllNodesWithText("101. Clip 99").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Move Clip 99 earlier").assertIsFocused()
        compose.onNodeWithContentDescription("Move Clip 99 earlier").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.waitUntil(10_000){compose.onAllNodesWithText("100. Clip 99").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Move Clip 99 earlier").assertIsFocused()
    }
    @Test fun individualEntriesCanBeRemovedAndTheLastRemovalIsExplicitAndCancelable() {
        val store=SavedPlaylists(context)
        assertTrue(store.save("Editable",listOf(QueueMedia("content://videos/1","One"),QueueMedia("content://videos/2","Two")),1))
        val initial=store.read().single();var shown by mutableStateOf(true)
        compose.setContent {PlayerTheme(Prefs(context)){if(shown)PlaylistEditor(context,initial){shown=false}}}
        fun remove(title:String)=compose.onNodeWithContentDescription("Remove $title from playlist").performSemanticsAction(SemanticsActions.OnClick){it()}
        fun confirm()=compose.onNodeWithText("Remove").performSemanticsAction(SemanticsActions.OnClick){it()}
        remove("One");confirm()
        compose.waitUntil(10_000){compose.onAllNodesWithText("1. Two").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnIdle {assertEquals(0,store.read().single().index)}
        remove("Two")
        compose.onNodeWithText("This is the last entry. Removing it also removes this playlist. Its media file will stay on your device.").assertExists()
        compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertEquals("Two",store.read().single().entries.single().title)}
        remove("Two");confirm()
        compose.waitUntil(10_000){!shown}
        compose.runOnIdle {assertTrue(store.read().isEmpty())}
    }

    @Test fun thePlaylistPickerExplainsUnreadableStorageAndCanRetryWithoutOverwritingIt() {
        val store=SavedPlaylists(context)
        assertTrue(store.save("Watch next",listOf(QueueMedia("content://videos/1","One"))))
        val encrypted=context.getSharedPreferences(app.ember.player.storage.SecretVault.FILE,android.content.Context.MODE_PRIVATE)
        val key="player.playlists."+Prefs(context).currentProfile
        val original=encrypted.getString(key,null)!!
        encrypted.edit().putString(key,"%unreadable%").commit()
        compose.setContent {PlayerTheme(Prefs(context)){PlaylistPicker(context,listOf(QueueMedia("content://videos/2","Two"))){}}}
        val message=context.getString(app.ember.tv.R.string.ui_playlists_unreadable)
        compose.waitUntil(10_000){compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasSetTextAction()).performTextReplacement("New playlist")
        compose.onNodeWithText("Create playlist").assertIsNotEnabled()
        compose.runOnIdle {assertEquals("%unreadable%",encrypted.getString(key,null));encrypted.edit().putString(key,original).commit()}
        compose.onNodeWithText("Refresh").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.waitUntil(10_000){compose.onAllNodesWithText("Watch next").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.runOnIdle {assertEquals(original,encrypted.getString(key,null))}
    }

}
