package app.ember.player

import android.app.Application
import android.app.job.JobScheduler
import app.ember.player.storage.SecretVault
import app.ember.player.storage.TokenStore
import app.ember.tv.player.MediaLibraryScanner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class BackupStorageIntegrityTest {
    @Test fun restoringAPinFreeBackupRequiresTheCurrentPinAndClearsTheProtectionMarker() {
        val app:Application=RuntimeEnvironment.getApplication()
        PlayerUiStoreFixture(app).use {
            assertTrue(PrivateMedia.configure("123456"))
            val snapshot=PlayerBackups.snapshot(app)
            val noPin=snapshot.copy(secrets=snapshot.secrets-PrivateMedia.KEY)
            assertFalse(PlayerBackups.restore(app,noPin))
            assertFalse(PlayerBackups.restore(app,noPin,"999999"))
            assertTrue(PrivateMedia.enabled)
            assertTrue(PlayerBackups.restore(app,noPin,"123456"))
            assertFalse(PrivateMedia.enabled)
            assertFalse(app.getSharedPreferences("playerSecurity",0).getBoolean("enabled",true))
            assertFalse(PrivateMedia.hidden("content://media/video/1"))
            MediaLibraryScanner.cancel(app)
        }
    }
    @Test fun restoringAPortableBackupRetainsTheDeviceIndexKeyAndSchedulesARefresh() {
        val app:Application=RuntimeEnvironment.getApplication()
        PlayerUiStoreFixture(app).use {
            val tokens=TokenStore(app)
            tokens.putEncrypted(PlayerBackups.LOCAL_INDEX_KEY,"this-device-key")
            tokens.putEncrypted("connection","old-value")
            val backup=PlayerBackups.snapshot(app)
            assertFalse(PlayerBackups.LOCAL_INDEX_KEY in backup.secrets)
            val incoming=backup.copy(secrets=mapOf(PlayerBackups.LOCAL_INDEX_KEY to "other-device-key","connection" to "restored-value"))
            PlayerBackups.restore(app,incoming)
            assertEquals("this-device-key",tokens.get(PlayerBackups.LOCAL_INDEX_KEY))
            assertEquals("restored-value",tokens.get("connection"))
            val scheduler=app.getSystemService(JobScheduler::class.java)
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
            while(scheduler.getPendingJob(MediaLibraryScanner.JOB_ID)==null&&System.nanoTime()<deadline)Thread.sleep(10)
            assertNotNull(scheduler.getPendingJob(MediaLibraryScanner.JOB_ID))
            MediaLibraryScanner.cancel(app)
        }
    }
    @Test fun unreadableSecretsStopBackupBeforeAnIncompleteSnapshotCanBeReturned() {
        val app:Application=RuntimeEnvironment.getApplication()
        PlayerUiStoreFixture(app).use {
            val prefs=app.getSharedPreferences(SecretVault.FILE,0)
            TokenStore(app).putEncrypted("connection","saved")
            prefs.edit().putString("connection","corrupted sealed text!").commit()
            val original=prefs.all.toMap()
            try {PlayerBackups.snapshot(app);fail("An unreadable secret must refuse export")}catch(_:IllegalStateException){}
            assertEquals(original,prefs.all)
        }
    }
}
