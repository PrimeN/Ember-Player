package app.ember.player

import android.app.Application
import android.content.Context
import app.ember.player.storage.*
import app.ember.tv.data.Prefs
import app.ember.tv.player.QueueMedia
import app.ember.tv.player.SavedPlaylists
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class)
class SavedCollectionsStorageTest {
    private val context:Context get()=RuntimeEnvironment.getApplication()
    private val sealed get()=context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE)
    private var locked=false
    private fun vault()=SecretVault(sealed,object:SecretCipher {
        override fun encrypt(name:String,value:ByteArray)=value.copyOf()
        override fun decrypt(name:String,blob:ByteArray):ByteArray {check(!locked);return blob.copyOf()}
    })
    private fun useVault(value:SecretVault?) {TokenStore::class.java.getDeclaredField("instance").apply {isAccessible=true;set(null,value)}}
    @Before fun setup(){useVault(vault());PrivateMedia.initialize(context);PrivateMedia.reload(context)}
    @After fun cleanup(){useVault(null)}
    private fun refused(action:()->Unit) {
        try {action();fail("Unreadable storage must refuse writes")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}
    }
    @Test fun corruptDownloadMetadataCannotOverwriteTheRecordOrDeleteItsFiles() {
        val store=DownloadStore(context);val item=store.add("https://example.test/video.mkv")
        assertEquals("video.mkv",item.name)
        val file=DownloadStore.file(context,item).apply {parentFile!!.mkdirs();writeText("keep")}
        val good=sealed.getString("player.downloads",null)!!
        for(raw in listOf("not json"," ".repeat(1024*1024+1),Json.encodeToString(listOf(item,item.copy(id="bad"))))) {
            assertTrue(TokenStore(context).putEncrypted("player.downloads",raw))
            val previous=sealed.all.toMap()
            assertTrue(store.read().isEmpty())
            refused {store.add("https://example.test/new.webm")}
            refused {store.change(item.id){it.copy(state="paused")}}
            refused {store.remove(item.id)}
            assertEquals(previous,sealed.all);assertEquals("keep",file.readText())
        }
        sealed.edit().putString("player.downloads",good).commit()
        assertEquals(item.id,store.read().single().id)
        store.remove(item.id);assertFalse(file.exists())
    }
    @Test fun failedUnlockCannotReplaceExistingCollectionsAndCanBeRetriedAfterRecovery() {
        val downloads=DownloadStore(context);val item=downloads.add("https://example.test/video.webm","Custom.webm")
        assertEquals("Custom.webm",item.name)
        val playlists=SavedPlaylists(context);assertTrue(playlists.save("Keep",listOf(QueueMedia(item.url,"Video"))))
        val id=playlists.read().single().id
        val previous=sealed.all.toMap()
        locked=true;useVault(vault())
        // An older plain copy must not mask a failed read of the current encrypted record.
        context.getSharedPreferences(TokenMigration.PLAIN,Context.MODE_PRIVATE).edit()
            .putString("player.downloads","[]").putString("player.playlists."+Prefs(context).currentProfile,"[]").commit()
        assertTrue(downloads.read().isEmpty());assertTrue(playlists.read().isEmpty())
        refused {downloads.add("https://example.test/other.mp4")};refused {downloads.remove(item.id)}
        assertFalse(playlists.remove(id));assertFalse(playlists.save("New",listOf(QueueMedia(item.url,"Video"))))
        assertEquals(previous,sealed.all)
        locked=false
        assertEquals(item.id,downloads.read().single().id)
        assertTrue(playlists.rename(id,"Recovered"));assertEquals("Recovered",playlists.read().single().name)
        downloads.change(item.id){it.copy(state="failed")};assertEquals("failed",downloads.read().single().state)
    }
    @Test fun unreadableCiphertextAndUnavailableVaultAreNotMissingRecords() {
        sealed.edit().putString("player.downloads","not-base64!").commit()
        refused {DownloadStore(context).add("https://example.test/video.mp4")}
        assertEquals("not-base64!",sealed.getString("player.downloads",null))
        val unavailable=TokenStore(context){null}
        refused {unavailable.getForUpdate("player.downloads")}
        assertNull(unavailable.getForUpdate("never-saved"))
    }
}
