package app.ember.tv.player

enum class Kind { MOVIE, SHOW, ANIME }

data class PlaybackItem(val key:String,val title:String,val kind:Kind=Kind.MOVIE,val imdbId:String?=null,
    val tmdbId:Long?=null,val season:Int?=null,val episode:Int?=null)
data class Chapter(val name:String,val startMs:Long)
data class Marker(val kind:String,val startMs:Long,val endMs:Long)
