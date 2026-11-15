package app.ember.tv.player

import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

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
