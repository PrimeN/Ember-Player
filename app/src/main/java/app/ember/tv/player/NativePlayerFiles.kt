package app.ember.tv.player

import android.content.Context
import android.system.Os
import java.io.File

/** The upstream fontconfig build has no Android app-specific default config.
 * Point it at read-only system fonts and a private, non-media cache before loading libmpv.
 */
internal object NativePlayerFiles {
    internal data class Prepared(val certificates:File,val shader:File)
    @Volatile private var prepared:Prepared?=null
    suspend fun await(context:Context)=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){prepare(context.applicationContext)}
    fun requirePrepared()=checkNotNull(prepared){"Native playback resources are unavailable."}
    fun upscaleShader(context:Context)=requirePrepared().shader
    @Synchronized private fun prepare(context:Context):Prepared {
        prepared?.let {return it}
        val directory=File(context.noBackupFilesDir,"player-fontconfig").apply {check(isDirectory||mkdirs())}
        val cache=File(context.cacheDir,"player-fonts").apply {check(isDirectory||mkdirs())}
        val roots=listOf("/system/fonts","/product/fonts","/system/product/fonts").filter {File(it).isDirectory}
        check(roots.isNotEmpty()) {"System fonts are unavailable."}
        val file=File(directory,"fonts.conf")
        val text=config(roots,cache.absolutePath)
        if(!file.isFile||file.readText()!=text)file.writeText(text)
        Os.setenv("FONTCONFIG_FILE",file.absolutePath,true)
        Os.setenv("FONTCONFIG_PATH",directory.absolutePath,true)
        val shader=extractShader(context)
        val factory=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()).apply {init(null as java.security.KeyStore?)}
        val certificates=factory.trustManagers.filterIsInstance<javax.net.ssl.X509TrustManager>().flatMap {it.acceptedIssuers.toList()}
        check(certificates.isNotEmpty()) {"System certificate store unavailable."}
        val ca=File(directory,"player-ca.pem")
        val pem=certificates.joinToString("\n") {"-----BEGIN CERTIFICATE-----\n"+java.util.Base64.getMimeEncoder(64,byteArrayOf(10)).encodeToString(it.encoded)+"\n-----END CERTIFICATE-----"}
        if(!ca.isFile||ca.readText()!=pem)ca.writeText(pem)
        return Prepared(ca,shader).also {prepared=it}
    }
    @androidx.annotation.WorkerThread
    internal fun extractShader(context:Context):File {
        val shaderRoot=File(context.noBackupFilesDir,"player-shaders").apply {check(isDirectory||mkdirs())}
        val shader=File(shaderRoot,"fast-upscale.glsl")
        val bytes=context.assets.open("shaders/fast-upscale.glsl").use {it.readBytes()}
        if(!shader.isFile||!shader.readBytes().contentEquals(bytes))shader.writeBytes(bytes)
        return shader
    }
    fun config(roots:List<String>,cache:String):String {
        fun xml(value:String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
        return "<?xml version=\"1.0\"?><fontconfig>"+roots.joinToString(""){"<dir>${xml(it)}</dir>"}+
            "<cachedir>${xml(cache)}</cachedir><alias><family>sans-serif</family><prefer><family>Roboto</family><family>Noto Sans</family></prefer></alias></fontconfig>"
    }
}
