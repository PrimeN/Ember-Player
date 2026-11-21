package app.ember.tv.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import app.ember.tv.player.PlaybackConfig

class Prefs(context:Context) {
    private val sp=context.applicationContext.getSharedPreferences("ember",Context.MODE_PRIVATE)
    private val json=Json {ignoreUnknownKeys=true}
    private inline fun <reified T> decoded(key:String,default:T):T=runCatching {json.decodeFromString<T>(sp.getString(key,null) ?: return default)}.getOrDefault(default)
    private inline fun <reified T> put(key:String,value:T) {sp.edit {putString(key,json.encodeToString(value))}}
    var networkMediaPreviews:Boolean
        get()=sp.getBoolean("networkMediaPreviews",true)
        set(value) {sp.edit {putBoolean("networkMediaPreviews",value)}}
    var playerLibraryGrid:Boolean
        get()=decoded("playerLibraryGrid",true)
        set(value)=put("playerLibraryGrid",value)
    var playerLibrarySort:String
        get()=decoded("playerLibrarySort","modified")
        set(value)=put("playerLibrarySort",value)
    var playerLibraryFilter:String
        get()=decoded("playerLibraryFilter","all")
        set(value)=put("playerLibraryFilter",value)
    var cacheConfig:CacheConfig
        get()=decoded("cacheConfig",CacheConfig())
        set(value)=put("cacheConfig",value)
    var subtitleConfig:SubtitleConfig
        get()=decoded("subtitleConfig",SubtitleConfig(stremioOpenSubs=false))
        set(value)=put("subtitleConfig",value)
    var playbackConfig:PlaybackConfig
        get()=decoded("playbackConfig",PlaybackConfig(playerInterface="modern"))
        set(value)=put("playbackConfig",value)
    var subtitlePresets:List<app.ember.tv.player.SubtitleStylePreset>
        get()=decoded("subtitlePresets",emptyList())
        set(value)=put("subtitlePresets",value.take(12))
    var audioPresets:List<app.ember.tv.player.SavedAudioPreset>
        get()=decoded("audioPresets",emptyList())
        set(value)=put("audioPresets",value.take(12))
    var customPerformance:CustomPerformance
        get()=decoded("customPerformance",CustomPerformance())
        set(value)=put("customPerformance",value)
    var profiles:List<Profile>
        get()=decoded("profiles",listOf(Profile(Profile.MAIN,"Player")))
        set(value)=put("profiles",value)
    var currentProfile:String
        get()=sp.getString("currentProfile","main") ?: "main"
        set(value) {sp.edit {putString("currentProfile",value)}}
    var uiStyle:String
        get()=sp.getString("uiStyle","modern") ?: "modern"
        set(value) {sp.edit {putString("uiStyle",value)}}
    var contentDensity:String
        get()=sp.getString("contentDensity","standard") ?: "standard"
        set(value) {sp.edit {putString("contentDensity",value)}}
    var artworkQuality:String
        get()=sp.getString("artworkQuality","balanced") ?: "balanced"
        set(value) {sp.edit {putString("artworkQuality",value)}}
    var devicePerformance:String
        get()=sp.getString("devicePerformance","auto") ?: "auto"
        set(value) {sp.edit {putString("devicePerformance",value)}}
    var navigationGlassMethod:String
        get()=sp.getString("navigationGlassMethod","prepared") ?: "prepared"
        set(value) {sp.edit {putString("navigationGlassMethod",value)}}
    var remoteSettingsKey:String
        get()=sp.getString("remoteSettingsKey","ember") ?: "ember"
        set(value) {sp.edit {putString("remoteSettingsKey",value)}}
    var theme:String
        get()=sp.getString("theme","night") ?: "night"
        set(value) {sp.edit {putString("theme",value)}}
    var screenMargin:String
        get()=sp.getString("screenMargin","0") ?: "0"
        set(value) {sp.edit {putString("screenMargin",value)}}
    var uiSize:String
        get()=sp.getString("uiSize","normal") ?: "normal"
        set(value) {sp.edit {putString("uiSize",value)}}
    var textSize:String
        get()=sp.getString("textSize","normal") ?: "normal"
        set(value) {sp.edit {putString("textSize",value)}}
    var accent:Int
        get()=sp.getInt("accent",0)
        set(value) {sp.edit {putInt("accent",value)}}
    var navigationGlass:Boolean
        get()=sp.getBoolean("navigationGlass",true)
        set(value) {sp.edit {putBoolean("navigationGlass",value)}}
    var oled:Boolean
        get()=sp.getBoolean("oled",false)
        set(value) {sp.edit {putBoolean("oled",value)}}
    var subtitlesOn:Boolean
        get()=sp.getBoolean("subtitlesOn",true)
        set(value) {sp.edit {putBoolean("subtitlesOn",value)}}
    companion object {const val PLAYER_BUILTIN="builtin";const val PLAYER_ASK="ask"}
}
