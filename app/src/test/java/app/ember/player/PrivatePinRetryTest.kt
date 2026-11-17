package app.ember.player

import org.junit.Assert.*
import org.junit.Test

class PrivatePinRetryTest {
    @Test fun monotonicDeadlineSurvivesReloadWithoutConsultingTheDeviceDate() {
        val value=PrivacyData(failures=5,retryElapsed=90000,retryBoot=7)
        assertEquals(90000L,PrivatePinRetry.normalized(value,30000,7).retryElapsed)
        assertEquals(90000L,PrivatePinRetry.normalized(value,50000,7).retryElapsed)
        assertEquals(0L,PrivatePinRetry.normalized(value,90001,7).retryElapsed)
    }
    @Test fun rebootAndOldWallClockDeadlinesProduceAtMostOneMinuteOfWait() {
        val old=PrivacyData(failures=5,retryAfter=Long.MAX_VALUE)
        val migrated=PrivatePinRetry.normalized(old,2000,7)
        assertEquals(62000L,migrated.retryElapsed);assertEquals(0L,migrated.retryAfter)
        assertEquals(62000L,PrivatePinRetry.normalized(migrated,10000,7).retryElapsed)
        val rebooted=PrivatePinRetry.normalized(migrated,100,8)
        assertEquals(60100L,rebooted.retryElapsed)
        assertEquals(60100L,PrivatePinRetry.normalized(rebooted,1000,8).retryElapsed)
        assertEquals(0L,PrivatePinRetry.normalized(rebooted,60101,8).retryElapsed)
        assertEquals(60001L,PrivatePinRetry.normalized(old.copy(retryAfter=0,retryElapsed=Long.MAX_VALUE,retryBoot=8),1,8).retryElapsed)
    }
}
