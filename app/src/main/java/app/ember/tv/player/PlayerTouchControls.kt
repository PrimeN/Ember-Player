package app.ember.tv.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

internal interface TouchPlaybackHost {
    val position: Long
    val duration: Long
    val seekable: Boolean
    val playing: Boolean
    var speed: Float
    val controlsVisible: Boolean
    val menuVisible: Boolean
    fun seek(position: Long)
    fun togglePlayback()
    fun toggleControls()
    fun hideControls()
    fun skipItem(forward:Boolean):Boolean = false
    fun finishMagnification() {}
    fun magnify(scale: Float)
    val subtitleState:SubtitleTouchState? get()=null
    fun previewSubtitles(value:SubtitleTouchState) {}
    fun applySubtitles(value:SubtitleTouchState) {}
}

/** Observes video-area touches only; buttons, seek bars and modal menus keep their normal input. */
internal class PlayerTouchControls(
    private val activity: Activity,
    private val root: FrameLayout,
    private val host: TouchPlaybackHost,
    private val settings: () -> TouchControlsConfig,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val density = activity.resources.displayMetrics.density
    private val slop = ViewConfiguration.get(activity).scaledTouchSlop * 2
    private var accepted = false
    private var multiTouch = false
    private var axis = 0
    private var downX = 0f
    private var downY = 0f
    private var startPosition = 0L
    private var previewPosition = 0L
    private var startVolume = 0
    private var startBrightness = 0
    private var heldSpeed: Float? = null
    private var scale = 1f
    private var videoScaled=false
    private var subtitleStart:SubtitleTouchState?=null
    private var subtitlePreview:SubtitleTouchState?=null
    private var subtitleScaleFactor=1f
    private var lastSubtitlePreview=0L
    private val originalBrightness = activity.window.attributes.screenBrightness
    var locked = false
        private set
    var onLockChanged:()->Unit={}
    private val hint = TextView(activity).apply {
        setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
        setPadding(dp(18), dp(10), dp(18), dp(10))
        background = pill(); visibility = View.GONE; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val unlock = Button(activity).apply {
        text = "Unlock playback"; isAllCaps = false; textSize = 14f
        setTextColor(Color.WHITE); background = pill(); visibility = View.GONE
        setPadding(dp(18), dp(8), dp(18), dp(8)); minHeight = dp(48)
        setOnClickListener { setLocked(false) }
    }
    private val clearHint = Runnable { hint.visibility = View.GONE }

    private val gestures = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!locked && !host.menuVisible && !multiTouch && axis == 0) host.toggleControls()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (locked || host.menuVisible || multiTouch || subtitleStart!=null) return true
            val cfg=settings()
            val region=TouchActions.region(e.x/root.width.coerceAtLeast(1),cfg.seekAreaPercent)
            when(TouchActions.action(cfg,region)) {
                "play_pause"->host.togglePlayback()
                "controls"->host.toggleControls()
                "previous","next"->if(!host.skipItem(TouchActions.action(cfg,region)=="next"))showHint("No file in that direction")
                "seek"->if(host.seekable) {
                    val delta=TouchPlaybackMath.step(cfg.seekSeconds)*if(region==0)-1 else 1
                    host.seek(TouchPlaybackMath.seek(host.position,delta,host.duration))
                    showHint("${if(delta<0)"−" else "+"}${abs(delta)/1000} seconds")
                }
            }
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            if (!settings().holdToSpeed || locked || multiTouch || axis != 0 || !host.playing || host.menuVisible || subtitleStart!=null) return
            heldSpeed = host.speed
            val value=TouchPlaybackMath.holdSpeed(settings().holdSpeed)
            host.speed = value
            showHint("${value}× · release to return", sticky = true)
        }
    })
    private val scaling = ScaleGestureDetector(activity, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            releaseSpeed()
            axis = 0
            return subtitleStart!=null || settings().pinchZoom
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            subtitleStart?.let {initial->
                subtitleScaleFactor*=detector.scaleFactor.takeIf {it.isFinite()&&it>0} ?: 1f
                subtitlePreview=(subtitlePreview?:initial).copy(scalePercent=SubtitleTouchMath.scale(initial.scalePercent,subtitleScaleFactor))
                previewSubtitleStyle()
                showHint("Subtitle size ${subtitlePreview!!.scalePercent}%")
                return true
            }
            if (!settings().pinchZoom) return false
            videoScaled=true
            scale = TouchPlaybackMath.zoom(scale, detector.scaleFactor)
            host.magnify(scale)
            showHint("Zoom ${(scale * 100).roundToInt()}%")
            return true
        }
    })

    init {
        root.addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(unlock, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            setMargins(dp(24), dp(24), dp(24), dp(24))
        })
        applyOrientation()
    }

    fun restoreMagnification(value:Float){scale=value.takeIf {it.isFinite()}?.coerceIn(1f,4f) ?: 1f}

    fun applyOrientation() {
        if (isTelevision(activity)) return
        val orientation = when (settings().orientation) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "sensor" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (activity.requestedOrientation != orientation) activity.requestedOrientation = orientation
    }

    /** True means the video gesture owns this entire pointer stream. */
    fun dispatch(event: MotionEvent): Boolean {
        if (locked) {
            // Route touches on the one unlock control through the regular view hierarchy.
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val rectangle = android.graphics.Rect()
                unlock.getGlobalVisibleRect(rectangle)
                accepted = !rectangle.contains(event.rawX.toInt(), event.rawY.toInt())
            }
            return accepted
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val usable = settings().enabled && !isTelevision(activity) && !host.menuVisible
            // Keep the header and lower controls tappable while the player controls are visible.
            accepted = usable && (!host.controlsVisible || event.y in root.height * .23f..root.height * .57f)
            if (!accepted) return false
            multiTouch = false; videoScaled=false; axis = 0; downX = event.x; downY = event.y
            startPosition = host.position; previewPosition = startPosition
            subtitleStart=if(settings().subtitleGestures && event.y>=root.height*.65f)host.subtitleState else null
            subtitlePreview=subtitleStart;subtitleScaleFactor=1f;lastSubtitlePreview=0L
            startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            startBrightness = ((activity.window.attributes.screenBrightness.takeIf { it >= 0 }
                ?: (Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)) * 100).roundToInt()
        }
        if (!accepted) return false
        if (event.pointerCount > 1 && !multiTouch) {
            multiTouch = true
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            gestures.onTouchEvent(cancel); cancel.recycle()
        }
        scaling.onTouchEvent(event)
        if (!multiTouch) gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && !multiTouch && heldSpeed == null) {
            val dx = event.x - downX; val dy = downY - event.y
            if (axis == 0 && (abs(dx) > slop || abs(dy) > slop)) {
                axis = if (abs(dx) > abs(dy)) 1 else 2
                // Stop long-press/single-tap callbacks once a swipe has begun.
                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                gestures.onTouchEvent(cancel); cancel.recycle()
            }
            val subtitle=subtitleStart
            if(subtitle!=null && axis!=0) {
                subtitlePreview=if(axis==1)subtitle.copy(offsetMs=SubtitleTouchMath.timing(subtitle.offsetMs,dx/root.width.coerceAtLeast(1)))
                    else subtitle.copy(bottomMarginPercent=SubtitleTouchMath.position(subtitle.bottomMarginPercent,dy/root.height.coerceAtLeast(1)))
                previewSubtitleStyle()
                showHint(if(axis==1)String.format(java.util.Locale.US,"Subtitle timing %+.2f s",subtitlePreview!!.offsetMs/1000.0)
                    else "Subtitle position ${subtitlePreview!!.bottomMarginPercent}%",sticky=true)
            } else if (axis == 1 && settings().horizontalSeek && host.seekable) {
                previewPosition = TouchPlaybackMath.swipeSeek(startPosition, dx / root.width.coerceAtLeast(1), host.duration)
                showHint("${clock(previewPosition)} / ${clock(host.duration)}", sticky = true)
            } else if (axis == 2) {
                val fraction = dy / (root.height.coerceAtLeast(1) * .7f)
                if (TouchActions.brightness(downX < root.width / 2f,settings()) && settings().brightnessSwipe) {
                    val level = TouchPlaybackMath.level(startBrightness, fraction, 100).coerceAtLeast(1)
                    activity.window.attributes = activity.window.attributes.apply { screenBrightness = level / 100f }
                    showHint("Brightness $level%", sticky = true)
                } else if (!TouchActions.brightness(downX < root.width / 2f,settings()) && settings().volumeSwipe && !audio.isVolumeFixed) {
                    val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val level = TouchPlaybackMath.level(startVolume, fraction, maximum)
                    runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0) }
                    showHint("Volume ${if (maximum > 0) level * 100 / maximum else 0}%", sticky = true)
                }
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            val initial=subtitleStart
            if(initial!=null) {
                val value=subtitlePreview?:initial
                if(event.actionMasked==MotionEvent.ACTION_UP && value!=initial)host.applySubtitles(value)
                else host.previewSubtitles(initial)
                subtitleStart=null;subtitlePreview=null
            } else if (event.actionMasked == MotionEvent.ACTION_UP && axis == 1 && settings().horizontalSeek && !multiTouch && host.seekable) host.seek(previewPosition)
            if(event.actionMasked==MotionEvent.ACTION_UP && videoScaled)host.finishMagnification()
            releaseSpeed()
            handler.postDelayed(clearHint, 800)
        }
        return true
    }

    fun dispatchKey(event: KeyEvent): Boolean {
        if (!locked) return false
        if (event.keyCode in setOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_MUTE)) return false
        if (event.keyCode in setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER) && event.action == KeyEvent.ACTION_UP) setLocked(false)
        return true
    }

    fun setLocked(value: Boolean) {
        cancel()
        locked = value
        onLockChanged()
        unlock.visibility = if (value) View.VISIBLE else View.GONE
        if (value) { host.hideControls(); unlock.requestFocus(); showHint("Playback locked · Back also unlocks") }
        else host.toggleControls()
    }

    fun cancel() {
        subtitleStart?.let(host::previewSubtitles);subtitleStart=null;subtitlePreview=null
        releaseSpeed()
        val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        gestures.onTouchEvent(cancel); scaling.onTouchEvent(cancel); cancel.recycle()
        accepted = false; axis = 0; multiTouch = false
        handler.removeCallbacks(clearHint); hint.visibility = View.GONE
    }

    private fun previewSubtitleStyle() {
        val now=android.os.SystemClock.uptimeMillis()
        if(lastSubtitlePreview==0L || now-lastSubtitlePreview>=32) {
            subtitlePreview?.let(host::previewSubtitles);lastSubtitlePreview=now
        }
    }

    fun close() {
        cancel()
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = originalBrightness }
        root.removeView(hint); root.removeView(unlock)
    }

    private fun releaseSpeed() { heldSpeed?.let { host.speed = it }; heldSpeed = null }
    private fun showHint(text: String, sticky: Boolean = false) {
        hint.text = text; hint.visibility = View.VISIBLE
        handler.removeCallbacks(clearHint)
        if (!sticky) handler.postDelayed(clearHint, 1000)
    }
    private fun pill() = GradientDrawable().apply { setColor(0xCC202024.toInt()); cornerRadius = dp(24).toFloat(); setStroke(dp(1), 0x33FFFFFF) }
    private fun dp(value: Int) = (value * density).roundToInt()
    private fun clock(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
    }

    companion object {
        fun isTelevision(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }
}
