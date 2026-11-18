package app.ember.tv.player

import android.view.KeyEvent

internal object PlaybackKeys {
    enum class Action { CAPTIONS, AUDIO, STOP, TOGGLE, FORWARD_FRAME, BACK_FRAME, PERCENT }
    fun action(code:Int):Action?=when(code) {
        KeyEvent.KEYCODE_CAPTIONS->Action.CAPTIONS
        KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK->Action.AUDIO
        KeyEvent.KEYCODE_MEDIA_STOP->Action.STOP
        KeyEvent.KEYCODE_SPACE->Action.TOGGLE
        KeyEvent.KEYCODE_PERIOD,KeyEvent.KEYCODE_MEDIA_STEP_FORWARD->Action.FORWARD_FRAME
        KeyEvent.KEYCODE_COMMA,KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD->Action.BACK_FRAME
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9,in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9->Action.PERCENT
        else->null
    }
    fun digit(code:Int)=if(code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9)code-KeyEvent.KEYCODE_0 else code-KeyEvent.KEYCODE_NUMPAD_0
    fun position(duration:Long,digit:Int):Long?=if(duration>0 && digit in 0..9)duration/10*digit else null
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.playbackKey(event:KeyEvent):Boolean {
    if(event.isAltPressed || event.isCtrlPressed || event.isMetaPressed || currentFocus?.onCheckIsTextEditor()==true || panelVisible || controls.menu!=null || controls.sheet!=null)return false
    val action=PlaybackKeys.action(event.keyCode) ?: return false
    val p=player ?: return false
    if(event.action!=KeyEvent.ACTION_UP || event.isCanceled)return true
    when(action) {
        PlaybackKeys.Action.CAPTIONS->if(subsInitialized)openMenu("options","subtitles")
        PlaybackKeys.Action.AUDIO->if(subsInitialized)openMenu("options","audio")
        PlaybackKeys.Action.STOP->endPlayback()
        PlaybackKeys.Action.TOGGLE->{p.playWhenReady=!p.playWhenReady;showControls()}
        PlaybackKeys.Action.PERCENT->if(p.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))PlaybackKeys.position(p.duration,PlaybackKeys.digit(event.keyCode))?.let(p::seekTo)
        PlaybackKeys.Action.FORWARD_FRAME,PlaybackKeys.Action.BACK_FRAME->{
            val engine=playbackEngine ?: return true
            val wanted=p.playWhenReady;p.pause();engine.flushNavigation()
            if(engine.compatibility==null && !engine.switch()) {
                p.playWhenReady=wanted
                android.widget.Toast.makeText(this,getString(app.ember.tv.R.string.ui_frame_inspection_requires_compatibility_playback_and_a_supported_audio),android.widget.Toast.LENGTH_LONG).show()
            } else engine.compatibility?.stepFrame(action==PlaybackKeys.Action.FORWARD_FRAME)
        }
    }
    return true
}
