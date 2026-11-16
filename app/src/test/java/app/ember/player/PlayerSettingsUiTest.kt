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

    @Test fun hdrOutputChoicesPersistWithoutChangingTheDefaultEngine() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("HDR to SDR output") and hasClickAction())
        compose.onNode(hasText("HDR to SDR output") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Tone mapping · BT.2390"))
        compose.onNodeWithText("Tone mapping · BT.2390").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Reinhard").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {
            val p=Prefs(RuntimeEnvironment.getApplication()).playbackConfig
            assertEquals(true,p.hdrOutput.sdr);assertEquals("reinhard",p.hdrOutput.tone);assertEquals("auto",p.engine)
        }
    }
    @Test fun usersCanNameAndSaveAPictureProfileFromSettings() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Profile name",substring=true))
        compose.onNodeWithText("Profile name",substring=true).performTouchInput {click()}
        compose.onNode(hasSetTextAction()).performTextInput("Movie night")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Save current picture settings") and hasClickAction())
        compose.onNodeWithText("Save current picture settings").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertEquals("Movie night",Prefs(RuntimeEnvironment.getApplication()).playbackConfig.pictureProfiles.single().name)}
    }

    @Test fun customNetworkAndRendererChoicesPersistThroughSettings() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Custom buffering") and hasClickAction())
        compose.onNode(hasText("Custom buffering") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertEquals(true,Prefs(RuntimeEnvironment.getApplication()).playbackConfig.stream.custom)}
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Compatibility renderer · Automatic"))
        compose.onNodeWithText("Compatibility renderer · Automatic").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Advanced GPU").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertEquals("gpu-next",Prefs(RuntimeEnvironment.getApplication()).playbackConfig.renderer)}
    }
    @Test fun experimentalExpansionIsOptionalAndDoesNotSelectANewEngineInSettings() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("SDR to HDR · experimental") and hasClickAction())
        compose.onNode(hasText("SDR to HDR · experimental") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {
            val config=Prefs(RuntimeEnvironment.getApplication()).playbackConfig
            assertEquals(true,config.hdrOutput.expand);assertEquals(false,config.hdrOutput.sdr);assertEquals("auto",config.engine)
        }
    }

}
