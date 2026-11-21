package app.ember.player

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.ember.tv.data.Prefs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w360dp-h720dp-port-mdpi")
class PhoneLibraryNavigationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun dockKeepsOneSelectionAndOpensSettings() {
        var openedSettings=false
        compose.setContent {
            PlayerTheme(Prefs(LocalContext.current)) {
                var destination by remember {mutableStateOf(PlayerLibraryDestination.VIDEOS)}
                var searching by remember {mutableStateOf(false)}
                LibraryDock(
                    destination,searching,
                    home={destination=PlayerLibraryDestination.VIDEOS;searching=false},
                    files={destination=PlayerLibraryDestination.FOLDERS;searching=false},
                    search={searching=!searching},
                    settings={openedSettings=true}
                )
            }
        }
        compose.onNodeWithText("Home").assertIsSelected()
        compose.onNodeWithText("Files").performClick().assertIsSelected()
        compose.onNodeWithText("Search").performClick().assertIsSelected()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected,true)).assertCountEquals(1)
        compose.onNodeWithText("Home").performClick().assertIsSelected()
        compose.onNodeWithText("Settings").performClick()
        compose.runOnIdle {assertTrue(openedSettings)}
    }

    @Test fun filesMenuExposesEveryFileSection() {
        compose.setContent {
            PlayerTheme(Prefs(LocalContext.current)) {
                var destination by remember {mutableStateOf(PlayerLibraryDestination.FOLDERS)}
                LibraryFilesNavigation(destination){destination=it}
            }
        }
        var current="Folders"
        for(label in listOf("Network","Playlists","Downloads","Private media","Folders")) {
            compose.onNodeWithText(current).performClick()
            compose.onNodeWithText(label).assertIsDisplayed().performClick()
            compose.onNodeWithText(label).assertIsDisplayed()
            current=label
        }
    }
}
