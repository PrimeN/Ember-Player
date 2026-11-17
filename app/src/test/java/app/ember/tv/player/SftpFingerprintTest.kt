package app.ember.tv.player

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class SftpFingerprintTest {
    private val key="server host key".toByteArray()
    private val encoded=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(key))
    @Test fun pastedFingerprintsAcceptSurroundingWhitespaceAndOptionalPadding() {
        for(value in listOf("SHA256:$encoded"," \tSHA256:$encoded\r\n","\n SHA256:${encoded.trimEnd('=')} ")) {
            assertTrue(NetworkLocation.fingerprintMatches(value,key))
            assertEquals("SHA256:${encoded.trimEnd('=')}",NetworkLocation.normalizeFingerprint(value))
        }
    }
    @Test fun malformedAndDifferentKeysRemainRejected() {
        for(value in listOf("SHA256:","SHA256:bad",encoded,"sha256:$encoded","SHA256: $encoded","SHA256:$encoded extra")) {
            assertNull(value,NetworkLocation.normalizeFingerprint(value))
            assertFalse(NetworkLocation.fingerprintMatches(value,key))
        }
        assertFalse(NetworkLocation.fingerprintMatches("SHA256:$encoded","another key".toByteArray()))
    }
}
