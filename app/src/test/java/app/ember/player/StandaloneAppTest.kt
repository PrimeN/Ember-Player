package app.ember.player

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import app.ember.tv.data.Prefs
import app.ember.tv.player.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class StandaloneAppTest {
    @Test fun installationIsSeparateAndHasNoHomeOrAccessibilityEntryPoint() {
        val app:Application=RuntimeEnvironment.getApplication();val pm=app.packageManager
        assertTrue(app.packageName.startsWith("app.ember.player"))
        assertEquals(0,pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(app.packageName),0).size)
        val info=pm.getPackageInfo(app.packageName,PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS)
        assertTrue(info.services.orEmpty().none {it.permission=="android.permission.BIND_ACCESSIBILITY_SERVICE"})
        assertEquals(0,app.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertFalse(pm.getActivityInfo(ComponentName(app,PlayerActivity::class.java),0).exported)
    }
    @Test fun externalEntryForwardsTheMediaGrantButDropsPrivateProfileAndServerCommands() {
        val uri=Uri.parse("content://files.example/video/1")
        val request=Intent(Intent.ACTION_VIEW,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra("watchProfile","other-profile").putExtra("serverSession","untrusted")
            .putExtra("title","A movie")
        val activity=Robolectric.buildActivity(OpenMediaActivity::class.java,request).create().get()
        val next=shadowOf(activity).nextStartedActivity
        assertEquals(uri,next.data);assertEquals("A movie",next.getStringExtra(PlayRequest.EXTRA_TITLE))
        assertNotEquals(0,next.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertFalse(next.hasExtra("watchProfile"));assertFalse(next.hasExtra("serverSession"))
    }
    @Test fun standalonePreferencesPersistPlaybackChoicesAndDefaultToOfflineSubtitleUse() {
        val app:Application=RuntimeEnvironment.getApplication();val prefs=Prefs(app)
        assertFalse(prefs.subtitleConfig.stremioOpenSubs)
        val config=prefs.playbackConfig.copy(engine="compatibility",playerInterface="streaming",seekMode="fast",bluetoothDelayMs=200)
        prefs.playbackConfig=config
        assertEquals(config,Prefs(app).playbackConfig)
    }
    @Test fun namedBookmarksSurviveReopenAndKeepOtherActivityScopesSeparate() {
        val app:Application=RuntimeEnvironment.getApplication()
        val store=PlaybackBookmarks(app,"shared")
        assertTrue(store.add("file-key",15000,"The reveal"))
        val loaded=PlaybackBookmarks(app,"shared").read("file-key").single()
        assertEquals(15000L,loaded.positionMs)
        assertTrue(PlaybackBookmarks(app,"local:other").read("file-key").isEmpty())
        assertTrue(store.rename("file-key",loaded.id,"New name"))
        assertTrue(store.remove("file-key",loaded.id));assertTrue(store.read("file-key").isEmpty())
    }
}
