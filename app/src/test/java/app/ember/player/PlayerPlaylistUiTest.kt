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
}
