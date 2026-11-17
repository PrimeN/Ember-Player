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
import java.net.ServerSocket
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class ReceiverLocaleTest {
    @Test fun translatedInterfacesCannotChangeReceiverCommandsOrTheirArguments() {
        Net.init(RuntimeEnvironment.getApplication())
        val requests=LinkedBlockingQueue<Pair<Map<String,String>,String>>()
        val socket=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
        val worker=Thread {
            repeat(4){socket.accept().use {client->
                client.soTimeout=5000
                val reader=client.getInputStream().bufferedReader()
                reader.readLine()
                val headers=mutableMapOf<String,String>()
                while(true){val line=reader.readLine() ?: break;if(line.isEmpty())break;headers[line.substringBefore(':').lowercase(Locale.ROOT)]=line.substringAfter(':').trim()}
                val content=CharArray(headers.getValue("content-length").toInt())
                var read=0
                while(read<content.size){val n=reader.read(content,read,content.size-read);check(n>0);read+=n}
                requests.put(headers to String(content))
                val body="<Envelope/>".toByteArray()
                client.getOutputStream().apply {write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray());write(body);flush()}
            }}
        }.apply {isDaemon=true;start()}
        val before=Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-XB"))
            val type="urn:schemas-upnp-org:service:AVTransport:1"
            val volumeType="urn:schemas-upnp-org:service:RenderingControl:1"
            val url="http://127.0.0.1:${socket.localPort}/control"
            val receiver=DlnaReceiver("Receiver","127.0.0.1",url,type,url,volumeType)
            for((action,value,wire) in listOf(Triple("play",0.0,"Play"),Triple("pause",0.0,"Pause"),Triple("seek",3723000.0,"Seek"),Triple("volume",.5,"SetVolume"))) {
                DlnaReceivers.control(receiver,action,value)
                val (headers,body)=requests.poll(5,TimeUnit.SECONDS) ?: error("Missing receiver command")
                assertEquals("\"${if(action=="volume")volumeType else type}#$wire\"",headers["soapaction"])
                assertTrue(body.contains("<InstanceID>0</InstanceID>"))
                when(action) {
                    "play"->assertTrue(body.contains("<Speed>1</Speed>"))
                    "seek"->{assertTrue(body.contains("<Unit>REL_TIME</Unit>"));assertTrue(body.contains("<Target>01:02:03</Target>"))}
                    "volume"->{assertTrue(body.contains("<Channel>Master</Channel>"));assertTrue(body.contains("<DesiredVolume>50</DesiredVolume>"))}
                }
            }
        }finally {Locale.setDefault(before);socket.close();worker.join(1000)}
    }
}
