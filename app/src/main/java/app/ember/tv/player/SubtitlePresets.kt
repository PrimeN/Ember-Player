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
        val clean=PictureProfiles.name(name);if(clean.isBlank())throw PresetValidationException(app.ember.tv.R.string.ui_style_name_required)
        val existing=values.filterNot {it.name.equals(clean,true)}
        if(existing.size>=12)throw PresetValidationException(app.ember.tv.R.string.ui_style_limit_reached)
        return existing+SubtitleStylePreset(clean,SubtitleStyle.from(config))
    }
}
