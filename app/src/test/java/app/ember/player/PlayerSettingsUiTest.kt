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
@Config(sdk=[35,36],application=Application::class,qualifiers="w960dp-h540dp-land-mdpi")
class PlayerSettingsUiTest {
    @get:Rule val compose=createAndroidComposeRule<PlayerSettingsActivity>()
    private fun category(label:String){compose.onNodeWithTag("settingsCategories").performScrollToNode(hasText(label));compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick){it()}}
    @Test fun controlStylePickerPersistsTheSelectedPlayerInterface() {
        category("Playback")
        compose.onNodeWithText("Controls · Glass").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Streaming").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertEquals("streaming",Prefs(RuntimeEnvironment.getApplication()).playbackConfig.playerInterface)}
        compose.onNodeWithText("Controls · Streaming").assertExists()
    }
    @Test fun pictureEnhancementHasAPersistentMasterOffSwitch() {
        category("Picture & HDR")
        compose.onNodeWithTag("settingsContent").performScrollToNode(hasText("Picture enhancement") and hasClickAction())
        compose.onNode(hasText("Picture enhancement") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertEquals(false,Prefs(RuntimeEnvironment.getApplication()).playbackConfig.picture.enabled)}
        compose.onNode(hasText("Picture enhancement") and hasClickAction()).assertExists()
    }

    @Test fun hdrOutputChoicesPersistWithoutChangingTheDefaultEngine() {
        category("Picture & HDR")
        compose.onNodeWithTag("settingsContent").performScrollToNode(hasText("HDR to SDR output") and hasClickAction())
        compose.onNode(hasText("HDR to SDR output") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithTag("settingsContent").performScrollToNode(hasText("Tone mapping · BT.2390"))
        compose.onNodeWithText("Tone mapping · BT.2390").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Reinhard").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {
            val p=Prefs(RuntimeEnvironment.getApplication()).playbackConfig
            assertEquals(true,p.hdrOutput.sdr);assertEquals("reinhard",p.hdrOutput.tone);assertEquals("auto",p.engine)
        }
    }
    @Test fun usersCanNameAndSaveAPictureProfileFromSettings() {
        category("Picture & HDR")
        compose.onNodeWithTag("settingsContent").performScrollToNode(hasText("Profile name",substring=true))
        compose.onNodeWithText("Profile name",substring=true).performTouchInput {click()}
        compose.onNode(hasSetTextAction()).performTextInput("Movie night")
        compose.onNodeWithTag("settingsContent").performScrollToNode(hasText("Save current picture settings") and hasClickAction())
        compose.onNodeWithText("Save current picture settings").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertEquals("Movie night",Prefs(RuntimeEnvironment.getApplication()).playbackConfig.pictureProfiles.single().name)}
    }

    @Test fun customNetworkAndRendererChoicesPersistThroughSettings() {
        category("Network & cache")
        compose.onNodeWithTag("settingsContent").performScrollToNode(hasText("Custom buffering") and hasClickAction())
        compose.onNode(hasText("Custom buffering") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertEquals(true,Prefs(RuntimeEnvironment.getApplication()).playbackConfig.stream.custom)}
        compose.onNodeWithContentDescription("Back").performSemanticsAction(SemanticsActions.OnClick){it()}
        category("Picture & HDR")
        compose.onNodeWithTag("settingsContent").performScrollToNode(hasText("Compatibility renderer · Automatic"))
        compose.onNodeWithText("Compatibility renderer · Automatic").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Advanced GPU").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertEquals("gpu-next",Prefs(RuntimeEnvironment.getApplication()).playbackConfig.renderer)}
    }
    @Test fun experimentalExpansionIsOptionalAndDoesNotSelectANewEngineInSettings() {
        category("Picture & HDR")
        compose.onNodeWithTag("settingsContent").performScrollToNode(hasText("SDR to HDR · experimental") and hasClickAction())
        compose.onNode(hasText("SDR to HDR · experimental") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {
            val config=Prefs(RuntimeEnvironment.getApplication()).playbackConfig
            assertEquals(true,config.hdrOutput.expand);assertEquals(false,config.hdrOutput.sdr);assertEquals("auto",config.engine)
        }
    }

    @Test fun settingsSearchOpensTheRealAudioSectionAndBackRestoresResults() {
        compose.onNodeWithText("Search settings").performTextInput("Bluetooth")
        compose.onNodeWithText("Audio").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithTag("settingsContent").performScrollToNode(hasText("Bluetooth delay:",substring=true))
        compose.onNodeWithText("Bluetooth delay:",substring=true).assertExists()
        compose.onNodeWithContentDescription("Back").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithText("Audio").assertExists()
        compose.onNodeWithText("Bluetooth").assertExists()
    }
    @Test fun emptySearchDoesNotInventResultsAndPrivacyAndBackupRemainReachable() {
        compose.onNodeWithText("Search settings").performTextInput("not-a-setting")
        compose.onNodeWithText("No settings match",substring=true).assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("")
        category("Private media")
        compose.onNodeWithTag("settingsContent").performScrollToNode(hasText("Backups"))
        compose.onNodeWithText("Backups").assertExists()
    }

}
