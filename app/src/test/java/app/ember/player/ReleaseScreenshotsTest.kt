package app.ember.player

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.ember.tv.data.Prefs
import app.ember.tv.player.*
import app.ember.tv.ui.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private fun captureEnabled()=TestRule {base,_->object:Statement() {
    override fun evaluate() {
        assumeTrue("Set EMBER_SCREENSHOTS_DIR to capture the release gallery",!System.getenv("EMBER_SCREENSHOTS_DIR").isNullOrBlank())
        base.evaluate()
    }
}}
private fun SemanticsNodeInteraction.saveScreenshot(name:String) {
    val file=File(System.getenv("EMBER_SCREENSHOTS_DIR"),"$name.png")
    file.parentFile?.mkdirs()
    file.outputStream().use {captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w360dp-h800dp-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReleaseLibraryScreenshotsTest {
    @get:Rule(order=0) val enabled=captureEnabled()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MediaFilesActivity>()
    private fun capture(name:String, toolbarAction:String) {
        compose.onNode(
            hasText("Home") and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Heading)
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription(toolbarAction).assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("library-loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Add files").assertIsDisplayed()
        compose.onNodeWithText("Media folders").assertIsDisplayed()
        compose.waitForIdle()
        compose.onRoot().saveScreenshot(name)
    }
    @Test fun phone()=capture("mobile", "Open file")
    @Test @Config(qualifiers="sw600dp-w960dp-h600dp-land-xhdpi") fun tablet()=capture("tablet", "Open file")
    @Test @Config(qualifiers="w960dp-h540dp-land-television-xhdpi") fun television()=capture("tv", "Browse library")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w360dp-h800dp-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReleaseSettingsScreenshotsTest {
    @get:Rule(order=0) val enabled=captureEnabled()
    @get:Rule(order=1) val compose=createAndroidComposeRule<PlayerSettingsActivity>()
    @Test fun settings() {
        compose.onNodeWithText("Search settings").assertIsDisplayed()
        compose.onRoot().saveScreenshot("settings")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReleasePlaybackScreenshotsTest {
    @get:Rule(order=0) val enabled=captureEnabled()
    @get:Rule(order=1) val compose=createComposeRule()
    @Test fun playbackControls() {
        val state=ControlsState().apply {
            visible=true;design=PlayerInterfaces.GLASS;title="The Last Light"
            position=38*60_000L+24_000;duration=101*60_000L+8_000;buffered=duration
            audioTracks=listOf("English · Stereo" to true)
            subtitleOptions=listOf("Off" to true,"English" to false)
        }
        val actions=ControlsActions(playPause={},seekBy={},skip={},seekStep={10_000L},
            openMenu={_,_->},options={emptyList()},next={},touched={})
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF183D4E),Color(0xFF0B1924))))) {
                    AdvancedControls(state,actions)
                }
            }
        }}
        compose.onNodeWithContentDescription("Forward 10 seconds").assertIsDisplayed()
        compose.onRoot().saveScreenshot("player")
    }
}
