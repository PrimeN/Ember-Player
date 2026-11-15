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
    var cacheConfig:CacheConfig
        get()=decoded("cacheConfig",CacheConfig())
        set(value)=put("cacheConfig",value)
    var subtitleConfig:SubtitleConfig
        get()=decoded("subtitleConfig",SubtitleConfig(stremioOpenSubs=false,addonSubs=false))
        set(value)=put("subtitleConfig",value)
    var playbackConfig:PlaybackConfig
        get()=decoded("playbackConfig",PlaybackConfig(playerInterface="modern"))
        set(value)=put("playbackConfig",value)
    var audioPresets:List<app.ember.tv.player.SavedAudioPreset>
        get()=decoded("audioPresets",emptyList())
        set(value)=put("audioPresets",value.take(12))
    var playRules:PlayRules
        get()=decoded("playRules",PlayRules())
        set(value)=put("playRules",value)
    var metaConfig:MetaConfig
        get()=decoded("metaConfig",MetaConfig())
        set(value)=put("metaConfig",value)
    var posterConfig:PosterConfig
        get()=decoded("posterConfig",PosterConfig())
        set(value)=put("posterConfig",value)
    var customPerformance:CustomPerformance
        get()=decoded("customPerformance",CustomPerformance())
        set(value)=put("customPerformance",value)
    var profiles:List<Profile>
        get()=decoded("profiles",listOf(Profile(Profile.MAIN,"Player")))
        set(value)=put("profiles",value)
    var addons:List<String>
        get()=decoded("addons",emptyList())
        set(value)=put("addons",value)
    var addonNames:Map<String,String>
        get()=decoded("addonNames",emptyMap())
        set(value)=put("addonNames",value)
    var pausedAddons:Set<String>
        get()=decoded("pausedAddons",emptySet())
        set(value)=put("pausedAddons",value)
    var trackersQuiet:Set<String>
        get()=decoded("trackersQuiet",emptySet())
        set(value)=put("trackersQuiet",value)
    var featuresOff:Set<String>
        get()=decoded("featuresOff",emptySet())
        set(value)=put("featuresOff",value)
    var watchlist:List<Meta>
        get()=decoded("watchlist",emptyList())
        set(value)=put("watchlist",value)
    var ratingsOff:Set<String>
        get()=decoded("ratingsOff",emptySet())
        set(value)=put("ratingsOff",value)
    var ratingsOrder:List<String>
        get()=decoded("ratingsOrder",emptyList())
        set(value)=put("ratingsOrder",value)
    var detailedBadgeCategoriesOff:Set<String>
        get()=decoded("detailedBadgeCategoriesOff",emptySet())
        set(value)=put("detailedBadgeCategoriesOff",value)
    var badgesOff:Set<String>
        get()=decoded("badgesOff",emptySet())
        set(value)=put("badgesOff",value)
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
    var badgeMode:String
        get()=sp.getString("badgeMode","detailed") ?: "detailed"
        set(value) {sp.edit {putString("badgeMode",value)}}
    var watchRegion:String
        get()=sp.getString("watchRegion","US") ?: "US"
        set(value) {sp.edit {putString("watchRegion",value)}}
    var player:String
        get()=sp.getString("player","builtin") ?: "builtin"
        set(value) {sp.edit {putString("player",value)}}
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
    var addonTimeout:Int
        get()=sp.getInt("addonTimeout",20)
        set(value) {sp.edit {putInt("addonTimeout",value)}}
    var countdown:Int
        get()=sp.getInt("countdown",10)
        set(value) {sp.edit {putInt("countdown",value)}}
    var navigationGlass:Boolean
        get()=sp.getBoolean("navigationGlass",true)
        set(value) {sp.edit {putBoolean("navigationGlass",value)}}
    var autoplay:Boolean
        get()=sp.getBoolean("autoplay",true)
        set(value) {sp.edit {putBoolean("autoplay",value)}}
    var ratingsOn:Boolean
        get()=sp.getBoolean("ratingsOn",true)
        set(value) {sp.edit {putBoolean("ratingsOn",value)}}
    var trackerSync:Boolean
        get()=sp.getBoolean("trackerSync",true)
        set(value) {sp.edit {putBoolean("trackerSync",value)}}
    var remoteSounds:Boolean
        get()=sp.getBoolean("remoteSounds",false)
        set(value) {sp.edit {putBoolean("remoteSounds",value)}}
    var oled:Boolean
        get()=sp.getBoolean("oled",false)
        set(value) {sp.edit {putBoolean("oled",value)}}
    var subtitlesOn:Boolean
        get()=sp.getBoolean("subtitlesOn",true)
        set(value) {sp.edit {putBoolean("subtitlesOn",value)}}
    var showWatchedBadges:Boolean
        get()=sp.getBoolean("showWatchedBadges",true)
        set(value) {sp.edit {putBoolean("showWatchedBadges",value)}}
    var blurUnwatchedEpisodes:Boolean
        get()=sp.getBoolean("blurUnwatchedEpisodes",false)
        set(value) {sp.edit {putBoolean("blurUnwatchedEpisodes",value)}}
    companion object {const val PLAYER_BUILTIN="builtin";const val PLAYER_ASK="ask"}
}
object Features {const val LIVE="live";const val SERVERS="servers";const val TRACKERS="trackers";const val CAST="cast"}
