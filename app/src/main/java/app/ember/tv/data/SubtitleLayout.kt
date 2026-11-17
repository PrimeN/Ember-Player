package app.ember.tv.data

import app.ember.tv.R

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class SubtitleLayout(
    val scalePercent:Int=100,
    val foreground:String="",
    val background:String="",
    val edgeColor:String="#FF000000",
    val outlineWidth:Float=2.5f,
    val alignment:String="auto",
    val letterSpacing:Float=0f,
    val lineSpacing:Float=0f,
    val bottomMarginPercent:Int?=null,
) {
    val needsCompatibility:Boolean get()=alignment in setOf("left","center","right") ||
        outlineWidth.isFinite()&&outlineWidth!=2.5f || letterSpacing.isFinite()&&letterSpacing!=0f || lineSpacing.isFinite()&&lineSpacing!=0f
    val scale get()=scalePercent.coerceIn(50,200)/100f
    fun position(fallback:Float)=bottomMarginPercent?.coerceIn(0,35)?.div(100f) ?: fallback
    fun textColor(legacy:String):Int=color(foreground,when(legacy){"yellow"->0xFFFFE650.toInt();"green"->0xFF99ED9A.toInt();"cyan"->0xFF9CE9F5.toInt();else->-1})
    fun boxColor():Int=color(background,0xBE000000.toInt())
    fun borderColor():Int=color(edgeColor,0xFF000000.toInt())
    fun nativeOptions():Map<String,String> = buildMap {
        put("sub-align-x",alignment.takeIf {it in setOf("left","center","right")} ?: "center")
        put("sub-spacing",finite(letterSpacing,-2f,10f).toString())
        put("sub-line-spacing",finite(lineSpacing,-5f,20f).toString())
    }
    companion object {
        fun validColor(value:String)=value.matches(Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
        fun color(value:String,fallback:Int):Int=if(validColor(value))runCatching {
            val number=value.drop(1).toLong(16)
            if(value.length==7)(number or 0xFF000000L).toInt() else number.toInt()
        }.getOrDefault(fallback) else fallback
        fun hex(value:Int)="#%08X".format(Locale.ROOT,value)
        fun finite(value:Float,min:Float,max:Float)=if(value.isFinite())value.coerceIn(min,max) else 0f
    }
}

object SubtitleEncodings {
    val options=listOf("auto" to R.string.ui_automatic,"UTF-8" to R.string.ui_utf_8,"UTF-16LE" to R.string.ui_utf_16_little_endian,"UTF-16BE" to R.string.ui_utf_16_big_endian,
        "windows-1250" to R.string.ui_central_european,"windows-1251" to R.string.ui_cyrillic,"windows-1252" to R.string.ui_western_european,"windows-1253" to R.string.ui_greek,
        "windows-1254" to R.string.ui_turkish,"windows-1255" to R.string.ui_hebrew,"windows-1256" to R.string.ui_arabic,"windows-1257" to R.string.ui_baltic,"windows-1258" to R.string.ui_vietnamese,
        "windows-874" to R.string.ui_thai,"Shift_JIS" to R.string.ui_japanese_shift_jis,"GB18030" to R.string.ui_chinese_gb18030,"Big5" to R.string.ui_chinese_big5,"EUC-KR" to R.string.ui_korean)
    fun decode(bytes:ByteArray,language:String,encoding:String):String {
        if(bytes.size>=3 && bytes[0]==0xEF.toByte() && bytes[1]==0xBB.toByte() && bytes[2]==0xBF.toByte())return String(bytes,3,bytes.size-3,Charsets.UTF_8)
        SubtitleFiles.bom(bytes)?.let {return it}
        val chosen=options.firstOrNull {it.first==encoding && it.first!="auto"}?.first
        return if(chosen==null)Subtitles.decode(bytes,language) else String(bytes,java.nio.charset.Charset.forName(chosen)).removePrefix("\uFEFF")
    }
}
