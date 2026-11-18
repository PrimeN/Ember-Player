package app.ember.tv.player

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SmbConnectionPolicyTest {
    private val account=NetworkAccount(label="Router",address="smb://router/media")
    @Test fun defaultsRequireSigningAndEncryptionAndEachCanBeRelaxedSeparately() {
        val secure=NetworkFiles.smbConfig(account)
        assertTrue(secure.isSigningRequired);assertTrue(secure.isEncryptData)
        val unsigned=NetworkFiles.smbConfig(account.copy(allowUnsignedSmb=true))
        assertFalse(unsigned.isSigningRequired);assertTrue(unsigned.isEncryptData)
        val unencrypted=NetworkFiles.smbConfig(account.copy(allowUnencryptedSmb=true))
        assertTrue(unencrypted.isSigningRequired);assertFalse(unencrypted.isEncryptData)
        val guest=NetworkFiles.smbConfig(account.copy(allowUnsignedSmb=true,allowUnencryptedSmb=true))
        assertFalse(guest.isSigningRequired);assertFalse(guest.isEncryptData)
    }
    @Test fun oldSavedAccountsKeepSecureDefaultsAndNewChoicesRoundTrip() {
        val old=Json.decodeFromString<NetworkAccount>("""{"label":"Router","address":"smb://router/media"}""")
        assertFalse(old.allowUnsignedSmb);assertFalse(old.allowUnencryptedSmb)
        val value=old.copy(allowUnsignedSmb=true,allowUnencryptedSmb=false)
        assertEquals(value,Json.decodeFromString<NetworkAccount>(Json.encodeToString(value)))
    }
}
