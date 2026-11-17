package app.ember.tv.player

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.ember.tv.data.*
import app.ember.player.storage.TokenStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class ManualSubtitleFile(val name:String,val uris:List<String> = emptyList(),val network:NetworkQueueEntry?=null)

/** A remembered subtitle contains a document grant or account reference, never a relay URL. */
internal class ManualSubtitleChoices(private val readValue:()->String?,private val writeValue:(String)->Boolean) {
    constructor(context:Context,profile:String):this(
        {TokenStore(context.applicationContext).get("player.subtitleChoices.$profile")},
        {TokenStore(context.applicationContext).putEncrypted("player.subtitleChoices.$profile",it)},
    )
    private fun read():Map<String,ManualSubtitleFile> = readValue()?.takeIf {it.length<=1024*1024}?.let {raw->
        runCatching {json.decodeFromString<Map<String,ManualSubtitleFile>>(raw)}.getOrNull()?.filter {(key,value)->
            key.matches(Regex("[a-f0-9]{64}")) && valid(value)
        }?.entries?.toList()?.takeLast(256)?.associate {it.toPair()}
    }.orEmpty()
    fun get(key:String)=read()[CachePolicy.digest(key)]
    fun put(key:String,value:ManualSubtitleFile):Boolean=synchronized(lock) {
        if(!valid(value))return@synchronized false
        val digest=CachePolicy.digest(key)
        val updated=(read().filterKeys {it!=digest}+(digest to value)).entries.toList().takeLast(256).associate {it.toPair()}
        val raw=json.encodeToString(updated)
        raw.length<=1024*1024 && writeValue(raw)
    }
    fun remove(key:String):Boolean=synchronized(lock) {writeValue(json.encodeToString(read().filterKeys {it!=CachePolicy.digest(key)}))}
    companion object {
        private val json=Json {ignoreUnknownKeys=true};private val lock=Any()
        fun valid(value:ManualSubtitleFile):Boolean {
            if(value.name.isBlank() || value.name.length>180 || value.name.any {it.isISOControl()})return false
            if(value.network!=null)return value.uris.isEmpty() && SavedPlaylists.valid(SavedPlaylist("subtitle","Subtitle",listOf(PlaylistEntry("",value.name,network=value.network))))
            return value.uris.size in 1..2 && value.uris.all {it.length<=16384 && runCatching {val uri=Uri.parse(it);uri.scheme=="content" && !uri.authority.isNullOrBlank()}.getOrDefault(false)}
        }
    }
}

internal object NearbySubtitles {
    private val json=Json {ignoreUnknownKeys=true}
    private val textExtensions=setOf("srt","vtt","ass","ssa","ttml","dfxp","smi","sami","sub")
    fun matches(video:String,subtitle:String):Boolean {
        if(subtitle.substringAfterLast('.').lowercase() !in textExtensions)return false
        val stem=video.substringBeforeLast('.',video).lowercase()
        val candidate=subtitle.substringBeforeLast('.').lowercase()
        if(stem.isBlank())return false
        return candidate==stem || candidate.startsWith(stem) && candidate.drop(stem.length).firstOrNull() in listOf('.','_','-',' ')
    }
    fun folders(context:Context,profile:String)=DeviceFolders(
        {TokenStore(context.applicationContext).get("player.subtitleFolders.$profile")},
        {TokenStore(context.applicationContext).putEncrypted("player.subtitleFolders.$profile",it)},
    )
    fun localChoice(context:Context,uris:List<Uri>):ManualSubtitleFile {
        val named=uris.take(16).map {it to MediaFiles.name(context,it)}
        val sup=named.firstOrNull {it.second.endsWith(".sup",true)}
        val idx=named.firstOrNull {it.second.endsWith(".idx",true)}
        val sub=named.firstOrNull {it.second.endsWith(".sub",true)}
        val chosen=when {sup!=null->listOf(sup);idx!=null&&sub!=null->listOf(idx,sub);else->listOf(named.first())}
        chosen.forEach {MediaFiles.retain(context,it.first)}
        return ManualSubtitleFile(chosen.first().second,chosen.map {it.first.toString()})
    }
    private fun accounts(context:Context,profile:String):Map<String,NetworkAccount> {
        val raw=TokenStore(context).get("media.network.$profile") ?: "[]"
        require(raw.length<=1024*1024)
        return json.decodeFromString<List<NetworkAccount>>(raw).associateBy {it.id}
    }
    fun find(context:Context,profile:String,media:QueueMedia,check:()->Unit={}):List<ManualSubtitleFile> {
        val result=mutableListOf<ManualSubtitleFile>()
        try {media.network?.let {reference->
            val account=accounts(context,profile)[reference.accountId] ?: return@let
            val directory=reference.path.substringBeforeLast('/',"")
            var offset:Int?=0;var pages=0
            while(offset!=null && pages++<8 && result.size<24) {
                check()
                val page=NetworkFiles.listPage(account,directory,offset,check)
                page.entries.filter {!it.directory && it.size in 1..SubtitleFiles.MAX_DOWNLOAD.toLong() && matches(media.title,it.name)}.forEach {entry->
                    result+=ManualSubtitleFile(entry.name,network=NetworkQueueEntry(account.id,NetworkLocation.child(directory,entry.name),entry.size))
                }
                offset=page.next
            }
        }
        } catch(e:kotlinx.coroutines.CancellationException){throw e} catch(_:Exception){}
        val directories=mutableListOf<Uri>()
        media.directory?.let {runCatching {Uri.parse(it)}.getOrNull()}?.let(directories::add)
        val video=runCatching {Uri.parse(media.uri)}.getOrNull()
        if(video?.scheme=="content") {
            val id=runCatching {DocumentsContract.getDocumentId(video)}.getOrNull()
            val parent=id?.let {if('/' in it)it.substringBeforeLast('/') else if(':' in it)it.substringBefore(':')+":" else null}
            if(parent!=null)DeviceFolders(context).read().forEach {folder->
                val tree=Uri.parse(folder.uri)
                val root=runCatching {DocumentsContract.getTreeDocumentId(tree)}.getOrNull()
                if(tree.authority==video.authority && root!=null && (parent==root || parent.startsWith(root.trimEnd('/')+"/") || root.endsWith(':')&&parent.startsWith(root)))
                    directories+=DocumentsContract.buildDocumentUriUsingTree(tree,parent)
            }
        }
        folders(context,profile).read().forEach {folder->directories+=DeviceFiles.root(Uri.parse(folder.uri))}
        val videoName=if(video?.scheme=="content")MediaFiles.name(context,video) else media.title
        for(directory in directories.distinct().take(20)) {
            check()
            try {
                val uri=DocumentsContract.buildChildDocumentsUriUsingTree(directory,DocumentsContract.getDocumentId(directory))
                context.contentResolver.query(uri,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),android.os.Bundle.EMPTY,null)?.use {cursor->
                    var count=0
                    while(cursor.moveToNext() && ++count<=4000 && result.size<40) {
                        check()
                        val id=cursor.getString(0) ?: continue;val name=cursor.getString(1) ?: continue
                        if(matches(videoName,name))result+=ManualSubtitleFile(name.take(180),listOf(DocumentsContract.buildDocumentUriUsingTree(directory,id).toString()))
                    }
                }
            } catch(e:kotlinx.coroutines.CancellationException){throw e} catch(_:Exception){}
        }
        return result.distinct().sortedBy {it.name.lowercase()}.take(40)
    }
    fun networkText(context:Context,profile:String,choice:ManualSubtitleFile,config:SubtitleConfig):Pair<String,String> {
        val ref=checkNotNull(choice.network)
        val account=accounts(context,profile)[ref.accountId] ?: error("Subtitle connection unavailable.")
        val bytes=NetworkFiles.resource(account,ref.path,ref.size).open(0).use {SubtitleFiles.read(it)}
        val text=SubtitleEncodings.decode(bytes,config.languages.firstOrNull() ?: java.util.Locale.getDefault().language,config.encoding)
        return text to SubtitleFiles.format(choice.name,text.toByteArray())
    }
}
