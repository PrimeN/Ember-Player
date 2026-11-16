package app.ember.player

import android.content.ComponentName
import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class)
class StartupResourcesTest {
    @Test fun optionalUiDependenciesCannotStartFontDownloadsAutomatically() {
        val context=RuntimeEnvironment.getApplication()
        @Suppress("DEPRECATION")
        val provider=context.packageManager.getProviderInfo(ComponentName(context,"androidx.startup.InitializationProvider"),PackageManager.GET_META_DATA)
        assertFalse(provider.metaData.containsKey("androidx.emoji2.text.EmojiCompatInitializer"))
        assertTrue(provider.metaData.containsKey("androidx.profileinstaller.ProfileInstallerInitializer"))
    }
}
