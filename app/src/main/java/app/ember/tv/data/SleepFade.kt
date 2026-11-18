package app.ember.tv.data

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player

/** Tracks the user's unfaded level separately from the timer's gain. */
internal class SleepVolume {
    private var base=1f
    private var applied:Float?=null
    fun original(current:Float):Float = if(applied?.let {kotlin.math.abs(it-current)<.0001f}==true)base else current
    fun apply(current:Float,nextGain:Float):Float {
        base=original(current);val gain=nextGain.coerceIn(0f,1f)
        return (base*gain).also {applied=it}
    }
    fun reset(current:Float):Float=original(current).also {base=it;applied=null}
    companion object {
        fun gain(state:SleepTimerState,now:Long):Float=if(state.deadline>0)(state.remaining(now)/60000f).coerceIn(0f,1f) else 1f
    }
}

/** Runs once per second only while a timed countdown is active, and restores volume on cancel. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class SleepFade(private val player:()->Player?):java.io.Closeable {
    private val handler=Handler(Looper.getMainLooper())
    private var target:Player?=null
    private var volume=SleepVolume()
    private var fired=SleepTimer.fired
    private var closed=false
    private val tick=object:Runnable {override fun run(){update()}}
    private val watch=SleepTimer.changes {update()}
    init {update()}
    fun refresh()=update()
    fun original(current:Float)=if(target===player())volume.original(current) else current
    private fun update() {
        handler.removeCallbacks(tick)
        if(closed)return
        val next=player()
        if(next!==target){restore();target=next;volume=SleepVolume()}
        val state=SleepTimer.state.value
        if(state.deadline<=0){restore();fired=SleepTimer.fired;return}
        fired=SleepTimer.fired
        target?.let {it.volume=volume.apply(it.volume,SleepVolume.gain(state,SystemClock.elapsedRealtime()))}
        handler.postDelayed(tick,1000)
    }
    private fun restore(){if(fired==SleepTimer.fired)target?.let {runCatching {it.volume=volume.reset(it.volume)}}}
    override fun close(){closed=true;handler.removeCallbacks(tick);watch.close();restore();target=null}
}
