package app.ember.player

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.ember.tv.data.Prefs
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class,qualifiers="w540dp-h900dp-port-mdpi")
class PrivatePinActionsUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var storage:PlayerUiStoreFixture
    @Before fun setup(){storage=PlayerUiStoreFixture(context);PrivateMedia.configure("123456")}
    @After fun cleanup(){storage.close()}
    private fun click(label:String)=compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick){it()}
    @Test fun changingThePinShowsAndChecksTheCurrentPinBeforeSaving() {
        val original=PrivateMedia.state.value.pin
        var completed by androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {PlayerTheme(Prefs(context)){if(!completed)PrivatePinPrompt({},{completed=true},createNew=true)}}
        compose.onNodeWithContentDescription("Current PIN").assertExists()
        fun fill(current:String) {
            val fields=compose.onAllNodes(hasSetTextAction())
            fields[0].performTextReplacement(current);fields[1].performTextReplacement("654321");fields[2].performTextReplacement("654321")
        }
        fill("000000");click("Save PIN")
        compose.waitUntil(10_000){compose.onAllNodesWithText("Incorrect PIN").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnIdle {assertEquals(original,PrivateMedia.state.value.pin);assertFalse(completed)}
        fill("123456");click("Save PIN")
        compose.waitUntil(10_000){completed}
        compose.runOnIdle {assertTrue(PrivatePin.verify("654321",PrivateMedia.state.value.pin))}
    }
    @Test fun removingProtectionCannotSucceedWithAnEmptyOrWrongCurrentPin() {
        compose.setContent {PlayerTheme(Prefs(context)){Column {PrivacySettings()}}}
        click("Remove PIN protection")
        compose.onNodeWithContentDescription("Current PIN").assertExists()
        click("Remove protection")
        compose.waitUntil(10_000){compose.onAllNodesWithText("Incorrect PIN").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnIdle {assertTrue(PrivateMedia.enabled)}
        compose.onNode(hasSetTextAction()).performTextReplacement("123456")
        click("Remove protection")
        compose.waitUntil(10_000){!PrivateMedia.enabled}
        compose.onNodeWithText("Create PIN").assertExists()
    }
}
