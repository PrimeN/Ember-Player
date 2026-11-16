package app.ember.player

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.ember.tv.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w960dp-h540dp-land-mdpi")
class PlayerSettingsUiTest {
    @get:Rule val compose=createAndroidComposeRule<PlayerSettingsActivity>()
    @Test fun controlStylePickerPersistsTheSelectedPlayerInterface() {
        compose.onNodeWithText("Controls · Glass").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Streaming").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertEquals("streaming",Prefs(RuntimeEnvironment.getApplication()).playbackConfig.playerInterface)}
        compose.onNodeWithText("Controls · Streaming").assertExists()
    }
    @Test fun pictureEnhancementHasAPersistentMasterOffSwitch() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Picture enhancement") and hasClickAction())
        compose.onNode(hasText("Picture enhancement") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertEquals(false,Prefs(RuntimeEnvironment.getApplication()).playbackConfig.picture.enabled)}
        compose.onNode(hasText("Picture enhancement") and hasClickAction()).assertExists()
    }

}
