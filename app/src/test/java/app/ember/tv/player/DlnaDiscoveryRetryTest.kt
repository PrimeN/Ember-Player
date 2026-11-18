package app.ember.tv.player

import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.InetAddress

class DlnaDiscoveryRetryTest {
    @Test fun aLostFirstSearchStillFindsAReceiverWithoutExtendingTheDiscoveryWindow() {
        var clock=0L;val searches=mutableListOf<Long>();var replied=false
        val locations=DlnaReceivers.discoverLocations({}, {clock},{searches+=clock},{
            clock+=100_000_000
            if(searches.size>=2&&!replied){replied=true;reply("http://192.168.1.5/description.xml")}else null
        })
        assertEquals(listOf(0L,1_000_000_000L,2_000_000_000L),searches)
        assertEquals(4_000_000_000L,clock)
        assertEquals(mapOf("http://192.168.1.5/description.xml" to "192.168.1.5"),locations)
    }
    @Test fun retriesPreserveDeduplicationAndPeerValidation() {
        var clock=0L
        val packets=listOf(reply("http://192.168.1.5/a"),reply("http://192.168.1.5/a"),reply("http://192.168.1.6/foreign"),reply("http://192.168.1.5/redirect", "8.8.8.8"))
        var i=0
        val result=DlnaReceivers.discoverLocations({}, {clock},{},{clock+=100_000_000;packets.getOrNull(i++)})
        assertEquals(mapOf("http://192.168.1.5/a" to "192.168.1.5"),result)
    }
    @Test fun cancellationStopsBeforeAnotherSearchIsSent() {
        var clock=0L;var sent=0
        try {
            DlnaReceivers.discoverLocations({if(clock>=1_000_000_000L)throw kotlinx.coroutines.CancellationException()}, {clock},{sent++},{clock+=250_000_000;null})
            fail("Expected cancellation")
        }catch(_:kotlinx.coroutines.CancellationException){}
        assertEquals(1,sent)
    }
    private fun reply(location:String,ip:String="192.168.1.5"):DatagramPacket {
        val bytes="HTTP/1.1 200 OK\r\nLOCATION: $location\r\n\r\n".toByteArray()
        return DatagramPacket(bytes,bytes.size,InetAddress.getByName(ip),1900)
    }
}
