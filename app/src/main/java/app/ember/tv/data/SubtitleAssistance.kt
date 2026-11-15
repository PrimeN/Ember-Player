package app.ember.tv.data

import kotlinx.serialization.Serializable

@Serializable
data class SubtitleAssistance(val translate:Boolean=false,val readAloud:Boolean=false,val source:String="",val target:String="",val voice:String="")
