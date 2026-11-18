package app.ember.tv.player

import android.app.Application
import android.content.pm.ProviderInfo
import android.os.Looper
import android.provider.DocumentsContract
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.util.ReflectionHelpers
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ClipExportThreadTest {
    private fun activity():PlayerActivity=Robolectric.buildActivity(PlayerActivity::class.java).get().apply {
        currentUrl="https://example.test/movie.mp4"
        player=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(ExoPlayer::class.java)) {_,method,_->
            check(Looper.myLooper()==Looper.getMainLooper()) {"Player read off its application thread"}
            when(method.name){"getCurrentMediaItem"->null;else->null}
        } as ExoPlayer
    }
    private fun prepare(export:PlayerClipExport,activity:PlayerActivity) {
        ReflectionHelpers.setField(export,"source",activity.currentUrl)
        ReflectionHelpers.setField(export,"visit",activity.playbackVisit)
        ReflectionHelpers.setField(export,"startMs",1000L);ReflectionHelpers.setField(export,"endMs",4000L)
    }
    private fun until(condition:()->Boolean) {
        val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while(!condition()&&System.nanoTime()<end){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10)}
        assertTrue(condition())
    }
    @Test fun copyingUsesAnImmutableSnapshotAndNeverReadsThePlayerOnTheWorker() {
        val activity=activity();val started=CountDownLatch(1);val release=CompletableDeferred<Unit>()
        var captured:ClipCopyRequest?=null
        val export=PlayerClipExport(activity) {request,check,_->
            assertNotEquals(Looper.getMainLooper(),Looper.myLooper())
            check();captured=request;started.countDown();release.await();check()
        }
        try {
            activity.sourceHeaders=mapOf("Authorization" to "original")
            prepare(export,activity);export.begin(android.net.Uri.parse("content://exports/clip.mp4"))
            shadowOf(Looper.getMainLooper()).idle();assertTrue(started.await(5,TimeUnit.SECONDS))
            activity.sourceHeaders=mapOf("Authorization" to "changed")
            assertEquals("original",captured!!.headers["Authorization"])
            assertEquals(1000L,captured!!.start);assertEquals(4000L,captured!!.end)
            release.complete(Unit);until {!export.active}
        } finally {release.complete(Unit);export.close();activity.handler.removeCallbacksAndMessages(null)}
    }
    @Test fun cancellationRemovesPartialOutputAndDoesNotChangeAnUnownedScreenFlag() {
        val app:Application=RuntimeEnvironment.getApplication()
        val provider=DeviceTestProvider();provider.attachInfo(app,ProviderInfo().apply {
            authority="device.files";exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission="android.permission.MANAGE_DOCUMENTS"
        });ShadowContentResolver.registerProviderInternal("device.files",provider)
        val activity=activity();activity.window.decorView.keepScreenOn=true
        val started=CountDownLatch(1)
        val export=PlayerClipExport(activity) {_,check,_->check();started.countDown();awaitCancellation()}
        try {
            export.cancel();assertTrue(activity.window.decorView.keepScreenOn)
            prepare(export,activity);export.begin(DocumentsContract.buildDocumentUri("device.files","video"))
            shadowOf(Looper.getMainLooper()).idle();assertTrue(started.await(5,TimeUnit.SECONDS))
            export.cancel();until {!provider.files.containsKey("video")}
            assertTrue(activity.window.decorView.keepScreenOn)
        }finally {export.close();activity.handler.removeCallbacksAndMessages(null)}
    }
}
