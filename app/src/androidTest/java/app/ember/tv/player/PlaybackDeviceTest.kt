package app.ember.tv.player

import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaCodecList
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.decoder.iamf.IamfLibrary
import androidx.media3.decoder.mpegh.MpeghLibrary
import androidx.media3.exoplayer.text.TextOutput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ember.tv.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class PlaybackDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun packagedAudioAndSubtitleLibrariesLoad() {
        assertTrue("Nextlib FFmpeg and libass", nativeAssAvailable())
        assertTrue("IAMF JNI", IamfLibrary.isAvailable())
        assertTrue("MPEG-H JNI", MpeghLibrary.isAvailable())
        assertTrue("HDR Surface JNI", HdrSurface.available)
    }

    @Test fun nativeAssDrawsAndClearsOnSeek() {
        assertTrue(nativeAssAvailable())
        val script = """
            [Script Info]
            ScriptType: v4.00+
            PlayResX: 640
            PlayResY: 360

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
            Style: Default,Roboto,32,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,1,0,2,10,10,10,1

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,{\pos(320,220)}Ember
        """.trimIndent()
        val drew = CountDownLatch(1)
        val cleared = CountDownLatch(1)
        val redrew = CountDownLatch(1)
        val renderer = AssOverlay(context, script, object : TextOutput {
            override fun onCues(cueGroup: CueGroup) {
                val visible = cueGroup.cues.any { it.bitmap != null }
                when {
                    visible && cleared.count == 0L -> redrew.countDown()
                    visible -> drew.countDown()
                    drew.count == 0L -> cleared.countDown()
                }
            }
        }) {}
        try {
            renderer.draw(2_000_000, 640, 360)
            assertTrue("libass produces a bitmap", drew.await(15, TimeUnit.SECONDS))
            renderer.draw(0, 640, 360)
            assertTrue("Backward seek clears the bitmap", cleared.await(5, TimeUnit.SECONDS))
            renderer.draw(2_000_000, 1280, 720)
            assertTrue("Resized viewport renders the subtitle again", redrew.await(5, TimeUnit.SECONDS))
        } finally {
            renderer.close()
        }
    }

    @Test fun hardwareAvcAndRoundTripBetweenEngines() = withFixture("avc") { activity ->
        await(activity, "MediaCodec AVC playback") { screen ->
            screen.player?.isPlaying == true && screen.videoDecoder?.let { decoder ->
                MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { it.name == decoder && it.isHardwareAccelerated }
            } == true
        }
        activity.onActivity { screen ->
            val player = checkNotNull(screen.player)
            player.pause()
            player.seekTo(2_000L)
            player.setPlaybackSpeed(1.25f)
            player.volume = .4f
            screen.playbackEngine?.mode("compatibility")
        }
        await(activity, "libmpv renders the same file") {
            it.playbackEngine?.compatibility != null && it.player?.playbackState == Player.STATE_READY &&
                (it.player?.videoSize?.width ?: 0) > 0
        }
        activity.onActivity { screen ->
            assertFalse(checkNotNull(screen.player).playWhenReady)
            screen.playbackEngine?.mode("standard")
        }
        await(activity, "Media3 resumes the transferred position and controls") { screen ->
            val player = checkNotNull(screen.player)
            screen.playbackEngine?.compatibility == null && player.playbackState == Player.STATE_READY &&
                player.currentPosition in 1750L..2250L && player.playbackParameters.speed == 1.25f &&
                player.volume == .4f && !player.playWhenReady
        }
    }

    @Test fun hdrFixtureSurvivesPictureInPicture() {
        assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
        withFixture("hdr") { activity ->
            await(activity, "HDR10 or HLG frames from MediaCodec") {
                val engine = it.playbackEngine
                (engine?.standard?.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 0 &&
                    HdrOutputValidator.source(engine?.standard?.videoFormat) in setOf(HdrSignal.HDR10, HdrSignal.HLG)
            }
            activity.onActivity {
                assertTrue(it.enterPictureInPictureMode(it.pictureInPictureParams()))
            }
            await(activity, "HDR source stays selected in PiP") {
                it.isInPictureInPictureMode && HdrOutputValidator.source(it.player?.videoFormat) in
                    setOf(HdrSignal.HDR10, HdrSignal.HLG)
            }
        }
    }

    @Test fun trueHdNegotiatesOutput() =
        verifyEncodedOutput("truehd", MimeTypes.AUDIO_TRUEHD, setOf(C.ENCODING_DOLBY_TRUEHD))

    @Test fun dtsHdNegotiatesOutput() =
        verifyEncodedOutput("dtshd", MimeTypes.AUDIO_DTS_HD, setOf(C.ENCODING_DTS_HD))

    @Test fun atmosNegotiatesOutput() =
        verifyEncodedOutput("eac3joc", MimeTypes.AUDIO_E_AC3_JOC, setOf(C.ENCODING_E_AC3, C.ENCODING_E_AC3_JOC))

    private fun verifyEncodedOutput(fixture: String, mime: String, encodings: Set<Int>) {
        val expected = InstrumentationRegistry.getArguments().getString("audioOutput") ?: "pcm"
        require(expected in setOf("pcm", "hdmi", "hdmi-bluetooth-hdmi"))
        withPlaybackConfig({ it.copy(engine = "standard", passthrough = expected != "pcm", passthroughMode = "auto") }) {
            withFixture(fixture) { activity ->
                await(activity, "$fixture on $expected") { screen ->
                    val output = screen.standardAudioTrack ?: return@await false
                    screen.player?.isPlaying == true && screen.player?.audioFormat?.sampleMimeType == mime &&
                        if (expected == "pcm") Util.isEncodingLinearPcm(output.encoding)
                        else !screen.bluetoothOutput && !output.offload && output.encoding in encodings
                }
                if (expected == "hdmi") {
                    activity.onActivity { screen ->
                        assertTrue(screen.recover(PlaybackException(null, null, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED)))
                    }
                    await(activity, "$fixture resumes as PCM after an output error") { screen ->
                        screen.player?.isPlaying == true && screen.codecs.pcm && screen.standardAudioTrack?.let {
                            Util.isEncodingLinearPcm(it.encoding)
                        } == true
                    }
                }
                if (expected == "hdmi-bluetooth-hdmi") {
                    activity.onActivity { it.player?.repeatMode = Player.REPEAT_MODE_ONE }
                    await(activity, "select the Bluetooth device while playback is active", 90_000L) { screen ->
                        screen.player?.isPlaying == true && screen.bluetoothOutput && screen.standardAudioTrack?.let {
                            Util.isEncodingLinearPcm(it.encoding)
                        } == true
                    }
                    await(activity, "switch back to HDMI while playback is active", 90_000L) { screen ->
                        screen.player?.isPlaying == true && !screen.bluetoothOutput && screen.standardAudioTrack?.let {
                            !it.offload && it.encoding in encodings
                        } == true
                    }
                }
            }
        }
    }

    @Test fun ffmpegThenMpvSurviveRepeatedInitialization() = exerciseNativeEngines("standard")

    @Test fun mpvThenFfmpegSurviveRepeatedInitialization() = exerciseNativeEngines("compatibility")

    private fun exerciseNativeEngines(initialEngine: String) {
        val abi = InstrumentationRegistry.getArguments().getString("nativeAbi")
        if (abi != null) {
            require(abi == "arm64-v8a" || abi == "armeabi-v7a")
            assertEquals(abi == "arm64-v8a", Process.is64Bit())
        }
        val other = if (initialEngine == "standard") "compatibility" else "standard"
        withPlaybackConfig({ it.copy(engine = initialEngine, ffmpeg = "prefer", passthrough = false, rememberDecoderChoices = false) }) {
            repeat(2) {
                withFixture("truehd", initialEngine) { activity ->
                    awaitNativeEngine(activity, initialEngine)
                    repeat(3) {
                        for (mode in listOf(other, initialEngine)) {
                            activity.onActivity { screen ->
                                screen.player?.seekTo(0)
                                screen.playbackEngine?.mode(mode)
                            }
                            awaitNativeEngine(activity, mode)
                        }
                    }
                }
            }
        }
    }

    private fun awaitNativeEngine(activity: ActivityScenario<PlayerActivity>, engine: String) {
        await(activity, "$engine plays the encoded fixture") { screen ->
            screen.player?.isPlaying == true && screen.player?.audioFormat != null &&
                if (engine == "compatibility") screen.playbackEngine?.compatibility != null
                else screen.playbackEngine?.compatibility == null && screen.audioDecoder?.contains("ffmpeg", true) == true
        }
    }

    private fun withPlaybackConfig(transform: (PlaybackConfig) -> PlaybackConfig, action: () -> Unit) {
        val prefs = Prefs(context)
        val previous = prefs.playbackConfig
        try {
            prefs.playbackConfig = transform(previous)
            action()
        } finally {
            prefs.playbackConfig = previous
        }
    }

    @Test fun iamfFixtureUsesABundledOrPlatformDecoder() =
        verifyAudio("iamf", setOf("audio/iamf"), "IamfDecoder")

    @Test fun mpegHFixtureUsesABundledOrPlatformDecoder() =
        verifyAudio("mpegh", setOf("audio/mhm1", "audio/mha1"), "libmpegh")

    private fun verifyAudio(fixture: String, formats: Set<String>, bundled: String) = withFixture(fixture) { activity ->
        val platform = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { codec ->
            !codec.isEncoder && codec.supportedTypes.any { it.lowercase() in formats }
        }.map { it.name }.toSet()
        await(activity, "$fixture audio decoder") { screen ->
            screen.player?.isPlaying == true && screen.player?.audioFormat?.sampleMimeType in formats &&
                (screen.audioDecoder == bundled || screen.audioDecoder in platform)
        }
    }

    // Pass an app-readable path with -e avc, -e hdr, -e iamf or -e mpegh.
    private fun withFixture(argument: String, engine: String = "standard", action: (ActivityScenario<PlayerActivity>) -> Unit) {
        val path = InstrumentationRegistry.getArguments().getString(argument)
        assumeTrue("Supply the $argument fixture path", !path.isNullOrBlank())
        val file = File(checkNotNull(path))
        assertTrue("Fixture must be readable by the player", file.isFile && file.canRead())
        val intent = Intent(context, PlayerActivity::class.java)
            .putExtra(PlayRequest.EXTRA_URL, Uri.fromFile(file).toString())
            .putExtra(PlayRequest.EXTRA_TITLE, "Playback regression")
            .putExtra(PlayRequest.EXTRA_RELEASE, file.name)
            .putExtra(PlayRequest.EXTRA_START_OVER, true)
        ActivityScenario.launch<PlayerActivity>(intent).use { activity ->
            await(activity, "Player initialized") { it.playbackEngine != null && it.player != null }
            activity.onActivity { it.playbackEngine?.mode(engine) }
            action(activity)
        }
    }

    private fun await(
        activity: ActivityScenario<PlayerActivity>,
        expected: String,
        timeoutMs: Long = 25_000,
        ready: (PlayerActivity) -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            var reached = false
            activity.onActivity { reached = ready(it) }
            if (reached) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for $expected")
    }
}
