package app.ember.tv.player

import app.ember.tv.R

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class TouchControlsConfig(
    val enabled: Boolean = true,
    val seekSeconds: Int = 10,
    val volumeSwipe: Boolean = true,
    val brightnessSwipe: Boolean = true,
    val pinchZoom: Boolean = true,
    val holdToSpeed: Boolean = true,
    val orientation: String = "auto",
    val holdSpeed:Float=2f,
    val subtitleGestures:Boolean=false,
    val leftDoubleTap:String="seek",
    val centerDoubleTap:String="play_pause",
    val rightDoubleTap:String="seek",
    val seekAreaPercent:Int=35,
    val horizontalSeek:Boolean=true,
    val swapBrightnessVolume:Boolean=false,
)

/** Bounded gesture calculations shared by both playback engines. */
internal object TouchPlaybackMath {
    fun seek(position: Long, delta: Long, duration: Long): Long {
        if (duration <= 0) return position.coerceAtLeast(0)
        return (position.coerceIn(0, duration).toDouble() + delta.toDouble()).coerceIn(0.0, duration.toDouble()).toLong()
    }

    fun swipeSeek(position: Long, fraction: Float, duration: Long): Long =
        if (!fraction.isFinite()) position else seek(position, (fraction.coerceIn(-1f, 1f) * 120_000).toLong(), duration)

    fun level(initial: Int, fraction: Float, maximum: Int): Int =
        if (!fraction.isFinite()) initial.coerceIn(0, maximum.coerceAtLeast(0))
        else (initial + fraction.coerceIn(-1f, 1f) * maximum).roundToInt().coerceIn(0, maximum.coerceAtLeast(0))

    fun zoom(current: Float, factor: Float): Float =
        if (!current.isFinite() || !factor.isFinite() || factor <= 0) 1f else (current * factor).coerceIn(1f, 4f)

    fun step(seconds: Int): Long = seconds.coerceIn(5, 60) * 1000L
    fun holdSpeed(value:Float)=if(value.isFinite())value.coerceIn(1.25f,4f) else 2f
}

/** Invalid restored values fall back to the familiar layout rather than disabling controls. */
internal object TouchActions {
    val options=listOf("seek" to R.string.ui_seek, "play_pause" to R.string.ui_play_pause, "previous" to R.string.ui_previous_file,
        "next" to R.string.ui_next_file, "controls" to R.string.ui_show_hide_controls, "none" to R.string.ui_do_nothing)
    fun region(fraction:Float,percent:Int):Int {
        val edge=percent.coerceIn(20,45)/100f
        return if(fraction<edge)0 else if(fraction>1-edge)2 else 1
    }
    fun action(config:TouchControlsConfig,region:Int):String {
        val value=when(region){0->config.leftDoubleTap;2->config.rightDoubleTap;else->config.centerDoubleTap}
        return value.takeIf {v->options.any {it.first==v}} ?: if(region==1)"play_pause" else "seek"
    }
    fun brightness(left:Boolean,config:TouchControlsConfig)=left!=config.swapBrightnessVolume
}
