package app.ember.tv.player

import android.content.Context
import app.ember.player.storage.TokenStore
import app.ember.tv.data.Prefs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Every mutation starts with the current encrypted copy and refuses unreadable records. */
internal class SavedNetworkFolders(private val readValue:()->String?,private val writeValue:(String)->Boolean) {
    constructor(context:Context,profile:String=Prefs(context).currentProfile):this(
        {TokenStore(context.applicationContext).getForUpdate("media.network."+profile)},
        {TokenStore(context.applicationContext).putEncrypted("media.network."+profile,it)},
    )
    fun read():List<NetworkAccount> {
        val raw=readValue() ?: return emptyList()
        require(raw.length<=1024*1024)
        val values=json.decodeFromString<List<NetworkAccount>>(raw)
        require(values.all(::valid) && values.distinctBy {it.id}.size==values.size)
        return values
    }
    fun add(value:NetworkAccount):List<NetworkAccount>?=update {values->
        require(valid(value) && values.none {it.id==value.id})
        values+value
    }
    fun remove(id:String):List<NetworkAccount>?=update {values->values.filterNot {it.id==id}}
    fun trust(expected:NetworkAccount,fingerprint:String):List<NetworkAccount>?=update {values->
        val current=values.firstOrNull {it.id==expected.id}
        require(current==expected && expected.fingerprint.isBlank() && NetworkLocation.parse(expected.address).scheme=="sftp")
        val normalized=requireNotNull(NetworkLocation.normalizeFingerprint(fingerprint))
        values.map {if(it.id==expected.id)it.copy(fingerprint=normalized) else it}
    }
    private fun update(change:(List<NetworkAccount>)->List<NetworkAccount>):List<NetworkAccount>?=synchronized(lock) {
        runCatching {
            val updated=change(read())
            val raw=json.encodeToString(updated)
            check(raw.length<=1024*1024 && writeValue(raw))
            updated
        }.getOrNull()
    }
    companion object {
        private val lock=Any()
        private val json=Json {ignoreUnknownKeys=true}
        private fun valid(a:NetworkAccount):Boolean=runCatching {
            NetworkLocation.parse(a.address)
            a.id.isNotBlank() && a.id.length<=80 && a.label.isNotBlank() && a.label.length<=80 &&
                a.address.length<=4096 && a.username.none {it.isISOControl()} && a.authentication in setOf("password","key") &&
                (a.fingerprint.isBlank() || NetworkLocation.normalizeFingerprint(a.fingerprint)!=null)
        }.getOrDefault(false)
    }
}
