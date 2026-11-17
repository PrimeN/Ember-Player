package app.ember.tv

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.SubtitleView
import app.ember.tv.data.*
import app.ember.tv.player.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SubtitleStartupTest {

    @Test fun lateOptionsDoNotUndoAnExplicitSubtitlesOffChoice() {
        val activity=Robolectric.buildActivity(ComponentActivity::class.java).get()
        val player=ExoPlayer.Builder(activity).build()
        try {
            val controller=SubtitleController(activity,{player},SubtitleEngine(SubtitleView(activity)),SubtitleConfig(autoSelect="always"),{})
            controller.selectOff();controller.onReady()
            controller.addOptions(listOf(SubtitleOption("Local","en","https://example.invalid/sub.srt")))
            assertTrue(controller.candidates.isNotEmpty());assertEquals(SubtitleController.Selection.Off,controller.selection)
        } finally {player.release()}
    }
}
