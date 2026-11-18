package app.ember.player

import android.app.Application
import android.content.Context
import app.ember.player.storage.*
import app.ember.tv.data.Prefs
import app.ember.tv.player.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SavedListRecoveryTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private data class Case(val key:String,val good:String,val bad:String,val read:()->List<*>,val write:()->Boolean,val reset:()->Boolean)
    @Test fun resetsRecoverOnlyTheSelectedUnreadableListAndKeepFilesAndOtherData() {
        PlayerUiStoreFixture(context).use {
            val profile=Prefs(context).currentProfile;val tokens=TokenStore(context)
            val folders=DeviceFolders(context);val playlists=SavedPlaylists(context);val network=SavedNetworkFolders(context);val downloads=DownloadStore(context)
            val folder=DeviceFolder("content://docs/tree/root","Root")
            val playlist=SavedPlaylist("playlist","Keep",listOf(PlaylistEntry("content://videos/1","One")))
            val account=NetworkAccount(id="account",label="Keep",address="sftp://server/media",password="saved-password",privateKey="saved-private-key")
            val download=MediaDownload("11111111-1111-1111-1111-111111111111","https://example.test/keep.mkv","Keep.mkv")
            val file=DownloadStore.file(context,download).apply {parentFile!!.mkdirs();writeText("keep media")}
            val badFolders=Json.encodeToString(listOf(folder,folder.copy(uri="content://docs/tree/bad",name="")))
            val badPlaylists=Json.encodeToString(listOf(playlist,playlist.copy(id="bad",entries=listOf(PlaylistEntry("file:///invalid","Bad")))))
            val badNetwork=Json.encodeToString(listOf(account,account.copy(id="bad",address="invalid")))
            val badDownloads=Json.encodeToString(listOf(download,download.copy(id="22222222-2222-2222-2222-222222222222",state="invalid")))
            val cases=listOf(
                Case("player.folders",Json.encodeToString(listOf(folder)),badFolders,folders::readForDisplay,{folders.add(folder.copy(uri="content://docs/tree/new"))},folders::resetUnreadable),
                Case("player.playlists."+profile,Json.encodeToString(listOf(playlist)),badPlaylists,playlists::readForDisplay,{playlists.save("New",listOf(QueueMedia("content://videos/new","New")))},playlists::resetUnreadable),
                Case("media.network."+profile,Json.encodeToString(listOf(account)),badNetwork,network::read,{network.add(account.copy(id="new"))!=null},network::resetUnreadable),
                Case("player.downloads",Json.encodeToString(listOf(download)),badDownloads,downloads::readForDisplay,{runCatching {downloads.add("https://example.test/new.mkv","New.mkv")}.isSuccess},downloads::resetUnreadable),
            )
            cases.forEach {assertTrue(tokens.putEncrypted(it.key,it.good))}
            assertTrue(tokens.putEncrypted("unrelated","keep unrelated data"))
            val encrypted=context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE)
            val original=encrypted.all
            cases.forEach {assertFalse(it.reset())}
            assertEquals(original,encrypted.all)
            assertNull(shadowOf(context).nextStartedService)
            for(case in cases) {
                assertTrue(tokens.putEncrypted(case.key,case.bad))
                val before=encrypted.all
                try {case.read();fail("Expected invalid saved entry")}
                catch(_:IllegalArgumentException) {}
                assertFalse(case.write());assertEquals(before,encrypted.all)
                assertTrue(case.reset());assertTrue(case.read().isEmpty())
                assertEquals(before-case.key,encrypted.all-case.key)
                assertTrue(case.write());assertTrue(case.read().isNotEmpty())
                assertEquals("keep media",file.readText())
            }
            assertEquals("pause",shadowOf(context).nextStartedService?.action)
            assertNull(shadowOf(context).nextStartedService)
            // Decryption failure is also eligible, without decrypting or changing other collections.
            val target=cases.first()
            encrypted.edit().putString(target.key,"%unreadable%").commit()
            val damaged=encrypted.all
            assertTrue(target.reset());assertTrue(target.read().isEmpty())
            assertEquals(damaged-target.key,encrypted.all-target.key)
        }
    }
    @Test fun failedRecoveryWritesAndCancellationDoNotBecomeSuccessfulResets() {
        var raw="broken";var writes=0
        val store=SavedPlaylists({raw},{writes++;false})
        assertFalse(store.resetUnreadable());assertEquals(1,writes);assertEquals("broken",raw)
        raw="[]";writes=0
        assertFalse(store.resetUnreadable());assertEquals(0,writes)
        try {
            resetUnreadableList({throw kotlinx.coroutines.CancellationException()}){writes++;true}
            fail("Expected cancellation")
        }catch(_:kotlinx.coroutines.CancellationException) {}
        assertEquals(0,writes)
    }
}
