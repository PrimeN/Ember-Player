package app.ember.tv.player

import app.ember.tv.data.CachePolicy
import kotlinx.serialization.Serializable

@Serializable
data class PictureState(val picture:PictureEnhancement=PictureEnhancement(),val scaler:String="bilinear",
    val deband:Boolean=false,val interpolation:Boolean=false,val hdr:HdrOutput=HdrOutput()) {
    fun normalized()=copy(picture=picture.normalized(),scaler=scaler.takeIf {it in setOf("bilinear","bicubic","lanczos")} ?: "bilinear",hdr=hdr.normalized())
    fun applyTo(config:PlaybackConfig):PlaybackConfig=normalized().let {config.copy(picture=it.picture,scaler=it.scaler,deband=it.deband,interpolation=it.interpolation,hdrOutput=it.hdr)}
    companion object {fun from(config:PlaybackConfig)=PictureState(config.picture,config.scaler,config.deband,config.interpolation,config.hdrOutput).normalized()}
}
@Serializable
data class PictureProfile(val id:String,val name:String,val state:PictureState)

internal object PictureProfiles {
    const val MAX_PROFILES=16
    const val MAX_MEDIA=256
    private val profileId=Regex("[a-zA-Z0-9-]{1,64}")
    private val mediaId=Regex("[0-9a-f]{64}")
    fun name(value:String)=value.trim().filterNot {it.isISOControl() || it=='\u2028' || it=='\u2029'}.take(32)
    fun normalized(config:PlaybackConfig):PlaybackConfig {
        val seen=hashSetOf<String>();val names=hashSetOf<String>()
        val profiles=config.pictureProfiles.mapNotNull {p->
            val n=name(p.name)
            if(!profileId.matches(p.id) || n.isBlank() || !seen.add(p.id) || !names.add(n.lowercase(java.util.Locale.ROOT)))null
            else p.copy(name=n,state=p.state.normalized())
        }.take(MAX_PROFILES)
        val ids=profiles.map {it.id}.toSet()
        val bindings=config.mediaPictureProfiles.filter {(k,v)->mediaId.matches(k)&&v in ids}.entries.toList().takeLast(MAX_MEDIA).associate {it.key to it.value}
        return config.copy(pictureProfiles=profiles,mediaPictureProfiles=bindings)
    }
    fun selected(config:PlaybackConfig,media:String?):PictureProfile? {
        if(media.isNullOrBlank() || config.mediaPictureProfiles.isEmpty())return null
        val id=config.mediaPictureProfiles[CachePolicy.digest(media)] ?: return null
        return config.pictureProfiles.take(MAX_PROFILES).firstOrNull {it.id==id && name(it.name).isNotBlank()}?.let {it.copy(name=name(it.name),state=it.state.normalized())}
    }
    fun effective(config:PlaybackConfig,media:String?)=selected(config,media)?.state?.applyTo(config) ?: config
    fun save(config:PlaybackConfig,title:String,state:PictureState,id:()->String={java.util.UUID.randomUUID().toString()}):PlaybackConfig {
        val safe=normalized(config);val n=name(title)
        if(n.isBlank())throw PresetValidationException(app.ember.tv.R.string.ui_profile_name_required)
        val existing=safe.pictureProfiles.firstOrNull {it.name.equals(n,true)}
        if(existing==null && safe.pictureProfiles.size>=MAX_PROFILES)throw PresetValidationException(app.ember.tv.R.string.ui_profile_limit_reached)
        val value=PictureProfile(existing?.id ?: id(),n,state.normalized())
        return normalized(safe.copy(pictureProfiles=safe.pictureProfiles.filterNot {it.id==value.id}+value))
    }
    fun remove(config:PlaybackConfig,id:String)=normalized(config.copy(pictureProfiles=config.pictureProfiles.filterNot {it.id==id}))
    fun bind(config:PlaybackConfig,media:String,id:String?):PlaybackConfig {
        require(media.isNotBlank())
        val safe=normalized(config);val key=CachePolicy.digest(media)
        val map=safe.mediaPictureProfiles.toMutableMap().apply {remove(key);if(id!=null && safe.pictureProfiles.any {it.id==id})put(key,id)}
        return normalized(safe.copy(mediaPictureProfiles=map))
    }
    fun moved(config:PlaybackConfig,from:String,to:String):PlaybackConfig {
        if(from==to)return config
        val selected=selected(config,from) ?: return config
        return bind(bind(config,from,null),to,selected.id)
    }
    /** Editing a media-bound profile updates that named profile; unrelated global settings stay intact. */
    fun edit(config:PlaybackConfig,media:String?,change:(PictureState)->PictureState):PlaybackConfig {
        val selected=selected(config,media)
        return if(selected==null)change(PictureState.from(config)).applyTo(config)
        else normalized(config.copy(pictureProfiles=config.pictureProfiles.map {if(it.id==selected.id)it.copy(state=change(it.state).normalized()) else it}))
    }
}
