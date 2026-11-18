package app.ember.player

import app.ember.tv.R

import android.app.*
import android.content.*
import android.os.*
import androidx.core.content.FileProvider
import app.ember.player.storage.TokenStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
internal data class MediaDownload(val id:String,val url:String,val name:String,val state:String="paused",val bytes:Long=0,val total:Long=-1,val validator:String="",val session:String="")
internal class DownloadStore(private val context:Context) {
    private fun load():List<MediaDownload> {
        val raw=TokenStore(context).getForUpdate(KEY) ?: return emptyList()
        require(raw.length<=1024*1024) {"Saved downloads could not be read."}
        val values=Json.decodeFromString<List<MediaDownload>>(raw)
        require(values.size<=50&&values.all(::valid)&&values.distinctBy {it.id}.size==values.size) {"Saved downloads could not be read."}
        return values.map {
            when {
                it.state=="complete"&&!file(context,it).isFile->it.copy(state="paused",bytes=0,total=-1,validator="",session="")
                it.state in setOf("queued","downloading")&&it.session!=SESSION->it.copy(state="paused",session="")
                else->it
            }
        }
    }
    fun resetUnreadable():Boolean=synchronized(lock) {
        app.ember.player.storage.resetUnreadableList(::load) {
            MediaDownloadService.pause(context)
            save(emptyList());true
        }
    }
    fun readForDisplay():List<MediaDownload> = load()
    fun read():List<MediaDownload> = runCatching {load()}.getOrDefault(emptyList())
    fun add(url:String,name:String=""):MediaDownload=synchronized(lock) {
        require(supported(url)) {"Use a direct HTTP or HTTPS media file, rather than a playlist or protected stream."}
        val values=load();check(values.size<50) {"Remove an old download first."}
        val item=MediaDownload(java.util.UUID.randomUUID().toString(),url,safeName(name.ifBlank {suggestedName(url)}))
        save(values+item);item
    }
    fun change(id:String,change:(MediaDownload)->MediaDownload)=synchronized(lock){save(load().map {if(it.id==id)change(it) else it})}
    fun remove(id:String)=synchronized(lock){save(load().filterNot {it.id==id});folder(context,id).deleteRecursively();Unit}
    private fun save(values:List<MediaDownload>){
        val raw=Json.encodeToString(values)
        require(values.size<=50&&values.all(::valid)&&raw.length<=1024*1024)
        check(TokenStore(context).putEncrypted(KEY,raw)) {"Encrypted download storage is unavailable."}
    }
    companion object {
        private val lock=Any();private const val KEY="player.downloads"
        val SESSION=java.util.UUID.randomUUID().toString()
        fun supported(url:String)=url.length in 1..16384 && runCatching {
            val uri=java.net.URI(url);uri.scheme?.lowercase() in setOf("http","https") && !uri.host.isNullOrBlank() &&
                uri.fragment==null && uri.path.orEmpty().substringAfterLast('.').lowercase() !in setOf("m3u8","m3u","mpd","ism")
        }.getOrDefault(false)
        fun safeName(name:String)=name.substringAfterLast('/').substringAfterLast('\\').filter {it.isLetterOrDigit()||it in " ._-"}.trim().take(120).takeUnless {it.isEmpty()||it in setOf(".","..")} ?: "media.mp4"
        fun suggestedName(url:String):String {
            val segment=url.trim().toHttpUrlOrNull()?.pathSegments?.lastOrNull().orEmpty()
            return segment.takeIf {it.isNotBlank()&&it !in setOf(".","..")&&it.any(Char::isLetterOrDigit)}?.let(::safeName)?.let {if(it.substringAfterLast('.',"").matches(Regex("[A-Za-z0-9]{1,10}")))it else it.take(116)+".mp4"} ?: "media.mp4"
        }
        private fun valid(item:MediaDownload)=item.id.matches(Regex("[a-f0-9-]{36}"))&&supported(item.url)&&item.name==safeName(item.name)&&item.bytes>=0&&item.total>=-1&&item.validator.length<=512&&item.state in setOf("paused","queued","downloading","complete","failed")
        fun folder(context:Context,id:String):File {require(id.matches(Regex("[a-f0-9-]{36}")));return File(context.filesDir,"offline-media/$id")}
        fun file(context:Context,item:MediaDownload)=File(folder(context,item.id),item.name)
        fun uri(context:Context,item:MediaDownload)=FileProvider.getUriForFile(context,context.packageName+".downloads",file(context,item))
    }
}

/** Downloads begin only from an explicit user action. URLs and validators stay encrypted. */
class MediaDownloadService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var task:Job?=null
    @Volatile private var call:okhttp3.Call?=null
    private var active:String?=null
    private var preparing:String?=null
    private val waiting=linkedSetOf<String>()
    private data class Command(val action:String,val id:String?=null,val startId:Int=0,val run:Long=0)
    private val commands=Channel<Command>(Channel.UNLIMITED)
    private var processedStartId=0
    private var activeRun=0L
    @Volatile private var progressText=""
    override fun onBind(intent:Intent?)=null
    override fun onCreate() {
        super.onCreate()
        progressText=getString(R.string.ui_preparing_download)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("downloads",resources.getString(R.string.ui_media_downloads),NotificationManager.IMPORTANCE_LOW))
        scope.launch {
            try {
                for(command in commands) {
                    if(command.startId>0)processedStartId=command.startId
                    when(command.action) {
                        "start"-> {
                            val id=command.id ?: continue
                            if(id==active||id in waiting)continue
                            preparing=id
                            val queued=withContext(Dispatchers.IO) {
                                runCatching {
                                    val store=DownloadStore(this@MediaDownloadService)
                                    val item=store.read().firstOrNull {it.id==id}
                                    if(item==null||item.state=="complete"||PrivateMedia.hidden(item.url)||PrivateMedia.hidden(DownloadStore.uri(this@MediaDownloadService,item).toString()))false
                                    else {store.change(id){it.copy(state="queued",session=DownloadStore.SESSION)};true}
                                }.getOrDefault(false)
                            }
                            if(queued)waiting+=id
                            preparing=null
                            advance()
                        }
                        "finished"->if(active==command.id&&activeRun==command.run){active=null;task=null;advance()}
                        "pause"-> {
                            val paused=waiting.toList();waiting.clear()
                            task?.cancel();call?.cancel();task?.join();task=null;active=null
                            withContext(Dispatchers.IO){paused.forEach {id->runCatching {DownloadStore(this@MediaDownloadService).change(id){it.copy(state="paused")}}}}
                            advance()
                        }
                        "remove"-> {
                            val id=command.id ?: continue
                            waiting-=id
                            if(active==id){task?.cancel();call?.cancel();task?.join();task=null;active=null}
                            withContext(Dispatchers.IO){runCatching {DownloadStore(this@MediaDownloadService).remove(id)}}
                            getSystemService(NotificationManager::class.java).cancel("download:$id",731)
                            advance()
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    task?.cancel();call?.cancel();task?.join()
                    val paused=(waiting+listOfNotNull(active,preparing)).distinct();waiting.clear()
                    withContext(Dispatchers.IO){paused.forEach {id->runCatching {DownloadStore(this@MediaDownloadService).change(id){if(it.state in setOf("queued","downloading"))it.copy(state="paused") else it}}}}
                }
            }
        }
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        val action=intent?.action?.takeIf {it in setOf("pause","remove")} ?: "start"
        val id=intent?.getStringExtra("id")
        if(action=="start"&&id==null){stopSelf(startId);return START_NOT_STICKY}
        if(action=="start")startForeground(730,notification(progressText))
        commands.trySend(Command(action,id,startId))
        return START_NOT_STICKY
    }
    private fun advance() {
        if(active!=null)return
        val id=waiting.firstOrNull()
        if(id==null){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(processedStartId);return}
        waiting-=id;active=id;progressText=resources.getString(R.string.ui_preparing_download)
        startForeground(730,notification(progressText))
        val run=++activeRun
        task=scope.launch(start=CoroutineStart.LAZY) {
            try {withContext(Dispatchers.IO){download(id)}}
            catch(e:CancellationException){throw e}
            catch(_:Exception){withContext(NonCancellable+Dispatchers.IO){runCatching {DownloadStore(this@MediaDownloadService).change(id){it.copy(state="failed")}}}}
            finally {commands.trySend(Command("finished",id,run=run))}
        }.also {it.start()}
    }
    private suspend fun download(id:String) {
        val store=DownloadStore(this)
        val item=store.read().firstOrNull {it.id==id} ?: return
        val localUri=DownloadStore.uri(this,item).toString()
        if(PrivateMedia.hidden(item.url)||PrivateMedia.hidden(localUri)){store.change(id){it.copy(state="paused")};return}
        val folder=DownloadStore.folder(this,id).apply {mkdirs()};val part=File(folder,"pending.part")
        val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(20,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
        var failed=false;var complete=false
        try {
            var url=item.url;var response:okhttp3.Response?=null
            for(hop in 0..5) {
                currentCoroutineContext().ensureActive()
                val request=Request.Builder().url(url).header("User-Agent",app.ember.tv.data.HttpIdentity.userAgent).apply {
                    if(part.length()>0&&item.validator.isNotBlank()) {header("Range","bytes=${part.length()}-");header("If-Range",item.validator)}
                }.build()
                val next=client.newCall(request);call=next
                currentCoroutineContext().ensureActive()
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
                        check(!PrivateMedia.hidden(item.url)&&!PrivateMedia.hidden(localUri))
                        val n=input.read(buffer);if(n<0)break
                        check(StatFs(folder.absolutePath).availableBytes>n+1024L*1024)
                        output.write(buffer,0,n);count+=n
                        if(SystemClock.elapsedRealtime()-savedAt>=1000) {
                            savedAt=SystemClock.elapsedRealtime()
                            store.change(id){value->value.copy(bytes=count)}
                            progressText=if(total>0)resources.getString(R.string.ui_downloading ,(count*100/total).toString()) else resources.getString(R.string.ui_downloading_mb ,(count/1024/1024).toString())
                            if(notificationsAllowed())getSystemService(NotificationManager::class.java).notify(730,notification(progressText))
                        }
                    }
                    output.fd.sync()
                }}
                check(total<0||count==total)
                currentCoroutineContext().ensureActive();check(!PrivateMedia.hidden(item.url)&&!PrivateMedia.hidden(localUri))
                check(part.renameTo(DownloadStore.file(this,item)))
                store.change(id){value->value.copy(state="complete",bytes=count,total=count)};complete=true
                notifyComplete(item)
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
        return Notification.Builder(this,"downloads").setSmallIcon(R.drawable.ic_player_notification).setContentTitle(resources.getString(R.string.ui_ember_player)).setContentText(text).setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null,resources.getString(R.string.ui_pause_downloads),pause).build()).build()
    }
    private fun notificationsAllowed()=Build.VERSION.SDK_INT<33||checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED
    private fun notifyComplete(item:MediaDownload) {
        if(!notificationsAllowed()||PrivateMedia.hidden(item.url))return
        val uri=DownloadStore.uri(this,item)
        if(PrivateMedia.hidden(uri.toString()))return
        val intent=Intent(this,OpenMediaActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(uri,"application/octet-stream")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val open=PendingIntent.getActivity(this,item.id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,"downloads").setSmallIcon(R.drawable.ic_player_notification)
            .setContentTitle(resources.getString(R.string.ui_ember_player)).setContentText(resources.getString(R.string.ui_download_complete)).setContentIntent(open).setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build()
        runCatching {getSystemService(NotificationManager::class.java).notify("download:${item.id}",731,notification)}
    }
    override fun onDestroy(){commands.close();scope.cancel();call?.cancel();super.onDestroy()}
    override fun onTimeout(startId:Int,fgsType:Int){task?.cancel();call?.cancel();stopSelf()}
    companion object {
        fun start(context:Context,id:String){context.startForegroundService(Intent(context,MediaDownloadService::class.java).putExtra("id",id))}
        fun pause(context:Context){context.startService(Intent(context,MediaDownloadService::class.java).setAction("pause"))}
        fun remove(context:Context,id:String){context.startService(Intent(context,MediaDownloadService::class.java).setAction("remove").putExtra("id",id))}
        fun validPartial(header:String?,offset:Long):Boolean=Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(header.orEmpty())?.let {
            val start=it.groupValues[1].toLongOrNull();val end=it.groupValues[2].toLongOrNull()
            val total=it.groupValues[3].toLongOrNull()
            start==offset&&end!=null&&end>=offset&&total!=null&&total>0&&end==total-1
        } ?: false
    }
}
