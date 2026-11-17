package app.ember.tv.data

import kotlinx.serialization.Serializable

/** Existing saved activity namespaces remain readable after upgrading. */
@Serializable
data class Profile(val id:String,val name:String,val activityMode:String=LocalActivity.SHARED) {
    companion object {const val MAIN="main"}
}
