package app.ember.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import app.ember.tv.R
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
class PlayerBrandTest {
    private fun draw(id:Int,size:Int):Bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).also {bitmap->
        RuntimeEnvironment.getApplication().getDrawable(id)!!.apply {setBounds(0,0,size,size);draw(Canvas(bitmap))}
    }
    @Test fun adaptiveIconKeepsTheFlameVisibleAtSmallSizes() {
        val icon=draw(R.mipmap.ic_launcher,48)
        var flame=0;var play=0
        for(y in 0 until 48)for(x in 0 until 48){val c=icon.getPixel(x,y)
            if(Color.alpha(c)>200&&Color.red(c)>180&&Color.green(c) in 30..190&&Color.blue(c)<85)flame++
            if(Color.red(c)>225&&Color.green(c)>200&&Color.blue(c)>175)play++}
        assertTrue("The flame should survive the adaptive mask at 48 px",flame>20)
        assertTrue("The surrounding play symbol should remain legible",play>200)
    }
    @Test fun monochromeIconUsesAFlameCutoutRatherThanLosingTheBrand() {
        val icon=draw(R.drawable.ic_launcher_monochrome,108)
        assertEquals(255,Color.alpha(icon.getPixel(35,54)))
        assertEquals(0,Color.alpha(icon.getPixel(46,58)))
    }
}
