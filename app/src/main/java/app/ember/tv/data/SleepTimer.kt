package app.ember.tv.data

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal data class SleepTimerState(val deadline:Long=0,val endOfItem:Boolean=false) {
    fun remaining(now:Long)=if(deadline>0)(deadline-now).coerceAtLeast(0) else 0
}

/** A single application timer follows playback between the video and audio owners. */
object SleepTimer {
    @Volatile var fired:Int=0;private set
    private val handler=Handler(Looper.getMainLooper())
    private val mutable=MutableStateFlow(SleepTimerState())
    internal val state:StateFlow<SleepTimerState> = mutable
    private val expire=Runnable {fire()}
    private val listeners=java.util.concurrent.CopyOnWriteArraySet<()->Unit>()
    internal fun listen(expired:()->Unit):java.io.Closeable {listeners+=expired;return java.io.Closeable {listeners-=expired}}
    @Synchronized fun after(minutes:Int) {
        require(minutes in 1..1440)
        handler.removeCallbacks(expire)
        mutable.value=SleepTimerState(SystemClock.elapsedRealtime()+minutes*60000L)
        handler.postDelayed(expire,minutes*60000L)
    }
    @Synchronized fun atEnd(){handler.removeCallbacks(expire);mutable.value=SleepTimerState(endOfItem=true)}
    @Synchronized fun cancel(){handler.removeCallbacks(expire);mutable.value=SleepTimerState()}
    @Synchronized fun itemEnded():Boolean {if(!mutable.value.endOfItem)return false;fire();return true}
    @Synchronized fun fire(){handler.removeCallbacks(expire);mutable.value=SleepTimerState();fired++;listeners.forEach {it()}}
}
