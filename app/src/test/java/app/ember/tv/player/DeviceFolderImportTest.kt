package app.ember.tv.player

import android.app.Application
import android.content.Intent
import android.content.pm.ProviderInfo
import android.net.Uri
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class)
class DeviceFolderImportTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val tree=Uri.parse("content://device.files/tree/root")
    private lateinit var provider:DeviceTestProvider
    private val read=Intent.FLAG_GRANT_READ_URI_PERMISSION
    private val write=Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    @Before fun setup() {
        provider=DeviceTestProvider()
        provider.attachInfo(context,ProviderInfo().apply {authority="device.files";exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission="android.permission.MANAGE_DOCUMENTS"})
        ShadowContentResolver.registerProviderInternal("device.files",provider)
    }
    private fun grants()=context.contentResolver.persistedUriPermissions.associate {it.uri to ((if(it.isReadPermission)read else 0) or (if(it.isWritePermission)write else 0))}
    @Test fun theSeventeenthFolderIsRejectedBeforeAcquiringPermission() {
        val original=Json.encodeToString((1..16).map {DeviceFolder("content://device.files/tree/$it","Folder $it")})
        var writes=0
        val store=DeviceFolders({original},{writes++;true})
        try {saveDeviceFolder(context,tree,store);fail("Expected folder limit")}
        catch(_:SavedFolderLimitException) {}
        assertEquals(0,writes);assertTrue(grants().isEmpty());assertEquals(16,store.read().size)
    }
    @Test fun writeAndMetadataFailuresReleaseNewGrantsButKeepExistingAccess() {
        val other=Uri.parse("content://device.files/tree/other")
        context.contentResolver.takePersistableUriPermission(other,read or write)
        for(prior in listOf(0,read,read or write)) {
            if(prior!=0)context.contentResolver.takePersistableUriPermission(tree,prior)
            val previous=grants()
            try {saveDeviceFolder(context,tree,DeviceFolders({null},{false}));fail("Expected storage failure")}
            catch(_:IllegalStateException) {}
            assertEquals(previous,grants())
            provider.failQueries=true
            var writes=0
            try {saveDeviceFolder(context,tree,DeviceFolders({null},{writes++;true}));fail("Expected metadata failure")}
            catch(_:Exception) {}
            assertEquals(0,writes);assertEquals(previous,grants())
            provider.failQueries=false
            if(prior!=0)context.contentResolver.releasePersistableUriPermission(tree,prior)
        }
    }
    @Test fun aConcurrentLimitFailureAndCancellationAlsoReleaseNewAccess() {
        val full=Json.encodeToString((1..16).map {DeviceFolder("content://device.files/tree/$it","Folder $it")})
        var reads=0
        val store=DeviceFolders({if(reads++==0)null else full},{fail("Full lists must not be written");false})
        try {saveDeviceFolder(context,tree,store);fail("Expected concurrent limit")}
        catch(_:SavedFolderLimitException) {}
        assertTrue(grants().isEmpty())
        var checks=0;var writes=0
        try {saveDeviceFolder(context,tree,DeviceFolders({null},{writes++;true})){if(++checks==2)throw kotlinx.coroutines.CancellationException()};fail("Expected cancellation")}
        catch(_:kotlinx.coroutines.CancellationException) {}
        assertEquals(0,writes);assertTrue(grants().isEmpty())
    }
    @Test fun successfulImportsKeepTheirGrantAndExistingFoldersCanBeUpdatedAtTheLimit() {
        var raw:String?=null
        val store=DeviceFolders({raw},{raw=it;true})
        val value=saveDeviceFolder(context,tree,store)
        assertEquals(listOf(value),store.read());assertEquals(read or write,grants()[tree])
        raw=Json.encodeToString(listOf(value.copy(name="Old"))+(1..15).map {DeviceFolder("content://device.files/tree/$it","Folder $it")})
        saveDeviceFolder(context,tree,store)
        assertEquals(16,store.read().size);assertEquals("Root",store.read().first {it.uri==tree.toString()}.name)
        assertEquals(read or write,grants()[tree])
    }
}
