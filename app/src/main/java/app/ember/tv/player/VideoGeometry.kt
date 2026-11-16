package app.ember.tv.player

import app.ember.tv.data.CachePolicy
import kotlinx.serialization.Serializable

@Serializable
data class VideoGeometry(val zoom:String="fit",val aspect:Int=0,val magnification:Float=1f) {
    fun normalized()=copy(zoom=zoom.takeIf {it in setOf("fit","crop","stretch")} ?: "fit",aspect=aspect.coerceIn(0,5),
        magnification=if(magnification.isFinite())magnification.coerceIn(1f,4f) else 1f)
}
internal object GeometryMemory {
    fun selected(config:PlaybackConfig,key:String)=config.mediaGeometry[CachePolicy.digest(key)]?.normalized()
    fun set(config:PlaybackConfig,key:String,value:VideoGeometry?):PlaybackConfig {
        if(key.isBlank())return config
        val hash=CachePolicy.digest(key)
        val map=config.mediaGeometry.toMutableMap().apply {remove(hash);if(value!=null)put(hash,value.normalized())}
        return config.copy(mediaGeometry=map.entries.toList().takeLast(256).associate {it.key to it.value})
    }
    fun moved(config:PlaybackConfig,from:String,to:String):PlaybackConfig=selected(config,from)?.let {set(set(config,from,null),to,it)} ?: config
}
