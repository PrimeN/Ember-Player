package app.ember.player

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.ember.tv.data.Prefs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w360dp-h720dp-port-mdpi")
class PhoneLibraryNavigationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun everySectionCanBeSelectedDirectlyOnANarrowPhone() {
        compose.setContent {PlayerTheme(Prefs(LocalContext.current)) {
            var selected by remember {mutableStateOf(PlayerLibraryDestination.VIDEOS)}
            PhoneLibraryNavigation(selected){selected=it}
        }}
        for(destination in PlayerLibraryDestination.entries) {
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(destination.label))
            compose.onNodeWithText(destination.label).performClick()
            compose.onNodeWithText(destination.label).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected,true))
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected,true)).assertCountEquals(1)
        }
    }
}
