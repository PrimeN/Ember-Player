package app.ember.tv.data

import android.content.Context
import app.ember.tv.tracker.TokenStore

/** Optional personal keys. No provider registration or developer credential is bundled. */
object ApiKeys {
    private var application:Context?=null
    @Volatile private var values=emptyMap<String,String>()
    var revision=0;private set
    fun init(context:Context) {application=context.applicationContext;reload()}
    fun get(id:String)=values[id].orEmpty()
    fun set(id:String,value:String):Boolean {
        val context=application ?: return false
        require(id.matches(Regex("[A-Z0-9_]{1,80}")))
        val saved=TokenStore(context).putEncrypted("apikey.$id",value.trim())
        if(saved)reload()
        return saved
    }
    fun reload() {val context=application ?: return;values=TokenStore(context).all().filterKeys {it.startsWith("apikey.")}.mapKeys {it.key.removePrefix("apikey.")};revision++}
}
