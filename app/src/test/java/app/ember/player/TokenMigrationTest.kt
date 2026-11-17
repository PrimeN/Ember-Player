package app.ember.player

import android.app.Application
import android.content.Context
import app.ember.player.storage.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class TokenMigrationTest {
    private val context:Context get()=RuntimeEnvironment.getApplication()
    private fun plain()=context.getSharedPreferences(TokenMigration.PLAIN,Context.MODE_PRIVATE)
    private fun vault(fail:Boolean=false)=SecretVault(context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE),object:SecretCipher {
        override fun encrypt(name:String,value:ByteArray):ByteArray {check(!fail);return value.copyOf()}
        override fun decrypt(name:String,blob:ByteArray)=blob.copyOf()
    })
    @Test fun verifiedMigrationPreservesCurrentValuesAndClearsPlainPreferences() {
        plain().edit().putString("existing","old").putString("missing","imported").commit()
        val vault=vault();assertTrue(vault.write(mapOf("existing" to "current")))
        TokenMigration.run(context,vault)
        assertEquals(mapOf("existing" to "current","missing" to "imported"),vault.all())
        assertTrue(plain().all.isEmpty())
        TokenMigration.run(context,vault)
        assertEquals("current",vault.get("existing"))
    }
    @Test fun failedEncryptionLeavesPlainValuesAvailableForRetry() {
        plain().edit().putString("saved","keep").commit()
        try {TokenMigration.run(context,vault(fail=true));fail("Unavailable encryption must stop migration")}catch(_:IllegalStateException){}
        assertEquals("keep",plain().getString("saved",null))
        val recovered=vault();TokenMigration.run(context,recovered)
        assertEquals("keep",recovered.get("saved"));assertTrue(plain().all.isEmpty())
    }
}
