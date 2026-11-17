package app.ember.player

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.ember.tv.data.Prefs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerPaletteUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun renderedSwatchesMatchTheAppliedPhoneAndTvThemeInBothDisplayModes() {
        val prefs=Prefs(RuntimeEnvironment.getApplication()).apply {theme="night"}
        var touch=Color.Unspecified;var tv=Color.Unspecified
        compose.setContent {PlayerTheme(prefs) {
            val phoneColor=androidx.compose.material3.MaterialTheme.colorScheme.primary
            val televisionColor=androidx.tv.material3.MaterialTheme.colorScheme.primary
            SideEffect {touch=phoneColor;tv=televisionColor}
            Column {PlayerPalettePicker(prefs.accent){prefs.accent=it}}
        }}
        for(mode in listOf("night","day")) {
            compose.runOnIdle {prefs.theme=mode}
            compose.waitForIdle()
            for((id,label) in listOf(0 to "Ember",1 to "Charcoal",5 to "Android TV",6 to "Red")) {
                // Compare resting colors without a touch indication covering the swatch.
                val image=compose.onNodeWithTag("paletteSwatch_$id",useUnmergedTree=true).captureToImage().asAndroidBitmap()
                val shown=image.getPixel(image.width/2,image.height/2)
                compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick){it()}
                compose.waitForIdle()
                compose.runOnIdle {
                    assertEquals(id,prefs.accent);assertEquals(touch,tv)
                    val expected=touch.toArgb()
                    for(shift in listOf(0,8,16))assertEquals("$label in $mode: ${expected.toUInt().toString(16)} / ${shown.toUInt().toString(16)}",((expected ushr shift) and 255).toDouble(),((shown ushr shift) and 255).toDouble(),1.0)
                }
            }
        }
        compose.onNodeWithText("Modern Ember").assertDoesNotExist()
        compose.onNodeWithText("Cinema Red").assertDoesNotExist()
    }
}
