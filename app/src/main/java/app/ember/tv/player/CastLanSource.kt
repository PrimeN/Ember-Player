package app.ember.tv.player

import android.content.Context
import android.net.Uri
import app.ember.tv.data.Net
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.Closeable
import java.io.InputStream
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress

/** One selected LAN peer, opaque URLs, and a lifetime bounded by the remote playback session. */
internal class CastLanSource(private val context:Context,peer:InetAddress):Closeable {
    private val address=localAddress(peer)
    private val relay=MediaRelay(MediaRelay.LanRelayAccess(address,peer))
    private val aliases=java.util.concurrent.ConcurrentHashMap<String,String>()
    private val representationIds=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val bandwidths=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val descriptors=java.util.concurrent.CopyOnWriteArrayList<Closeable>()
    private val client=Net.client(10,20).newBuilder().cache(null).followRedirects(false).followSslRedirects(false).build()
    private val calls=java.util.concurrent.ConcurrentHashMap.newKeySet<okhttp3.Call>()
    private var transport:NativeHttpTransport?=null
    @Volatile private var closed=false
    data class Media(val url:String,val mime:String)
    fun prepare(source:String,name:String,headers:Map<String,String>):Media {
        check(!closed)
        val uri=Uri.parse(source)
        if(uri.scheme=="content") {
            val asset=context.contentResolver.openAssetFileDescriptor(uri,"r") ?: error("Media unavailable")
            descriptors+=asset
            val size=if(asset.declaredLength>=0)asset.declaredLength else asset.parcelFileDescriptor.statSize.takeIf {it>=0}?.minus(asset.startOffset) ?: -1L
            require(size>0) {"A seekable file is required"}
            val mime=context.contentResolver.getType(uri)?.substringBefore(';') ?: mime(name)
            val resource=object:RemoteMedia {
                override val length=size
                override val mime=mime
                override fun open(offset:Long):InputStream {
                    require(offset in 0..size)
                    return PositionedMediaInput(size,offset,{check(!closed)}) {at,bytes,start,count->
                        android.system.Os.pread(asset.fileDescriptor,bytes,start,count,asset.startOffset+at)
                    }
                }
            }
            return Media(relay.register(resource,name),mime)
        }
        require(uri.scheme in setOf("http","https")) {"This source cannot be sent to a receiver"}
        val proxy=NativeHttpTransport(headers,listOf(source),::alias,receiverMode=true,representations={ids,rates->representationIds.addAll(ids);bandwidths.addAll(rates)});transport=proxy
        val exposed=proxy.expose(source)
        // Resolve MIME through the private loopback transport; credentials never enter the LAN URL.
        val internal=aliases.entries.firstOrNull {it.value==exposed}?.key
        val type=internal?.takeUnless {CastUrlTemplate.tokens(it).isNotEmpty()}?.let {url->request(url,true).use {it.header("Content-Type")?.substringBefore(';')}}
        return Media(exposed,type?.takeUnless {it=="application/octet-stream"} ?: mime(name+" "+uri.path.orEmpty()))
    }
    private fun alias(value:String):String=aliases.getOrPut(value) {
        require(value.toHttpUrl().host=="127.0.0.1")
        val tokens=CastUrlTemplate.tokens(value)
        if(tokens.isEmpty())relay.factory({resource(value)},value.substringBefore('?'))
        else {
            val prefix=relay.origin {suffix->resource(CastUrlTemplate.resolve(value,suffix,representationIds,bandwidths))}
            prefix+"/v/"+tokens.joinToString("/")
        }
    }
    private fun request(url:String,head:Boolean=false,range:String?=null):okhttp3.Response {
        check(!closed)
        val request=Request.Builder().url(url).apply {if(head)head();if(range!=null)header("Range",range)}.build()
        val call=client.newCall(request);calls+=call
        try {val response=call.execute();calls-=call;check(response.code in setOf(200,206,416)){response.close();"Media unavailable"};return response}
        catch(e:Exception){calls-=call;throw e}
    }
    private fun resource(url:String):RemoteMedia=object:RemoteHttpMedia {
        override fun response(range:String?,head:Boolean)=request(url,head,range).relayResponse(head,range)
    }
    override fun close(){closed=true;relay.close();transport?.close();calls.forEach {it.cancel()};calls.clear();aliases.clear();descriptors.forEach {runCatching {it.close()}};descriptors.clear()}
    companion object {
        fun localAddress(peer:InetAddress):InetAddress {
            require(peer is Inet4Address&&(peer.isSiteLocalAddress||peer.isLinkLocalAddress||peer.isLoopbackAddress))
            return DatagramSocket().use {it.connect(peer,9);it.localAddress}.also {require(it is Inet4Address&&!it.isAnyLocalAddress)}
        }
        fun mime(name:String):String=when {
            name.contains(".m3u8",true)->"application/vnd.apple.mpegurl"
            name.contains(".mpd",true)->"application/dash+xml"
            else->android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringBefore('?').substringAfterLast('.').lowercase()) ?: "video/mp4"
        }
    }
}

internal object CastUrlTemplate {
    private val pattern=Regex("\\$(RepresentationID|Number|Time|Bandwidth)(?:%0\\d{1,2}d)?\\$")
    fun tokens(value:String)=pattern.findAll(value).map {it.value}.distinct().toList()
    fun resolve(template:String,suffix:String,representationIds:Set<String>?=null,bandwidths:Set<String>?=null):String {
        val tokens=tokens(template);val parts=suffix.substringBefore('?').trim('/').split('/')
        require(parts.firstOrNull()=="v"&&parts.size==tokens.size+1)
        var result=template
        tokens.forEachIndexed {index,token->
            val value=parts[index+1]
            require(value.matches(Regex("[A-Za-z0-9._-]{1,180}"))&&value !in setOf(".",".."))
            if(!token.contains("RepresentationID"))require(value.toLongOrNull()?.let {it>=0}==true)
            if(token.contains("RepresentationID")&&representationIds!=null)require(value in representationIds)
            if(token.contains("Bandwidth")&&bandwidths!=null)require(value.toLong().toString() in bandwidths)
            result=result.replace(token,value)
        }
        return result
    }
}
