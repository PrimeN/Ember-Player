package app.ember.player

import android.content.Context
import app.ember.player.storage.*

internal class PlayerUiStoreFixture(private val context:Context):AutoCloseable {
    init {
        val vault=SecretVault(context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE),object:SecretCipher {
            override fun encrypt(name:String,value:ByteArray)=value.copyOf()
            override fun decrypt(name:String,blob:ByteArray)=blob.copyOf()
        })
        TokenStore::class.java.getDeclaredField("instance").apply {isAccessible=true;set(null,vault)}
        PrivateMedia.initialize(context);PrivateMedia.reload(context)
    }
    override fun close() {
        context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("playerSecurity",Context.MODE_PRIVATE).edit().clear().commit()
        PrivateMedia.reload(context)
        TokenStore::class.java.getDeclaredField("instance").apply {isAccessible=true;set(null,null)}
    }
}
