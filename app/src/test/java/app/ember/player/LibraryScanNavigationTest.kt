package app.ember.player

import android.app.Application
import android.app.job.JobScheduler
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.ember.tv.data.Prefs
import app.ember.tv.player.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class)
class LibraryScanNavigationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun leavingTheScanPanelKeepsItsBackgroundJobScheduled() {
        val app:Application=RuntimeEnvironment.getApplication()
        PlayerUiStoreFixture(app).use {
            val activity=Robolectric.buildActivity(ComponentActivity::class.java).get()
            val visible=mutableStateOf(true)
            compose.setContent {PlayerTheme(Prefs(app)) {if(visible.value)MediaLibraryPanel(activity,0)}}
            compose.onNodeWithText("Refresh library").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()}
            val scheduler=app.getSystemService(JobScheduler::class.java)
            compose.waitUntil(10_000){scheduler.getPendingJob(MediaLibraryScanner.JOB_ID)!=null}
            val scheduled=scheduler.getPendingJob(MediaLibraryScanner.JOB_ID)
            compose.runOnIdle {visible.value=false}
            assertEquals(scheduled,scheduler.getPendingJob(MediaLibraryScanner.JOB_ID))
            compose.runOnIdle {visible.value=true}
            compose.onNode(hasText("Cancel refresh",substring=true)).assertExists()
            compose.onNode(hasText("Cancel refresh",substring=true)).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()}
            compose.waitUntil(10_000){scheduler.getPendingJob(MediaLibraryScanner.JOB_ID)==null}
        }
    }
}
