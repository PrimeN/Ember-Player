package app.ember.player

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import app.ember.tv.data.Prefs
import app.ember.tv.ui.AccentTones
import app.ember.tv.ui.LocalRenderOptions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class PlayerThemePreferenceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun appearanceAndPerformanceRefreshWithoutRecreatingTheScreen() {
        val prefs=Prefs(RuntimeEnvironment.getApplication()).apply {theme="night";devicePerformance="high"}
        var background=Color.Unspecified;var surface=Color.Unspecified;var low=false
        compose.setContent {PlayerTheme(prefs) {
            val palette=androidx.compose.material3.MaterialTheme.colorScheme
            val limited=LocalRenderOptions.current.lowPower
            SideEffect {background=palette.background;surface=palette.surface;low=limited}
        }}
        compose.runOnIdle {
            assertEquals(AccentTones.page(prefs.accent), background)
            assertEquals(AccentTones.panel(prefs.accent, oled=false), surface)
            assertFalse(low)
            prefs.theme="day";prefs.devicePerformance="low"
        }
        compose.waitForIdle()
        compose.runOnIdle {assertTrue(background.luminance()>.8f);assertTrue(low)}
    }
    @Test fun followingSystemChangesColorsWhileExplicitChoicesRemainStable() {
        val prefs=Prefs(RuntimeEnvironment.getApplication()).apply {theme="system"}
        var system by androidx.compose.runtime.mutableStateOf(android.content.res.Configuration().apply {uiMode=android.content.res.Configuration.UI_MODE_NIGHT_NO})
        var background=Color.Unspecified
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides system) {
                PlayerTheme(prefs) {val color=androidx.compose.material3.MaterialTheme.colorScheme.background;SideEffect {background=color}}
            }
        }
        compose.runOnIdle {assertTrue(background.luminance()>.8f);system=android.content.res.Configuration(system).apply {uiMode=android.content.res.Configuration.UI_MODE_NIGHT_YES}}
        compose.waitForIdle()
        compose.runOnIdle {assertTrue(background.luminance()<.1f);prefs.theme="day"}
        compose.waitForIdle()
        compose.runOnIdle {assertTrue(background.luminance()>.8f);prefs.theme="night"}
        compose.waitForIdle()
        compose.runOnIdle {assertTrue(background.luminance()<.1f)}
    }

}
