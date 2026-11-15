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
}
