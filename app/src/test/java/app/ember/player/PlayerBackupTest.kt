package app.ember.player

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

class PlayerBackupTest {
    private fun sample(text:String="1")=PlayerBackupData(preferences=PlayerBackups.files.associateWith {file->
        if(file=="ember")mapOf("theme" to BackupValue("string",text)) else emptyMap()
    },secrets=mapOf("connection" to "private-value"))
    @Test fun encryptedExportHidesPrivateDataAndUsesFreshSaltAndNonce() {
        val bytes="private-value".toByteArray()
        val key="a sufficiently long password".toCharArray()
        val first=PlayerBackupCipher.encrypt(bytes,key)
        val second=PlayerBackupCipher.encrypt(bytes,key)
        assertFalse(first.contentEquals(second))
        assertFalse(String(first).contains("private-value"))
        assertArrayEquals(bytes,PlayerBackupCipher.decrypt(first,key))
    }
    @Test fun wrongPasswordAndModifiedHeaderOrPayloadAreRejected() {
        val key="a sufficiently long password".toCharArray()
        val encrypted=PlayerBackupCipher.encrypt("data".toByteArray(),key)
        listOf(8,24,encrypted.lastIndex).forEach {index->
            val changed=encrypted.clone();changed[index]=(changed[index].toInt() xor 1).toByte()
            try {PlayerBackupCipher.decrypt(changed,key);fail("Expected authenticated failure")} catch(_:AEADBadTagException) {}
        }
        try {PlayerBackupCipher.decrypt(encrypted,"a different long password".toCharArray());fail("Expected password failure")} catch(_:AEADBadTagException) {}
    }
    @Test fun invalidBackupNeverWritesAndFailedRestoreRollsBack() {
        val original=sample("original")
        var current=original;var writes=0
        try {PlayerBackups.replace(sample().copy(version=99),{current},{writes++;current=it});fail("Expected validation failure")} catch(_:IllegalArgumentException) {}
        assertEquals(0,writes);assertEquals(original,current)
        try {
            PlayerBackups.replace(sample("restored"),{current},{value->current=value;writes++;if(writes==1)error("Interrupted storage write")})
            fail("Expected rollback")
        } catch(_:IllegalStateException) {}
        assertEquals(2,writes);assertEquals(original,current)
    }
    @Test fun unsupportedTypesAndMalformedPositionsAreRejectedBeforeRestore() {
        val invalid=sample().copy(preferences=sample().preferences+ ("positions" to mapOf("file:key" to BackupValue("string","-1,10"))))
        try {PlayerBackups.validate(invalid);fail("Expected invalid position")} catch(_:IllegalArgumentException) {}
        val unsafe=sample().copy(preferences=sample().preferences+("unexpected" to emptyMap()))
        try {PlayerBackups.validate(unsafe);fail("Expected unknown section failure")} catch(_:IllegalArgumentException) {}
    }
}
