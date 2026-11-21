package app.ember.tv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class CompatibilityPcmAudioTest {
    private fun audio(mime: String, id: String? = null, language: String = "en"): Format =
        Format.Builder().setSampleMimeType(mime).setId(id).setLanguage(language).build()

    @Test fun conventionalAudioRemainsPreferredUntilSpecialAudioIsSelected() {
        val ordinary = audio(MimeTypes.AUDIO_AAC)
        val iamf = audio(MimeTypes.AUDIO_IAMF, "12")
        val mpegH = audio(MimeTypes.AUDIO_MPEGH_MHM1, "14", "fr")
        assertNull(CompatibilityPcmAudio.choose(listOf(iamf, ordinary, mpegH), null, listOf("en")))
        assertEquals(0, CompatibilityPcmAudio.choose(listOf(iamf, ordinary, mpegH), iamf, listOf("fr")))
        assertEquals(1, CompatibilityPcmAudio.choose(listOf(iamf, mpegH), null, listOf("fr")))
    }

    @Test fun ambiguousExplicitTracksAreNeverSilentlySwapped() {
        val first = audio(MimeTypes.AUDIO_MPEGH_MHA1, "1")
        val second = audio(MimeTypes.AUDIO_MPEGH_MHA1, "2")
        assertEquals(1, CompatibilityPcmAudio.choose(listOf(first, second), second, emptyList()))
        assertNull(CompatibilityPcmAudio.choose(listOf(first, second), first.buildUpon().setId(null).build(), emptyList()))
    }

    @Test fun defaultTrackWinsWithinThePreferredLanguage() {
        val first = audio(MimeTypes.AUDIO_IAMF, "1", "en")
        val second = audio(MimeTypes.AUDIO_MPEGH_MHM1, "2", "en").buildUpon()
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val french = audio(MimeTypes.AUDIO_MPEGH_MHM1, "3", "fr")
        assertEquals(1, CompatibilityPcmAudio.choose(listOf(first, second, french), null, listOf("en")))
        assertEquals(2, CompatibilityPcmAudio.choose(listOf(first, second, french), null, listOf("fr")))
    }

    @Test fun waveWriterAlignsTimestampsAndWritesSeekablePcmHeader() {
        val output = File.createTempFile("ember-test", ".wav")
        try {
            RandomAccessFile(output, "rw").use { file ->
                val writer = CompatibilityWaveWriter(file)
                writer.append(ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)), 8_000, 1, 250)
                writer.append(ByteBuffer.wrap(byteArrayOf(5, 6, 7, 8)), 8_000, 1, 375)
                writer.finish()
                assertEquals(0L, file.filePointer)
            }
            val bytes = output.readBytes()
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", bytes.decodeToString(0, 4))
            assertEquals("WAVE", bytes.decodeToString(8, 12))
            assertEquals(8_000, header.getInt(24))
            assertEquals(10, header.getInt(40))
            assertArrayEquals(byteArrayOf(0, 0, 0, 0, 1, 2, 3, 4, 7, 8), bytes.copyOfRange(44, bytes.size))
        } finally {
            output.delete()
        }
    }
}
