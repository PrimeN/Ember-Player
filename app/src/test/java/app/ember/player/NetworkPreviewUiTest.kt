package app.ember.player

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.ember.tv.data.Prefs
import app.ember.tv.player.NetworkAccount
import app.ember.tv.player.NetworkEntry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w540dp-h900dp-port-mdpi")
class NetworkPreviewUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun disablingPreviewsKeepsNetworkRowsBrowsableWithoutOpeningTheirMedia() {
        val prefs=Prefs(RuntimeEnvironment.getApplication()).apply {networkMediaPreviews=false}
        var selected=false
        // An invalid server would fail during preview setup if the row attempted to fetch metadata.
        val account=NetworkAccount(label="Unavailable server",address="invalid server")
        compose.setContent {PlayerTheme(prefs){NetworkMediaRow(account,"",NetworkEntry("Video.mkv",false,1024),false){selected=true}}}
        compose.onNodeWithText("Video.mkv").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertTrue(selected);assertFalse(Prefs(RuntimeEnvironment.getApplication()).networkMediaPreviews)}
    }
}
