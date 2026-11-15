package app.ember.tv.player

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
}
