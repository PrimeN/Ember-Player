package app.ember.player

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class ExternalIntentRoutingTest {
    private fun resolves(url:String,type:String?):Boolean {
        val app:Application=RuntimeEnvironment.getApplication()
        val intent=Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url),type)
            .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(app.packageName)
        return app.packageManager.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY)
            .any {it.activityInfo.name==OpenMediaActivity::class.java.name}
    }
    @Test fun supportedStreamsResolveWithAndWithoutMediaTypes() {
        for(scheme in listOf("http","https","rtsp","rtmp","rtmps","rtp","srtp","udp","tcp")) {
            val url="$scheme://media.example:1234/live"
            assertTrue(url,ExternalMedia.allowed(url))
            assertTrue(url,resolves(url,null))
            assertTrue(url,resolves(url,"video/mp4"))
            assertTrue(url,resolves(url,"audio/mpeg"))
        }
    }
    @Test fun contentUrisStillNeedAMediaTypeAndLocalFilesAreNotExposed() {
        assertTrue(resolves("content://documents.example/video/1","video/mp4"))
        assertFalse(resolves("content://documents.example/video/1",null))
        assertFalse(resolves("file:///data/private.mp4","video/mp4"))
        assertFalse(resolves("unknown://media.example/live",null))
    }
}
