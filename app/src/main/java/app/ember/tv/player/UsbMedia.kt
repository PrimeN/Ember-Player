package app.ember.tv.player

import android.Manifest
import android.content.*
import android.net.Uri
import android.os.*
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.Closeable
import java.util.Locale

internal data class UsbDrive(val volume:String,val name:String)
internal data class UsbPage(val items:List<QueueMedia>,val next:Long?)

/** Uses mounted Android volumes, not raw USB protocols or filesystem drivers. */
internal object UsbMedia {
    const val PAGE_SIZE=100
    fun permissions(sdk:Int)=if(sdk>=34)arrayOf(Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_AUDIO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else if(sdk>=33)arrayOf(Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_AUDIO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun permitted(context:Context)=permissions(Build.VERSION.SDK_INT).any {ContextCompat.checkSelfPermission(context,it)==android.content.pm.PackageManager.PERMISSION_GRANTED}
    fun drives(context:Context):List<UsbDrive> = runCatching {
        val storage=context.getSystemService(StorageManager::class.java) ?: return@runCatching emptyList<UsbDrive>()
        storage.storageVolumes.filter {it.isRemovable && it.state in setOf(Environment.MEDIA_MOUNTED,Environment.MEDIA_MOUNTED_READ_ONLY)}.mapNotNull {volume->
            val name=if(Build.VERSION.SDK_INT>=30)volume.mediaStoreVolumeName else volume.uuid?.lowercase(Locale.ROOT)
            name?.takeIf {it !in setOf("external","external_primary","internal")}?.let {UsbDrive(it,volume.getDescription(context).take(120))}
        }
    }.getOrDefault(emptyList())
    fun volume(uri:String):String?=runCatching {Uri.parse(uri).takeIf {it.scheme=="content"&&it.authority=="media"}?.pathSegments?.firstOrNull()?.takeIf {it !in setOf("external","external_primary","internal")}}.getOrNull()
    fun page(context:Context,drive:UsbDrive,after:Long=0,signal:CancellationSignal=CancellationSignal(),check:()->Unit={}):UsbPage {
        require(after>=0)
        val collection=MediaStore.Files.getContentUri(drive.volume)
        val columns=arrayOf(MediaStore.Files.FileColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.RELATIVE_PATH)
        val args=Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION,"_id > ? AND media_type IN (?, ?) AND is_pending = 0"+(if(Build.VERSION.SDK_INT>=30)" AND is_trashed = 0" else ""))
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,arrayOf(after.toString(),MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString()))
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,"_id ASC")
            putInt(ContentResolver.QUERY_ARG_LIMIT,PAGE_SIZE+1)
        }
        val items=mutableListOf<QueueMedia>();var last:Long?=null;var scanned=0;var lastId=after
        context.contentResolver.query(collection,columns,args,signal)?.use {cursor->
            while(cursor.moveToNext()) {
                check();signal.throwIfCanceled()
                if(scanned==PAGE_SIZE){last=lastId;break}
                val id=cursor.getLong(0);if(id<=after)continue
                scanned++;lastId=id
                val fileName=cursor.getString(1)?.take(4096) ?: "Media"
                val name=fileName.take(180)
                val relative=cursor.getString(2).orEmpty()
                val document=android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents",drive.volume+":"+(if(relative.isBlank())"" else relative.trimEnd('/')+"/")+fileName).toString()
                if(app.ember.player.PrivateMedia.hidden(document))continue
                items+=QueueMedia(ContentUris.withAppendedId(collection,id).toString(),name)
            }
        } ?: error("This drive is not available to Android’s media library.")
        return UsbPage(items.sortedWith(compareBy(NaturalOrder){it.title}),last)
    }
    fun watch(context:Context,uri:String,onRemoved:()->Unit):Closeable? {
        val volume=volume(uri) ?: return null
        val app=context.applicationContext
        var notified=false;var closed=false
        val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,intent:Intent?){
            if(!closed&&!notified&&drives(app).none {it.volume==volume}){notified=true;onRemoved()}
        }}
        val filter=IntentFilter().apply {
            listOf(Intent.ACTION_MEDIA_REMOVED,Intent.ACTION_MEDIA_UNMOUNTED,Intent.ACTION_MEDIA_BAD_REMOVAL,Intent.ACTION_MEDIA_EJECT).forEach(::addAction)
            addDataScheme("file")
        }
        try {ContextCompat.registerReceiver(app,receiver,filter,ContextCompat.RECEIVER_NOT_EXPORTED)}catch(_:SecurityException){return null}
        return Closeable {if(!closed){closed=true;runCatching {app.unregisterReceiver(receiver)}}}
    }
}
