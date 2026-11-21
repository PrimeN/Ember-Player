package app.ember.player

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.ember.tv.player.MediaFilesActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class,qualifiers="w960dp-h540dp-land-mdpi")
class PlayerHomeUiTest {
    @get:Rule val compose=createAndroidComposeRule<MediaFilesActivity>()
    @Test fun firstScreenHasFileSelectionAndStreamDialogCanClose() {
        compose.onNodeWithText("Ember Player").assertExists()
        compose.onNodeWithContentDescription("Open file").assertExists()
        compose.onNodeWithContentDescription("More options").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Open stream URL").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Open file").assertExists()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }
    @Test fun browserDestinationsKeepFolderAndNetworkToolsReachable() {
        compose.onNodeWithText("Files").performClick().assertIsSelected()
        compose.onNodeWithText("Add a media folder").assertExists()
        compose.onNodeWithText("USB drives").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Heading))
        compose.onNodeWithText("Search media library").assertExists()
        compose.onNodeWithText("Browse USB drives").assertDoesNotExist()
        compose.onNodeWithText("Folders").performClick()
        compose.onNodeWithText("Network").assertIsNotSelected().performClick()
        compose.onNodeWithText("Add network folder").assertExists()
        compose.onNodeWithText("Files").assertIsSelected().performClick()
        compose.onNodeWithText("Add a media folder").assertExists()
        compose.onNodeWithText("Folders").performClick()
        compose.onNodeWithText("Playlists").assertIsNotSelected().performClick()
        compose.onNodeWithText("New playlist from files").assertExists()
    }
    @Test fun gridChoicePersistsAndSearchCanBeOpenedAndClosed() {
        compose.onNodeWithContentDescription("List view").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {org.junit.Assert.assertFalse(app.ember.tv.data.Prefs(org.robolectric.RuntimeEnvironment.getApplication()).playerLibraryGrid)}
        compose.onNodeWithContentDescription("Grid view").assertExists()
        compose.onNodeWithText("Search").performClick().assertIsSelected()
        compose.onNodeWithText("Search videos").assertExists()
        compose.onNodeWithText("Home").assertIsNotSelected()
        compose.onNodeWithText("Search").performClick().assertIsNotSelected()
        compose.onNodeWithText("Search videos").assertDoesNotExist()
        compose.onNodeWithText("Home").assertIsSelected()
    }
    @Test @Config(qualifiers="w960dp-h540dp-land-television-mdpi")
    fun televisionStartsOnFileActionAndKeepsRemoteNavigationAvailable() {
        compose.onNodeWithContentDescription("Open file").assertIsFocused()
        compose.onNodeWithText("Folders").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Add a media folder").assertExists()
        compose.onNodeWithContentDescription("Open file").assertExists()
        compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithText("Videos").assertIsFocused()
    }

    @Test @Config(qualifiers="w320dp-h800dp-port-mdpi")
    fun compactHeaderShowsTheFullTitleAndOffersDirectFileSelection() {
        val result=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Ember Player",useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(result)}
        org.junit.Assert.assertFalse(result.single().hasVisualOverflow)
        compose.onNodeWithContentDescription("Open file").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithContentDescription("More options").assertIsDisplayed()
    }
    @Test @Config(qualifiers="w360dp-h800dp-port-mdpi")
    fun floatingDockKeepsNavigationReachableAndBrowseChoicesPersist() {
        for(label in listOf("Home","Files","Search","Settings")) {
            compose.onAllNodesWithText(label).assertCountEquals(1)
            compose.onNodeWithText(label).assertIsDisplayed().assertHasClickAction()
        }
        compose.onNodeWithText("Home").assertIsSelected()
        compose.onNodeWithText("All media").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Watched").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Date modified").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Name A–Z").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {
            val stored=app.ember.tv.data.Prefs(compose.activity)
            org.junit.Assert.assertEquals("watched",stored.playerLibraryFilter)
            org.junit.Assert.assertEquals("name",stored.playerLibrarySort)
        }
        compose.onNodeWithText("Files").performClick().assertIsSelected()
        compose.onNodeWithText("Add a media folder").assertExists()
        compose.onNodeWithText("Home").performClick().assertIsSelected()
        compose.onNode(hasText("Watched") and hasClickAction()).assertExists()
    }

}
