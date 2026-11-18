package app.ember.tv.player

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import app.ember.player.PrivateMedia
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.security.MessageDigest

internal data class LibraryScanState(val active:Boolean=false,val scanned:Int=0,val revision:Int=0,val result:ScanResult?=null)
internal enum class ScanResult { COMPLETE, EMPTY, FAILED, UNAVAILABLE }

/** A short freshness window avoids rewalking large trees after every playback return. */
internal object MediaLibraryScanner {
    const val JOB_ID=741
    internal const val MAX_AGE=15*60_000L
    val state=MutableStateFlow(LibraryScanState())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val lock=Any()
    internal fun fingerprint(folders:List<DeviceFolder>):String=MessageDigest.getInstance("SHA-256")
        .digest(folders.map {it.uri+":"+PrivateMedia.hidden(it.uri)}.sorted().joinToString("\n").toByteArray())
        .joinToString(""){"%02x".format(it)}
    internal fun stale(now:Long,last:Long,saved:String?,current:String)=last<=0 || now<last || now-last>=MAX_AGE || saved!=current
    fun request(context:Context,force:Boolean=false):Job {
        val app=context.applicationContext
        return scope.launch {
            try {
                val folders=DeviceFolders(app).readForDisplay()
                val signature=fingerprint(folders)
                synchronized(lock) {
                    val saved=app.getSharedPreferences("libraryScan",Context.MODE_PRIVATE)
                    if(!force && !stale(System.currentTimeMillis(),saved.getLong("completed",0),saved.getString("roots",null),signature) &&
                        (folders.isEmpty() || app.getDatabasePath("media-library.db").exists()))return@synchronized
                    val scheduler=app.getSystemService(JobScheduler::class.java)
                    if(!force && scheduler.getPendingJob(JOB_ID)!=null)return@synchronized
                    check(scheduler.schedule(JobInfo.Builder(JOB_ID,ComponentName(app,MediaLibraryScanJob::class.java)).setMinimumLatency(0).build())==JobScheduler.RESULT_SUCCESS)
                    state.update {it.copy(active=true,scanned=0,result=null)}
                }
            } catch(e:CancellationException){throw e}
              catch(_:Exception){state.update {it.copy(active=false,result=ScanResult.FAILED)}}
        }
    }
    fun cancel(context:Context) {
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        state.update {it.copy(active=false,result=null)}
    }
    internal fun completed(context:Context,folders:List<DeviceFolder>) {
        check(context.getSharedPreferences("libraryScan",Context.MODE_PRIVATE).edit()
            .putString("roots",fingerprint(folders)).putLong("completed",System.currentTimeMillis()).commit())
    }
}

/** JobScheduler owns the scan; closing Folders or the library activity cannot cancel it. */
class MediaLibraryScanJob:JobService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var scan:Job?=null
    private var visit=0
    override fun onStartJob(params:JobParameters):Boolean {
        val current=++visit
        val before=scan;before?.cancel()
        scan=scope.launch {
            var result:ScanResult?=null
            try {
                before?.join()
                MediaLibraryScanner.state.update {it.copy(active=true,scanned=0,result=null)}
                result=withContext(Dispatchers.IO) {
                    val folders=DeviceFolders(this@MediaLibraryScanJob).readForDisplay()
                    if(folders.isNotEmpty() || getDatabasePath("media-library.db").exists()) {
                        MediaLibrary(this@MediaLibraryScanJob).use {index->index.refresh(folders,{ensureActive()}) {count->
                            MediaLibraryScanner.state.update {it.copy(scanned=count)}
                        }}
                    }
                    ensureActive()
                    MediaLibraryScanner.completed(this@MediaLibraryScanJob,folders)
                    if(folders.isEmpty())ScanResult.EMPTY else ScanResult.COMPLETE
                }
            } catch(e:CancellationException){throw e}
              catch(_:Exception){result=ScanResult.FAILED}
              catch(_:LinkageError){result=ScanResult.UNAVAILABLE}
            finally {
                if(current==visit) {
                    MediaLibraryScanner.state.update {it.copy(active=false,revision=it.revision+1,result=result)}
                    if(isActive)jobFinished(params,false)
                }
            }
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {scan?.cancel();return true}
    override fun onDestroy(){scan?.cancel();scope.cancel();super.onDestroy()}
}
