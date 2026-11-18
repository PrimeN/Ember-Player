package app.ember.tv.player

import android.app.Application
import androidx.media3.common.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class BackgroundAudioTrackTest {
    @Test fun preservingStereoDoesNotSwitchToAnotherSameLanguageSurroundTrack() {
        val stereo=Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("en").setChannelCount(2).setLabel("Stereo").build()
        val surround=Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3).setLanguage("en").setChannelCount(6).setLabel("Surround").build()
        val group=TrackGroup("audio",surround,stereo)
        val tracks=Tracks(listOf(Tracks.Group(group,false,intArrayOf(C.FORMAT_HANDLED,C.FORMAT_HANDLED),booleanArrayOf(false,true))))
        val choice=BackgroundAudioTrack.from(stereo)!!
        assertEquals(listOf(1),choice.selection(tracks)?.trackIndices)
        assertEquals(choice,BackgroundAudioTrack.from(choice.bundle()))
        assertEquals(choice,BackgroundAudioTrack.selected(tracks))
    }
    @Test fun identicalAudioTracksKeepTheSelectionFromTheActiveProgram() {
        val format=Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("en").setChannelCount(2).build()
        val first=TrackGroup("program-one",format);val second=TrackGroup("program-two",format)
        val tracks=Tracks(listOf(Tracks.Group(first,false,intArrayOf(C.FORMAT_HANDLED),booleanArrayOf(false)),Tracks.Group(second,false,intArrayOf(C.FORMAT_HANDLED),booleanArrayOf(true))))
        assertEquals(second,BackgroundAudioTrack.from(format)!!.selection(tracks)?.mediaTrackGroup)
    }
    @Test fun aMissingSoundtrackFallsBackWithoutSelectingAnUnsupportedTrack() {
        val format=Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("fr").setChannelCount(2).build()
        val tracks=Tracks(listOf(Tracks.Group(TrackGroup("audio",format),false,intArrayOf(C.FORMAT_UNSUPPORTED_TYPE),booleanArrayOf(false))))
        assertNull(BackgroundAudioTrack.from(format)!!.selection(tracks))
        assertNull(BackgroundAudioTrack.selected(tracks))
    }
}
