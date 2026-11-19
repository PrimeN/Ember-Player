package app.ember.tv.player

import app.ember.tv.R

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.*
import androidx.media3.common.util.*
import androidx.media3.effect.*
import kotlin.math.pow

@androidx.annotation.OptIn(UnstableApi::class)
internal object StandardPictureEffects {
    fun supported(format:Format?)=format!=null && format.drmInitData==null && format.sampleMimeType!=MimeTypes.VIDEO_DOLBY_VISION &&
        format.colorInfo?.colorTransfer==C.COLOR_TRANSFER_SDR
    fun effects(config:PictureEnhancement,width:Int,height:Int,format:Format?):List<Effect> {
        if(!config.enabled || !supported(format))return emptyList()
        val p=config.normalized()
        return buildList {
            val quarterTurn=format!!.rotationDegrees%180!=0
            val inputWidth=if(quarterTurn)format.height else format.width
            val inputHeight=if(quarterTurn)format.width else format.height
            if(p.upscaling && width>inputWidth && height>inputHeight && inputWidth>0 && inputHeight>0)
                add(LanczosResample.scaleToFit(width,height))
            if(p.brightness!=0)add(Brightness(p.brightness/100f))
            if(p.contrast!=0)add(Contrast(p.contrast/100f))
            if(p.saturation!=0 || p.hue!=0)add(HslAdjustment.Builder().adjustSaturation(p.saturation.toFloat()).adjustHue(p.hue*1.8f).build())
            if(p.gamma!=0 || p.sharpness!=0)add(PictureDetailEffect(p.gamma,p.sharpness))
        }
    }
}

/** Small SDR-only detail pass; defaults are neutral. HDR keeps the original renderer path. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PictureDetailEffect(private val gamma:Int,private val sharpness:Int):GlEffect {
    override fun toGlShaderProgram(context:Context,useHdr:Boolean):GlShaderProgram {
        if(useHdr)throw VideoFrameProcessingException(context.resources.getString(R.string.ui_picture_detail_processing_requires_sdr))
        return object:BaseGlShaderProgram(false,1) {
            private val program=try {GlProgram(VERTEX,FRAGMENT).apply {
                setBufferAttribute("aPosition",GlUtil.getNormalizedCoordinateBounds(),4)
                setFloatUniform("uGamma",2f.pow(gamma.coerceIn(-50,50)/50f))
                setFloatUniform("uSharpness",sharpness.coerceIn(0,100)/200f)
            }}catch(e:Exception){throw VideoFrameProcessingException(e)}
            override fun configure(inputWidth:Int,inputHeight:Int):Size {
                require(inputWidth>0 && inputHeight>0)
                program.setFloatsUniform("uPixel",floatArrayOf(1f/inputWidth,1f/inputHeight))
                return Size(inputWidth,inputHeight)
            }
            override fun drawFrame(inputTexId:Int,presentationTimeUs:Long) {
                try {program.use();program.setSamplerTexIdUniform("uTexture",inputTexId,0);program.bindAttributesAndUniforms()
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GlUtil.checkGlError()
                }catch(e:Exception){throw VideoFrameProcessingException(e,presentationTimeUs)}
            }
            override fun release() {try{super.release()}finally{try{program.delete()}catch(e:Exception){throw VideoFrameProcessingException(e)}}}
        }
    }
    companion object {
        const val VERTEX="""attribute vec4 aPosition;
varying vec2 vTexture;
void main(){ gl_Position=aPosition; vTexture=aPosition.xy*0.5+0.5; }
"""
        const val FRAGMENT="""precision highp float;
uniform sampler2D uTexture;
uniform vec2 uPixel;
uniform float uGamma;
uniform float uSharpness;
varying vec2 vTexture;
void main(){
 vec4 center=texture2D(uTexture,vTexture);
 vec3 left=texture2D(uTexture,vTexture-vec2(uPixel.x,0.0)).rgb;
 vec3 right=texture2D(uTexture,vTexture+vec2(uPixel.x,0.0)).rgb;
 vec3 above=texture2D(uTexture,vTexture+vec2(0.0,uPixel.y)).rgb;
 vec3 below=texture2D(uTexture,vTexture-vec2(0.0,uPixel.y)).rgb;
 vec3 low=min(center.rgb,min(min(left,right),min(above,below)));
 vec3 high=max(center.rgb,max(max(left,right),max(above,below)));
 vec3 sharpened=clamp(center.rgb+uSharpness*(center.rgb-0.25*(left+right+above+below)),low,high);
 gl_FragColor=vec4(pow(clamp(sharpened,0.0,1.0),vec3(1.0/uGamma)),center.a);
}
"""
    }
}
