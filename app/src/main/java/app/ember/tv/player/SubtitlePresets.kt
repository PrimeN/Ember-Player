package app.ember.tv.player

import app.ember.tv.data.*
import kotlinx.serialization.Serializable

/** Appearance only: applying a style never changes languages, online providers or speech services. */
@Serializable
data class SubtitleStyle(val font:String="default",val customFontName:String="",val size:String="normal",val background:String="outline",
    val color:String="white",val position:String="normal",val authored:Boolean=true,val system:Boolean=false,val layout:SubtitleLayout=SubtitleLayout()) {
    fun applyTo(c:SubtitleConfig)=c.copy(font=SubtitleFonts.normalize(font),customFontName=customFontName.take(120),size=size,
        background=background,color=color,position=position,authoredStyles=authored,systemStyle=system,layout=layout)
    companion object {fun from(c:SubtitleConfig)=SubtitleStyle(c.font,c.customFontName,c.size,c.background,c.color,c.position,c.authoredStyles,c.systemStyle,c.layout)}
}
@Serializable
data class SubtitleStylePreset(val name:String,val style:SubtitleStyle)
internal object SubtitlePresets {
    fun save(values:List<SubtitleStylePreset>,name:String,config:SubtitleConfig):List<SubtitleStylePreset> {
        val clean=PictureProfiles.name(name);require(clean.isNotBlank()){"Enter a style name."}
        val existing=values.filterNot {it.name.equals(clean,true)}
        require(existing.size<12){"Delete a saved style before adding another."}
        return existing+SubtitleStylePreset(clean,SubtitleStyle.from(config))
    }
}
