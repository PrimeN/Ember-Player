package app.ember.tv.player

import androidx.media3.common.*
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SubtitleCoordinationTest {
    @Test fun positiveDelayKeepsCuesAtZeroHiddenUntilTheirTime() {
        assertEquals(-500000L,SubtitleClock.timeUs(0,delayMs=500))
        assertEquals(-1000L,SubtitleClock.timeUs(499,delayMs=500))
        assertEquals(0L,SubtitleClock.timeUs(500,delayMs=500))
        assertEquals(200000L,SubtitleClock.timeUs(0,delayMs=-200))
        assertEquals(-500000L,SubtitleClock.timeUs(100,partOffsetMs=1000,delayMs=500))
    }
    @Test fun nativeAndOverlayTimingAgreeForSpeedDelayAndMultipartSources() {
        for(speed in listOf(.959f,1f,1.0427f))for(delay in listOf(-200L,0L,500L)) {
            val relativeMs=44000L;val prefix=3600000L
            val nativeLocal=(relativeMs/1000.0-SubtitleTiming.nativeDelay(delay,speed))/SubtitleTiming.nativeSpeed(speed)
            val nativeWhole=(relativeMs/1000.0-SubtitleTiming.nativeDelay(delay,speed,prefix))/SubtitleTiming.nativeSpeed(speed)
            assertEquals(nativeLocal,SubtitleClock.timeUs(prefix+relativeMs,prefix,speed,delay)/1e6,.000002)
            assertEquals(nativeWhole,SubtitleClock.timeUs(prefix+relativeMs,0,speed,delay)/1e6,.000002)
        }
        assertEquals(1000000L,SubtitleClock.timeUs(1000,speed=Float.NaN))
    }
    @Test fun eachLayerHasOneOwnerAndClearsBeforeOwnershipChanges() {
        val transitions=mutableListOf<Triple<SubtitleLayer,SubtitleOwner,SubtitleOwner>>()
        lateinit var coordinator:SubtitleRenderCoordinator
        coordinator=SubtitleRenderCoordinator {layer,old,next->
            assertEquals(old,coordinator.owner(layer));transitions+=Triple(layer,old,next)
        }
        coordinator.select(SubtitleOwner.EMBEDDED)
        coordinator.select(SubtitleOwner.EXTERNAL)
        coordinator.select(SubtitleOwner.NATIVE)
        coordinator.select(SubtitleOwner.NATIVE)
        coordinator.select(SubtitleOwner.EMBEDDED,SubtitleLayer.SECONDARY)
        assertEquals(4,transitions.size)
        assertEquals(SubtitleOwner.NATIVE,coordinator.owner())
        coordinator.select(SubtitleOwner.OFF)
        assertEquals(SubtitleOwner.EMBEDDED,coordinator.owner(SubtitleLayer.SECONDARY))
    }
    @Test fun authoredAssIsDetectedWithoutTreatingPlainSubtitlesAsStyledAss() {
        for(tag in listOf("pos(10,20)","move(1,2,3,4)","kf30","t(0,100,\\fscx120)","fnCustom"))
            assertTrue(tag,SubtitleStyleProbe.styled("[Events]\nDialogue: {\\$tag}Hello"))
        assertTrue(SubtitleStyleProbe.styled("[V4+ Styles]\nStyle: Sign,Custom font,24"))
        assertFalse(SubtitleStyleProbe.styled("1\n00:00:01,000 --> 00:00:02,000\nHello"))
        assertFalse(SubtitleStyleProbe.styled("[V4+ Styles]\nStyle: Default,Arial,24"))
    }
    @Test fun automaticSubtitleRoutingPreservesHdrSpecialAudioAndExplicitChoices() {
        val video=Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264)
            .setColorInfo(ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_SDR).build()).build()
        val audio=Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()
        fun route(v:Format?=video,a:Format?=audio,auto:Boolean=true,system:Boolean=false)=SubtitleStyleProbe.routeToMpv(auto,true,system,true,v,a)
        assertTrue(route());assertFalse(route(auto=false));assertFalse(route(system=true));assertFalse(route(v=null))
        assertFalse(route(v=video.buildUpon().setColorInfo(null).build()))
        for(transfer in listOf(C.COLOR_TRANSFER_ST2084,C.COLOR_TRANSFER_HLG))
            assertFalse(route(v=video.buildUpon().setColorInfo(ColorInfo.Builder().setColorTransfer(transfer).build()).build()))
        for(mime in listOf("audio/ac4","audio/mha1","audio/mhm1","audio/iamf"))assertFalse(route(a=audio.buildUpon().setSampleMimeType(mime).build()))
        assertFalse(route(v=video.buildUpon().setDrmInitData(DrmInitData("cenc",DrmInitData.SchemeData(C.WIDEVINE_UUID,"video/mp4",byteArrayOf(1)))).build()))
    }
}
