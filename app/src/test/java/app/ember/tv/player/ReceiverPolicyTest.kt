package app.ember.tv.player

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ReceiverPolicyTest {
    @Test fun queuesKeepResourcesUntilTheLastOwnerReleasesThem() {
        var closed=0
        val id=MediaQueues.put(listOf(QueueMedia("https://example.test/video","Video")),listOf(java.io.Closeable {closed++}))
        val first=MediaQueues.retain(id)!!;val second=MediaQueues.retain(id)!!
        MediaQueues.release(id);assertNotNull(MediaQueues.get(id));assertEquals(0,closed)
        first.close();first.close();assertNotNull(MediaQueues.get(id))
        second.close();assertEquals(1,closed);assertNull(MediaQueues.get(id))
    }
    @Test fun manifestTemplatesKeepCredentialsOutOfReceiverPaths() {
        val secret="http://127.0.0.1:9000/private/video-\$RepresentationID\$-\$Number%05d\$.m4s?api_key=private"
        val tokens=CastUrlTemplate.tokens(secret)
        assertEquals(listOf("\$RepresentationID\$","\$Number%05d\$"),tokens)
        assertEquals(secret.replace("\$RepresentationID\$","hd").replace("\$Number%05d\$","00003"),CastUrlTemplate.resolve(secret,"/v/hd/00003"))
        for(path in listOf("/v/other/00003","/v/../../00003","/v/hd/-3","/v/hd/anything","/v/hd/3/extra","/bad/hd/3")) {
            try {CastUrlTemplate.resolve(secret,path,setOf("hd"),emptySet());fail(path)}catch(_:IllegalArgumentException){}
        }
    }
    @Test fun receiverControlCannotRedirectToAnInternetHostOrEmbedCredentials() {
        assertTrue(DlnaReceivers.localUrl("http://192.168.1.5:1400/av/control","192.168.1.5"))
        for(url in listOf("http://example.com/control","http://192.168.1.6/control","http://user:pass@192.168.1.5/control","file:///data/file","http://192.168.1.5/control#x"))assertFalse(url,DlnaReceivers.localUrl(url,"192.168.1.5"))
        assertEquals(3723000L,DlnaReceivers.time("01:02:03"));assertEquals(0L,DlnaReceivers.time("01:99:03"))
        assertEquals("01:02:03",DlnaReceivers.clock(3723000))
    }
    @Test fun independentMediaReadsSeekAndRespectBounds() {
        val bytes=ByteArray(100){it.toByte()}
        fun stream(at:Long)=PositionedMediaInput(bytes.size.toLong(),at){position,out,start,count->bytes.copyInto(out,start,position.toInt(),position.toInt()+count);count}
        val first=stream(10);val second=stream(80)
        assertEquals(10,first.read());assertEquals(80,second.read());assertEquals(11,first.read())
        assertEquals(0,second.read(ByteArray(0)))
        assertEquals(19,second.readBytes().size);assertEquals(-1,second.read())
        first.close();try {first.read();fail("Closed stream must stop")}catch(_:IllegalStateException){}
        val stalled=PositionedMediaInput(10,0){_,_,_,_->0}
        try {stalled.read();fail("A stalled source cannot spin forever")}catch(_:IOException){}
    }
    @Test fun directUsbPositionsHaveStableOpaqueIdentities() {
        val first=DirectUsbIdentity.key("device/serial","/Movies/A.mp4")
        assertEquals(first,DirectUsbIdentity.key("device/serial","/Movies/A.mp4"))
        assertNotEquals(first,DirectUsbIdentity.key("another device","/Movies/A.mp4"))
        assertFalse(first.contains("serial"));assertFalse(first.contains("Movies"))
    }
}
