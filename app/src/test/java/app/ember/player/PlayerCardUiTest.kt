package app.ember.player

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.ember.tv.data.Prefs
import app.ember.tv.player.QueueMedia
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w360dp-h720dp-port-mdpi")
class PlayerCardUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun resumeCardKeepsItsTimeAndClickTargetWhenTextIsEnlarged() {
        val title="A long filename with several words and an episode number"
        val entry=PlayerLibraryMedia(QueueMedia("https://example.invalid/video",title),60_000,300_000)
        var opened=false
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {
            CompositionLocalProvider(LocalDensity provides Density(1f,1.8f)) {
                PlayerMediaCard(entry,Modifier.width(280.dp),0){opened=true}
            }
        }}
        compose.onNodeWithText("1:00 / 5:00",useUnmergedTree=true).assertExists()
        compose.onNodeWithText(title).performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertTrue(opened)}
    }
    @Test fun unwatchedMediaDoesNotShowInventedResumeProgress() {
        val entry=PlayerLibraryMedia(QueueMedia("https://example.invalid/unwatched","Unwatched file"))
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)){PlayerMediaCard(entry,Modifier.width(280.dp),0){}}}
        compose.onNodeWithText("Unwatched file").assertExists()
        compose.onAllNodesWithText("0:00",substring=true).assertCountEquals(0)
        assertFalse(entry.continuing)
    }
}
