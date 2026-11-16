package app.ember.player

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.view.accessibility.CaptioningManager
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.ember.tv.data.*

/** Uses the same selected font and caption priority as playback, with a multiline sample. */
@Composable
internal fun SubtitleStylePreview(config:SubtitleConfig) {
    Box(Modifier.fillMaxWidth()) {
        AndroidView(factory={SubtitlePreviewView(it)},update={it.configure(config)},
            modifier=Modifier.widthIn(max=400.dp).fillMaxWidth().aspectRatio(16f/9f))
    }
}

internal data class SubtitlePreviewStyle(val typeface:Typeface,val foreground:Int,val background:Int,val edge:Int,
    val outline:Float,val shadow:Boolean,val scale:Float,val alignment:Layout.Alignment,val spacing:Float,val lineSpacing:Float,val bottom:Float) {
    companion object {
        fun create(context:Context,c:SubtitleConfig,face:Typeface=SubtitleFonts.typeface(context,c.font)):SubtitlePreviewStyle {
            val manager=context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
            val system=manager?.userStyle?.takeIf {c.systemStyle}
            val edgeType=system?.edgeType
            val size=when(c.size){"small"->.8f;"large"->1.25f;"xlarge"->1.5f;else->1f}
            return SubtitlePreviewStyle(system?.typeface ?: face,system?.foregroundColor ?: c.layout.textColor(c.color),
                system?.backgroundColor ?: if(c.background=="box")c.layout.boxColor() else Color.TRANSPARENT,
                system?.edgeColor ?: c.layout.borderColor(),
                if(system!=null){if(edgeType==CaptioningManager.CaptionStyle.EDGE_TYPE_OUTLINE)2.5f else 0f}
                else if(c.background=="outline")SubtitleLayout.finite(c.layout.outlineWidth,0f,6f) else 0f,
                if(system!=null)edgeType==CaptioningManager.CaptionStyle.EDGE_TYPE_DROP_SHADOW else c.background=="shadow",
                c.layout.scale*size*(if(c.systemStyle)manager?.fontScale?:1f else 1f),
                when(c.layout.alignment){"left"->Layout.Alignment.ALIGN_NORMAL;"right"->Layout.Alignment.ALIGN_OPPOSITE;else->Layout.Alignment.ALIGN_CENTER},
                SubtitleLayout.finite(c.layout.letterSpacing,-2f,10f),SubtitleLayout.finite(c.layout.lineSpacing,-5f,20f),
                c.layout.position(when(c.position){"low"->.03f;"high"->.2f;else->.08f}))
        }
    }
}
internal class SubtitlePreviewView(context:Context):View(context) {
    private val sample="A moment worth watching.\nSubtitles, just the way you like them."
    private val paint=TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val backgroundPaint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var config=SubtitleConfig()
    private var fontStamp=""
    private var face=Typeface.DEFAULT_BOLD
    private var layout:StaticLayout?=null
    private var style:SubtitlePreviewStyle?=null
    private val captions=context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
    private val captionListener=object:CaptioningManager.CaptioningChangeListener() {
        override fun onUserStyleChanged(userStyle:CaptioningManager.CaptionStyle){refresh()}
        override fun onFontScaleChanged(fontScale:Float){refresh()}
    }
    init {contentDescription="Subtitle style preview";importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES}
    fun configure(value:SubtitleConfig) {
        val file=if(value.font==SubtitleFonts.CUSTOM)SubtitleFonts.file(context) else null
        val stamp="${value.font}|${file?.length()}|${file?.lastModified()}"
        if(stamp!=fontStamp){face=SubtitleFonts.typeface(context,value.font);fontStamp=stamp}
        config=value;refresh()
    }
    private fun refresh(){style=SubtitlePreviewStyle.create(context,config,face);layout=null;invalidate()}
    override fun onAttachedToWindow(){super.onAttachedToWindow();captions?.addCaptioningChangeListener(captionListener);refresh()}
    override fun onDetachedFromWindow(){captions?.removeCaptioningChangeListener(captionListener);super.onDetachedFromWindow()}
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){layout=null}
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        if(width<=0||height<=0)return
        backgroundPaint.color=Color.WHITE
        backgroundPaint.shader=LinearGradient(0f,0f,width.toFloat(),height.toFloat(),intArrayOf(0xFF193047.toInt(),0xFF536057.toInt(),0xFF231E23.toInt()),null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),12f,12f,backgroundPaint);backgroundPaint.shader=null
        val s=style?:return
        val reference=height/540f
        paint.typeface=s.typeface;paint.textSize=(height*.0533f*s.scale).coerceAtLeast(1f)
        paint.letterSpacing=s.spacing*reference/paint.textSize
        val maxWidth=(width*.9f).toInt().coerceAtLeast(1)
        val block=layout ?: StaticLayout.Builder.obtain(sample,0,sample.length,paint,maxWidth)
            .setAlignment(s.alignment).setLineSpacing(s.lineSpacing*reference,1f).setIncludePad(true).build().also {layout=it}
        val x=(width-maxWidth)/2f;val y=(height*(1-s.bottom)-block.height).coerceAtLeast(0f)
        canvas.save();canvas.clipRect(0f,0f,width.toFloat(),height.toFloat());canvas.translate(x,y)
        if(Color.alpha(s.background)>0) {
            backgroundPaint.color=s.background
            for(i in 0 until block.lineCount)canvas.drawRoundRect(block.getLineLeft(i)-4*reference,block.getLineTop(i).toFloat(),block.getLineRight(i)+4*reference,block.getLineBottom(i).toFloat(),2f,2f,backgroundPaint)
        }
        paint.clearShadowLayer()
        if(s.shadow)paint.setShadowLayer(3*reference,2.5f*reference,2.5f*reference,s.edge)
        if(s.outline>0){paint.style=Paint.Style.STROKE;paint.strokeJoin=Paint.Join.ROUND;paint.strokeWidth=2*s.outline*reference;paint.color=s.edge;block.draw(canvas)}
        paint.style=Paint.Style.FILL;paint.color=s.foreground;block.draw(canvas);paint.clearShadowLayer();canvas.restore()
    }
}
