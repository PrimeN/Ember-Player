package app.ember.tv.player

import android.app.Application
import android.app.job.JobScheduler
import android.content.Context
import app.ember.player.storage.TokenStore
import app.ember.player.PlayerUiStoreFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class)
class MediaLibrarySchedulingTest {
    private val app:Application get()=RuntimeEnvironment.getApplication()
    private val scheduler get()=app.getSystemService(JobScheduler::class.java)
    private val folder=DeviceFolder("content://device.files/tree/root","Root")
    @Test fun firstStartupSchedulesTheJobAndFreshReturnsDoNotRescan()=runBlocking {
        PlayerUiStoreFixture(app).use {
            assertTrue(DeviceFolders(app).add(folder))
            MediaLibraryScanner.request(app).join()
            val job=scheduler.getPendingJob(MediaLibraryScanner.JOB_ID)!!
            assertEquals(MediaLibraryScanJob::class.java.name,job.service.className)
            assertEquals(0,job.minLatencyMillis)
            MediaLibraryScanner.cancel(app)
            app.getDatabasePath("media-library.db").apply {parentFile!!.mkdirs();writeText("")}
            MediaLibraryScanner.completed(app,listOf(folder))
            MediaLibraryScanner.request(app).join()
            assertNull(scheduler.getPendingJob(MediaLibraryScanner.JOB_ID))
            MediaLibraryScanner.request(app,true).join()
            assertNotNull(scheduler.getPendingJob(MediaLibraryScanner.JOB_ID))
            MediaLibraryScanner.cancel(app)
        }
    }
    @Test fun changedFoldersAndMissingOrOldIndexesScheduleAnotherScan()=runBlocking {
        PlayerUiStoreFixture(app).use {
            assertTrue(DeviceFolders(app).add(folder))
            val db=app.getDatabasePath("media-library.db").apply {parentFile!!.mkdirs();writeText("")}
            MediaLibraryScanner.completed(app,listOf(folder))
            assertTrue(DeviceFolders(app).add(folder.copy(uri="content://device.files/tree/second")))
            MediaLibraryScanner.request(app).join()
            assertNotNull(scheduler.getPendingJob(MediaLibraryScanner.JOB_ID));MediaLibraryScanner.cancel(app)
            MediaLibraryScanner.completed(app,DeviceFolders(app).read())
            db.delete()
            MediaLibraryScanner.request(app).join()
            assertNotNull(scheduler.getPendingJob(MediaLibraryScanner.JOB_ID));MediaLibraryScanner.cancel(app)
            db.writeText("")
            app.getSharedPreferences("libraryScan",Context.MODE_PRIVATE).edit().putLong("completed",System.currentTimeMillis()-MediaLibraryScanner.MAX_AGE-1).commit()
            MediaLibraryScanner.request(app).join()
            assertNotNull(scheduler.getPendingJob(MediaLibraryScanner.JOB_ID));MediaLibraryScanner.cancel(app)
        }
    }
    @Test fun unreadableSavedFoldersCannotScheduleARefreshThatRemovesTheirIndex()=runBlocking {
        PlayerUiStoreFixture(app).use {
            TokenStore(app).putEncrypted("player.folders","broken")
            MediaLibraryScanner.request(app,true).join()
            assertNull(scheduler.getPendingJob(MediaLibraryScanner.JOB_ID))
            assertEquals(ScanResult.FAILED,MediaLibraryScanner.state.value.result)
        }
    }
    @Test fun freshnessHandlesClockChangesAndStoresOnlyADigestOfTheRoots() {
        val digest=MediaLibraryScanner.fingerprint(listOf(folder))
        assertFalse(digest.contains("content"));assertEquals(64,digest.length)
        assertFalse(MediaLibraryScanner.stale(1000,999,digest,digest))
        assertTrue(MediaLibraryScanner.stale(998,999,digest,digest))
        assertTrue(MediaLibraryScanner.stale(MediaLibraryScanner.MAX_AGE+999,999,digest,digest))
        assertTrue(MediaLibraryScanner.stale(1000,999,digest,"changed"))
    }
}
