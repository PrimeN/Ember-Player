package app.ember.tv.player

import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class SftpAuthenticationTest {
    @Test fun unknownKeysAreRejectedUntilExplicitlyPinnedAndChangedKeysStayRejected() {
        val key="first host key".toByteArray()
        val first=SftpHostKeys("")
        assertEquals(HostKeyRepository.NOT_INCLUDED,first.check("server",key))
        assertEquals(NetworkLocation.fingerprint(key),first.confirmation)
        first.add(com.jcraft.jsch.HostKey("server",com.jcraft.jsch.HostKey.SSHRSA,key),null)
        assertEquals(HostKeyRepository.NOT_INCLUDED,first.check("server",key))
        val trusted=SftpHostKeys(first.confirmation!!)
        assertEquals(HostKeyRepository.OK,trusted.check("server",key))
        assertEquals(HostKeyRepository.CHANGED,trusted.check("server","different key".toByteArray()))
        assertTrue(trusted.mismatch);assertNull(trusted.confirmation)
    }
    @Test fun encryptedPrivateKeysRequireTheCorrectPassphraseAndSelectPublicKeyOnly() {
        val pair=KeyPair.genKeyPair(JSch(),KeyPair.RSA,2048)
        try {
            val out=ByteArrayOutputStream();pair.writePrivateKey(out,"fixture passphrase".toByteArray())
            val account=NetworkAccount(label="Server",address="sftp://server/media",authentication="key",privateKey=SftpCredentials.read(out.toByteArray().inputStream()),keyPassphrase="fixture passphrase")
            val jsch=JSch()
            assertEquals("publickey",SftpCredentials.install(jsch,account))
            assertEquals(1,jsch.identityRepository.identities.size)
            assertFalse(jsch.identityRepository.identities.single().isEncrypted)
            jsch.removeAllIdentity()
            try {SftpCredentials.install(jsch,account.copy(keyPassphrase="wrong"));fail("Wrong passphrases must be rejected")}catch(_:IllegalArgumentException){}
            assertTrue(jsch.identityRepository.identities.isEmpty())
            assertEquals("password,keyboard-interactive",SftpCredentials.install(jsch,account.copy(authentication="password")))
            assertFalse(account.toString().contains("fixture"))
        }finally {pair.dispose()}
    }
    @Test fun oversizedOrNonPrivateKeyImportsAreRejected() {
        for(bytes in listOf(ByteArray(SftpCredentials.MAX_KEY_BYTES+1),'x'.toString().toByteArray())) {
            try {SftpCredentials.read(bytes.inputStream());fail("Invalid imports must be rejected")}catch(_:IllegalArgumentException){}
        }
    }
}
