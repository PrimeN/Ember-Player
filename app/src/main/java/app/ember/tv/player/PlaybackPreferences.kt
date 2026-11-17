package app.ember.tv.player

import app.ember.tv.R

import kotlinx.serialization.Serializable
import java.util.Locale

/** Preferences are hints; normal decoder recovery remains available. */
@Serializable
data class DecoderRules(val local: String = "inherit", val network: String = "inherit",
    val codecs: Map<String, String> = emptyMap(), val extensions: Map<String, String> = emptyMap()) {
    fun preference(url: String, mime: String? = null, filename: String? = null): String {
        val codec = codecs[mime]?.takeIf { it in modes && it != "inherit" }
        if (codec != null) return codec
        val uri = runCatching { java.net.URI(url) }.getOrNull()
        val nameExtension = filename.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        val extension = nameExtension.takeIf {it in extensions} ?: uri?.path.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        val scheme=uri?.scheme?.lowercase(Locale.ROOT) ?: url.substringBefore(':', "").lowercase(Locale.ROOT).takeIf {it.isNotBlank()}
        return extensions[extension]?.takeIf { it in modes && it != "inherit" }
            ?: (if (scheme in setOf(null, "file", "content", "fd")) local else network).takeIf { it in modes }
            ?: "inherit"
    }
    companion object {
        val modes = setOf("inherit", "auto", "hardware", "software")
        val choices = listOf("inherit" to R.string.ui_use_default, "auto" to R.string.ui_automatic, "hardware" to R.string.ui_prefer_hardware, "software" to R.string.ui_prefer_software)
        val codecChoices = listOf("video/avc" to "H.264", "video/hevc" to "HEVC", "video/av01" to "AV1", "video/x-vnd.on2.vp9" to "VP9", "video/mpeg2" to "MPEG-2")
        val extensionChoices = listOf("mkv", "mp4", "avi", "ts", "webm", "wmv", "vob")
    }
}

@Serializable
data class AudioEffects(
    val preset: String = "off",
    val bass: Int = 0,
    val channels: String = "normal",
    val bands: List<Int> = emptyList(),
    val treble: Int = 0,
    val stereoWidth: Int = 100,
    val compression:String="off",
) {
    val gains: List<Int> get() = when (preset) {
        "dialogue" -> listOf(-3, -3, -3, -2, 0, 2, 3, 4, 2, 1)
        "warm" -> listOf(3, 3, 2, 2, 1, 0, 0, -1, -2, -2)
        "bright" -> listOf(-2, -2, -1, -1, 0, 0, 1, 2, 3, 3)
        "custom" -> frequencies.indices.map { bands.getOrElse(it) { 0 }.coerceIn(-12, 12) }
        else -> List(frequencies.size) { 0 }
    }
    val processedGains: List<Int> get() = gains.mapIndexed { i, gain ->
        gain + when (i) { 2 -> bass.coerceIn(0, 6); 8 -> treble.coerceIn(0, 6); else -> 0 }
    }
    val active get() = NightListening.preset(compression)!=null || processedGains.any { it != 0 } || channels in setOf("mono", "swap") || stereoWidth.coerceIn(0, 200) != 100
    fun headroomDb(channelCount: Int): Double = processedGains.maxOrNull()!!.coerceAtLeast(0).toDouble() +
        (if (channelCount >= 2 && channels != "mono" && stereoWidth > 100) 20 * kotlin.math.log10(stereoWidth.coerceAtMost(200) / 100.0) else 0.0) +
        (NightListening.preset(compression)?.makeupDb ?: 0.0)
    fun band(index: Int, gain: Int): AudioEffects {
        if (index !in frequencies.indices) return this
        return copy(preset = "custom", bands = gains.toMutableList().apply { this[index] = gain.coerceIn(-12, 12) })
    }
    /** A fixed vocabulary only; no user-entered filter text reaches the native engine. */
    fun nativeFilters(channelCount: Int = 2): String {
        val filters = mutableListOf<String>()
        processedGains.zip(frequencies).filter { it.first != 0 }.forEach { (gain, frequency) ->
            filters += "equalizer=f=$frequency:t=o:w=1:g=$gain"
        }
        when (channels) {
            "mono" -> filters += "aformat=channel_layouts=mono"
            "swap" -> if (channelCount in 2..8) filters += "pan=${channelCount}c|" + (0 until channelCount).joinToString("|") { "c$it=c${if (it == 0) 1 else if (it == 1) 0 else it}" }
        }
        val width = stereoWidth.coerceIn(0, 200)
        if (channelCount in 2..8 && channels != "mono" && width != 100) {
            val a = (1 + width / 100.0) / 2; val b = (1 - width / 100.0) / 2
            fun number(value: Double) = String.format(Locale.US, "%.4f", value)
            fun mix(first: Int, second: Int) = "${number(a)}*c$first${if (b < 0) "-" else "+"}${number(kotlin.math.abs(b))}*c$second"
            filters += "pan=${channelCount}c|" + (0 until channelCount).joinToString("|") { "c$it=" + when (it) { 0 -> mix(0, 1); 1 -> mix(1, 0); else -> "c$it" } }
        }
        NightListening.preset(compression)?.let {filters+=it.filter()}
        val headroom = headroomDb(channelCount)
        if (headroom > 0) filters.add(0, "volume=-${String.format(Locale.US, "%.4f", headroom)}dB")
        return if (filters.isEmpty()) "" else "lavfi=[${filters.joinToString(",")}]"
    }
    companion object {
        val frequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        fun frequencyLabel(index: Int): String = frequencies[index].let { if (it < 1000) "$it Hz" else "${it / 1000} kHz" }
        val presets = listOf("off" to R.string.ui_off_flat, "dialogue" to R.string.ui_clear_dialogue, "warm" to R.string.ui_warm, "bright" to R.string.ui_bright, "custom" to R.string.ui_custom)
        val channelChoices = listOf("normal" to R.string.ui_original_channels, "mono" to R.string.ui_mono, "swap" to R.string.ui_swap_left_and_right)
    }
}

@Serializable
data class SavedAudioPreset(val name: String, val effects: AudioEffects)

object AudioPresets {
    fun save(values: List<SavedAudioPreset>, name: String, effects: AudioEffects): List<SavedAudioPreset>? {
        val clean = name.filterNot { it.isISOControl() }.trim().take(48)
        if (clean.isBlank()) return null
        val existing = values.filterNot { it.name.equals(clean, true) }.take(12)
        if (existing.size >= 12) return null
        return existing + SavedAudioPreset(clean, effects.copy(bands = effects.gains))
    }
}


object PlayerShortcuts {
    val defaults = listOf("audio", "subtitles", "speed", "zoom")
    val choices = listOf("audio" to R.string.ui_audio_track, "subtitles" to R.string.ui_subtitles, "speed" to R.string.ui_playback_speed, "zoom" to R.string.ui_picture_fit, "delay" to R.string.ui_lip_sync, "decoder" to R.string.ui_video_decoder, "effects" to R.string.ui_audio_effects, "chapters" to R.string.ui_chapters, "queue" to R.string.ui_queue)
    fun normalized(values: List<String>) = values.filter { value -> choices.any { it.first == value } }.distinct().take(8)
}

internal object SeekPreference {
    fun fast(value: String) = value == "fast"
    fun flags(value: String, precise: Boolean) = if (!precise && fast(value)) "absolute+keyframes" else "absolute+exact"
}

/** Defer optional processing until a known decoded track is available. */
internal object AudioProcessingPolicy {
    private val encoded=setOf("audio/ac3","audio/eac3","audio/eac3-joc","audio/vnd.dts","audio/vnd.dts.hd","audio/true-hd")
    fun allowed(mime:String?,passthroughRequested:Boolean,bitstreamActive:Boolean,pcmFallback:Boolean) =
        mime!=null && !bitstreamActive && (!passthroughRequested || pcmFallback || mime !in encoded)
}
