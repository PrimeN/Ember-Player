import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.hierynomus.security.bc.BCSecurityProvider;
import com.hierynomus.security.Cipher;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.crypto.spec.GCMParameterSpec;

public class NetworkCryptoSmoke {
    public static void main(String[] args) throws Exception {
        BCSecurityProvider provider = new BCSecurityProvider();
        var hex = java.util.HexFormat.of();
        if (!hex.formatHex(provider.getDigest("MD4").digest()).equals("31d6cfe0d16ae931b73c59d7e0c089c0"))
            throw new AssertionError("NTLM MD4 vector");
        var cmac = provider.getMac("AESCMAC");
        cmac.init(hex.parseHex("2b7e151628aed2a6abf7158809cf4f3c"));
        cmac.update(hex.parseHex("6bc1bee22e409f96e93d7e117393172a"));
        if (!hex.formatHex(cmac.doFinal()).equals("070a16b46b4d4144f79bdd9dd04a287c"))
            throw new AssertionError("SMB AES-CMAC vector");
        var hmac = provider.getMac("HMACSHA256");
        hmac.init("key".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        hmac.update("The quick brown fox jumps over the lazy dog".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        if (!hex.formatHex(hmac.doFinal()).equals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"))
            throw new AssertionError("SMB HMAC-SHA256 vector");
        for (String name : new String[]{"AES/CCM/NoPadding", "AES/GCM/NoPadding"}) {
            var cipher = provider.getAEADBlockCipher(name);
            byte[] key = new byte[16], aad = new byte[16], plain = new byte[64];
            var spec = new GCMParameterSpec(128, new byte[12]);
            cipher.init(Cipher.CryptMode.ENCRYPT, key, spec); cipher.updateAAD(aad,0,aad.length);
            byte[] encrypted = cipher.doFinal(plain,0,plain.length);
            cipher.init(Cipher.CryptMode.DECRYPT,key,spec);cipher.updateAAD(aad,0,aad.length);
            if (!Arrays.equals(plain,cipher.doFinal(encrypted,0,encrypted.length))) throw new AssertionError(name);
        }
        for (int type : new int[]{KeyPair.RSA, KeyPair.ECDSA}) {
            var pair = KeyPair.genKeyPair(new JSch(),type,type == KeyPair.RSA ? 2048 : 256);
            var bytes = new ByteArrayOutputStream();
            pair.writePrivateKey(bytes,"temporary test passphrase".getBytes());
            var loaded=KeyPair.load(new JSch(),bytes.toByteArray(),null);
            if(loaded.isEncrypted() && !loaded.decrypt("temporary test passphrase")) throw new AssertionError("SFTP private key");
            loaded.dispose();pair.dispose();
        }
        var signer = new com.jcraft.jsch.bc.SignatureEd25519();
        signer.init();
        var keys = new com.jcraft.jsch.bc.KeyPairGenEdDSA(); keys.init("Ed25519",256);
        signer.setPrvKey(keys.getPrv());signer.update(new byte[32]);var signature=signer.sign();
        signer.init();signer.setPubKey(keys.getPub());signer.update(new byte[32]);
        byte[] algorithm="ssh-ed25519".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] wrapped=java.nio.ByteBuffer.allocate(8+algorithm.length+signature.length)
            .putInt(algorithm.length).put(algorithm).putInt(signature.length).put(signature).array();
        if(!signer.verify(wrapped))throw new AssertionError("BC Ed25519");
        System.out.println("SMB signing/encryption and SFTP keys passed after R8 shrinking.");
    }
}
