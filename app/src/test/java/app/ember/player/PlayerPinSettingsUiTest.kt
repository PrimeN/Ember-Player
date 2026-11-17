package app.ember.player

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import app.ember.player.storage.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class,qualifiers="w960dp-h540dp-land-mdpi")
class PlayerPinSettingsUiTest {
    @get:Rule val compose=createAndroidComposeRule<PlayerSettingsActivity>()
    private val context:Context get()=RuntimeEnvironment.getApplication()
    @Before fun setup() {
        val vault=SecretVault(context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE),object:SecretCipher {
            override fun encrypt(name:String,value:ByteArray)=value.copyOf()
            override fun decrypt(name:String,blob:ByteArray)=blob.copyOf()
        })
        TokenStore::class.java.getDeclaredField("instance").apply {isAccessible=true;set(null,vault)}
        PrivateMedia.initialize(context);PrivateMedia.reload(context)
    }
    @After fun cleanup() {
        context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("playerSecurity",Context.MODE_PRIVATE).edit().clear().commit()
        PrivateMedia.reload(context)
        TokenStore::class.java.getDeclaredField("instance").apply {isAccessible=true;set(null,null)}
    }
    private fun click(label:String)=compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick){it()}
    private fun confirmPin(pin:String,button:String) {
        val inputs=compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
        inputs[0].performTextInput(pin);inputs[1].performTextInput(pin)
        compose.onNode(hasText(button) and hasAnyAncestor(isDialog())).performSemanticsAction(SemanticsActions.OnClick){it()}
    }
    @Test fun pinSearchCanCreateChangeAndRemoveProtectionWithoutLosingPrivateEntries() {
        compose.onNodeWithText("Search settings").performTextInput("PIN")
        click("Private media")
        compose.onNodeWithTag("settingsContent").performScrollToNode(hasText("Create PIN") and hasClickAction())
        click("Create PIN");confirmPin("123456","Create PIN")
        compose.waitUntil(10_000){PrivateMedia.enabled&&PrivateMedia.unlocked.value}
        compose.runOnIdle {PrivateMedia.add(PrivateItem("content://private.example/video","Keep this item"))}
        val previousPin=PrivateMedia.state.value.pin
        click("Change PIN");confirmPin("654321","Save PIN")
        compose.waitUntil(10_000){PrivateMedia.state.value.pin!=previousPin}
        compose.runOnIdle {
            assertTrue(PrivateMedia.hidden("content://private.example/video"))
            assertTrue(PrivatePin.verify("654321",PrivateMedia.state.value.pin))
            assertFalse(PrivatePin.verify("123456",PrivateMedia.state.value.pin))
        }
        click("Remove PIN protection")
        compose.onNodeWithText("Make private media visible?").assertExists()
        compose.runOnIdle {assertTrue(PrivateMedia.enabled)}
        click("Cancel")
        compose.runOnIdle {assertTrue(PrivateMedia.hidden("content://private.example/video"))}
        click("Remove PIN protection");click("Remove protection")
        compose.waitUntil(10_000){!PrivateMedia.enabled}
        compose.runOnIdle {assertFalse(PrivateMedia.hidden("content://private.example/video"))}
        compose.onNodeWithText("Create PIN").assertExists()
    }
}
