package app.ember.player

import android.app.Application
import android.graphics.*
import android.text.Layout
import app.ember.tv.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubtitleStylePreviewTest {
    @Test fun previewRepresentsSelectedColoursEdgesSpacingAndPosition() {
        val context=RuntimeEnvironment.getApplication()
        val config=SubtitleConfig(font="mono",background="outline",layout=SubtitleLayout(foreground="#FFFFAA00",edgeColor="#FF112233",outlineWidth=4f,alignment="right",letterSpacing=2f,lineSpacing=5f,bottomMarginPercent=20))
        val style=SubtitlePreviewStyle.create(context,config)
        assertEquals(0xFFFFAA00.toInt(),style.foreground);assertEquals(0xFF112233.toInt(),style.edge)
        assertEquals(4f,style.outline,0f);assertFalse(style.shadow)
        assertEquals(Layout.Alignment.ALIGN_OPPOSITE,style.alignment)
        assertEquals(2f,style.spacing,0f);assertEquals(5f,style.lineSpacing,0f);assertEquals(.2f,style.bottom,0f)
        assertEquals(0,Color.alpha(style.background))
        val shadow=SubtitlePreviewStyle.create(context,config.copy(background="shadow"))
        assertTrue(shadow.shadow);assertEquals(0f,shadow.outline,0f)
    }
    @Test fun updatedPreviewDrawsDifferentStylesWithoutChangingItsBounds() {
        val context=RuntimeEnvironment.getApplication()
        val view=SubtitlePreviewView(context)
        view.layout(0,0,400,225)
        fun draw(value:SubtitleConfig):IntArray {
            view.configure(value)
            val bitmap=Bitmap.createBitmap(400,225,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap));val pixels=IntArray(400*225);bitmap.getPixels(pixels,0,400,0,0,400,225);bitmap.recycle();return pixels
        }
        val outlined=draw(SubtitleConfig(authoredStyles=false))
        val boxed=draw(SubtitleConfig(background="box",font="mono",layout=SubtitleLayout(foreground="#FFFFAA00",background="#88000000",alignment="left",scalePercent=150,lineSpacing=5f)))
        assertFalse(outlined.contentEquals(boxed));assertTrue(boxed.any {Color.red(it)>200 && Color.green(it)>80})
        assertEquals(400,view.width);assertEquals(225,view.height)
        assertEquals("Subtitle style preview",view.contentDescription)
    }
    @Test fun invalidSpacingAndScaleRemainBoundedInThePreview() {
        val context=RuntimeEnvironment.getApplication()
        val style=SubtitlePreviewStyle.create(context,SubtitleConfig(layout=SubtitleLayout(scalePercent=Int.MAX_VALUE,outlineWidth=Float.NaN,letterSpacing=Float.POSITIVE_INFINITY,lineSpacing=Float.NaN,bottomMarginPercent=100)))
        assertEquals(2f,style.scale,0f);assertEquals(0f,style.outline,0f)
        assertEquals(0f,style.spacing,0f);assertEquals(0f,style.lineSpacing,0f);assertEquals(.35f,style.bottom,0f)
    }
}
