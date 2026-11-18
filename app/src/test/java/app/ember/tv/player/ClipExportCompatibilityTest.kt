package app.ember.tv.player

import android.app.Application
import android.os.ParcelFileDescriptor
import androidx.media3.common.Format
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ClipExportCompatibilityTest {
    @Test fun compatibilityAudioOrdinalsDisambiguateMatchingTracksWithoutGuessing() {
        val tracks=listOf(LosslessClip.Track(4,20,"audio/mp4a-latm","eng",2,48000),LosslessClip.Track(7,30,"audio/mp4a-latm","eng",2,48000))
        val format=Format.Builder().setId("mpv:audio:2").setSampleMimeType("audio/mp4a-latm").setLanguage("en").build()
        assertEquals(7,LosslessClip.chooseAudio(tracks,format,false))
        assertNull(LosslessClip.chooseAudio(tracks,format,true))
        try {LosslessClip.chooseAudio(tracks,format.buildUpon().setId("mpv:audio:3").build(),false);fail("An unavailable ordinal must offer video-only export")}catch(_:LosslessClip.AudioUnavailable){}
    }
    @Test fun providersRejectingTruncatingModeAreRetriedWithoutClosingTheOutput() {
        val file=java.io.File.createTempFile("clip-output",".mp4",RuntimeEnvironment.getApplication().cacheDir).apply {writeText("old output bytes")}
        val modes=mutableListOf<String>()
        try {
            LosslessClip.openDestination {mode->
                modes+=mode
                if(mode=="rwt")throw java.io.FileNotFoundException("Mode not supported")
                ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_WRITE)
            }.use {assertTrue(it.fileDescriptor.valid())}
            assertEquals(listOf("rwt","rw"),modes)
        }finally {file.delete()}
    }
}
