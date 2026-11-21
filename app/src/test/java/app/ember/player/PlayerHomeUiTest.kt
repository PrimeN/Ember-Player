package app.ember.player

import android.app.Application
import androidx.compose.ui.input.key.Key
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
        compose.onNode(hasText("Home") and !hasClickAction()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Open file").assertExists()
        compose.onNodeWithContentDescription("More options").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Open stream URL").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Open file").assertExists()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }
    @Test fun browserDestinationsKeepFolderAndNetworkToolsReachable() {
        compose.onNode(hasText("Files") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Add a media folder").assertExists()
        compose.onNodeWithText("USB drives").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Heading))
        compose.onNodeWithText("Search media library").assertExists()
        compose.onNodeWithText("Browse USB drives").assertDoesNotExist()
        compose.onNodeWithText("Folders").performClick()
        compose.onNodeWithText("Network").assertIsNotSelected().performClick()
        compose.onNodeWithText("Add network folder").assertExists()
        compose.onNode(hasText("Files") and hasClickAction()).assertIsSelected().performClick()
        compose.onNodeWithText("Add a media folder").assertExists()
        compose.onNodeWithText("Folders").performClick()
        compose.onNodeWithText("Playlists").assertIsNotSelected().performClick()
        compose.onNodeWithText("New playlist from files").assertExists()
    }
    @Test @Config(qualifiers="sw360dp-w800dp-h360dp-land-mdpi")
    fun landscapePhoneKeepsTheSameHomeDockAndSavedLibraryTools() {
        compose.onNode(hasText("Home") and hasClickAction()).assertIsDisplayed().assertIsSelected()
        val more = compose.onNodeWithContentDescription("More options")
        more.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithContentDescription("List view").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { org.junit.Assert.assertFalse(app.ember.tv.data.Prefs(compose.activity).playerLibraryGrid) }
        val search = compose.onNode(hasText("Search") and hasClickAction())
        search.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Search videos").assertIsDisplayed()
        search.assertIsSelected().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Search videos").assertDoesNotExist()
    }
    @Test @Config(qualifiers="w960dp-h540dp-land-television-mdpi")
    fun televisionHomeShortcutsKeepRemoteNavigationAvailable() {
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("Browse library").assertIsFocused()
        compose.onNodeWithContentDescription("Open file").assertDoesNotExist()
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        val more = compose.onNodeWithContentDescription("More options")
        more.performSemanticsAction(SemanticsActions.RequestFocus)
        more.assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        compose.onNodeWithContentDescription("Open file").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        compose.onNodeWithContentDescription("Settings").assertHasClickAction()
        compose.onNodeWithContentDescription("Show media · All media").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        compose.onNodeWithContentDescription("All media").assertIsSelected().assertIsFocused()
        compose.onNodeWithContentDescription("Close").performSemanticsAction(SemanticsActions.OnClick) { it() }
        more.assertIsFocused()
        val folders = compose.onNode(hasText("Folders") and hasClickAction())
        folders.performSemanticsAction(SemanticsActions.RequestFocus)
        folders.assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        val network = compose.onNode(hasText("Network") and hasClickAction())
        network.assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        compose.onNodeWithText("Add network folder").assertIsDisplayed()
        val browse = compose.onNodeWithContentDescription("Browse library")
        browse.assertIsFocused().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithContentDescription("Folders").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Add a media folder").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Home").assertIsDisplayed()
        browse.assertIsFocused()
    }

    @Test @Config(qualifiers="w320dp-h800dp-port-mdpi")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun compactHeaderShowsHomeAndOffersDirectFileSelection() {
        val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNode(hasText("Home") and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Heading), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
        org.junit.Assert.assertFalse("Home title should fit the header", result.single().hasVisualOverflow)
        compose.onNodeWithContentDescription("Open file").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithContentDescription("More options").assertIsDisplayed()
    }

    @Test @Config(qualifiers="w360dp-h800dp-port-mdpi")
    fun phoneDockAndToolbarKeepNavigationReachableAndBrowseChoicesPersist() {
        val home = compose.onNode(hasText("Home") and hasClickAction())
        home.assertIsDisplayed().assertIsSelected()
        compose.onNode(hasText("Settings") and hasClickAction()).assertIsDisplayed()
        val search = compose.onNode(hasText("Search") and hasClickAction())
        search.performSemanticsAction(SemanticsActions.OnClick) { it() }
        search.assertIsSelected()
        compose.onNodeWithText("Search videos").assertIsDisplayed()
        home.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Search videos").assertDoesNotExist()

        for (label in listOf("Show media", "Sort media", "Reload displayed media")) {
            compose.onNodeWithContentDescription(label).assertDoesNotExist()
        }
        val more = compose.onNodeWithContentDescription("More options")
        more.performClick()
        compose.onNodeWithContentDescription("Show media · All media").performClick()
        compose.onNodeWithContentDescription("Watched").performSemanticsAction(SemanticsActions.OnClick) { it() }
        more.performClick()
        compose.onNodeWithContentDescription("Sort media · Date modified").performClick()
        compose.onNodeWithContentDescription("Name A–Z").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            val stored = app.ember.tv.data.Prefs(compose.activity)
            org.junit.Assert.assertEquals("watched", stored.playerLibraryFilter)
            org.junit.Assert.assertEquals("name", stored.playerLibrarySort)
        }

        val files = compose.onNode(hasText("Files") and hasClickAction())
        files.performSemanticsAction(SemanticsActions.OnClick) { it() }
        files.assertIsSelected()
        compose.onNodeWithText("Add a media folder").assertExists()
        compose.onNodeWithText("Folders").performClick()
        compose.onNodeWithText("Network").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Add network folder").assertExists()
        home.performSemanticsAction(SemanticsActions.OnClick) { it() }
        home.assertIsSelected()
        more.performClick()
        compose.onNodeWithContentDescription("Sort media · Name A–Z").assertExists()
        compose.onNodeWithContentDescription("Show media · Watched").performClick()
        compose.onNodeWithContentDescription("Watched").assertIsSelected()
    }
    @Test @Config(qualifiers="sw600dp-w960dp-h720dp-land-mdpi")
    fun tabletDockAndLibraryShortcutsOpenExistingDestinations() {
        val home = compose.onNode(hasText("Home") and hasClickAction())
        home.assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Library shortcuts").assertIsDisplayed()
        compose.onNode(hasText("Network") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Add network folder").assertIsDisplayed()
        compose.onNode(hasText("Files") and hasClickAction()).assertIsSelected()
        home.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Library shortcuts").assertIsDisplayed()
        compose.onNode(hasText("Search") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Search videos").assertIsDisplayed()
    }
}
