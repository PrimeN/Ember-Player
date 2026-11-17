package app.ember.player

import android.app.Application
import android.content.Context
import app.ember.player.storage.SecretVault
import app.ember.player.storage.TokenStore
import app.ember.tv.data.Prefs
import app.ember.tv.player.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SavedFolderStorageTest {
    @Test fun unreadableEncryptedRecordsCannotBeMistakenForMissingCollections() {
        val context=RuntimeEnvironment.getApplication()
        PlayerUiStoreFixture(context).use {
            val tokens=TokenStore(context)
            val folders=DeviceFolders(context)
            val network=SavedNetworkFolders(context)
            val folder=DeviceFolder("content://provider/tree/videos","Videos")
            val account=NetworkAccount(id="saved",label="Server",address="sftp://server/media",password="keep-password")
            assertTrue(folders.add(folder));assertNotNull(network.add(account))
            val encrypted=context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE)
            val keys=listOf("player.folders","media.network."+Prefs(context).currentProfile)
            val original=keys.associateWith {encrypted.getString(it,null)!!}
            keys.forEach {encrypted.edit().putString(it,"%unreadable%").commit()}
            val damaged=encrypted.all
            assertFalse(folders.add(folder.copy(uri="content://provider/tree/new")))
            assertFalse(folders.remove(folder.uri))
            assertNull(network.add(account.copy(id="new")))
            assertNull(network.remove(account.id))
            assertNull(network.trust(account,"SHA256:"+java.util.Base64.getEncoder().encodeToString(ByteArray(32))))
            assertEquals(damaged,encrypted.all)
            original.forEach {(key,value)->encrypted.edit().putString(key,value).commit()}
            assertEquals(listOf(folder),folders.read());assertEquals(listOf(account),network.read())
            assertNotNull(network.add(account.copy(id="new")))
            assertTrue(folders.remove(folder.uri))
        }
    }
}
