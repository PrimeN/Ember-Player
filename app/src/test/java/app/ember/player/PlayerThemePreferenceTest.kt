package app.ember.player

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import app.ember.tv.data.Prefs
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
        var background=Color.Unspecified;var low=false
        compose.setContent {PlayerTheme(prefs) {
            val color=androidx.compose.material3.MaterialTheme.colorScheme.background
            val limited=LocalRenderOptions.current.lowPower
            SideEffect {background=color;low=limited}
        }}
        compose.runOnIdle {assertTrue(background.luminance()<.1f);assertFalse(low);prefs.theme="day";prefs.devicePerformance="low"}
        compose.waitForIdle()
        compose.runOnIdle {assertTrue(background.luminance()>.8f);assertTrue(low)}
    }
}
