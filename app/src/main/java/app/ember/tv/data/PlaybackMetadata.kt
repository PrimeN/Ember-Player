package app.ember.tv.data

data class Person(val name:String,val role:String?,val photo:String?=null,val isCrew:Boolean=false,val tmdbId:Long?=null)
