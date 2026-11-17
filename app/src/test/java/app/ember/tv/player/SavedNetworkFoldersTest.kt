package app.ember.tv.player

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SavedNetworkFoldersTest {
    private val first=NetworkAccount(id="first",label="First",address="sftp://server/media",password="saved-password")
    private val second=NetworkAccount(id="second",label="Second",address="sftp://server/other",authentication="key",privateKey="saved-private-key",keyPassphrase="saved-passphrase")
    private val fingerprint="SHA256:"+java.util.Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32){it.toByte()})
    @Test fun mutationsRefuseUnreadableMalformedPartialAndOversizedDataWithoutWriting() {
        val originals=listOf("not json","[", " ".repeat(1024*1024+1),Json.encodeToString(listOf(first,second.copy(address="invalid"))),Json.encodeToString(listOf(first,first)))
        for(original in originals) {
            var raw=original;var writes=0
            val store=SavedNetworkFolders({raw},{writes++;raw=it;true})
            assertNull(store.add(second));assertNull(store.remove(first.id));assertNull(store.trust(first,fingerprint))
            assertEquals(0,writes);assertEquals(original,raw)
        }
        var writes=0
        val store=SavedNetworkFolders({throw IllegalStateException("Locked")},{writes++;true})
        assertNull(store.add(second));assertNull(store.remove(first.id));assertNull(store.trust(first,fingerprint));assertEquals(0,writes)
    }
    @Test fun freshReadsPreserveCredentialsAddedSinceTheScreenLoaded() {
        var raw:String?=Json.encodeToString(listOf(first))
        val store=SavedNetworkFolders({raw},{raw=it;true})
        val displayed=store.read().single()
        raw=Json.encodeToString(listOf(first,second))
        val added=NetworkAccount(id="third",label="Third",address="smb://server/Films")
        assertEquals(listOf(first,second,added),store.add(added))
        val trusted=store.trust(displayed,fingerprint)!!
        assertEquals(second,trusted.first {it.id==second.id})
        assertEquals(fingerprint,trusted.first {it.id==first.id}.fingerprint)
        assertEquals(listOf(second,added),store.remove(first.id))
    }
    @Test fun hostKeyConfirmationRejectsAChangedServerOrAnAlreadyTrustedAccount() {
        for(changed in listOf(first.copy(address="sftp://other/media"),first.copy(password="changed"),first.copy(fingerprint=fingerprint))) {
            val original=Json.encodeToString(listOf(changed,second));var raw=original
            val store=SavedNetworkFolders({raw},{raw=it;true})
            assertNull(store.trust(first,fingerprint));assertEquals(original,raw)
        }
    }
    @Test fun missingStorageCanBeCreatedAndFailedWritesAreReported() {
        var raw:String?=null
        val store=SavedNetworkFolders({raw},{raw=it;true})
        assertEquals(listOf(first),store.add(first))
        val original=raw
        val failed=SavedNetworkFolders({raw},{false})
        assertNull(failed.add(second));assertNull(failed.remove(first.id));assertNull(failed.trust(first,fingerprint))
        assertEquals(original,raw)
    }
}
