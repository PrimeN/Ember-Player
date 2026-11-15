package app.ember.tv.player

import android.Manifest
import android.app.Application
import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class UsbMediaTest {
    private val context:Context get()=RuntimeEnvironment.getApplication()
    private lateinit var provider:UsbMediaProvider
    @Before fun setup(){
        provider=UsbMediaProvider();provider.attachInfo(context,ProviderInfo().apply {authority="media";exported=true})
        ShadowContentResolver.registerProviderInternal("media",provider)
    }
    @Test fun permissionsAreScopedToTheAndroidVersionAndSelectedMediaIsRespected() {
        assertArrayEquals(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),UsbMedia.permissions(29))
        assertEquals(2,UsbMedia.permissions(33).size)
        assertTrue(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED in UsbMedia.permissions(34))
    }
    @Test fun filesArePagedByStableIdsEvenWhenProviderIgnoresLimit() {
        val first=UsbMedia.page(context,UsbDrive("removable","Drive"))
        assertEquals(100,first.items.size);assertEquals(100L,first.next)
        val second=UsbMedia.page(context,UsbDrive("removable","Drive"),first.next!!)
        assertEquals(50,second.items.size);assertNull(second.next)
        assertTrue(first.items.map {it.uri}.intersect(second.items.map {it.uri}.toSet()).isEmpty())
        assertTrue(provider.lastCursor!!.isClosed)
    }
    @Test fun cancelledQueryClosesItsCursorAndDoesNotReturnPartialMedia() {
        val signal=CancellationSignal()
        try {UsbMedia.page(context,UsbDrive("removable","Drive"),signal=signal){signal.cancel()};fail("Expected cancellation")}
        catch(_:OperationCanceledException){}
        assertTrue(provider.lastCursor!!.isClosed)
    }
    @Test fun removableMediaMonitorStopsOnceAndUnregistersOnClose() {
        assertNull(UsbMedia.volume("content://media/external_primary/file/1"))
        assertNull(UsbMedia.volume("https://example.invalid/movie"))
        assertEquals("removable",UsbMedia.volume("content://media/removable/file/1"))
        var removed=0
        val monitor=UsbMedia.watch(context,"content://media/removable/file/1"){removed++}!!
        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_REMOVED,Uri.parse("file:///storage/removable")))
        shadowOf(Looper.getMainLooper()).idle();assertEquals(1,removed)
        monitor.close()
        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_REMOVED,Uri.parse("file:///storage/removable")))
        shadowOf(Looper.getMainLooper()).idle();assertEquals(1,removed)
    }
}

class UsbMediaProvider:ContentProvider() {
    var lastCursor:MatrixCursor?=null
    override fun onCreate()=true
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor=error("Use structured query")
    override fun query(uri:Uri,projection:Array<out String>?,queryArgs:Bundle?,cancellationSignal:CancellationSignal?):Cursor {
        val after=queryArgs?.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)?.first()?.toLong() ?: 0
        return MatrixCursor(projection!!).apply {
            for(id in after+1..150)addRow(projection.map {column->when(column){
                MediaStore.Files.FileColumns._ID->id
                MediaStore.MediaColumns.DISPLAY_NAME->"Movie $id.mkv"
                MediaStore.MediaColumns.RELATIVE_PATH->"Films/"
                else->null
            }})
            lastCursor=this
        }
    }
    override fun getType(uri:Uri)="video/x-matroska"
    override fun insert(uri:Uri,values:ContentValues?):Uri?=null
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
}
