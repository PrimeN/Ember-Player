package app.ember.player.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Keeps sign-in tokens, API keys and private source details encrypted with an AES-256-GCM key
 * held in the Android Keystore (see [SecretVault]). Opening it touches the Keystore, so every
 * TokenStore shares one copy per process.
 *
 * Any values in the old plain file ("ember_tokens_plain") are moved into the Keystore store
 * once, verified, and only then cleared ([TokenMigration]). When the Keystore is unusable,
 * existing plain values stay readable but nothing new is written in cleartext.
 */
class TokenStore internal constructor(context: Context, private val vaultProvider:(Context)->SecretVault?) {
    constructor(context: Context):this(context,::shared)
    private val app=context.applicationContext
    private val legacy=app.getSharedPreferences(TokenMigration.PLAIN,Context.MODE_PRIVATE)
    private val encrypted=app.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE)
    private val vaultState=lazy {vaultProvider(app)}
    private val vault: SecretVault? by vaultState

    /** Empty installations have nothing to decrypt or migrate. Do not generate a key to read null. */
    private fun existingVault():SecretVault? = if(vaultState.isInitialized() || encrypted.all.isNotEmpty() ||
        legacy.all.isNotEmpty())vault else null

    companion object {
        // Reset by reflection in tests; a failed open is not cached, so a later call can retry.
        @Volatile private var instance: SecretVault? = null

        private fun shared(context: Context): SecretVault? =
            instance ?: synchronized(this) { instance ?: open(context)?.also { instance = it } }

        private fun open(context: Context): SecretVault? = runCatching {
            val vault=SecretVault(context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE),KeystoreSecretCipher())
            vault.selfTest()
            TokenMigration.run(context,vault)
            vault
        }.getOrNull()
    }

    fun get(key: String): String? = existingVault()?.get(key) ?: legacy.getString(key,null)
    /** A missing record is empty; an unreadable existing record must never become a replacement list. */
    internal fun getForUpdate(key:String):String? {
        if(encrypted.contains(key))return checkNotNull(existingVault()?.get(key)) {"Saved encrypted data could not be read."}
        return legacy.getString(key,null)
    }
    fun put(key: String, value: String) {putAll(mapOf(key to value))}
    fun putAll(values:Map<String,String>) {
        check(writeEncrypted(values)) {"Encrypted storage is unavailable or credentials could not be saved."}
    }
    val encryptionAvailable: Boolean get() = vault != null
    /** Private source data must never fall back to cleartext when the keystore is unavailable. */
    fun putEncrypted(key: String, value: String): Boolean=writeEncrypted(mapOf(key to value))
    private fun writeEncrypted(values:Map<String,String>):Boolean {
        val store=vault ?: return false
        return synchronized(store) {
            val previous=values.keys.associateWith {store.get(it)}
            val expected:Map<String,String?> = values
            val saved = VerifiedSecretWrite.replace(previous,expected,
                { next -> store.write(next) },
                {values.keys.associateWith {store.get(it)}})
            if (saved) legacy.edit(commit = true) {values.keys.forEach {remove(it)}}
            saved
        }
    }
    fun remove(vararg keys: String) {
        val store=existingVault()
        synchronized(store ?: legacy) {
            store?.write(keys.associateWith {null})
            legacy.edit { keys.forEach { remove(it) } }
        }
    }

    /** Every saved token and key, for backups. */
    fun all(): Map<String, String> {
        val store=existingVault()
        return synchronized(store ?: legacy) {
            legacy.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap() + store?.all().orEmpty()
        }
    }

    /** Replaces everything with [values], for restoring a backup. Written synchronously. */
    fun replaceAll(values: Map<String, String>) {
        val target=if(values.isEmpty())existingVault() else vault
        synchronized(target ?: legacy) {
            check(values.isEmpty() || target != null) { "Encrypted storage is unavailable. Restore was not applied." }
            target?.let { store ->
                val saved = VerifiedSecretWrite.replace(store.all(), values, { next -> store.write(next, clear = true) }, store::all)
                check(saved) { "Could not save restored credentials. Previous configuration was kept." }
            }
            legacy.edit(commit = true) { clear() }
        }
    }
}

/** Moves old plain values into [SecretVault], clearing them only after verification. */
internal object TokenMigration {
    const val PLAIN = "ember_tokens_plain"
    private const val META = "ember_tokens_meta"
    private const val DONE = "keystoreMigrated"

    fun run(context: Context, vault: SecretVault) {
        val meta=context.getSharedPreferences(META,Context.MODE_PRIVATE)
        val plain=context.getSharedPreferences(PLAIN,Context.MODE_PRIVATE)
        if(meta.getBoolean(DONE,false) && plain.all.isEmpty()) return
        val values = strings(plain)
        val pending = values.filter { (k, _) -> vault.get(k) == null }
        if (pending.isNotEmpty() && !vault.write(pending)) return
        if (!values.all { (k, v) -> vault.get(k) == v || k !in pending }) return
        plain.edit(commit = true) { clear() }
        meta.edit(commit = true) { putBoolean(DONE, true) }
    }

    private fun strings(prefs: SharedPreferences) = prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
}
