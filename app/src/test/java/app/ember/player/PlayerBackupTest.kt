package app.ember.player

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

class PlayerBackupTest {
    @Test fun malformedPrivateProtectionCannotBeImportedAsAValidBackup() {
        val invalid=sample().copy(secrets=mapOf(PrivateMedia.KEY to "{\"pin\":\"invalid\"}"))
        try {PlayerBackups.validate(invalid);fail("Invalid private protection must be rejected")}catch(_:IllegalArgumentException){}
    }
    @Test fun portableBackupsCannotReplaceTheLocalIndexEncryptionKey() {
        val incoming=mapOf(PlayerBackups.LOCAL_INDEX_KEY to "old-device-key","connection" to "restored")
        assertEquals(mapOf("connection" to "restored"),PlayerBackups.portableSecrets(incoming))
        assertEquals(mapOf("connection" to "restored",PlayerBackups.LOCAL_INDEX_KEY to "this-device-key"),PlayerBackups.restoredSecrets(incoming,"this-device-key"))
        assertEquals(mapOf("connection" to "restored"),PlayerBackups.restoredSecrets(incoming,null))
    }
    @Test fun legacyBackupsRemainReadableAndNewBackupsUseTheVersionTwoEnvelope() {
        val password="a sufficiently long password".toCharArray()
        val salt=ByteArray(16){it.toByte()};val nonce=ByteArray(12){(it+16).toByte()}
        val spec=javax.crypto.spec.PBEKeySpec(password,salt,210000,256)
        val key=javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        val header="EMBERPB1".toByteArray()+salt+nonce
        val cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,javax.crypto.spec.SecretKeySpec(key,"AES"),javax.crypto.spec.GCMParameterSpec(128,nonce))
        cipher.updateAAD(header)
        val original="legacy settings".toByteArray()
        assertArrayEquals(original,PlayerBackupCipher.decrypt(header+cipher.doFinal(original),password))
        val current=PlayerBackupCipher.encrypt(original,password)
        assertEquals("EMBERPB2",String(current,0,8,Charsets.US_ASCII))
        val unknown=current.clone().apply {this[7]='9'.code.toByte()}
        try {PlayerBackupCipher.decrypt(unknown,password);fail("Unknown formats must not select arbitrary derivation parameters")}catch(_:IllegalArgumentException){}
        key.fill(0);password.fill('\u0000')
    }
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
    @Test fun encryptedBackupRestoresProfilesHdrAndDecoderChoices() {
        val config=app.ember.tv.player.PictureProfiles.bind(
            app.ember.tv.player.PictureProfiles.save(app.ember.tv.player.PlaybackConfig(compatibilityDecoder="copy",hdrOutput=app.ember.tv.player.HdrOutput(sdr=true)),"Evening",
                app.ember.tv.player.PictureState(picture=app.ember.tv.player.PictureEnhancement(contrast=15),hdr=app.ember.tv.player.HdrOutput(sdr=true,tone="hable")),id={"evening"}),"media","evening")
        val encoded=kotlinx.serialization.json.Json.encodeToString(app.ember.tv.player.PlaybackConfig.serializer(),config)
        val data=sample().copy(preferences=sample().preferences+("ember" to mapOf("playbackConfig" to BackupValue("string",encoded))))
        val password="a sufficiently long password".toCharArray()
        val bytes=PlayerBackupCipher.encrypt(kotlinx.serialization.json.Json.encodeToString(PlayerBackupData.serializer(),data).toByteArray(),password)
        val restored=PlayerBackups.decode(bytes,password)
        val result=kotlinx.serialization.json.Json.decodeFromString(app.ember.tv.player.PlaybackConfig.serializer(),restored.preferences.getValue("ember").getValue("playbackConfig").text)
        assertEquals(config,result)
        assertEquals(15,app.ember.tv.player.PictureProfiles.effective(result,"media").picture.contrast)
        assertEquals("hable",app.ember.tv.player.PictureProfiles.effective(result,"media").hdrOutput.tone)
    }

    @Test fun encryptedBackupKeepsNetworkRendererFramingCompressionAndSubtitleStyles() {
        val config=app.ember.tv.player.GeometryMemory.set(app.ember.tv.player.PlaybackConfig(
            stream=app.ember.tv.player.StreamConfig(custom=true,connectSeconds=45,readSeconds=60),renderer="gpu-next",
            audioEffects=app.ember.tv.player.AudioEffects(compression="night"),hdrOutput=app.ember.tv.player.HdrOutput(expand=true)),"saved-video",app.ember.tv.player.VideoGeometry("crop",3,1.5f))
        val styles=listOf(app.ember.tv.player.SubtitleStylePreset("Evening",app.ember.tv.player.SubtitleStyle(color="yellow")))
        val json=kotlinx.serialization.json.Json
        val values=mapOf("playbackConfig" to BackupValue("string",json.encodeToString(app.ember.tv.player.PlaybackConfig.serializer(),config)),
            "subtitlePresets" to BackupValue("string",json.encodeToString(kotlinx.serialization.builtins.ListSerializer(app.ember.tv.player.SubtitleStylePreset.serializer()),styles)))
        val data=sample().copy(preferences=sample().preferences+("ember" to values))
        val password="a sufficiently long password".toCharArray()
        val encrypted=PlayerBackupCipher.encrypt(json.encodeToString(PlayerBackupData.serializer(),data).toByteArray(),password)
        val restored=PlayerBackups.decode(encrypted,password)
        assertEquals(data,restored)
        val decoded=json.decodeFromString(app.ember.tv.player.PlaybackConfig.serializer(),restored.preferences.getValue("ember").getValue("playbackConfig").text)
        assertEquals(config,decoded);assertEquals(1.5f,app.ember.tv.player.GeometryMemory.selected(decoded,"saved-video")!!.magnification)
    }
    @Test fun malformedOrOversizedSubtitleStyleBackupsAreRejected() {
        for(styles in listOf("not json","[{\"name\":\"\",\"style\":{}}]")) {
            val data=sample().copy(preferences=sample().preferences+("ember" to mapOf("subtitlePresets" to BackupValue("string",styles))))
            try {PlayerBackups.validate(data);fail("Expected invalid styles")}catch(_:IllegalArgumentException){}
        }
    }

}
