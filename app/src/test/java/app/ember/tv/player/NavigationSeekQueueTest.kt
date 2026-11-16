package app.ember.tv.player

import org.junit.Assert.*
import org.junit.Test

class NavigationSeekQueueTest {
    @Test fun heldInputUsesFixedWindowsAndAppliesOnlyTheLatestTarget() {
        var scheduled=0;var cancelled=0
        val seeks=mutableListOf<Pair<Long,Boolean>>()
        val queue=NavigationSeekQueue({scheduled++},{cancelled++}){at,fast->seeks+=at to fast}
        queue.request(10000,true);queue.request(20000,true);queue.request(30000,false)
        assertEquals(1,scheduled);assertEquals(30000L,queue.target);assertTrue(seeks.isEmpty())
        queue.flush();assertEquals(listOf(30000L to false),seeks);assertNull(queue.target)
        queue.request(40000,true);assertEquals(2,scheduled)
        queue.flush();queue.flush();assertEquals(listOf(30000L to false,40000L to true),seeks)
        assertEquals(2,cancelled)
    }
    @Test fun cancellationCannotApplyAStaleSeekToAnotherSource() {
        val seeks=mutableListOf<Long>()
        val queue=NavigationSeekQueue({},{}){at,_->seeks+=at}
        queue.request(30000,false);queue.cancel();queue.flush()
        assertTrue(seeks.isEmpty());assertNull(queue.target)
        queue.request(-100,true);queue.flush();assertEquals(listOf(0L),seeks)
    }
    @Test fun repeatedKeyMathCannotOverflowOrSeekOutsideTheTitle() {
        assertEquals(Long.MAX_VALUE,PlaybackSeeking.target(Long.MAX_VALUE-100,1000,-1))
        assertEquals(0L,PlaybackSeeking.target(100,Long.MIN_VALUE,1000))
        assertEquals(1000L,PlaybackSeeking.target(100,Long.MAX_VALUE,1000))
        assertEquals(0L,PlaybackSeeking.target(-100,0,1000))
    }
}
