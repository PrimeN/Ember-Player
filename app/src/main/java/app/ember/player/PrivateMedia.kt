package app.ember.player

import android.app.*
import android.content.Context
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import app.ember.tv.player.*
import app.ember.tv.data.LegacyCrashLog
import app.ember.player.storage.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@Serializable internal data class PrivateItem(val uri:String,val name:String,val folder:Boolean=false,val aliases:List<String> = emptyList())
@Serializable internal data class PrivacyData(val pin:String="",val items:List<PrivateItem> = emptyList(),val history:Boolean=true,val failures:Int=0,val retryAfter:Long=0,val retryElapsed:Long=0,val retryBoot:Int=-1)

internal object PrivatePinRetry {
    const val DELAY=60_000L
    fun normalized(value:PrivacyData,now:Long,boot:Int):PrivacyData {
        val pending=value.retryElapsed>0 || value.retryAfter>0
        val remaining=when {
            !pending->0L
            value.retryAfter>0 || value.retryBoot!=boot->DELAY
            else->(value.retryElapsed-now).coerceIn(0,DELAY)
        }
        return value.copy(retryAfter=0,retryElapsed=if(remaining>0)now+remaining else 0,retryBoot=boot)
    }
}

internal object PrivatePin {
    private fun derive(pin:CharArray,salt:ByteArray,iterations:Int=600000):ByteArray {
        val spec=PBEKeySpec(pin,salt,iterations,256)
        return try {SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded} finally {spec.clearPassword()}
    }
    fun valid(pin:String)=pin.length in 6..12 && pin.all {it in '0'..'9'}
    fun create(pin:String):String {
        require(valid(pin))
        val salt=ByteArray(16).also {SecureRandom().nextBytes(it)};val chars=pin.toCharArray()
        return try {"v2:"+Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(chars,salt))} finally {chars.fill('\u0000')}
    }
    fun needsUpgrade(record:String)=!record.startsWith("v2:")
    private fun fields(record:String):List<String>? {
        val parts=record.split(':')
        return when {parts.size==2->parts;parts.size==3&&parts[0]=="v2"->parts.drop(1);else->null}
    }
    fun validRecord(record:String)=runCatching {val parts=fields(record) ?: return false
        Base64.getDecoder().decode(parts[0]).size==16&&Base64.getDecoder().decode(parts[1]).size==32
    }.getOrDefault(false)
    fun verify(pin:String,record:String):Boolean=runCatching {
        if(!valid(pin))return false
        val fields=fields(record) ?: return false
        val salt=Base64.getDecoder().decode(fields[0]);val expected=Base64.getDecoder().decode(fields[1])
        if(salt.size!=16||expected.size!=32)return false
        val chars=pin.toCharArray()
        try {val actual=derive(chars,salt,if(needsUpgrade(record))120000 else 600000);try {MessageDigest.isEqual(expected,actual)} finally {actual.fill(0)}} finally {chars.fill('\u0000')}
    }.getOrDefault(false)
}

internal class PrivateSession {
    val unlocked=MutableStateFlow(false)
    private var epoch=0L
    private val guard=Any()
    fun token():Long=synchronized(guard){epoch}
    fun unlock(token:Long):Boolean=synchronized(guard){if(token!=epoch)false else {unlocked.value=true;true}}
    fun lock()=synchronized(guard){epoch++;unlocked.value=false}
    fun change(token:Long,requireUnlocked:Boolean,action:()->Unit):Boolean=synchronized(guard) {
        if(token!=epoch || requireUnlocked&&!unlocked.value)false else {action();true}
    }
}

/** Document IDs stay stable across tree and single-document URI forms. */
internal object PrivateMediaPolicy {
    internal fun identity(value:String):Pair<String,String>?=runCatching {
        val uri=Uri.parse(value)
        if(uri.scheme=="content"&&uri.authority=="media"&&uri.pathSegments.lastOrNull()?.toLongOrNull()!=null)return@runCatching "media" to (uri.pathSegments.first().lowercase(java.util.Locale.ROOT)+":"+uri.pathSegments.last())
        val id=if(uri.scheme=="content")runCatching {DocumentsContract.getDocumentId(uri)}.getOrNull()
            ?: runCatching {DocumentsContract.getTreeDocumentId(uri)}.getOrNull() else null
        id?.let {val normalized=if(uri.authority=="com.android.externalstorage.documents"&&':' in it)it.substringBefore(':').lowercase(java.util.Locale.ROOT)+":"+it.substringAfter(':') else it;uri.authority.orEmpty() to normalized}
    }.getOrNull()
    internal fun treeIdentity(value:String):Pair<String,String>?=runCatching {
        val uri=Uri.parse(value)
        val root=DocumentsContract.getTreeDocumentId(uri)
        identity(DocumentsContract.buildDocumentUri(uri.authority,root).toString())
    }.getOrNull()
    fun contains(item:PrivateItem,value:String):Boolean {
        if(item.aliases.any {contains(item.copy(uri=it,aliases=emptyList()),value)})return true
        if(item.uri==value)return true
        val a=identity(item.uri);val b=identity(value)
        if(item.folder&&a!=null&&a==treeIdentity(value))return true
        if(a!=null&&b!=null&&a.first==b.first)return a.second==b.second || item.folder &&
            (b.second.startsWith(a.second.trimEnd('/')+"/") || a.second.endsWith(':')&&b.second.startsWith(a.second))
        return false
    }
}

/** Normalize protected identities once; browsing only normalizes the candidate URI. */
internal class PrivateMediaIndex(items:List<PrivateItem>) {
    private val exact=items.flatMap {listOf(it.uri)+it.aliases}.toHashSet()
    private val documents=exact.mapNotNull(PrivateMediaPolicy::identity).toHashSet()
    private val folders=items.filter {it.folder}.flatMap {item->(listOf(item.uri)+item.aliases).mapNotNull(PrivateMediaPolicy::identity)}.groupBy({it.first},{it.second})
    fun hidden(uri:String):Boolean {
        if(uri in exact)return true
        val id=PrivateMediaPolicy.identity(uri) ?: return false
        if(id in documents)return true
        val roots=folders[id.first].orEmpty()
        if(roots.isNotEmpty()&&PrivateMediaPolicy.treeIdentity(uri)?.let {it.first==id.first&&it.second in roots}==true)return true
        return roots.any {root->id.second.startsWith(root.trimEnd('/')+"/") || root.endsWith(':')&&id.second.startsWith(root)}
    }
}

internal object PrivacyRecords {
    private val json=Json {ignoreUnknownKeys=true}
    fun decode(raw:String):PrivacyData {
        require(raw.length<=1024*1024)
        return json.decodeFromString<PrivacyData>(raw).also(::validate)
    }
    fun validate(value:PrivacyData) {
        require(value.items.size<=256 && value.failures in 0..10 && value.retryAfter>=0 && value.retryElapsed>=0 && value.retryBoot>=-1)
        if(value.pin.isNotEmpty()) {
            require(value.pin.length<=100)
            require(PrivatePin.validRecord(value.pin))
        } else require(value.items.isEmpty())
        require(value.items.all {item->item.name.isNotBlank()&&item.name.length<=180&&item.uri.length<=16384&&ExternalMedia.allowed(item.uri)&&
            (!item.folder || item.uri.startsWith("content://"))&&item.aliases.size<=8&&item.aliases.all {it.length<=16384&&it.startsWith("content://")&&ExternalMedia.allowed(it)}})
    }
}

internal object PrivateMedia {
    const val KEY="player.privacy"
    private val json=Json {ignoreUnknownKeys=true}
    val state=MutableStateFlow(PrivacyData())
    private val session=PrivateSession()
    val unlocked=session.unlocked
    private lateinit var app:Context
    private var initialized=false
    @Volatile private var index=PrivateMediaIndex(emptyList())
    @Volatile private var unavailable=false
    private val handler=Handler(Looper.getMainLooper())
    private var started=0
    @Volatile private var pickerUntil=0L
    val enabled get()=state.value.pin.isNotEmpty()||unavailable
    fun initialize(context:Context) {if(!initialized){app=context.applicationContext;initialized=true;reload(context)}}
    @Synchronized fun reload(context:Context) {
        app=context.applicationContext
        val marker=app.getSharedPreferences("playerSecurity",Context.MODE_PRIVATE).getBoolean("enabled",false)
        val raw=TokenStore(app).get(KEY)
        val value=raw?.takeIf {it.length<=1024*1024}?.let {runCatching {json.decodeFromString<PrivacyData>(it).also(::validate)}.getOrNull()}
        unavailable=(raw!=null&&value==null)||(marker&&value==null)
        index=PrivateMediaIndex(value?.items.orEmpty())
        state.value=value ?: PrivacyData();lock()
        if(value!=null) {
            val normalized=PrivatePinRetry.normalized(value,SystemClock.elapsedRealtime(),bootCount())
            if(normalized!=value)save(normalized)
        }
        if(value!=null)app.getSharedPreferences("playerSecurity",Context.MODE_PRIVATE).edit().putBoolean("enabled",value.pin.isNotEmpty()).commit()
    }
    private fun validate(value:PrivacyData)=PrivacyRecords.validate(value)
    @Synchronized private fun save(value:PrivacyData) {
        validate(value)
        check(TokenStore(app).putEncrypted(KEY,json.encodeToString(value))){"Private storage is unavailable. No privacy change was saved."}
        check(app.getSharedPreferences("playerSecurity",Context.MODE_PRIVATE).edit().putBoolean("enabled",value.pin.isNotEmpty()).commit())
        index=PrivateMediaIndex(value.items)
        state.value=value
    }
    internal val libraryVisibility get()=unavailable to state.value.items
    fun hidden(uri:String)=unavailable || index.hidden(uri)
    fun historyAllowed(uri:String)=state.value.history&&!hidden(uri)
    @Synchronized fun configure(pin:String,currentPin:String?=null):Boolean {
        val changing=enabled
        check(!changing || unlocked.value);check(!unavailable)
        val ticket=session.token()
        if(changing && (currentPin==null || !authenticate(currentPin)))return false
        val record=PrivatePin.create(pin)
        check(session.change(ticket,changing) {
            save(state.value.copy(pin=record,failures=0,retryAfter=0,retryElapsed=0))
            check(session.unlock(ticket))
        }) {"Private session closed. Unlock again."}
        return true
    }
    private fun bootCount()=runCatching {android.provider.Settings.Global.getInt(app.contentResolver,android.provider.Settings.Global.BOOT_COUNT,-1)}.getOrDefault(-1)
    @Synchronized fun authenticate(pin:String,now:Long=SystemClock.elapsedRealtime()):Boolean {
        check(!unavailable){"Private storage cannot be unlocked on this device right now."}
        val ticket=session.token()
        val current=PrivatePinRetry.normalized(state.value,now,bootCount())
        if(current!=state.value)save(current)
        check(current.retryElapsed<=now){"Too many attempts. Wait before trying again."}
        val okay=PrivatePin.verify(pin,current.pin)
        val failures=if(okay)0 else (current.failures+1).coerceAtMost(10)
        val upgraded=if(okay&&PrivatePin.needsUpgrade(current.pin))PrivatePin.create(pin) else current.pin
        save(current.copy(pin=upgraded,failures=failures,retryAfter=0,retryElapsed=if(failures>=5)now+PrivatePinRetry.DELAY else 0))
        if(okay)check(session.unlock(ticket)){"Private session closed. Unlock again."}
        return okay
    }
    fun add(item:PrivateItem)=addAll(listOf(item))
    @Synchronized fun addAll(values:List<PrivateItem>) {
        check(enabled&&unlocked.value&&!unavailable)
        require(values.size in 1..256)
        val saved=values.map {item->
            require(ExternalMedia.allowed(item.uri)&&item.name.isNotBlank()&&item.name.length<=180&&item.uri.length<=16384)
            require(!item.folder || item.uri.startsWith("content://"))
            val mediaAlias=runCatching {val uri=Uri.parse(item.uri);val document=if(item.folder)DeviceFiles.root(uri) else uri;android.provider.MediaStore.getMediaUri(app,document)?.toString()}.getOrNull()
            item.copy(aliases=(item.aliases+listOfNotNull(mediaAlias)).distinct().filter {it.startsWith("content://")}.take(8))
        }.distinctBy {it.uri}
        val replacing=saved.mapTo(hashSetOf()) {it.uri}
        val items=state.value.items.filterNot {it.uri in replacing}+saved
        require(items.size<=256){"Private media is limited to 256 saved entries."}
        save(state.value.copy(items=items))
        // Removing old activity also removes titles from folder recents and saved resume markers.
        check(MediaHistory(app).purgeHidden())
        check(FolderPlaybackHistory(app).purgeHidden())
        DeviceThumbnails.clear()
        handler.post {BackgroundAudioService.stopPrivatePlayback()}
    }
    @Synchronized fun remove(uri:String){check(unlocked.value&&!unavailable);save(state.value.copy(items=state.value.items.filterNot {it.uri==uri}))}
    @Synchronized fun removeProtection(currentPin:String):Boolean {
        check(enabled&&unlocked.value&&!unavailable)
        val ticket=session.token()
        if(!authenticate(currentPin))return false
        check(session.change(ticket,true) {
            save(state.value.copy(pin="",items=emptyList(),failures=0,retryAfter=0,retryElapsed=0));lock()
        }) {"Private session closed. Unlock again."}
        return true
    }
    @Synchronized fun recordHistory(value:Boolean){check(!unavailable);save(state.value.copy(history=value));if(!value)clearHistory()}
    fun clearHistory() {
        check(LegacyCrashLog.clear(app))
        check(MediaHistory(app).clear())
        check(TokenStore(app).putEncrypted("player.folderRecents","{}"))
        check(PlaybackStateRetention.clearPositions(app))
        check(PlaybackBookmarks.clearAll(app))
        check(PlaybackStateRetention.clearTrackChoices(app))
        check(ManualSubtitleChoices.clearAll(app))
    }
    fun lock(){session.lock();pickerUntil=0;if(enabled)DeviceThumbnails.clear()}
    fun beginPicker(){check(unlocked.value);pickerUntil=SystemClock.elapsedRealtime()+120000}
    fun endPicker(){pickerUntil=0}
    fun install(application:Application) {
        initialize(application)
        application.registerActivityLifecycleCallbacks(object:Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(a:Activity){started++;handler.removeCallbacks(lockLater)}
            override fun onActivityStopped(a:Activity){started=(started-1).coerceAtLeast(0);if(started==0&&!a.isChangingConfigurations)handler.postDelayed(lockLater,350)}
            override fun onActivityCreated(a:Activity,b:Bundle?){}
            override fun onActivityResumed(a:Activity){}
            override fun onActivityPaused(a:Activity){}
            override fun onActivitySaveInstanceState(a:Activity,b:Bundle){}
            override fun onActivityDestroyed(a:Activity){}
        })
    }
    private val lockLater=object:Runnable {override fun run(){
        if(started>0)return
        val delay=pickerUntil-SystemClock.elapsedRealtime()
        if(delay>0)handler.postDelayed(this,delay) else lock()
    }}
}
