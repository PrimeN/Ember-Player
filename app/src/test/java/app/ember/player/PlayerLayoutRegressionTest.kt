package app.ember.player

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.ember.tv.data.Prefs
import app.ember.tv.player.QueueMedia
import app.ember.tv.player.BrowserPreview
import app.ember.tv.player.PlayerActionMenu
import app.ember.tv.player.PlayerDialogAction
import app.ember.tv.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w960dp-h540dp-land-television-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerLayoutRegressionTest {
    @get:Rule val compose=createComposeRule()
    private fun SemanticsNodeInteraction.layout():TextLayoutResult {
        val result=mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(result)}
        return result.single()
    }
    @Test fun longMenuWarningsWrapCompletelyEvenWithShortActions() {
        val message="The selected audio cannot be copied into this clip. You can try exporting without audio. The original video stays unchanged. ".repeat(3)
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {
            PlayerActionMenu("Clip",listOf(PlayerDialogAction("OK"){}),message,{})
        }}
        val node=compose.onNodeWithText(message,useUnmergedTree=true)
        val text=node.layout()
        assertTrue(text.lineCount>2)
        assertFalse(text.hasVisualOverflow)
        assertEquals(message.length,text.getLineEnd(text.lineCount-1))
        assertTrue(text.size.width>=400)
    }
    @Test @Config(qualifiers="w360dp-h800dp-port-mdpi")
    fun phoneWarningsFitTheWindowWithoutDroppingTheirLastLine() {
        val message="This action clears the saved bookmarks in this profile. The original media, other profiles and saved track choices will be kept. ".repeat(2)
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {
            PlayerActionMenu("Clear bookmarks",listOf(PlayerDialogAction("Clear"){}),message,{})
        }}
        val text=compose.onNodeWithText(message,useUnmergedTree=true).layout()
        assertTrue(text.lineCount>2)
        assertFalse(text.hasVisualOverflow)
        assertEquals(message.length,text.getLineEnd(text.lineCount-1))
        assertTrue(text.size.width in 200..312)
    }
    @Test fun televisionIconButtonsHaveOneAccessibleName() {
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {
            PlayerIconAction("Settings",androidx.compose.material.icons.Icons.Outlined.FilterList){}
        }}
        assertEquals(listOf("Settings"),compose.onNodeWithContentDescription("Settings").fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
    }
    @Test @Config(qualifiers="w320dp-h800dp-port-mdpi")
    fun compactPhoneHeadingFitsWithEnlargedTextAndKeepsItsActions() {
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,1.5f)) {
                PlayerHeading("Ember Player",Modifier.padding(horizontal=16.dp),compact=true) {
                    listOf("Search","Settings","More").forEach {name->PlayerIconAction(name,Icons.Outlined.FilterList){}}
                }
            }
        }}
        val title=compose.onNodeWithText("Ember Player",useUnmergedTree=true).layout()
        assertFalse(title.hasVisualOverflow)
        listOf("Search","Settings","More").forEach {compose.onNodeWithContentDescription(it).assertIsDisplayed()}
    }
    @Test fun neverPlayedLibraryCardsShowTheDurationFromTheirPreview() {
        val context=org.robolectric.RuntimeEnvironment.getApplication()
        val uri=android.net.Uri.parse("content://test/duration.mp3")
        app.ember.tv.player.DeviceThumbnails.clear()
        org.robolectric.shadows.ShadowMediaMetadataRetriever.addMetadata(org.robolectric.shadows.util.DataSource.toDataSource(context,uri),android.media.MediaMetadataRetriever.METADATA_KEY_DURATION,"125000")
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {
            PlayerMediaCard(PlayerLibraryMedia(QueueMedia(uri.toString(),"Unplayed.mp3")),Modifier.width(180.dp),0,portrait=true){}
        }}
        compose.waitUntil(5000){compose.onAllNodesWithText("2:05",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun chipsAndSwitchesHaveFullTargetsAndMixedRowsCenterTheirItems() {
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {
            ButtonRow(Modifier.width(900.dp)) {
                Chip("Option",modifier=Modifier.testTag("chip")){}
                SwitchChip("On",true,Modifier.testTag("switch")){}
                PlayerAction("Action",modifier=Modifier.testTag("action")){}
            }
        }}
        val controls=listOf("chip","switch","action").map {compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot}
        controls.forEach {assertTrue(it.height>=48f)}
        assertEquals(controls[0].center.y,controls[1].center.y,.5f)
        assertEquals(controls[0].center.y,controls[2].center.y,.5f)
    }
    @Test fun longSwitchLabelsWrapAndEllipsizeInsteadOfClippingMidLetter() {
        val label="Save recent activity and resume positions across all of the saved activity profiles"
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {SwitchChip(label,true,Modifier.width(280.dp)) {}}}
        val text=compose.onNodeWithText(label,useUnmergedTree=true).layout()
        assertEquals(2,text.lineCount)
        assertTrue(text.isLineEllipsized(1))
    }
    @Test fun fileNamesStayAlignedWhenThePreviewArrives() {
        var preview by mutableStateOf(BrowserPreview())
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {Column(Modifier.width(500.dp)) {
            PlayerFileRow("Folder",true){}
            PlayerFileRow("Without preview",false){}
            PlayerFileRow("Preview",false,preview=preview){}
        }}}
        fun left(name:String)=compose.onNodeWithText(name,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.left
        val start=left("Folder")
        assertEquals(start,left("Without preview"),.5f)
        assertEquals(start,left("Preview"),.5f)
        compose.runOnIdle {preview=BrowserPreview(Bitmap.createBitmap(80,45,Bitmap.Config.ARGB_8888),1000)}
        assertEquals(start,left("Preview"),.5f)
    }
    @Test fun textFieldPlaceholderAndTypingUseTheSameLineMetrics() {
        var value by mutableStateOf("")
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {
            TvTextField(value,{value=it},Modifier.width(400.dp),placeholder="Sample")
        }}
        val placeholder=compose.onNodeWithText("Sample",useUnmergedTree=true).layout()
        compose.runOnIdle {value="Sample"}
        val input=compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).layout()
        assertEquals(placeholder.layoutInput.style.lineHeight,input.layoutInput.style.lineHeight)
        assertEquals(placeholder.layoutInput.style.fontSize,input.layoutInput.style.fontSize)
        assertEquals(placeholder.getLineBaseline(0),input.getLineBaseline(0),.5f)
    }
    @Test fun portraitCardsReserveTheDurationLineBeforeMetadataIsKnown() {
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {Row {
            PlayerMediaCard(PlayerLibraryMedia(QueueMedia("content://test/one","One"),duration=60_000),Modifier.width(180.dp).testTag("known"),0,portrait=true){}
            PlayerMediaCard(PlayerLibraryMedia(QueueMedia("content://test/two","Two")),Modifier.width(180.dp).testTag("unknown"),0,portrait=true){}
        }}}
        val known=compose.onNodeWithTag("known").fetchSemanticsNode().boundsInRoot
        val unknown=compose.onNodeWithTag("unknown").fetchSemanticsNode().boundsInRoot
        assertEquals(known.height,unknown.height,.5f)
    }
    @Test fun libraryUtilitiesSpeakTheirVisibleLabelOnlyOnce() {
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {
            LibraryUtility("Unwatched",Icons.Outlined.FilterList,Modifier.width(300.dp),expanded=true){}
        }}
        compose.onNodeWithText("Unwatched").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }
    @Test fun remotePagesThroughTheLicenseAndCanReachCloseAtTheEnd() {
        var closed=false
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {
            PlayerLicenseDialog("License",(1..100).joinToString("\n"){"Paragraph $it of the license."}){closed=true}
        }}
        val body=compose.onNodeWithTag("license-content")
        body.assertIsFocused()
        fun range()=body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(0f,range().value(),.1f)
        body.performKeyInput {keyDown(Key.DirectionDown);keyUp(Key.DirectionDown)}
        compose.waitUntil {range().value()>0f}
        val page=range().value()
        val height=body.fetchSemanticsNode().boundsInRoot.height
        assertTrue(page>=height*.8f&&page<=height)
        body.performSemanticsAction(SemanticsActions.ScrollBy){it(0f,100_000f)}
        compose.waitUntil {range().value()>=range().maxValue()}
        body.performKeyInput {keyDown(Key.DirectionDown);keyUp(Key.DirectionDown)}
        compose.onNodeWithText("Close").assertIsFocused().performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertTrue(closed)}
    }
}
