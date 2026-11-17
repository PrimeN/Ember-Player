package app.ember.tv.ui

import androidx.core.graphics.scale
import kotlin.math.abs

data class HeroFocus(val x:Float=.65f,val y:Float=.32f)
data class HeroViewport(val left:Float,val top:Float,val width:Float,val height:Float,val crop:Boolean=false)
internal data class HeroFrame(val fit:Boolean,val xBias:Float,val yBias:Float)
internal data class HeroProjection(val left:Float,val top:Float,val width:Float,val height:Float)

internal object HeroFraming {
    fun supplied(x:Float?,y:Float?):HeroFocus?=if(x!=null && y!=null && x.isFinite() && y.isFinite())HeroFocus(x.coerceIn(0f,1f),y.coerceIn(0f,1f)) else null
    fun plan(width:Int,height:Int,viewWidth:Float,viewHeight:Float,focus:HeroFocus=HeroFocus()):HeroFrame {
        if(width<=0 || height<=0 || !viewWidth.isFinite() || !viewHeight.isFinite() || viewWidth<=0 || viewHeight<=0)return HeroFrame(true,1f,0f)
        val scale=maxOf(viewWidth/width,viewHeight/height)
        val w=width*scale;val h=height*scale
        val retained=minOf(viewWidth/w,viewHeight/h)
        if(fitHeroPortrait(width,height) || retained<.74f)return HeroFrame(true,1f,0f)
        fun bias(point:Float,target:Float,scaled:Float,view:Float):Float {
            val extra=scaled-view
            if(extra<.5f)return 0f
            val p=point.takeIf {it.isFinite()} ?: .5f
            return (((p.coerceIn(0f,1f)*scaled-target*view).coerceIn(0f,extra)/extra)*2f-1f).coerceIn(-1f,1f)
        }
        return HeroFrame(false,bias(focus.x,.72f,w,viewWidth),bias(focus.y,.40f,h,viewHeight))
    }
    fun projection(width:Int,height:Int,viewWidth:Float,viewHeight:Float,focus:HeroFocus=HeroFocus()):HeroProjection? {
        if(width<=0 || height<=0 || !viewWidth.isFinite() || !viewHeight.isFinite() || viewWidth<=0 || viewHeight<=0)return null
        val frame=plan(width,height,viewWidth,viewHeight,focus)
        val scale=if(frame.fit)minOf(viewWidth/width,viewHeight/height) else maxOf(viewWidth/width,viewHeight/height)
        val w=width*scale;val h=height*scale
        if(!w.isFinite() || !h.isFinite())return null
        return HeroProjection((viewWidth-w)*(frame.xBias+1)/2,(viewHeight-h)*(frame.yBias+1)/2,w,h)
    }
    fun glassProjection(width:Int,height:Int,viewport:HeroViewport,focus:HeroFocus=HeroFocus()):HeroProjection? {
        if(!viewport.left.isFinite() || !viewport.top.isFinite())return null
        if(viewport.crop) {
            if(width<=0 || height<=0 || !viewport.width.isFinite() || !viewport.height.isFinite() || viewport.width<=0 || viewport.height<=0)return null
            val scale=maxOf(viewport.width/width,viewport.height/height)
            val w=width*scale;val h=height*scale
            if(!w.isFinite() || !h.isFinite())return null
            return HeroProjection(viewport.left+(viewport.width-w)*(focus.x.takeIf(Float::isFinite) ?: .5f).coerceIn(0f,1f),
                viewport.top+(viewport.height-h)*(focus.y.takeIf(Float::isFinite) ?: .3f).coerceIn(0f,1f),w,h)
        }
        return projection(width,height,viewport.width,viewport.height,focus)?.let {it.copy(left=it.left+viewport.left,top=it.top+viewport.top)}
    }
    /** Detail-weighted framing estimate, not face recognition. Flat art keeps the conservative default. */
    fun estimate(pixels:IntArray,width:Int,height:Int):HeroFocus {
        require(width>=3 && height>=3 && pixels.size==width*height)
        fun luma(pixel:Int)=((pixel shr 16 and 255)*54+(pixel shr 8 and 255)*183+(pixel and 255)*19)/256f
        var total=0.0;var sx=0.0;var sy=0.0
        for(y in 1 until height-1)for(x in 1 until width-1) {
            val energy=abs(luma(pixels[y*width+x+1])-luma(pixels[y*width+x-1]))+
                abs(luma(pixels[(y+1)*width+x])-luma(pixels[(y-1)*width+x]))
            val weight=energy*(.35+x.toDouble()/width)*(.95-.45*y.toDouble()/height)
            total+=weight;sx+=weight*x/width;sy+=weight*y/height
        }
        if(total<width*height*2)return HeroFocus()
        return HeroFocus((sx/total).toFloat().coerceIn(.25f,.85f),(sy/total).toFloat().coerceIn(.18f,.55f))
    }
}

internal fun fitHeroPortrait(width:Int,height:Int)=width>0&&height>0&&width.toDouble()/height<1.45
