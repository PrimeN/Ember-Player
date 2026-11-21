package app.ember.tv.player

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.Decoder
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import androidx.media3.decoder.iamf.IamfDecoder
import androidx.media3.decoder.mpegh.MpeghDecoder
import androidx.media3.decoder.mpegh.MpeghUiCommandHelper
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.exoplayer.audio.IamfUtil
import androidx.media3.inspector.MediaExtractorCompat
import app.ember.player.PrivateMedia
import app.ember.tv.data.Subtitles
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Presents a decoded local track to mpv as ordinary seekable PCM. The bundled
 * decoders and mpv exchange a WAV file descriptor, without a native dependency
 * between their libraries. The temporary file is unlinked before any audio is written.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal object CompatibilityPcmAudio {
    class Prepared(val format: Format, private val descriptor: ParcelFileDescriptor) : Closeable {
        val url get() = "fd://${descriptor.fd}"
        override fun close() = descriptor.close()
    }

    fun supports(mime: String?) = mime == MimeTypes.AUDIO_IAMF ||
        mime == MimeTypes.AUDIO_MPEGH_MHA1 || mime == MimeTypes.AUDIO_MPEGH_MHM1

    fun eligible(value: String, filename: String?, source: EngineSource, config: PlaybackConfig): Boolean {
        val uri = Uri.parse(value)
        val suffix = (filename ?: uri.lastPathSegment)?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
        return config.ffmpeg != "off" && !source.live && source.urls.size == 1 && source.audio == null &&
            uri.scheme in setOf("content", "file") &&
            (suffix.isNullOrBlank() || suffix in setOf("mp4", "m4v", "m4a", "m4b", "mov"))
    }

    /** Without an explicit special-track selection, leave conventional audio to mpv. */
    internal fun choose(formats: List<Format>, requested: Format?, languages: List<String>): Int? {
        val audio = formats.indices.filter { MimeTypes.isAudio(formats[it].sampleMimeType) }
        if (requested != null) {
            if (!supports(requested.sampleMimeType)) return null
            val matches = audio.filter {
                val format = formats[it]
                format.sampleMimeType == requested.sampleMimeType &&
                    (requested.language.isNullOrBlank() || Subtitles.normalise(format.language) == Subtitles.normalise(requested.language)) &&
                    (requested.channelCount <= 0 || format.channelCount <= 0 || requested.channelCount == format.channelCount) &&
                    (requested.sampleRate <= 0 || format.sampleRate <= 0 || requested.sampleRate == format.sampleRate)
            }
            return matches.singleOrNull { requested.id != null && formats[it].id == requested.id } ?: matches.singleOrNull()
        }
        if (audio.any { !supports(formats[it].sampleMimeType) }) return null
        fun preferred(tracks: List<Int>) =
            tracks.firstOrNull { formats[it].selectionFlags and C.SELECTION_FLAG_DEFAULT != 0 } ?: tracks.firstOrNull()
        return languages.firstNotNullOfOrNull { language ->
            preferred(audio.filter { Subtitles.normalise(formats[it].language) == Subtitles.normalise(language) })
        } ?: preferred(audio)
    }

    suspend fun prepare(context: Context, value: String, requested: Format?, languages: List<String>): Prepared? {
        val cancellation = currentCoroutineContext()
        fun checkActive() {
            cancellation.ensureActive()
            check(!PrivateMedia.hidden(value)) { "Private media cannot be converted to a temporary audio track" }
        }
        checkActive()
        val extractor = MediaExtractorCompat(
            ExtractorsFactory {
                arrayOf(
                    Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED, Mp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA),
                    FragmentedMp4Extractor(SubtitleParser.Factory.UNSUPPORTED, FragmentedMp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA),
                )
            },
            DefaultDataSource.Factory(context),
        )
        try {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(value), "r")?.use { input ->
                // Reject pipes before probing. mpv can still use its normal playback path.
                Os.lseek(input.fileDescriptor, 0L, OsConstants.SEEK_CUR)
                extractor.setDataSource(input)
            } ?: return null
            if (extractor.drmInitData != null) return null
            val formats = List(extractor.trackCount) { index ->
                val mediaFormat = extractor.getTrackFormat(index)
                MediaFormatUtil.createFormatFromMediaFormat(mediaFormat).buildUpon().apply {
                    if (mediaFormat.containsKey(MediaFormat.KEY_TRACK_ID)) {
                        setId(mediaFormat.getInteger(MediaFormat.KEY_TRACK_ID).toString())
                    }
                    if (mediaFormat.getInteger(MediaFormat.KEY_IS_DEFAULT, 0) != 0) {
                        setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    }
                }.build()
            }
            val index = choose(formats, requested, languages) ?: return null
            val format = formats[index]
            if (format.drmInitData != null) return null
            checkActive()
            extractor.selectTrack(index)
            val file = File.createTempFile("ember-pcm-", ".wav", context.cacheDir)
            try {
                RandomAccessFile(file, "rw").use { output ->
                    check(file.delete()) { "Cannot unlink the temporary audio track" }
                    val destination = CompatibilityWaveWriter(output, context.cacheDir.usableSpace - 1024L * 1024L)
                    withDecoder(format) { decoder, rate, channels ->
                        decode(extractor, decoder, format, destination, rate, channels, ::checkActive)
                    }
                    checkActive()
                    destination.finish()
                    return Prepared(format, ParcelFileDescriptor.dup(output.fd))
                }
            } finally {
                if (file.exists()) file.delete()
            }
        } finally {
            extractor.release()
        }
    }

    private fun withDecoder(
        format: Format,
        run: (Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, *>, () -> Int, () -> Int) -> Unit,
    ) {
        if (format.sampleMimeType == MimeTypes.AUDIO_IAMF) {
            val decoder = IamfDecoder(
                format.initializationData,
                IamfUtil.OUTPUT_LAYOUT_ITU2051_SOUND_SYSTEM_A_0_2_0,
                IamfUtil.REQUESTED_MIX_PRESENTATION_ID_UNSET,
                IamfDecoder.OUTPUT_SAMPLE_TYPE_INT16_LITTLE_ENDIAN,
                IamfDecoder.CHANNEL_ORDERING_ANDROID_ORDERING,
            )
            try {
                run(decoder, decoder::getSampleRate, decoder::getNumberOfOutputChannels)
            } finally {
                decoder.release()
            }
        } else {
            val decoder = MpeghDecoder(format, 2, 2, MpeghUiCommandHelper())
            try {
                run(decoder, decoder::getSampleRate, decoder::getChannelCount)
            } finally {
                decoder.release()
            }
        }
    }

    private fun decode(
        extractor: MediaExtractorCompat,
        decoder: Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, *>,
        format: Format,
        destination: CompatibilityWaveWriter,
        rate: () -> Int,
        channels: () -> Int,
        checkActive: () -> Unit,
    ) {
        var inputEnded = false
        var advancedAt = System.nanoTime()
        while (true) {
            checkActive()
            var advanced = false
            decoder.dequeueOutputBuffer()?.let { output ->
                try {
                    if (output.isEndOfStream) return
                    val pcm = output.data
                    if (pcm != null && pcm.hasRemaining()) {
                        destination.append(pcm, rate(), channels(), output.timeUs)
                    }
                    advanced = true
                } finally {
                    output.release()
                }
            }
            if (!inputEnded) decoder.dequeueInputBuffer()?.let { input ->
                input.clear()
                val size = extractor.sampleSize
                if (size < 0L) {
                    input.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
                    inputEnded = true
                } else {
                    check(size in 1L..16L * 1024 * 1024 && extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                        "Unsupported audio sample"
                    }
                    val decoderPadding = if (MimeTypes.AUDIO_MPEGH_MHM1 == format.sampleMimeType) 2048 else 0
                    input.ensureSpaceForWrite(size.toInt() + decoderPadding)
                    val data = checkNotNull(input.data)
                    check(extractor.readSampleData(data, 0) == size.toInt())
                    data.position(size.toInt())
                    input.format = format
                    input.timeUs = extractor.sampleTime
                    input.flip()
                    extractor.advance()
                }
                decoder.queueInputBuffer(input)
                advanced = true
            }
            if (advanced) advancedAt = System.nanoTime() else {
                check(System.nanoTime() - advancedAt < 5_000_000_000L) { "Decoder made no progress" }
                Thread.sleep(1)
            }
        }
    }
}

/** Align PCM to the MP4 timeline, including leading silence and sample overlaps. */
internal class CompatibilityWaveWriter(
    private val file: RandomAccessFile,
    maxDataBytes: Long = 0xffff_ffffL - 36L,
) {
    private val capacity = minOf(maxDataBytes, 0xffff_ffffL - 36L)
    private val bytes = ByteArray(64 * 1024)
    private var rate = 0
    private var channels = 0
    private var frames = 0L

    fun append(pcm: ByteBuffer, sampleRate: Int, channelCount: Int, timestampUs: Long) {
        require(sampleRate in 8_000..192_000 && channelCount in 1..2 && timestampUs in -10_000_000L..(Long.MAX_VALUE - 500_000L) / sampleRate)
        val frameSize = channelCount * 2
        require(pcm.remaining() % frameSize == 0)
        if (rate == 0) {
            rate = sampleRate
            channels = channelCount
            file.seek(44L)
        }
        require(sampleRate == rate && channelCount == channels)
        val scaled = timestampUs * rate
        val start = (scaled + if (scaled >= 0L) 500_000L else -500_000L) / 1_000_000L
        val count = pcm.remaining() / frameSize
        val skip = (frames - start).coerceIn(0L, count.toLong())
        val newFrames = maxOf(frames, start + count)
        check(newFrames * frameSize <= capacity) { "Insufficient space for the decoded WAV audio" }
        // Extending a seekable file fills timeline gaps with zeros without allocating PCM buffers.
        if (start > frames) file.seek(44L + start * frameSize)
        pcm.position(pcm.position() + (skip * frameSize).toInt())
        while (pcm.hasRemaining()) {
            val n = minOf(pcm.remaining(), bytes.size)
            pcm.get(bytes, 0, n)
            file.write(bytes, 0, n)
        }
        frames = newFrames
    }

    fun finish() {
        check(frames > 0L) { "Decoder produced no PCM audio" }
        val frameSize = channels * 2
        val size = (frames * frameSize).toInt()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(size + 36)
            .put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
            .putShort(1).putShort(channels.toShort()).putInt(rate).putInt(rate * frameSize)
            .putShort(frameSize.toShort()).putShort(16)
            .put("data".toByteArray(Charsets.US_ASCII)).putInt(size)
        file.seek(0L)
        file.write(header.array())
        file.seek(0L)
    }
}
