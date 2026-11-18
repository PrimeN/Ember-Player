package app.ember.player

import android.app.Application
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.ember.tv.data.Prefs
import app.ember.tv.player.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class,qualifiers="w960dp-h540dp-land-mdpi")
class PlayerLibraryReturnUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun pageAndScrollSurviveProgressPrivacyAndDestinationRefreshes() {
        PlayerUiStoreFixture(RuntimeEnvironment.getApplication()).use {
            val activity=Robolectric.buildActivity(MediaFilesActivity::class.java).get()
            val values=(0 until 231).map {PlayerLibraryMedia(QueueMedia("content://media/video/$it","Clip $it"))}
            val reads=java.util.concurrent.atomic.AtomicInteger()
            val progressReads=java.util.concurrent.atomic.AtomicInteger()
            val positions=java.util.concurrent.atomic.AtomicReference(emptyMap<String,PositionStore.SavedPosition>())
            val repository=PlayerLibraryRepository({MediaLibrary.revision.get()},{_,_->reads.incrementAndGet();PlayerLibraryCatalog(values)},{emptyList()},{progressReads.incrementAndGet();positions.get()},{emptyList()})
            val revision=mutableIntStateOf(0);val shown=mutableStateOf(true)
            val restoration=StateRestorationTester(compose)
            restoration.setContent {PlayerTheme(Prefs(LocalContext.current)) {
                val holder=rememberSaveableStateHolder()
                if(shown.value)holder.SaveableStateProvider("videos") {Row {
                    PlayerVideos(activity,revision.intValue,"",false,false,PlayerLibraryFilter.ALL,PlayerLibrarySort.NAME,{},null,null,null,{},library=repository)
                }}
            }}
            compose.waitUntil(10_000){repository.peek("")?.entries?.size==231}
            compose.onNodeWithTag("library-media").performScrollToNode(hasText("Next page"))
            compose.onNodeWithText("Next page").performSemanticsAction(SemanticsActions.OnClick){it()}
            compose.onNodeWithTag("library-media").performScrollToNode(hasText("Clip 150"))
            fun scroll()=compose.onNodeWithTag("library-media").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            val before=scroll()
            for(version in 1..3) {
                compose.runOnIdle {
                    positions.set(mapOf(values[150].media.key to PositionStore.SavedPosition(60_000,100_000,version.toLong())))
                    revision.intValue=version
                }
                compose.waitForIdle()
                try {compose.waitUntil(10_000){progressReads.get()>=version+1 && repository.peek("")?.continuing?.singleOrNull()?.lastPlayed==version.toLong()}}
                catch(e:ComposeTimeoutException) {throw AssertionError("Version $version; reads $progressReads; continuing ${repository.peek("")?.continuing}; index reads $reads",e)}
                compose.onNodeWithText("Clip 150").assertIsDisplayed()
                compose.onNodeWithTag("library-loading").assertDoesNotExist()
                assertEquals(before,scroll(),0.01f)
            }
            compose.runOnIdle {shown.value=false}
            compose.runOnIdle {shown.value=true}
            compose.onNodeWithText("Clip 150").assertIsDisplayed()
            compose.onNodeWithTag("library-loading").assertDoesNotExist()
            assertEquals(before,scroll(),0.01f)
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Clip 150").assertIsDisplayed()
            assertEquals(before,scroll(),0.01f)
            compose.runOnIdle {
                assertEquals(1,reads.get())
                MediaLibraryScanner.state.value=MediaLibraryScanner.state.value.copy(active=true,scanned=50)
            }
            compose.onNodeWithText("Clip 150").assertIsDisplayed()
            assertEquals(before,scroll(),0.01f)
            compose.runOnIdle {
                assertEquals(1,reads.get())
                MediaLibrary.revision.incrementAndGet()
                MediaLibraryScanner.state.value=MediaLibraryScanner.state.value.let {it.copy(active=false,revision=it.revision+1,result=ScanResult.COMPLETE)}
            }
            compose.waitUntil(10_000){reads.get()==2}
            compose.onNodeWithText("Clip 150").assertIsDisplayed()
            assertEquals(before,scroll(),0.01f)
            compose.onNodeWithTag("library-loading").assertDoesNotExist()
        }
    }
}
