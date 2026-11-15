package app.ember.player

import android.app.*
import android.content.*
import android.os.*
import androidx.core.content.FileProvider
import app.ember.tv.tracker.TokenStore
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
internal data class MediaDownload(val id:String,val url:String,val name:String,val state:String="paused",val bytes:Long=0,val total:Long=-1,val validator:String="")
internal class DownloadStore(private val context:Context) {
    fun read():List<MediaDownload> = TokenStore(context).get(KEY)?.takeIf {it.length<=1024*1024}?.let {runCatching {Json.decodeFromString<List<MediaDownload>>(it)}.getOrNull()}.orEmpty().filter {valid(it)}.take(50).map {if(it.state=="complete"&&!file(context,it).isFile)it.copy(state="paused",bytes=0,total=-1,validator="") else it}
    fun add(url:String,name:String):MediaDownload=synchronized(lock) {
        require(supported(url)) {"Use a direct HTTP or HTTPS media file, rather than a playlist or protected stream."}
        val values=read();check(values.size<50) {"Remove an old download first."}
        val item=MediaDownload(java.util.UUID.randomUUID().toString(),url,safeName(name))
        save(values+item);item
    }
    fun change(id:String,change:(MediaDownload)->MediaDownload)=synchronized(lock){save(read().map {if(it.id==id)change(it) else it})}
    fun remove(id:String)=synchronized(lock){save(read().filterNot {it.id==id});folder(context,id).deleteRecursively();Unit}
    private fun save(values:List<MediaDownload>){check(TokenStore(context).putEncrypted(KEY,Json.encodeToString(values))) {"Encrypted download storage is unavailable."}}
    companion object {
        private val lock=Any();private const val KEY="player.downloads"
        fun supported(url:String)=url.length in 1..16384 && runCatching {
            val uri=java.net.URI(url);uri.scheme?.lowercase() in setOf("http","https") && !uri.host.isNullOrBlank() &&
                uri.fragment==null && uri.path.orEmpty().substringAfterLast('.').lowercase() !in setOf("m3u8","m3u","mpd","ism")
        }.getOrDefault(false)
        fun safeName(name:String)=name.substringAfterLast('/').substringAfterLast('\\').filter {it.isLetterOrDigit()||it in " ._-"}.trim().take(120).takeUnless {it.isEmpty()||it in setOf(".","..")} ?: "media.mp4"
        private fun valid(item:MediaDownload)=item.id.matches(Regex("[a-f0-9-]{36}"))&&supported(item.url)&&item.name==safeName(item.name)&&item.bytes>=0&&item.total>=-1&&item.validator.length<=512&&item.state in setOf("paused","downloading","complete","failed")
        fun folder(context:Context,id:String):File {require(id.matches(Regex("[a-f0-9-]{36}")));return File(context.filesDir,"offline-media/$id")}
        fun file(context:Context,item:MediaDownload)=File(folder(context,item.id),item.name)
        fun uri(context:Context,item:MediaDownload)=FileProvider.getUriForFile(context,context.packageName+".downloads",file(context,item))
    }
}

/** Downloads begin only from an explicit user action. URLs and validators stay encrypted. */
class MediaDownloadService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var task:Job?=null
    private var call:okhttp3.Call?=null
    private var active:String?=null
    override fun onBind(intent:Intent?)=null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("downloads","Media downloads",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="pause") {call?.cancel();task?.cancel();stopSelf();return START_NOT_STICKY}
        val id=intent?.getStringExtra("id") ?: run {stopSelf();return START_NOT_STICKY}
        startForeground(730,notification("Preparing download…"))
        val previous=task;call?.cancel();previous?.cancel()
        active=id
        task=scope.launch {
            previous?.join()
            try {withContext(Dispatchers.IO){download(id)}}
            finally {if(active==id) {active=null;stopSelf(startId)}}
        }
        return START_NOT_STICKY
    }
    private suspend fun download(id:String) {
        val store=DownloadStore(this)
        val item=store.read().firstOrNull {it.id==id} ?: return
        if(PrivateMedia.hidden(item.url))return
        val folder=DownloadStore.folder(this,id).apply {mkdirs()};val part=File(folder,"pending.part")
        val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(20,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
        var failed=false;var complete=false
        try {
            var url=item.url;var response:okhttp3.Response?=null
            for(hop in 0..5) {
                currentCoroutineContext().ensureActive()
                val request=Request.Builder().url(url).header("User-Agent","EmberPlayer/0.1").apply {
                    if(part.length()>0&&item.validator.isNotBlank()) {header("Range","bytes=${part.length()}-");header("If-Range",item.validator)}
                }.build()
                val next=client.newCall(request);call=next
                val reply=next.execute()
                if(reply.code in listOf(301,302,303,307,308)) {
                    val location=reply.header("Location");val resolved=location?.let {reply.request.url.resolve(it)};reply.close()
                    require(resolved!=null&&DownloadStore.supported(resolved.toString()))
                    require(!(url.startsWith("https:")&&resolved.scheme!="https"))
                    require(resolved.username.isEmpty()&&resolved.password.isEmpty())
                    url=resolved.toString();continue
                }
                response=reply;break
            }
            val reply=response ?: error("Too many redirects")
            reply.use {
                check(it.code==200||it.code==206)
                val body=it.body
                val type=it.header("Content-Type").orEmpty().substringBefore(';').lowercase()
                require(type !in setOf("text/html","application/vnd.apple.mpegurl","application/x-mpegurl","application/dash+xml"))
                val offset=if(it.code==206)part.length() else 0L
                if(it.code==206)check(validPartial(it.header("Content-Range"),offset))
                if(offset==0L&&part.exists())check(part.delete())
                val total=if(it.code==206)it.header("Content-Range")!!.substringAfterLast('/').toLong() else body.contentLength()
                val validator=it.header("ETag")?.takeUnless {value->value.startsWith("W/")} ?: it.header("Last-Modified").orEmpty()
                require(validator.length<=512)
                if(it.code==206&&item.validator.isNotEmpty()&&validator!=item.validator) {
                    part.delete();store.change(id){value->value.copy(validator="",bytes=0)}
                    error("Source changed")
                }
                store.change(id){value->value.copy(state="downloading",bytes=offset,total=total,validator=validator)}
                var count=offset;var savedAt=0L
                body.byteStream().use {input->java.io.FileOutputStream(part,offset>0).use {output->
                    val buffer=ByteArray(65536)
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        check(!PrivateMedia.hidden(item.url))
                        val n=input.read(buffer);if(n<0)break
                        check(StatFs(folder.absolutePath).availableBytes>n+1024L*1024)
                        output.write(buffer,0,n);count+=n
                        if(SystemClock.elapsedRealtime()-savedAt>=1000) {
                            savedAt=SystemClock.elapsedRealtime()
                            store.change(id){value->value.copy(bytes=count)}
                            if(Build.VERSION.SDK_INT<33||checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)
                                getSystemService(NotificationManager::class.java).notify(730,notification(if(total>0)"Downloading · ${count*100/total}%" else "Downloading · ${count/1024/1024} MB"))
                        }
                    }
                    output.fd.sync()
                }}
                check(total<0||count==total)
                currentCoroutineContext().ensureActive();check(!PrivateMedia.hidden(item.url))
                check(part.renameTo(DownloadStore.file(this,item)))
                store.change(id){value->value.copy(state="complete",bytes=count,total=count)};complete=true
            }
        } catch(e:CancellationException){throw e}
          catch(_:Exception){failed=currentCoroutineContext().isActive}
        finally {
            call=null
            withContext(NonCancellable+Dispatchers.IO) {
                if(!complete)runCatching {store.change(id){it.copy(state=if(failed)"failed" else "paused",bytes=part.length())}}
                if(PrivateMedia.hidden(item.url))part.delete()
            }
            client.dispatcher.executorService.shutdown();client.connectionPool.evictAll()
        }
    }
    private fun notification(text:String):Notification {
        val pause=PendingIntent.getService(this,730,Intent(this,MediaDownloadService::class.java).setAction("pause"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,"downloads").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Ember Player").setContentText(text).setOngoing(true)
            .addAction(Notification.Action.Builder(null,"Pause",pause).build()).build()
    }
    override fun onDestroy(){call?.cancel();scope.cancel();super.onDestroy()}
    override fun onTimeout(startId:Int,fgsType:Int){call?.cancel();task?.cancel();stopSelf()}
    companion object {
        fun start(context:Context,id:String){context.startForegroundService(Intent(context,MediaDownloadService::class.java).putExtra("id",id))}
        fun pause(context:Context){context.startService(Intent(context,MediaDownloadService::class.java).setAction("pause"))}
        fun validPartial(header:String?,offset:Long):Boolean=Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(header.orEmpty())?.let {
            val start=it.groupValues[1].toLongOrNull();val end=it.groupValues[2].toLongOrNull()
            val total=it.groupValues[3].toLongOrNull()
            start==offset&&end!=null&&end>=offset&&total!=null&&total>0&&end==total-1
        } ?: false
    }
}
