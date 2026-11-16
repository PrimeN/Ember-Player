package app.ember.tv.player

import android.app.Application
import app.ember.tv.data.Net
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.Closeable
import java.net.*
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class ReceiverRelayTest {
    @Test fun hlsCastingKeepsAuthenticationPrivateAndSupportsRangeReads() {
        val context=RuntimeEnvironment.getApplication();Net.init(context)
        SourceServer().use {upstream->CastLanSource(context,InetAddress.getByName("127.0.0.1")).use {source->
            val prepared=source.prepare(upstream.url+"/video.m3u8?api_key=private-value","video.m3u8",mapOf("Authorization" to "Bearer private-auth"))
            assertEquals("application/vnd.apple.mpegurl",prepared.mime)
            assertFalse(prepared.url.contains("private-value"));assertFalse(prepared.url.contains("api_key"))
            val playlist=URL(prepared.url).readText()
            assertTrue(playlist.trimEnd().endsWith("# END-MARKER"))
            val refreshed=URL(prepared.url).readText()
            assertTrue(refreshed.trimEnd().endsWith("# END-MARKER"));assertNotEquals(playlist,refreshed)
            assertFalse(playlist.contains("private-value"));assertFalse(playlist.contains("api_key"));assertFalse(playlist.contains("private-auth"))
            val segment=playlist.lineSequence().first {it.startsWith("http")}
            val response=URL(segment).openConnection() as HttpURLConnection
            response.setRequestProperty("Range","bytes=2-4")
            assertEquals(206,response.responseCode);assertEquals("234",response.inputStream.bufferedReader().readText());response.disconnect()
            assertTrue(upstream.authorized>0)
            val preflight=URL(prepared.url).openConnection() as HttpURLConnection
            preflight.requestMethod="OPTIONS";assertEquals(204,preflight.responseCode);assertEquals("*",preflight.getHeaderField("Access-Control-Allow-Origin"));preflight.disconnect()
        }}
    }
    @Test fun dashReceiverCanReadOnlyDeclaredRepresentations() {
        val context=RuntimeEnvironment.getApplication();Net.init(context)
        SourceServer().use {upstream->CastLanSource(context,InetAddress.getByName("127.0.0.1")).use {source->
            val prepared=source.prepare(upstream.url+"/video.mpd?api_key=private-value","video.mpd",mapOf("Authorization" to "Bearer private-auth"))
            val manifest=URL(prepared.url).readText()
            assertFalse(manifest.contains("private-value"))
            val document=SafePlayerXml.parse(manifest)
            val template=(document.getElementsByTagNameNS("*","SegmentTemplate").item(0) as org.w3c.dom.Element).getAttribute("media")
            val valid=template.replace("\$RepresentationID\$","hd").replace("\$Number%05d\$","00001")
            assertEquals("0123456789",URL(valid).readText())
            val before=upstream.authorized
            val invalid=URL(valid.replace("/v/hd/","/v/other/")).openConnection() as HttpURLConnection
            assertEquals(502,invalid.responseCode);invalid.disconnect()
            assertEquals(before,upstream.authorized)
        }}
    }
    @Test fun relayRejectsOtherPeersAndStaleUrls() {
        val address=InetAddress.getByName("127.0.0.1")
        val relay=MediaRelay(MediaRelay.LanRelayAccess(address,InetAddress.getByName("127.0.0.2")))
        val url=relay.register(object:RemoteMedia {override val length=3L;override fun open(offset:Long)="abc".toByteArray().inputStream().apply {skip(offset)}},"file.mp4")
        try {URL(url).openConnection().apply {connectTimeout=1000;readTimeout=1000}.inputStream.use {it.read()};fail("A different peer must be denied")}catch(_:java.io.IOException){}
        relay.close()
        try {URL(url).openConnection().apply {connectTimeout=1000;readTimeout=1000}.inputStream;fail("Closed relay must stop serving")}catch(_:java.io.IOException){}
    }
    @Test fun descriptorsCannotUseForeignControlEndpointsOrXmlEntities() {
        val device="""<root xmlns="urn:schemas-upnp-org:device-1-0"><device><friendlyName>Living room</friendlyName><serviceList><service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/control</controlURL></service></serviceList></device></root>"""
        assertEquals("http://192.168.1.5/control",DlnaReceivers.parse(device,"http://192.168.1.5/device.xml","192.168.1.5")!!.transport)
        assertNull(DlnaReceivers.parse(device.replace(">/control<",">http://example.com/control<"),"http://192.168.1.5/device.xml","192.168.1.5"))
        try {DlnaReceivers.parse("<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///private'>]>"+device,"http://192.168.1.5/device.xml","192.168.1.5");fail("External entities must be rejected")}catch(_:IllegalArgumentException){}
    }
    private class SourceServer:Closeable {
        private val socket=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
        private val workers=Executors.newCachedThreadPool {r->Thread(r,"Media source fixture").apply {isDaemon=true}}
        @Volatile private var closed=false
        @Volatile var authorized=0
        private val revisions=java.util.concurrent.atomic.AtomicInteger()
        val url="http://127.0.0.1:${socket.localPort}"
        init {workers.execute {while(!closed)try {val client=socket.accept();workers.execute {serve(client)}}catch(_:Exception){}}}
        private fun serve(client:Socket) {client.use {
            it.soTimeout=3000
            val input=it.getInputStream().bufferedReader();val start=input.readLine()?.split(' ') ?: return
            val headers=mutableMapOf<String,String>()
            while(true){val line=input.readLine() ?: break;if(line.isEmpty())break;headers[line.substringBefore(':').lowercase()]=line.substringAfter(':').trim()}
            if(headers["authorization"]=="Bearer private-auth")authorized++
            val playlist=start[1].substringBefore('?').endsWith("m3u8")
            val dash=start[1].substringBefore('?').endsWith("mpd")
            val mpd="""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011"><Period><AdaptationSet><SegmentTemplate media="segment-${'$'}RepresentationID${'$'}-${'$'}Number%05d${'$'}.m4s?api_key=private-value"/><Representation id="hd" bandwidth="1000000"/></AdaptationSet></Period></MPD>"""
            val text=if(dash)mpd else if(playlist)"#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"$url/key?api_key=private-value\"\n#EXTINF:5,\nsegment.ts?api_key=private-value\n# refresh ${"x".repeat(revisions.incrementAndGet()%50)}\n# END-MARKER\n" else "0123456789"
            val bytes=text.toByteArray();val range=ByteRange.parse(headers["range"],bytes.size.toLong())!!
            val part=bytes.copyOfRange(range.start.toInt(),range.end.toInt()+1)
            val partial=headers["range"]!=null
            val response="HTTP/1.1 ${if(partial)"206 Partial Content" else "200 OK"}\r\nContent-Type: ${if(dash)"application/dash+xml" else if(playlist)"application/vnd.apple.mpegurl" else "video/mp2t"}\r\nContent-Length: ${part.size}\r\nConnection: close\r\n"+(if(partial)"Content-Range: bytes ${range.start}-${range.end}/${bytes.size}\r\n" else "")+"\r\n"
            val output=it.getOutputStream();output.write(response.toByteArray());if(start[0]!="HEAD")output.write(part);output.flush()
        }}
        override fun close(){closed=true;socket.close();workers.shutdownNow()}
    }
}
