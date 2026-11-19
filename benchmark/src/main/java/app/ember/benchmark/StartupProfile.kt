package app.ember.benchmark

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StartupProfile {
    @get:Rule val profile=BaselineProfileRule()
    @Before fun representativeLibrary() {
        val done=CountDownLatch(1);var seeded=false
        val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){seeded=resultCode==Activity.RESULT_OK;done.countDown()}}
        InstrumentationRegistry.getInstrumentation().context.sendOrderedBroadcast(
            Intent("app.ember.player.SEED_PROFILE").addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES).setComponent(ComponentName("app.ember.player.benchmark","app.ember.tv.player.ProfileFixtures")),
            null,receiver,null,Activity.RESULT_CANCELED,null,null)
        assertTrue("Could not prepare the profiling library",done.await(45,TimeUnit.SECONDS)&&seeded)
    }
    @Test fun startup()=profile.collect(packageName="app.ember.player.benchmark",includeInStartupProfile=true,maxIterations=3,stableIterations=2) {
        pressHome();startActivityAndWait()
        assertTrue(device.wait(Until.hasObject(By.text("Sample episode 1")),30_000))
    }
    @Test fun libraryNavigationAndScrolling()=profile.collect(packageName="app.ember.player.benchmark",maxIterations=3,stableIterations=2) {
        pressHome();startActivityAndWait()
        assertTrue(device.wait(Until.hasObject(By.text("Sample episode 1")),30_000))
        device.findObjects(By.scrollable(true)).maxByOrNull {it.visibleBounds.width()*it.visibleBounds.height()}?.scroll(Direction.DOWN,1f)
        device.findObjects(By.scrollable(true)).maxByOrNull {it.visibleBounds.width()*it.visibleBounds.height()}?.scroll(Direction.UP,1f)
        device.pressDPadDown();device.pressDPadRight();device.pressDPadLeft()
        device.findObject(By.text("Folders"))?.click()
        assertTrue(device.wait(Until.hasObject(By.text("Add a media folder")),15_000))
        device.findObject(By.desc("Settings"))?.click()
        assertTrue(device.wait(Until.hasObject(By.text("Playback")),15_000))
        device.findObject(By.text("Audio"))?.click();device.waitForIdle()
        device.findObjects(By.scrollable(true)).maxByOrNull {it.visibleBounds.width()}?.scroll(Direction.DOWN,1f)
        device.findObjects(By.scrollable(true)).maxByOrNull {it.visibleBounds.width()}?.scroll(Direction.UP,1f)
        device.pressBack();device.pressBack()
    }
}
