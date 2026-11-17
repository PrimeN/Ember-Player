package app.ember.tv.player

import android.app.Application
import android.view.KeyEvent
import androidx.activity.BackEventCompat
import androidx.compose.ui.platform.ComposeView
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerBackNavigationTest {
    private fun fixture(test:(PlayerActivity)->Unit) {
        val activity=Robolectric.buildActivity(PlayerActivity::class.java).get()
        val player=ExoPlayer.Builder(activity).build()
        activity.player=player;activity.config=PlaybackConfig(confirmExit=false);activity.advanced=true
        activity.installBackHandler()
        try {test(activity)}finally {activity.handler.removeCallbacksAndMessages(null);player.release()}
    }
    @Test fun systemBackDismissesTheSheetThenControlsBeforeLeaving()=fixture {activity->
        activity.controls.visible=true;activity.controls.sheet="subtitles"
        activity.onBackPressedDispatcher.onBackPressed()
        assertNull(activity.controls.sheet);assertTrue(activity.controls.visible);assertFalse(activity.isFinishing)
        activity.onBackPressedDispatcher.onBackPressed()
        assertFalse(activity.controls.visible);assertFalse(activity.isFinishing)
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(activity.isFinishing)
    }
    @Test fun cancelledBackGesturesKeepTheOpenSheet()=fixture {activity->
        activity.controls.visible=true;activity.controls.sheet="audio"
        val gesture=BackEventCompat(0f,100f,.5f,BackEventCompat.EDGE_LEFT)
        activity.onBackPressedDispatcher.dispatchOnBackStarted(gesture)
        activity.onBackPressedDispatcher.dispatchOnBackProgressed(gesture)
        activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        assertEquals("audio",activity.controls.sheet)
        activity.onBackPressedDispatcher.onBackPressed()
        assertNull(activity.controls.sheet);assertTrue(activity.controls.visible);assertFalse(activity.isFinishing)
    }
    @Test @Config(sdk=[29])
    fun legacyTrackedBackKeysRespectCancellationBeforeDismissingASheet()=fixture {activity->
        activity.controls.visible=true;activity.controls.sheet="audio"
        fun event(flags:Int)=KeyEvent(0,0,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0,0,0,0,flags)
        activity.onKeyUp(KeyEvent.KEYCODE_BACK,event(KeyEvent.FLAG_TRACKING or KeyEvent.FLAG_CANCELED))
        assertEquals("audio",activity.controls.sheet)
        activity.onKeyUp(KeyEvent.KEYCODE_BACK,event(KeyEvent.FLAG_TRACKING))
        assertNull(activity.controls.sheet);assertTrue(activity.controls.visible);assertFalse(activity.isFinishing)
    }
    @Test fun systemBackDismissesAnActionDialogBeforePlaybackControls()=fixture {activity->
        var cancelled=0
        val dialog=PlayerActionDialog(ComposeView(activity),null,{cancelled++})
        activity.actionDialog=dialog;activity.controls.visible=true
        activity.onBackPressedDispatcher.onBackPressed()
        assertFalse(dialog.isShowing);assertEquals(1,cancelled)
        assertTrue(activity.controls.visible);assertFalse(activity.isFinishing)
        activity.onBackPressedDispatcher.onBackPressed()
        assertFalse(activity.controls.visible);assertEquals(1,cancelled)
    }
}
