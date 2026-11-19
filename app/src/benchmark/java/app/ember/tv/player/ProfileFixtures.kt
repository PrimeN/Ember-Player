package app.ember.tv.player

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Synthetic media belongs only to the optional profiling variant, never a distributable APK. */
class ProfileFixtures:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val result=goAsync()
        Thread {
            try {
                val entries=(1..60).map {QueueMedia("content://${context.packageName}.fixtures/media/$it","Sample episode $it")}
                check(MediaHistory(context).add(entries))
                val positions=PositionStore(context,app.ember.tv.data.LocalActivity.scope(context))
                entries.take(8).forEach {positions.put(it.key,60_000,120_000)}
                result.setResultCode(Activity.RESULT_OK)
            } catch(_:Exception) {result.setResultCode(Activity.RESULT_CANCELED)}
            finally {result.finish()}
        }.start()
    }
}

class ProfileMedia:ContentProvider() {
    private lateinit var sample:File
    override fun onCreate():Boolean {
        val app=checkNotNull(context)
        sample=File(app.cacheDir,"profile-sample.mp4")
        if(!sample.exists())app.assets.open("profile-sample.mp4").use {input->sample.outputStream().use {input.copyTo(it)}}
        return true
    }
    override fun getType(uri:Uri)="video/mp4"
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
        val columns=projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {addRow(columns.map {when(it){OpenableColumns.DISPLAY_NAME->"Sample episode ${uri.lastPathSegment}.mp4";OpenableColumns.SIZE->sample.length();else->null}})}
    }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        require(mode=="r")
        return ParcelFileDescriptor.open(sample,ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException()
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
}
