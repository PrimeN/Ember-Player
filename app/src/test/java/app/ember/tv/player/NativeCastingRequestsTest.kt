package app.ember.tv.player

import android.app.Application
import app.ember.tv.data.Net
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class NativeCastingRequestsTest {
    @Test fun castingAddsNoOriginProbeToSegmentReads() {
        val context=RuntimeEnvironment.getApplication();Net.init(context)
        NativeTransportRequestsTest.Origin().use {origin->CastLanSource(context,InetAddress.getByName("127.0.0.1")).use {source->
            val media=source.prepare("${origin.url}/live.m3u8","live.m3u8",emptyMap())
            val playlist=URL(media.url).readText()
            val segment=playlist.lineSequence().first {it.startsWith("http")}
            val before=origin.requests.size
            assertEquals("0123456789",URL(segment).readText())
            assertEquals(listOf("GET /part.ts"),origin.requests.drop(before))
        }}
    }
}
