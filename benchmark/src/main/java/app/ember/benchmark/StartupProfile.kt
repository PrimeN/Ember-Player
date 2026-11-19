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
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.StaleObjectException
import android.os.SystemClock
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
    private fun label(device:UiDevice,text:String,timeout:Long=15_000):UiObject2 {
        val end=SystemClock.elapsedRealtime()+timeout
        do {
            val node=device.findObject(By.text(text)) ?: device.findObject(By.desc(text)) ?: device.findObject(By.descStartsWith(text))
            if(node!=null)return node
            SystemClock.sleep(100)
        }while(SystemClock.elapsedRealtime()<end)
        throw AssertionError("Missing visible label: $text")
    }
    private fun click(device:UiDevice,text:String) {
        repeat(5) {
            try {label(device,text).click();return}
            catch(_:StaleObjectException){device.waitForIdle(3000)}
        }
        throw AssertionError("Could not click: $text")
    }
    private fun scroll(device:UiDevice,direction:Direction) {
        var stale:StaleObjectException?=null
        repeat(5) {
            device.waitForIdle(3000)
            try {
                val list=device.findObjects(By.scrollable(true)).maxByOrNull {it.visibleBounds.width()*it.visibleBounds.height()} ?: return
                if(!list.scroll(direction,.8f))list.scroll(if(direction==Direction.DOWN)Direction.RIGHT else Direction.LEFT,.8f)
                device.waitForIdle(3000)
                return
            } catch(e:StaleObjectException) {stale=e;SystemClock.sleep(250)}
        }
        throw AssertionError("The scrolling view did not settle",stale)
    }
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
        label(device,"Sample episode 1",30_000)
    }
    @Test fun libraryNavigationAndScrolling()=profile.collect(packageName="app.ember.player.benchmark",maxIterations=3,stableIterations=2) {
        pressHome();startActivityAndWait()
        label(device,"Sample episode 1",30_000)
        scroll(device,Direction.DOWN)
        scroll(device,Direction.UP)
        device.pressDPadDown();device.pressDPadRight();device.pressDPadLeft()
        click(device,"Folders")
        label(device,"Add a media folder")
        click(device,"Settings")
        label(device,"Playback")
        click(device,"Audio");device.waitForIdle()
        scroll(device,Direction.DOWN)
        scroll(device,Direction.UP)
        device.pressBack();device.pressBack()
    }
}
