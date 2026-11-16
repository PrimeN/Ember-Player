package app.ember.tv.player

import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleDelayMilliseconds

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerActivity.installTouchControls() {
    val content = findViewById<FrameLayout>(android.R.id.content)
    val root = content.getChildAt(0) as? FrameLayout ?: return
    touchControls = PlayerTouchControls(this, root, object : TouchPlaybackHost {
        override val position get() = player?.currentPosition ?: 0L
        override val duration get() = player?.duration ?: 0L
        override val seekable get() = player?.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) == true && duration > 0
        override val playing get() = player?.isPlaying == true
        override var speed: Float
            get() = player?.playbackParameters?.speed ?: this@installTouchControls.speed
            set(value) { this@installTouchControls.speed = value; player?.setPlaybackSpeed(value) }
        override val controlsVisible get() = if (advanced) controls.visible else view.isControllerFullyVisible
        override val menuVisible get() = panelVisible || controls.menu != null || controls.sheet != null || actionDialog?.isShowing == true || leaveDialog?.isShowing == true || upNextUi.visible
        override fun seek(position: Long) { navigateSeek(position) }
        override fun togglePlayback() { player?.let { it.playWhenReady = !it.playWhenReady } }
        override fun toggleControls() {
            if (advanced) { if (controls.visible) controls.visible = false else showControls() }
            else if (view.isControllerFullyVisible) view.hideController() else view.showController()
        }
        override fun hideControls() { controls.visible = false; controls.sheet = null; closeMenu(); view.hideController(); hidePanel() }
        override fun magnify(scale: Float) { touchMagnification = scale; applyTouchMagnification() }
        override val subtitleState:SubtitleTouchState? get() {
            if(!subsInitialized || prefs.subtitleConfig.systemStyle)return null
            val selected=player?.currentTracks?.groups?.any {it.type==androidx.media3.common.C.TRACK_TYPE_TEXT && it.isSelected}==true
            if(!selected && !subs.engine.active)return null
            val c=prefs.subtitleConfig
            val bottom=c.layout.bottomMarginPercent ?: when(c.position){"low"->3;"high"->20;else->8}
            return SubtitleTouchState(subs.engine.offsetMs,c.layout.scalePercent,bottom)
        }
        override fun previewSubtitles(value:SubtitleTouchState) {
            val c=prefs.subtitleConfig.let {it.copy(layout=it.layout.copy(scalePercent=value.scalePercent,bottomMarginPercent=value.bottomMarginPercent))}
            view.subtitleView?.let {SubtitleEngine.style(it,c)}
            playbackEngine?.compatibility?.captionStyle(c)
        }
        override fun applySubtitles(value:SubtitleTouchState) {
            if(!subsInitialized)return
            subs.engine.offsetMs=value.offsetMs
            player?.subtitleDelayMilliseconds=value.offsetMs
            playbackEngine?.compatibility?.subtitleDelay(value.offsetMs)
            val c=prefs.subtitleConfig
            applySubLook(c.copy(layout=c.layout.copy(scalePercent=value.scalePercent,bottomMarginPercent=value.bottomMarginPercent)))
            refreshSubtitlePanel()
        }
    }) { config.touch }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerActivity.applyTouchMagnification() {
    val native = playbackEngine?.compatibility
    native?.magnify(touchMagnification)
    view.videoSurfaceView?.apply {
        val value = if (native == null) touchMagnification else 1f
        scaleX = value; scaleY = value
    }
}
