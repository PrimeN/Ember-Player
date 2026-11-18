package app.ember.player

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ProviderInfo
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import app.ember.tv.R
import app.ember.tv.data.Prefs
import app.ember.tv.player.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class,qualifiers="w960dp-h720dp-land-mdpi")
class DeviceFoldersUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var storage:PlayerUiStoreFixture
    @Before fun setup() {
        storage=PlayerUiStoreFixture(compose.activity)
        val provider=DeviceTestProvider().apply {attachInfo(compose.activity,ProviderInfo().apply {
            authority="device.files";exported=true;grantUriPermissions=true
            readPermission="android.permission.MANAGE_DOCUMENTS";writePermission="android.permission.MANAGE_DOCUMENTS"
        })}
        ShadowContentResolver.registerProviderInternal("device.files",provider)
    }
    @After fun cleanup(){storage.close()}
    @Test fun theFolderLimitHasAVisibleExplanationAndDoesNotKeepAPermission() {
        val store=DeviceFolders(compose.activity)
        for(i in 1..16)assertTrue(store.add(DeviceFolder("content://device.files/tree/$i","Folder $i")))
        compose.setContent {PlayerTheme(Prefs(compose.activity)){Column(Modifier.verticalScroll(rememberScrollState())) {DeviceFilesPanel(compose.activity)}}}
        compose.onNodeWithText("Add a media folder").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {
            val shadow=shadowOf(compose.activity)
            val picker=shadow.nextStartedActivityForResult
            assertNotNull(picker)
            shadow.receiveResult(picker.intent,Activity.RESULT_OK,Intent().setData(Uri.parse("content://device.files/tree/root")))
        }
        val message=compose.activity.resources.getQuantityString(R.plurals.ui_saved_folder_limit,16,16)
        compose.waitUntil(10_000){compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.runOnIdle {assertEquals(16,store.read().size);assertTrue(compose.activity.contentResolver.persistedUriPermissions.isEmpty())}
    }
}
