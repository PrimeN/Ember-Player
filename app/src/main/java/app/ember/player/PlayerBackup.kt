package app.ember.player

import android.content.Context
import app.ember.tv.data.SubtitleConfig
import app.ember.tv.player.PlaybackBookmarks
import app.ember.tv.player.PlaybackConfig
import app.ember.tv.player.PlaybackStateRetention
import app.ember.player.storage.TokenStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
internal data class BackupValue(val type:String,val text:String="",val strings:Set<String> = emptySet())
@Serializable
internal data class PlayerBackupData(val version:Int=1,val preferences:Map<String,Map<String,BackupValue>>,val secrets:Map<String,String>)

/** The envelope has fixed KDF parameters; untrusted files cannot request excessive KDF work. */
internal object PlayerBackupCipher {
    const val MAX_BYTES=8*1024*1024
    private val magic="EMBERPB2".toByteArray(Charsets.US_ASCII)
    private val legacyMagic="EMBERPB1".toByteArray(Charsets.US_ASCII)
    private fun key(password:CharArray,salt:ByteArray,iterations:Int=600000):SecretKeySpec {
        require(password.size in 10..256) {"Use a password between 10 and 256 characters."}
        val spec=PBEKeySpec(password,salt,iterations,256)
        val bytes=try {SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded} finally {spec.clearPassword()}
        return try {SecretKeySpec(bytes,"AES")} finally {bytes.fill(0)}
    }
    fun encrypt(data:ByteArray,password:CharArray):ByteArray {
        require(data.size<=MAX_BYTES) {"This backup is too large."}
        val random=SecureRandom()
        val salt=ByteArray(16).also(random::nextBytes)
        val nonce=ByteArray(12).also(random::nextBytes)
        val header=magic+salt+nonce
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key(password,salt),GCMParameterSpec(128,nonce));cipher.updateAAD(header)
        return header+cipher.doFinal(data)
    }
    fun decrypt(data:ByteArray,password:CharArray):ByteArray {
        require(data.size in 52..MAX_BYTES+52) {"This is not an Ember Player backup."}
        val iterations=when {
            data.copyOfRange(0,8).contentEquals(magic)->600000
            data.copyOfRange(0,8).contentEquals(legacyMagic)->210000
            else->throw IllegalArgumentException("This is not an Ember Player backup.")
        }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(password,data.copyOfRange(8,24),iterations),GCMParameterSpec(128,data.copyOfRange(24,36)))
        cipher.updateAAD(data,0,36)
        return cipher.doFinal(data,36,data.size-36)
    }
}

internal object PlayerBackups {
    val files=setOf("ember","positions","trackMemory","bookmarks")
    private val json=Json {ignoreUnknownKeys=false}
    private val configurationJson=Json {ignoreUnknownKeys=true}
    fun validate(data:PlayerBackupData) {
        require(data.version==1 && data.preferences.keys==files) {"Unsupported backup version or settings sections."}
        require(data.preferences.values.sumOf {it.size}<=50000 && data.secrets.size<=500) {"Backup contains too many entries."}
        data.preferences.forEach {(file,values)->values.forEach {(key,value)->
            require(key.isNotBlank() && key.length<=2048 && key.none {it.isISOControl()})
            require(value.text.length<=512*1024 && value.strings.size<=1000 && value.strings.all {it.length<=4096})
            when(value.type) {
                "string"->if(file=="bookmarks")require(PlaybackBookmarks.valid(value.text))
                "boolean"->require(value.text in setOf("true","false"))
                "int"->require(value.text.toIntOrNull()!=null)
                "long"->require(value.text.toLongOrNull()!=null)
                "float"->require(value.text.toFloatOrNull()?.isFinite()==true)
                "set"->Unit
                else->error("Unsupported preference type.")
            }
            if(file in setOf("positions","trackMemory","bookmarks"))require(value.type=="string")
            if(file=="positions") {
                val numbers=value.text.split(',').map {it.toLongOrNull()}
                require(numbers.size==2 && numbers.all {it!=null && it>=0}) {"Invalid saved position."}
            }
            if(file=="ember" && key=="playbackConfig")configurationJson.decodeFromString<PlaybackConfig>(value.text)
            if(file=="ember" && key=="subtitlePresets") {
                val styles=configurationJson.decodeFromString<List<app.ember.tv.player.SubtitleStylePreset>>(value.text)
                require(styles.size<=12 && styles.all {it.name.isNotBlank() && it.name.length<=32}) {"Invalid subtitle styles."}
            }
            if(file=="ember" && key=="subtitleConfig")configurationJson.decodeFromString<SubtitleConfig>(value.text)
        }}
        require(data.secrets.all {(key,value)->key.isNotBlank() && key.length<=256 && key.none {it.isISOControl()} && value.length<=2*1024*1024})
    }
    fun snapshot(context:Context):PlayerBackupData {
        check(!PrivateMedia.enabled||PrivateMedia.unlocked.value){"Unlock private media before exporting settings."}
        PlaybackStateRetention.prune(context)
        val tokens=TokenStore(context)
        if(context.getSharedPreferences(app.ember.player.storage.SecretVault.FILE,Context.MODE_PRIVATE).all.isNotEmpty())
            check(tokens.encryptionAvailable) {"Stored connection details could not be unlocked. No backup was created."}
        val preferences=files.associateWith {file->context.getSharedPreferences(file,Context.MODE_PRIVATE).all.mapValues {(_,value)->
            when(value) {
                is String->BackupValue("string",value)
                is Boolean->BackupValue("boolean",value.toString())
                is Int->BackupValue("int",value.toString())
                is Long->BackupValue("long",value.toString())
                is Float->BackupValue("float",value.toString())
                is Set<*>->BackupValue("set",strings=value.filterIsInstance<String>().toSet())
                else->error("Unsupported preference value.")
            }
        }}
        return PlayerBackupData(preferences=preferences,secrets=tokens.all())
    }
    fun export(context:Context,password:CharArray):ByteArray {
        val value=snapshot(context);validate(value)
        val bytes=json.encodeToString(value).toByteArray()
        return try {
            require(bytes.size<=PlayerBackupCipher.MAX_BYTES) {"Saved bookmarks or settings exceed the backup size limit. Remove unneeded bookmarks or reduce saved settings, then export again."}
            PlayerBackupCipher.encrypt(bytes,password)
        } finally {bytes.fill(0)}
    }
    fun decode(bytes:ByteArray,password:CharArray):PlayerBackupData {
        val plain=PlayerBackupCipher.decrypt(bytes,password)
        return try {json.decodeFromString<PlayerBackupData>(String(plain,Charsets.UTF_8)).also(::validate)} finally {plain.fill(0)}
    }
    private fun write(context:Context,data:PlayerBackupData) {
        TokenStore(context).replaceAll(data.secrets)
        data.preferences.forEach {(file,values)->
            val edit=context.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear()
            values.forEach {(key,value)->when(value.type) {
                "string"->edit.putString(key,value.text)
                "boolean"->edit.putBoolean(key,value.text.toBooleanStrict())
                "int"->edit.putInt(key,value.text.toInt())
                "long"->edit.putLong(key,value.text.toLong())
                "float"->edit.putFloat(key,value.text.toFloat())
                "set"->edit.putStringSet(key,value.strings)
            }}
            check(edit.commit()) {"Could not write restored settings."}
        }
        context.getSharedPreferences("playbackStateRecency",Context.MODE_PRIVATE).edit().clear().apply()
    }
    fun restore(context:Context,data:PlayerBackupData)=synchronized(this) {
        check(!PrivateMedia.enabled||PrivateMedia.unlocked.value){"Unlock private media before restoring settings."}
        validate(data)
        val retained=data.copy(preferences=data.preferences.mapValues {(file,values)->
            if(file in setOf("positions","trackMemory")) {
                val kept=PlaybackStateRetention.bounded(file,values.mapValues {it.value.text})
                values.filterKeys {it in kept}
            } else values
        })
        replace(retained,{snapshot(context)},{write(context,it)})
        PrivateMedia.reload(context)
        app.ember.tv.data.CacheManagement.preferencesRestored(context.applicationContext)
    }
    /** Validate first, then verify the write. A failed write restores and verifies the old snapshot. */
    internal fun replace(target:PlayerBackupData,read:()->PlayerBackupData,write:(PlayerBackupData)->Unit) {
        validate(target)
        val previous=read()
        try {write(target);check(read()==target) {"Restored settings did not match the backup."}}
        catch(e:Exception) {
            try {write(previous);check(read()==previous)}
            catch(rollback:Exception) {throw IllegalStateException("Restore failed and the previous configuration could not be fully recovered.",rollback)}
            throw IllegalStateException("Restore failed. The previous configuration was kept.",e)
        }
    }
}
