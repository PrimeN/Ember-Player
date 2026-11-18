package app.ember.tv.player

import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class)
class DevicePreviewTest {
    @Test fun durationsAreCachedInMemoryAndClearedWithPrivateArtwork()=runBlocking {
        DeviceThumbnails.clear()
        val context=RuntimeEnvironment.getApplication();val uri=Uri.parse("content://videos/preview.mp3")
        val source=DataSource.toDataSource(context,uri)
        val file=DeviceFile(uri,uri,"Preview.mp3","audio/mpeg",100,1,0)
        ShadowMediaMetadataRetriever.addMetadata(source,MediaMetadataRetriever.METADATA_KEY_DURATION,"125000")
        assertEquals(125000L,DeviceThumbnails.preview(context,file).duration)
        ShadowMediaMetadataRetriever.addMetadata(source,MediaMetadataRetriever.METADATA_KEY_DURATION,"90000")
        assertEquals(125000L,DeviceThumbnails.preview(context,file).duration)
        DeviceThumbnails.clear()
        assertEquals(90000L,DeviceThumbnails.preview(context,file).duration)
        assertNull(DeviceThumbnails.preview(context,file).bitmap)
    }
    @Test fun unavailableMetadataAndDirectoriesRemainBrowsable()=runBlocking {
        DeviceThumbnails.clear()
        val context=RuntimeEnvironment.getApplication();val uri=Uri.parse("content://videos/missing")
        val file=DeviceFile(uri,uri,"Missing.mkv","video/*",100,1,0)
        ShadowMediaMetadataRetriever.addException(DataSource.toDataSource(context,uri),IllegalArgumentException("Unavailable"))
        assertEquals(BrowserPreview(),DeviceThumbnails.preview(context,file))
        assertEquals(BrowserPreview(),DeviceThumbnails.preview(context,file.copy(mime=android.provider.DocumentsContract.Document.MIME_TYPE_DIR)))
    }
    @Test fun disabledNetworkPreviewsDoNotReadRemoteMetadataOrOpenTheStream()=runBlocking {
        val metadataReads=java.util.concurrent.atomic.AtomicInteger()
        val streamOpens=java.util.concurrent.atomic.AtomicInteger()
        val source=object:RemoteMedia {
            override val length:Long get(){metadataReads.incrementAndGet();throw AssertionError("Remote metadata was accessed")}
            override fun open(offset:Long):java.io.InputStream {streamOpens.incrementAndGet();throw AssertionError("Remote stream was opened")}
        }
        assertEquals(BrowserPreview(),DeviceThumbnails.remote("disabled","Video.mkv",source,enabled={false}))
        assertEquals(0,metadataReads.get());assertEquals(0,streamOpens.get())
    }

}
