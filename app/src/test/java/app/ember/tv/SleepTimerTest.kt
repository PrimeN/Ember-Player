package app.ember.tv

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import app.ember.tv.data.SleepTimer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SleepTimerTest {
    @Before fun setup(){SleepTimer.cancel()}
    @After fun cleanup(){SleepTimer.cancel()}
    @Test fun countdownExpiresOnceAndNotifiesOnlyRegisteredOwners() {
        val before=SleepTimer.fired;var expired=0
        val watch=SleepTimer.listen {expired++}
        SleepTimer.after(1)
        assertEquals(60000L,SleepTimer.state.value.remaining(SystemClock.elapsedRealtime()))
        shadowOf(Looper.getMainLooper()).idleFor(59,TimeUnit.SECONDS);assertEquals(0,expired)
        shadowOf(Looper.getMainLooper()).idleFor(1,TimeUnit.SECONDS);assertEquals(1,expired);assertEquals(before+1,SleepTimer.fired)
        assertEquals(0L,SleepTimer.state.value.deadline)
        watch.close();SleepTimer.fire();assertEquals(1,expired)
    }
    @Test fun cancellingOrReplacingATimerRemovesTheOldDeadline() {
        val before=SleepTimer.fired
        SleepTimer.after(1);SleepTimer.after(2)
        shadowOf(Looper.getMainLooper()).idleFor(1,TimeUnit.MINUTES);assertEquals(before,SleepTimer.fired)
        SleepTimer.cancel();shadowOf(Looper.getMainLooper()).idleFor(5,TimeUnit.MINUTES);assertEquals(before,SleepTimer.fired)
    }
    @Test fun endOfItemStopsOnceAndCanBeReplacedWithCountdown() {
        SleepTimer.atEnd();assertTrue(SleepTimer.itemEnded());assertFalse(SleepTimer.itemEnded())
        SleepTimer.atEnd();SleepTimer.after(5);assertFalse(SleepTimer.state.value.endOfItem);assertFalse(SleepTimer.itemEnded())
    }
    @Test fun invalidDurationsCannotOverflowOrCreateAnUnboundedTimer() {
        for(value in listOf(0,-1,1441,Int.MAX_VALUE))try {SleepTimer.after(value);fail("Expected duration rejection")}catch(_:IllegalArgumentException){}
        assertEquals(0L,SleepTimer.state.value.deadline)
    }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun fadeBindingsRestoreOnCancelAndCarryTheUnfadedLevelBetweenOwners() {
        var level=.8f
        val player=java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,arrayOf(androidx.media3.common.Player::class.java)) {_,method,args->
            when(method.name){"getVolume"->level;"setVolume"->{level=args!![0] as Float;null};"equals"->false;"hashCode"->1;else->null}
        } as androidx.media3.common.Player
        val fade=app.ember.tv.data.SleepFade {player}
        SleepTimer.after(1)
        shadowOf(Looper.getMainLooper()).idleFor(30,TimeUnit.SECONDS)
        assertEquals(.4f,level,.02f);assertEquals(.8f,fade.original(level),.001f)
        SleepTimer.cancel();assertEquals(.8f,level,.001f)
        SleepTimer.after(1);shadowOf(Looper.getMainLooper()).idleFor(30,TimeUnit.SECONDS)
        fade.close();assertEquals(.8f,level,.001f)
        val next=app.ember.tv.data.SleepFade {player}
        assertEquals(.4f,level,.02f)
        SleepTimer.fire();assertEquals(.4f,level,.02f) // Expiry must not unmute before Stop.
        next.close()
    }

}
