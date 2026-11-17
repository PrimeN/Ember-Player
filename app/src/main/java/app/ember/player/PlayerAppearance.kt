package app.ember.player

internal object PlayerAppearance {
    fun light(choice:String,systemDark:Boolean)=when(choice) {
        "day"->true
        "night"->false
        else->!systemDark
    }
}
