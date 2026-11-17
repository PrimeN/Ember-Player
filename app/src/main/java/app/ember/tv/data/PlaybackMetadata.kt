package app.ember.tv.data

data class SubtitleOption(val addonName:String,val lang:String,val url:String,val forced:Boolean=false,val hearingImpaired:Boolean=false,val server:Boolean=false)
data class Person(val name:String,val role:String?,val photo:String?=null,val isCrew:Boolean=false,val tmdbId:Long?=null)
