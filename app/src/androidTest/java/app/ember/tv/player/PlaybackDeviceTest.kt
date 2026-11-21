package app.ember.tv.player

import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaCodecList
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.iamf.IamfLibrary
import androidx.media3.decoder.mpegh.MpeghLibrary
import androidx.media3.exoplayer.text.TextOutput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    @Test fun iamfFixtureUsesTheBundledDecoder() = withFixture("iamf") { activity ->
        await(activity, "IAMF decoder") { it.player?.isPlaying == true && it.audioDecoder == "IamfDecoder" }
    }

    @Test fun mpegHFixtureUsesTheBundledDecoder() = withFixture("mpegh") { activity ->
        await(activity, "MPEG-H decoder") { it.player?.isPlaying == true && it.audioDecoder == "libmpegh" }
    }

    // Pass an app-readable path with -e avc, -e hdr, -e iamf or -e mpegh.
    private fun withFixture(argument: String, action: (ActivityScenario<PlayerActivity>) -> Unit) {
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
            activity.onActivity { it.playbackEngine?.mode("standard") }
            action(activity)
        }
    }

    private fun await(activity: ActivityScenario<PlayerActivity>, expected: String, ready: (PlayerActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 25_000
        while (SystemClock.uptimeMillis() < deadline) {
            var reached = false
            activity.onActivity { reached = ready(it) }
            if (reached) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for $expected")
    }
}
