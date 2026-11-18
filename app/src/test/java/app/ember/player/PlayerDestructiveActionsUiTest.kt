package app.ember.player

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.FileProvider
import app.ember.player.storage.TokenStore
import app.ember.tv.data.Prefs
import app.ember.tv.player.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class,qualifiers="w960dp-h720dp-land-mdpi")
class PlayerDestructiveActionsUiTest {
    @get:Rule val compose=createAndroidComposeRule<MediaFilesActivity>()
    private val context:Context get()=RuntimeEnvironment.getApplication()
    private lateinit var storage:PlayerUiStoreFixture
    @Before fun setup(){
        storage=PlayerUiStoreFixture(context)
        val provider=context.packageManager.resolveContentProvider(context.packageName+".downloads",PackageManager.GET_META_DATA)!!
        FileProvider().attachInfo(context,provider)
    }
    @After fun cleanup(){storage.close()}
    private fun click(text:String)=compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick){it()}
    @Test fun downloadsAppearOnEntryAndDeletionIsNotDispatchedWhenCanceled() {
        val store=DownloadStore(context);val item=store.add("https://example.test/clip.mkv","Clip.mkv")
        val file=DownloadStore.file(context,item).apply {parentFile!!.mkdirs();writeBytes(ByteArray(32768))}
        store.change(item.id){it.copy(state="complete",bytes=32768,total=32768)}
        click("Downloads")
        compose.waitUntil(10_000){compose.onAllNodesWithText("Clip.mkv").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Downloaded",substring=true).assertExists()
        compose.onNodeWithText("Manage downloads").assertDoesNotExist()
        click("Remove");compose.onNodeWithText("Delete download?").assertExists();click("Cancel")
        compose.runOnIdle {assertTrue(file.exists());assertEquals(item.id,store.read().single().id);assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)}
        click("Remove");click("Delete download")
        compose.runOnIdle {
            val intent=shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
            assertNotNull(intent);assertEquals("remove",intent.action);assertEquals(item.id,intent.getStringExtra("id"))
        }
    }
    @Test fun clearingRecentMediaRequiresConfirmationAndKeepsResumePositions() {
        val history=MediaHistory(context);val media=QueueMedia("https://example.test/clip.mkv","Recent clip")
        assertTrue(history.add(listOf(media)));PositionStore(context).put(media.key,60_000,100_000)
        fun clearMenu(){compose.onNodeWithContentDescription("More options").performSemanticsAction(SemanticsActions.OnClick){it()};compose.onNodeWithContentDescription("Clear recent media").performSemanticsAction(SemanticsActions.OnClick){it()}}
        clearMenu();click("Cancel")
        compose.runOnIdle {assertEquals(1,history.read().size)}
        clearMenu();click("Clear recent media")
        compose.waitUntil(10_000){history.read().isEmpty()}
        compose.runOnIdle {assertEquals(60_000L,PositionStore(context).get(media.key)!!.first)}
    }
    @Test fun removingANetworkFolderKeepsCredentialsUntilConfirmationAndPreservesOtherAccounts() {
        val first=NetworkAccount(label="Test folder",address="sftp://127.0.0.1:9/media",username="user",authentication="key",privateKey="test-private-key")
        val other=NetworkAccount(label="Keep folder",address="sftp://127.0.0.1:9/other",username="other",password="keep-password")
        val key="media.network."+Prefs(context).currentProfile
        assertTrue(TokenStore(context).putEncrypted(key,Json.encodeToString(listOf(first,other))))
        click("Network")
        compose.waitUntil(10_000){compose.onAllNodesWithText("Test folder").fetchSemanticsNodes().isNotEmpty()}
        click("Test folder");click("Remove saved folder");click("Cancel")
        compose.runOnIdle {assertEquals(listOf(first,other),Json.decodeFromString<List<NetworkAccount>>(TokenStore(context).get(key)!!))}
        click("Remove saved folder");click("Remove folder")
        compose.waitUntil(10_000){Json.decodeFromString<List<NetworkAccount>>(TokenStore(context).get(key)!!).size==1}
        compose.runOnIdle {assertEquals(listOf(other),Json.decodeFromString<List<NetworkAccount>>(TokenStore(context).get(key)!!))}
    }
    @Test fun unreadableConnectionsReportStorageFailureWithoutClaimingTheFolderWasRemoved() {
        val profile=Prefs(context).currentProfile
        val encrypted=context.getSharedPreferences(app.ember.player.storage.SecretVault.FILE,Context.MODE_PRIVATE)
        encrypted.edit().putString("media.network."+profile,"%unreadable%").commit()
        val previous=encrypted.all
        compose.runOnIdle {compose.activity.intent.putExtra("network.profile",profile).putExtra("network.account","saved")}
        click("Network")
        val message=context.getString(app.ember.tv.R.string.ui_could_not_update_encrypted_folder_storage)
        compose.waitUntil(10_000){compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(context.getString(app.ember.tv.R.string.ui_this_saved_folder_was_removed_add_it_again_to)).assertDoesNotExist()
        compose.runOnIdle {assertEquals(previous,encrypted.all)}
    }
    @Test fun unreadablePlaylistAndDownloadPanelsExplainFailureAndRecoverWithoutDataLoss() {
        val playlists=SavedPlaylists(context)
        assertTrue(playlists.save("Recovered playlist",listOf(QueueMedia("content://videos/1","One"))))
        val downloads=DownloadStore(context)
        downloads.add("https://example.test/recovered.mkv","Recovered.mkv")
        val encrypted=context.getSharedPreferences(app.ember.player.storage.SecretVault.FILE,Context.MODE_PRIVATE)
        val playlistKey="player.playlists."+Prefs(context).currentProfile
        val downloadKey="player.downloads"
        val playlistCopy=encrypted.getString(playlistKey,null)!!
        val downloadCopy=encrypted.getString(downloadKey,null)!!
        encrypted.edit().putString(playlistKey,"%unreadable%").putString(downloadKey,"%unreadable%").commit()
        click("Playlists")
        val playlistError=context.getString(app.ember.tv.R.string.ui_playlists_unreadable)
        compose.waitUntil(10_000){compose.onAllNodesWithText(playlistError).fetchSemanticsNodes().isNotEmpty()}
        compose.runOnIdle {assertEquals("%unreadable%",encrypted.getString(playlistKey,null));encrypted.edit().putString(playlistKey,playlistCopy).commit()}
        compose.onNodeWithContentDescription("More options").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription("Refresh library view").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.waitUntil(10_000){compose.onAllNodesWithText("Recovered playlist",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(playlistError).assertDoesNotExist()
        click("Downloads")
        val downloadError=context.getString(app.ember.tv.R.string.ui_downloads_unreadable)
        compose.waitUntil(10_000){compose.onAllNodesWithText(downloadError).fetchSemanticsNodes().isNotEmpty()}
        compose.runOnIdle {assertEquals("%unreadable%",encrypted.getString(downloadKey,null));encrypted.edit().putString(downloadKey,downloadCopy).commit()}
        compose.waitUntil(10_000){compose.onAllNodesWithText("Recovered.mkv").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(downloadError).assertDoesNotExist()
        compose.runOnIdle {assertEquals(playlistCopy,encrypted.getString(playlistKey,null));assertEquals(downloadCopy,encrypted.getString(downloadKey,null))}
    }
    @Test fun playlistOptionsHaveASpokenNameAndDeletionCanBeCanceled() {
        val store=SavedPlaylists(context)
        assertTrue(store.save("Test playlist",listOf(QueueMedia("https://example.test/clip.mkv","Clip"))))
        click("Playlists")
        compose.waitUntil(10_000){compose.onAllNodesWithContentDescription("More options for Test playlist").fetchSemanticsNodes().isNotEmpty()}
        fun deleteMenu(){compose.onNodeWithContentDescription("More options for Test playlist").performSemanticsAction(SemanticsActions.OnClick){it()};compose.onNodeWithContentDescription("Delete playlist").performSemanticsAction(SemanticsActions.OnClick){it()}}
        deleteMenu();click("Cancel")
        compose.runOnIdle {assertEquals("Test playlist",store.read().single().name)}
        deleteMenu();click("Delete")
        compose.waitUntil(10_000){store.read().isEmpty()}
    }
}
