package app.ember.tv.player

import app.ember.tv.R

import android.content.Context
import android.net.wifi.WifiManager
import app.ember.tv.data.Net
import app.ember.tv.data.SubtitleFiles
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.net.*

internal data class DlnaReceiver(val name:String,val peer:String,val transport:String,val transportType:String,val rendering:String?=null,val renderingType:String?=null)
internal data class ReceiverStatus(val position:Long=0,val duration:Long=0,val playing:Boolean=false,val finished:Boolean=false,val volume:Double=.5)

/** Explicit, short discovery on the local network; descriptor/control URLs must stay on the replying peer. */
internal object DlnaReceivers {
    private val client=Net.client(4,5).newBuilder().cache(null).followRedirects(false).followSslRedirects(false).build()
    fun discover(context:Context,check:()->Unit):List<DlnaReceiver> {
        val wifi=context.applicationContext.getSystemService(WifiManager::class.java)
        val multicast=runCatching {wifi?.createMulticastLock("Ember receiver discovery")?.apply {setReferenceCounted(false);acquire()}}.getOrNull()
        val found=linkedMapOf<String,DlnaReceiver>()
        try {DatagramSocket().use {socket->
            socket.soTimeout=350
            val message=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n").toByteArray()
            socket.send(DatagramPacket(message,message.size,InetAddress.getByName("239.255.255.250"),1900))
            val until=System.nanoTime()+4_000_000_000L
            val locations=linkedMapOf<String,String>()
            while(System.nanoTime()<until&&locations.size<24) {
                check();val packet=DatagramPacket(ByteArray(8192),8192)
                try {socket.receive(packet)}catch(_:SocketTimeoutException){continue}
                val ip=packet.address.hostAddress ?: continue
                if(packet.address !is Inet4Address||(!packet.address.isSiteLocalAddress&&!packet.address.isLinkLocalAddress))continue
                val text=String(packet.data,0,packet.length,Charsets.US_ASCII)
                if(!text.startsWith("HTTP/1.1 200"))continue
                val location=text.lineSequence().firstOrNull {it.startsWith("LOCATION:",true)}?.substringAfter(':')?.trim() ?: continue
                if(localUrl(location,ip))locations[location]=ip
            }
            for((location,ip) in locations) {
                check()
                runCatching {
                    client.newCall(Request.Builder().url(location).build()).execute().use {response->
                        check(response.isSuccessful)
                        val body=String(SubtitleFiles.read(response.body.byteStream(),256*1024),Charsets.UTF_8)
                        parse(body,location,ip,context.getString(R.string.ui_tv_receiver))?.let {found[it.transport]=it}
                    }
                }
            }
        }}finally{if(multicast?.isHeld==true)multicast.release()}
        return found.values.sortedBy {it.name.lowercase()}
    }
    fun localUrl(value:String,peer:String):Boolean=runCatching {
        val uri=URI(value);uri.scheme in setOf("http","https")&&uri.host==peer&&uri.userInfo==null&&uri.fragment==null&&value.length<=4096&&
            peer.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))&&peer.split('.').all {it.toInt() in 0..255}&&InetAddress.getByName(peer).let {it.isSiteLocalAddress||it.isLinkLocalAddress||it.isLoopbackAddress}
    }.getOrDefault(false)
    fun parse(xml:String,location:String,peer:String,fallbackName:String=""):DlnaReceiver? {
        require(localUrl(location,peer))
        val document=SafePlayerXml.parse(xml)
        fun first(name:String)=document.getElementsByTagNameNS("*",name).item(0)?.textContent?.trim()
        val base=first("URLBase")?.takeIf {it.isNotBlank()} ?: location
        require(localUrl(base,peer))
        var av:Pair<String,String>?=null;var render:Pair<String,String>?=null
        val services=document.getElementsByTagNameNS("*","service")
        for(i in 0 until services.length) {
            val service=services.item(i) as? org.w3c.dom.Element ?: continue
            fun value(key:String)=service.getElementsByTagNameNS("*",key).item(0)?.textContent?.trim().orEmpty()
            val type=value("serviceType");val control=URI(base).resolve(value("controlURL")).toString()
            if(!localUrl(control,peer))continue
            if(type.matches(Regex("urn:schemas-upnp-org:service:AVTransport:[1-3]")))av=control to type
            if(type.matches(Regex("urn:schemas-upnp-org:service:RenderingControl:[1-3]")))render=control to type
        }
        val transport=av ?: return null
        return DlnaReceiver(first("friendlyName")?.filterNot {it.isISOControl()}?.take(100)?.ifBlank {fallbackName} ?: fallbackName,peer,transport.first,transport.second,render?.first,render?.second)
    }
    /** UI command IDs map to fixed wire names independently of the display locale. */
    fun control(receiver:DlnaReceiver,action:String,argument:Double=0.0) {
        require(argument.isFinite())
        when(action) {
            "play"->soap(receiver,"Play",mapOf("Speed" to "1"))
            "pause"->soap(receiver,"Pause")
            "seek"->soap(receiver,"Seek",mapOf("Unit" to "REL_TIME","Target" to clock(argument.coerceAtLeast(0.0).toLong())))
            "volume"->soap(receiver,"SetVolume",mapOf("Channel" to "Master","DesiredVolume" to (argument.coerceIn(0.0,1.0)*100).toInt().toString()),true)
        }
    }
    fun soap(receiver:DlnaReceiver,action:String,arguments:Map<String,String> = emptyMap(),rendering:Boolean=false):org.w3c.dom.Document {
        val url=if(rendering)receiver.rendering ?: error("Volume unavailable") else receiver.transport
        val type=if(rendering)receiver.renderingType ?: error("Volume unavailable") else receiver.transportType
        require(localUrl(url,receiver.peer));require(action.matches(Regex("[A-Za-z]{1,40}")))
        val fields=(mapOf("InstanceID" to "0")+arguments).entries.joinToString(""){(key,value)->require(key.matches(Regex("[A-Za-z]{1,40}")));"<$key>${escape(value)}</$key>"}
        val body="<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:$action xmlns:u=\"$type\">$fields</u:$action></s:Body></s:Envelope>"
        return client.newCall(Request.Builder().url(url).header("SOAPAction","\"$type#$action\"").post(body.toRequestBody("text/xml; charset=utf-8".toMediaType())).build()).execute().use {
            check(it.isSuccessful)
            SafePlayerXml.parse(String(SubtitleFiles.read(it.body.byteStream(),256*1024),Charsets.UTF_8))
        }
    }
    fun load(receiver:DlnaReceiver,media:CastLanSource.Media,title:String) {
        val kind=if(media.mime.startsWith("audio/"))"audioItem" else "videoItem"
        val metadata="<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"0\" parentID=\"0\" restricted=\"1\"><dc:title>${escape(title)}</dc:title><upnp:class>object.item.$kind</upnp:class><res protocolInfo=\"http-get:*:${escape(media.mime)}:*\">${escape(media.url)}</res></item></DIDL-Lite>"
        soap(receiver,"SetAVTransportURI",mapOf("CurrentURI" to media.url,"CurrentURIMetaData" to metadata))
        soap(receiver,"Play",mapOf("Speed" to "1"))
    }
    fun status(receiver:DlnaReceiver,volume:Double):ReceiverStatus {
        val position=soap(receiver,"GetPositionInfo");val transport=soap(receiver,"GetTransportInfo")
        fun text(document:org.w3c.dom.Document,name:String)=document.getElementsByTagNameNS("*",name).item(0)?.textContent.orEmpty()
        val at=time(text(position,"RelTime"));val duration=time(text(position,"TrackDuration"));val state=text(transport,"CurrentTransportState")
        return ReceiverStatus(at,duration,state=="PLAYING",state=="STOPPED",volume)
    }
    fun time(value:String):Long {
        val parts=value.substringBefore('.').split(':');if(parts.size!=3)return 0
        val hours=parts[0].toLongOrNull()?.takeIf {it in 0..999} ?: return 0
        val minutes=parts[1].toIntOrNull()?.takeIf {it in 0..59} ?: return 0
        val seconds=parts[2].toIntOrNull()?.takeIf {it in 0..59} ?: return 0
        return (hours*3600+minutes*60+seconds)*1000
    }
    fun clock(ms:Long):String {val seconds=(ms.coerceAtLeast(0)/1000).coerceAtMost(3599999);return "%02d:%02d:%02d".format(java.util.Locale.ROOT,seconds/3600,seconds/60%60,seconds%60)}
    fun escape(text:String)=text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
}
