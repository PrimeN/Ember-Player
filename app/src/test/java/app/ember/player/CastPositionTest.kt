package app.ember.player

import android.app.Application
import app.ember.tv.data.Prefs
import app.ember.tv.player.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class)
class CastPositionTest {
    private val app:Application get()=RuntimeEnvironment.getApplication()
    private fun request()=CastRequest("https://example.test/movie.mp4","Movie","movie",emptyMap(),40_000,600_000,false,null,profile=Prefs(app).currentProfile)
    @Test fun disconnectSavesTheLastReceiverReportEvenWhenTheUiShowsAnError() {
        PlayerUiStoreFixture(app).use {
            val controller=Robolectric.buildService(CastPlaybackService::class.java).create()
            val service=controller.get()
            ReflectionHelpers.setField(service,"request",request())
            ReflectionHelpers.setField(service,"receiverStatus",ReceiverStatus(234_567,600_000,false))
            CastPlaybackService.state.value=CastingState("","","",error="Disconnected")
            service.stopRemote(false)
            assertEquals(234_567L to 600_000L,PositionStore(app).get("movie"))
            controller.destroy()
            assertEquals(234_567L to 600_000L,PositionStore(app).get("movie"))
        }
    }
    @Test fun completedReceiversKeepWatchedStateEvenIfTheyReportZeroPositionOrDuration() {
        PlayerUiStoreFixture(app).use {
            saveCastPosition(app,request(),ReceiverStatus(finished=true))
            assertEquals(600_000L to 600_000L,PositionStore(app).get("movie"))
            assertNull(PositionStore(app).resume("movie"))
        }
    }
    @Test fun livePrivateHistoryDisabledAndOtherProfileSessionsNeverWriteProgress() {
        PlayerUiStoreFixture(app).use {
            val request=request();val status=ReceiverStatus(120_000,600_000)
            saveCastPosition(app,request.copy(live=true),status)
            saveCastPosition(app,request.copy(profile="other"),status)
            assertNull(PositionStore(app).get("movie"))
            assertTrue(PrivateMedia.configure("123456"))
            PrivateMedia.add(PrivateItem(request.source,"Private movie"))
            saveCastPosition(app,request,status)
            assertNull(PositionStore(app).get("movie"))
            PrivateMedia.recordHistory(false)
            saveCastPosition(app,request.copy(source="https://example.test/public.mp4",key="public"),status)
            assertTrue(app.getSharedPreferences("positions",0).all.isEmpty())
        }
    }
    @Test fun receiverStopKeepsTheLastPositionAndOnlyCompletionMarksItWatched() {
        PlayerUiStoreFixture(app).use {
            val request=request().copy(dlna=DlnaReceiver("TV","192.168.1.5","http://192.168.1.5/control","AVTransport"))
            val halfway=castProgressReport(request,ReceiverStatus(finished=true),ReceiverStatus(300_000,600_000,true))
            assertFalse(halfway.finished)
            saveCastPosition(app,request,halfway)
            assertEquals(300_000L to 600_000L,PositionStore(app).get("movie"))
            val completed=castProgressReport(request,ReceiverStatus(finished=true),ReceiverStatus(599_000,600_000,true))
            assertTrue(completed.finished)
            saveCastPosition(app,request,completed)
            assertEquals(600_000L to 600_000L,PositionStore(app).get("movie"))
            assertFalse(castProgressReport(request,ReceiverStatus(finished=true),null).finished)
        }
    }
    @Test fun invalidDurationsAreIgnoredAndPositionsStayInsideTheReceiverDuration() {
        PlayerUiStoreFixture(app).use {
            saveCastPosition(app,request().copy(duration=0),ReceiverStatus(60_000,0))
            assertNull(PositionStore(app).get("movie"))
            saveCastPosition(app,request(),ReceiverStatus(-100,300_000))
            assertEquals(0L to 300_000L,PositionStore(app).get("movie"))
            saveCastPosition(app,request(),ReceiverStatus(Long.MAX_VALUE,300_000))
            assertEquals(300_000L to 300_000L,PositionStore(app).get("movie"))
        }
    }
}
