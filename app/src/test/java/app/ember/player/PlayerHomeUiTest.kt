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
    @Test fun firstScreenFocusesFileSelectionAndStreamDialogCanClose() {
        compose.onNodeWithText("Ember Player").assertExists()
        compose.onNodeWithText("Choose files").assertIsFocused()
        compose.onNodeWithText("Open stream URL").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Choose files").assertExists()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }
}
