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
@Config(sdk=[35],application=Application::class,qualifiers="w960dp-h540dp-land-mdpi")
class PlayerHomeUiTest {
    @get:Rule val compose=createAndroidComposeRule<MediaFilesActivity>()
    @Test fun firstScreenHasFileSelectionAndStreamDialogCanClose() {
        compose.onNodeWithText("Ember Player").assertExists()
        compose.onNodeWithText("Open file").assertExists()
        compose.onNodeWithContentDescription("More options").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Open stream URL").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Open file").assertExists()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }
    @Test fun browserDestinationsKeepFolderAndNetworkToolsReachable() {
        compose.onNodeWithText("Folders").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Add a media folder").assertExists()
        compose.onNodeWithText("Network").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Add network folder").assertExists()
        compose.onNodeWithContentDescription("More options").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Playlists").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("New playlist from files").assertExists()
    }
    @Test fun gridChoicePersistsAndSearchCanBeOpenedAndClosed() {
        compose.onNodeWithContentDescription("List view").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {org.junit.Assert.assertFalse(app.ember.tv.data.Prefs(org.robolectric.RuntimeEnvironment.getApplication()).playerLibraryGrid)}
        compose.onNodeWithContentDescription("Grid view").assertExists()
        compose.onNodeWithContentDescription("Search media").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Search videos").assertExists()
        compose.onNodeWithContentDescription("Search media").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Search videos").assertDoesNotExist()
    }
    @Test @Config(qualifiers="w960dp-h540dp-land-television-mdpi")
    fun televisionStartsOnFileActionAndKeepsRemoteNavigationAvailable() {
        compose.onNodeWithText("Open file").assertIsFocused()
        compose.onNodeWithText("Folders").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Add a media folder").assertExists()
        compose.onNodeWithText("Open file").assertExists()
        compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithText("Videos").assertIsFocused()
    }

}
