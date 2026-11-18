package app.ember.tv.player

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@Implements(MediaMetadataRetriever::class)
class ArtworkRetrieverShadow {
    @Implementation fun setDataSource(context:Context,uri:Uri) {}
    @Implementation fun getEmbeddedPicture()=picture
    @Implementation fun release() {}
    companion object {var picture=byteArrayOf()}
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,shadows=[ArtworkRetrieverShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackgroundArtworkTest {
    @Test fun smallArtworkLoadsAndLargeArtworkIsDownsampled() {
        for((width,height,expected) in listOf(Triple(32,16,32),Triple(2048,1024,512))) {
            val source=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            ArtworkRetrieverShadow.picture=ByteArrayOutputStream().use {source.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}
            source.recycle()
            val bytes=BackgroundArtwork.read(RuntimeEnvironment.getApplication(),"file:///music.mp3")
            assertNotNull(bytes)
            val decoded=BitmapFactory.decodeByteArray(bytes!!,0,bytes.size)
            assertEquals(expected,decoded.width);decoded.recycle()
        }
    }
}
