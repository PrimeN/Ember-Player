package app.ember.player

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.ember.tv.data.Prefs
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w360dp-h800dp-port-mdpi")
class PlayerComponentsUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun folderRowsKeepTheirNamesAndAnnounceFolderAndSelectionState() {
        var opened=0
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)){Column {
            PlayerFileRow("Movies",true){opened++}
            PlayerFileRow("Episode.mkv",false,selected=true){}
            PlayerErrorText("Could not open this file.")
            PlayerStatusText("Library refreshed")
        }}}
        compose.onNodeWithContentDescription("Folder: Movies").performClick()
        compose.runOnIdle {assertEquals(1,opened)}
        compose.onNodeWithText("Episode.mkv").assertIsSelected()
        compose.onNodeWithText("Could not open this file.").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        compose.onNodeWithText("Library refreshed").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }
    @Test @Config(qualifiers="w960dp-h540dp-land-television-mdpi")
    fun destructiveConfirmationStartsOnCancelAndDoesNotActUntilConfirmed() {
        var shown by mutableStateOf(true);var deleted=0
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {
            if(shown)PlayerConfirmationDialog("Delete item?","The saved item will be removed.","Delete",{shown=false}){deleted++;shown=false}
        }}
        compose.onNodeWithText("Cancel").assertIsFocused().performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertEquals(0,deleted);shown=true}
        compose.onNodeWithText("Delete").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertEquals(1,deleted)}
        compose.onNodeWithText("Delete item?").assertDoesNotExist()
    }
    @Test fun downloadsUseReadableStatesAndDistinguishKnownAndUnknownProgress() {
        var item by mutableStateOf(MediaDownload("00000000-0000-0000-0000-000000000001","https://example.test/clip.mkv","Clip.mkv",state="downloading",bytes=524_288,total=1_048_576))
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)){DownloadRow(item,{},{},{})}}
        compose.onNodeWithText("Downloading",substring=true).assertExists()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(.5f,0f..1f))).assertExists()
        compose.onAllNodesWithText("· 0 MB",substring=true).assertCountEquals(0)
        compose.runOnIdle {item=item.copy(total=-1)}
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        compose.runOnIdle {item=item.copy(state="failed")}
        compose.onNodeWithText("Download failed",substring=true).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        compose.onNodeWithText("Resume").assertExists()
    }
    @Test @Config(qualifiers="w960dp-h540dp-land-television-mdpi")
    fun enlargedTvTextKeepsFolderRowsBoundedAndSpokenNamesComplete() {
        val name="A long folder name that should wrap without losing its accessible label"
        var opened=false
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {
            CompositionLocalProvider(LocalDensity provides Density(1f,1.8f)) {
                Column(Modifier.width(300.dp)){PlayerFileRow(name,true){opened=true}}
            }
        }}
        val row=compose.onNodeWithContentDescription("Folder: $name")
        val bounds=row.fetchSemanticsNode().boundsInRoot
        val text=compose.onNodeWithText(name,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(text.left>=bounds.left&&text.right<=bounds.right&&text.bottom<=bounds.bottom)
        row.performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertTrue(opened)}
    }
}
