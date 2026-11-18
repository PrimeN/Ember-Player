package app.ember.tv.player

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.view.accessibility.CaptioningManager
import app.ember.tv.data.SubtitleConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CompatibilitySettingsTest {
    @Test fun androidCaptionSettingsReplaceCustomFontAndColours() {
        val app:Application=RuntimeEnvironment.getApplication()
        Settings.Secure.putString(app.contentResolver,"accessibility_captioning_typeface","serif")
        Settings.Secure.putFloat(app.contentResolver,"accessibility_captioning_font_scale",1.5f)
        val values=mutableMapOf<String,Any>()
        SystemCaptionStyle.apply(app,SubtitleConfig(systemStyle=true,font="custom",customFontName="Imported Family"),
            {k,v->values[k]=v},{k,v->values[k]=v},{k,v->values[k]=v})
        val manager=app.getSystemService(Context.CAPTIONING_SERVICE) as CaptioningManager
        assertEquals("serif",values["sub-font"]);assertEquals("",values["sub-fonts-dir"])
        assertEquals("#%08X".format(java.util.Locale.ROOT,manager.userStyle.foregroundColor),values["sub-color"])
        assertEquals(38.0*manager.fontScale,values["sub-font-size"] as Double,.001)
        Settings.Secure.putString(app.contentResolver,"accessibility_captioning_typeface",null)
        assertEquals("sans-serif",SystemCaptionStyle.family(app))
    }
    @Test fun seamlessMatchingCannotSelectAResolutionChangeOrAnUnlistedRefreshRate() {
        fun mode(id:Int,width:Int,height:Int,rate:Float)=org.robolectric.util.ReflectionHelpers.callConstructor(android.view.Display.Mode::class.java,
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,id),
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,width),
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,height),
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(Float::class.javaPrimitiveType,rate))
        val current=mode(1,1920,1080,60f);val seamless=mode(2,1920,1080,48f)
        val disruptive=mode(3,1920,1080,24f);val resolution=mode(4,3840,2160,24f)
        org.robolectric.util.ReflectionHelpers.setField(current,"mAlternativeRefreshRates",floatArrayOf(48f))
        val modes=listOf(current,seamless,disruptive,resolution)
        assertEquals(2,Playback.modeFor(current,modes,24f,1920,1080,false,true)?.modeId)
        assertEquals(3,Playback.modeFor(current,modes,24f,1920,1080,false,false)?.modeId)
        org.robolectric.util.ReflectionHelpers.setField(current,"mAlternativeRefreshRates",floatArrayOf())
        assertNull(Playback.modeFor(current,modes,24f,1920,1080,false,true))
    }
    @Test fun switchingBackendReappliesTheCurrentRouteDelayIncludingBluetooth() {
        val delay=Playback.AudioDelay();val received=mutableListOf<Long>();var native=false
        val timing=AudioTiming(delay){if(native)received+=it}
        timing.set(80);assertTrue(received.isEmpty())
        native=true;timing.backendChanged();assertEquals(listOf(80L),received)
        timing.set(-120);assertEquals(-120_000L,delay.us)
        native=false;timing.set(240);native=true;timing.backendChanged()
        assertEquals(listOf(80L,-120L,240L),received)
        timing.set(0);assertEquals(0L,received.last())
    }
    @Test fun soundPanelStepsAndResetKeepStandardTimingInBounds() {
        val activity=Robolectric.buildActivity(PlayerActivity::class.java).get()
        activity.config=PlaybackConfig()
        activity.shiftSound(100);activity.shiftSound(20);assertEquals(120_000L,activity.audioDelay.us)
        activity.shiftSound(-20);activity.shiftSound(-100);assertEquals(0L,activity.audioDelay.us)
        repeat(20){activity.shiftSound(100)};assertEquals(1_000_000L,activity.audioDelay.us)
        activity.shiftSound(null);assertEquals(0L,activity.audioDelay.us)
    }
}
