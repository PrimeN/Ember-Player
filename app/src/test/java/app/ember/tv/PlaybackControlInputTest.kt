package app.ember.tv

import app.ember.tv.player.PlaybackControlInput
import app.ember.tv.player.SubtitleTouchMath
import org.junit.Assert.*
import org.junit.Test

class PlaybackControlInputTest {
    @Test fun acceptsSecondsMinutesHoursAndMillisecondsWithoutLocaleAmbiguity() {
        assertEquals(90_000L,PlaybackControlInput.position("90"))
        assertEquals(90_000L,PlaybackControlInput.position("1:30"))
        assertEquals(3_723_250L,PlaybackControlInput.position("01:02:03.250"))
        assertEquals(5_400_000L,PlaybackControlInput.position("90:00"))
        assertEquals(1_250L,PlaybackControlInput.position(" 1,25 "))
        assertEquals(0L,PlaybackControlInput.position("0"))
    }
    @Test fun malformedAndOverflowingTimesAreRejectedInsteadOfSeekingElsewhere() {
        for(value in listOf("", "-1", "+30", "1:60", "1:90:00", "1:2:3:4", "1::3", ":30", "1:30.", "1.0001", "NaN", "1e5", "999999999999999999999999999999999"))
            assertNull(value,PlaybackControlInput.position(value))
    }
    @Test fun speedsHaveAValidatedFiniteRangeAndSupportDecimalComma() {
        for(value in listOf("0.25","1.375","4","1,35"))assertNotNull(value,PlaybackControlInput.speed(value))
        assertEquals(1.375f,PlaybackControlInput.speed("1.375")!!,.0001f)
        for(value in listOf("0","-1","4.001","0.249","NaN","Infinity","1e2","2x","1.1234"))assertNull(value,PlaybackControlInput.speed(value))
    }
    @Test fun subtitleGesturesBoundOffsetsSizeAndPosition() {
        assertEquals(60_000L,SubtitleTouchMath.timing(Long.MAX_VALUE,1f))
        assertEquals(-60_000L,SubtitleTouchMath.timing(Long.MIN_VALUE,-1f))
        assertEquals(2500L,SubtitleTouchMath.timing(0,.25f))
        assertEquals(35,SubtitleTouchMath.position(8,1f))
        assertEquals(0,SubtitleTouchMath.position(8,-1f))
        assertEquals(200,SubtitleTouchMath.scale(100,8f))
        assertEquals(50,SubtitleTouchMath.scale(100,.1f))
        assertEquals(100,SubtitleTouchMath.scale(100,Float.NaN))
    }
}
