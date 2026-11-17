package app.ember.tv.player

import com.jcraft.jsch.*
import java.io.InputStream

internal class SftpHostKeys(expected:String):HostKeyRepository {
    private val trusted=NetworkLocation.normalizeFingerprint(expected)
    var confirmation:String?=null;private set
    var mismatch=false;private set
    init {require(expected.isBlank()||trusted!=null){"Enter a valid SHA256 host key fingerprint."}}
    override fun check(host:String,key:ByteArray):Int {
        if(trusted==null){confirmation=NetworkLocation.fingerprint(key);return HostKeyRepository.NOT_INCLUDED}
        if(NetworkLocation.fingerprintMatches(trusted,key))return HostKeyRepository.OK
        mismatch=true;return HostKeyRepository.CHANGED
    }
    override fun add(hostkey:HostKey,ui:UserInfo?){}
    override fun remove(host:String,type:String?){}
    override fun remove(host:String,type:String?,key:ByteArray?){}
    override fun getKnownHostsRepositoryID()="Ember pinned host key"
    override fun getHostKey()=emptyArray<HostKey>()
    override fun getHostKey(host:String?,type:String?)=emptyArray<HostKey>()
}

internal class SftpHostKeyConfirmationException(val fingerprint:String):java.io.IOException("Confirm the SFTP server's host key before connecting.")

internal object SftpCredentials {
    const val MAX_KEY_BYTES=64*1024
    fun read(input:InputStream):String {
        val bytes=ByteArray(MAX_KEY_BYTES+1);var count=0
        return try {
            while(count<bytes.size){val n=input.read(bytes,count,bytes.size-count);if(n<0)break;count+=n}
            require(count in 1..MAX_KEY_BYTES){"Choose a private key smaller than 64 KiB."}
            val text=String(bytes,0,count,Charsets.UTF_8)
            require(text.contains(Regex("-----BEGIN (?:OPENSSH |RSA |EC |DSA |ENCRYPTED )?PRIVATE KEY-----"))&&'\u0000' !in text){"Choose an OpenSSH or PEM private key."}
            text
        } finally {bytes.fill(0)}
    }
    fun install(jsch:JSch,account:NetworkAccount):String {
        if(account.authentication=="password")return "password,keyboard-interactive"
        require(account.authentication=="key"){"Choose password or private-key sign-in."}
        val key=account.privateKey.toByteArray();val passphrase=account.keyPassphrase.toByteArray()
        try {
            require(key.size in 1..MAX_KEY_BYTES)
            jsch.addIdentity("Ember SFTP key",key,null,passphrase)
            check(jsch.identityRepository.identities.isNotEmpty()&&jsch.identityRepository.identities.none {it.isEncrypted})
        }catch(_:Exception){jsch.removeAllIdentity();throw IllegalArgumentException("Could not unlock this private key. Check its format and passphrase.")}
        finally {key.fill(0);passphrase.fill(0)}
        return "publickey"
    }
}
