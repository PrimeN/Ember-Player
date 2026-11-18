package app.ember.player

import org.junit.Assert.*
import org.junit.Test

class ReceiverSleepVolumeTest {
    @Test fun staleReceiverReportsCannotCompoundTheFadeOrLoseItsOriginalLevel() {
        val fade=ReceiverSleepVolume()
        assertEquals(.4,fade.level(.8,.5f),.0001)
        assertEquals(.2,fade.level(.8,.25f),.0001)
        assertEquals(.8,fade.level(.2,1f),.0001)
        assertEquals(.8,fade.level(.8,1f),.0001)
        assertEquals(.3,fade.level(.6,.5f),.0001)
    }
    @Test fun stoppingRestoresTheStreamOnlyAfterStopAndBeforeClosingTheSession() {
        val fade=ReceiverSleepVolume();fade.level(.8,.1f)
        val events=mutableListOf<String>();var stopped:((Boolean)->Unit)?=null;var restored:((Boolean)->Unit)?=null
        fade.stopAndRestore({true},{events+="stop";stopped=it},{level,done->assertEquals(.8,level,.0001);events+="restore";restored=done},{events+="close"})
        assertEquals(listOf("stop"),events)
        stopped!!(true);assertEquals(listOf("stop","restore"),events)
        restored!!(true);assertEquals(listOf("stop","restore","close"),events)
    }
    @Test fun obsoleteStopCallbacksCannotChangeNewPlaybackAndFailedRestoresRemainRetryable() {
        val fade=ReceiverSleepVolume();fade.level(.8,.1f)
        var active=true;var callback:((Boolean)->Unit)?=null;var calls=0
        fade.stopAndRestore({active},{callback=it},{_,_->calls++},{calls++})
        active=false;callback!!(true);assertEquals(0,calls)
        fade.stopAndRestore({true},{it(true)},{_,done->done(false)},{calls++})
        assertEquals(1,calls);assertEquals(.8,fade.level(.08,1f),.0001)
    }
}
