package app.ember.player

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.ember.tv.R
import app.ember.tv.data.Prefs
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w400dp-h800dp-port-mdpi")
class DownloadFormUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun repeatedClicksCannotSubmitTwiceWhileTheFormIsSaving() {
        val calls=AtomicInteger();val pending=CompletableDeferred<MediaDownload>()
        PlayerUiStoreFixture(compose.activity).use {
            compose.activity.setContent {PlayerTheme(Prefs(compose.activity)) {
                DownloadsPanel(compose.activity,save={_,_->calls.incrementAndGet();pending.await()})
            }}
            compose.onNodeWithText(compose.activity.getString(R.string.ui_download_media_url)).performClick()
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("https://example.test/movie.mkv")
            val submit=compose.onNodeWithText(compose.activity.getString(R.string.ui_download)).fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            compose.runOnIdle {submit();submit()}
            compose.waitUntil(10000){calls.get()==1}
            compose.onNodeWithText(compose.activity.getString(R.string.ui_working)).assertIsNotEnabled()
            compose.onNodeWithText(compose.activity.getString(R.string.ui_cancel)).assertIsNotEnabled()
            compose.onNodeWithText("https://example.test/movie.mkv").assertIsNotEnabled()
            pending.completeExceptionally(IllegalStateException("Storage unavailable"))
            compose.waitUntil(10000){compose.onAllNodesWithText(compose.activity.getString(R.string.ui_could_not_start_this_download_check_available_space_and)).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText(compose.activity.getString(R.string.ui_download)).assertIsEnabled()
            assertEquals(1,calls.get())
        }
    }
}
