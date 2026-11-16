import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/** The two engines use different FFmpeg ABIs. Rename libmpv's private ELF dependencies
 * and its JNI loader strings without changing symbols, byte offsets or JNI class names.
 * Never use packaging pickFirst for these libraries: that would break one engine.
 */
@org.gradle.api.tasks.CacheableTask
abstract class IsolateMpvNative : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val upstream: org.gradle.api.file.RegularFileProperty
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val filters: org.gradle.api.file.DirectoryProperty
    @get:OutputFile abstract val output: org.gradle.api.file.RegularFileProperty
    private val names = linkedMapOf(
        "libavcodec.so" to "libemcodec.so", "libavutil.so" to "libemutil.so",
        "libavformat.so" to "libemformat.so", "libavfilter.so" to "libemfilter.so",
        "libavdevice.so" to "libemdevice.so", "libswresample.so" to "libemresample.so",
        "libswscale.so" to "libemscale.so", "libmpv.so" to "libemv.so", "libplayer.so" to "libemplay.so",
    )
    private fun renameNative(bytes: ByteArray, loader:Boolean): ByteArray {
        if(loader) {
            // Upstream's verbose JNI logger includes arbitrary URLs/headers in mpv messages.
            // Keep the prefix/level but suppress the message text (same-size format literal).
            val original="[%s:%s] %s\u0000".toByteArray();val redacted="[%s:%s] **\u0000".toByteArray()
            var changed=0
            for(i in 0..bytes.size-original.size)if(original.indices.all {bytes[i+it]==original[it]}) {redacted.copyInto(bytes,i);changed++}
            require(changed==1) {"Native log format changed; review privacy patch."}
        }
        names.forEach { (old,new) ->
            val a=(old+'\u0000').toByteArray(Charsets.US_ASCII);val b=(new+'\u0000').toByteArray(Charsets.US_ASCII)
            require(a.size==b.size)
            for(i in 0..bytes.size-a.size) if(a.indices.all { bytes[i+it]==a[it] }) b.copyInto(bytes,i)
        }
        return bytes
    }
    private fun loaderClass(bytes:ByteArray):ByteArray {
        val view=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        require(view.int==0xCAFEBABE.toInt());view.position(8);val count=view.short.toInt() and 65535
        var slot=1;var changed=0
        while(slot<count) {
            when(val tag=view.get().toInt() and 255) {
                1 -> {val length=view.short.toInt() and 65535;val position=view.position();val data=ByteArray(length);view.get(data)
                    val replacement=when(String(data,Charsets.UTF_8)){"mpv"->"emv";"player"->"emplay";else->null}
                    if(replacement!=null) {require(replacement.length==length);replacement.toByteArray().copyInto(bytes,position);changed++}}
                3,4,9,10,11,12,17,18 -> view.position(view.position()+4)
                5,6 -> {view.position(view.position()+8);slot++}
                7,8,16,19,20 -> view.position(view.position()+2)
                15 -> view.position(view.position()+3)
                else -> error("Unknown class constant tag $tag")
            };slot++
        }
        require(changed==2) { "Upstream native loader changed; review isolation before upgrading." }
        return bytes
    }
    private fun classes(bytes:ByteArray):ByteArray {
        val out=ByteArrayOutputStream()
        ZipInputStream(bytes.inputStream()).use { input -> ZipOutputStream(out).use { zip ->
            while(true) {val entry=input.nextEntry?:break;val data=input.readBytes()
                zip.putNextEntry(ZipEntry(entry.name).apply { time=0L })
                zip.write(if(entry.name=="dev/jdtech/mpv/MPVLib.class")loaderClass(data) else data);zip.closeEntry()
            }
        } };return out.toByteArray()
    }
    @TaskAction fun isolate() {
        val original=upstream.get().asFile
        val digest=MessageDigest.getInstance("SHA-256").digest(original.readBytes()).joinToString(""){"%02x".format(it)}
        require(digest=="df146592480fc8418415a06b1f1a1d6318b0088e21f52254b0e9a82b61ca8fa2") { "Unexpected libmpv artifact; review dependency before building." }
        val filterRoot=filters.get().asFile
        val hashes=Properties().apply {filterRoot.resolve("checksums.properties").inputStream().use {load(it)}}
        val replaced=mutableSetOf<String>()
        val target=output.get().asFile;target.parentFile.mkdirs()
        ZipFile(original).use { source -> ZipOutputStream(target.outputStream()).use { zip ->
            source.entries().asSequence().forEach { entry ->
                if(entry.name.startsWith("jni/") && entry.name.split('/').getOrNull(1) !in setOf("arm64-v8a","armeabi-v7a"))return@forEach
                val data=if(entry.name.startsWith("jni/") && entry.name.endsWith("/libavfilter.so")) {
                    val abi=entry.name.split('/')[1]
                    val replacement=filterRoot.resolve("$abi/libavfilter.so").readBytes()
                    val sha=MessageDigest.getInstance("SHA-256").digest(replacement).joinToString(""){"%02x".format(it)}
                    require(sha==hashes.getProperty("$abi.sha256")) {"Native filter checksum mismatch for $abi"}
                    replaced+=abi
                    replacement
                } else source.getInputStream(entry).use { it.readBytes() }
                val name=if(entry.name.startsWith("jni/"))entry.name.substringBeforeLast('/')+'/'+(names[entry.name.substringAfterLast('/')]?:entry.name.substringAfterLast('/')) else entry.name
                zip.putNextEntry(ZipEntry(name).apply { time=0L })
                zip.write(when {name=="classes.jar"->classes(data);name.startsWith("jni/")&&name.endsWith(".so")->renameNative(data,name.endsWith("/libemplay.so"));else->data});zip.closeEntry()
            }
        } }
        require(replaced==setOf("arm64-v8a","armeabi-v7a")) {"Missing native filter ABI: $replaced"}
    }
}
val mpvUpstream by configurations.creating { isCanBeConsumed=false;isTransitive=false }
val isolateMpvNative by tasks.registering(IsolateMpvNative::class) {
    upstream.set(layout.file(provider { mpvUpstream.singleFile }))
    filters.set(layout.projectDirectory.dir("native/ffmpeg-filters"))
    output.set(layout.buildDirectory.file("isolated-mpv/libmpv-1.0.0.aar"))
}

/** Android has no java.rmi. Preserve RPC parsing failures through Ember's IOException
 * subtype, retaining both upstream constructor signatures and all protocol bytecode.
 */
@org.gradle.api.tasks.CacheableTask
abstract class PrepareSmbRpc : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val upstream: org.gradle.api.file.RegularFileProperty
    @get:OutputFile abstract val output: org.gradle.api.file.RegularFileProperty
    private fun androidClass(bytes:ByteArray):ByteArray {
        val result=ByteArrayOutputStream()
        DataInputStream(bytes.inputStream()).use {input->DataOutputStream(result).use {out->
            val magic=input.readInt();require(magic==0xCAFEBABE.toInt());out.writeInt(magic)
            out.writeShort(input.readUnsignedShort());out.writeShort(input.readUnsignedShort())
            val count=input.readUnsignedShort();out.writeShort(count)
            var slot=1
            while(slot<count) {
                val tag=input.readUnsignedByte();out.writeByte(tag)
                if(tag==1) {
                    val data=ByteArray(input.readUnsignedShort());input.readFully(data)
                    val value=String(data,Charsets.UTF_8)
                    val names=listOf("java/rmi/UnmarshalException","java/rmi/MarshalException")
                    require("java/rmi/" !in value || names.any {it in value}) {"Unexpected desktop RMI dependency"}
                    val patched=if(names.any {it in value})names.fold(value) {text,from->text.replace(from,"app/ember/tv/player/RpcParseException")}.toByteArray(Charsets.UTF_8) else data
                    out.writeShort(patched.size);out.write(patched)
                } else {
                    val size=when(tag) {3,4,9,10,11,12,17,18->4;5,6->8;7,8,16,19,20->2;15->3;else->error("Unknown class constant tag $tag")}
                    val data=ByteArray(size);input.readFully(data);out.write(data)
                    if(tag==5 || tag==6)slot++
                }
                slot++
            }
            input.copyTo(out)
        }}
        return result.toByteArray()
    }
    @TaskAction fun prepare() {
        val original=upstream.get().asFile
        val sha=MessageDigest.getInstance("SHA-256").digest(original.readBytes()).joinToString(""){"%02x".format(it)}
        require(sha=="ea3f970d4b6f5bedbca843551b9c4fe250de34942aa02e02a7f957a402f5cbb8") {"Unexpected SMB RPC artifact; review before upgrading."}
        val target=output.get().asFile;target.parentFile.mkdirs()
        ZipFile(original).use {source->ZipOutputStream(target.outputStream()).use {zip->
            source.entries().asSequence().forEach {entry->
                val data=source.getInputStream(entry).use {it.readBytes()}
                zip.putNextEntry(ZipEntry(entry.name).apply {time=0L})
                zip.write(if(entry.name.endsWith(".class"))androidClass(data) else data);zip.closeEntry()
            }
        }}
    }
}
val smbRpcUpstream by configurations.creating {isCanBeConsumed=false;isTransitive=false}
val prepareSmbRpc by tasks.registering(PrepareSmbRpc::class) {
    upstream.set(layout.file(provider {smbRpcUpstream.singleFile}))
    output.set(layout.buildDirectory.file("android-smb-rpc/dcerpc-0.12.13.jar"))
}

// MPEG-H is a standalone, statically linked decoder; APK support remains ARM-only.
val verifyMpeghNative by tasks.registering {
    inputs.dir(layout.projectDirectory.dir("native/mpegh"))
    inputs.files(fileTree("src/main/jniLibs") { include("**/libmpeghJNI.so") })
    doLast {
        val hashes = Properties().apply { file("native/mpegh/checksums.properties").inputStream().use { load(it) } }
        val expected = mapOf("arm64-v8a" to 183, "armeabi-v7a" to 40)
        val files = fileTree("src/main/jniLibs") { include("**/libmpeghJNI.so") }.files
        require(files.map { it.parentFile.name }.toSet() == expected.keys) { "MPEG-H must contain exactly the supported ARM ABIs" }
        for (library in files) {
            val abi = library.parentFile.name
            val bytes = library.readBytes()
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            require(sha == hashes.getProperty("$abi.sha256")) { "MPEG-H checksum mismatch: $abi" }
            require(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort(18).toInt() == expected[abi]) { "MPEG-H CPU mismatch: $abi" }
        }
        val source = file("native/mpegh/source/mpeghdec-r3.0.2.tar.gz").readBytes()
        require(MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it) } == hashes.getProperty("source.sha256")) { "MPEG-H source checksum mismatch" }
    }
}
tasks.named("preBuild") { dependsOn(verifyMpeghNative) }

val verifyIamfNative by tasks.registering {
    inputs.dir(layout.projectDirectory.dir("native/iamf"))
    inputs.files(fileTree("src/main/jniLibs") { include("**/libiamfJNI.so") })
    doLast {
        val hashes = Properties().apply {file("native/iamf/checksums.properties").inputStream().use {load(it)}}
        val expected = mapOf("arm64-v8a" to 183, "armeabi-v7a" to 40)
        val files = fileTree("src/main/jniLibs") {include("**/libiamfJNI.so")}.files
        require(files.map {it.parentFile.name}.toSet() == expected.keys) {"IAMF must contain exactly the supported ARM ABIs"}
        files.forEach {library ->
            val bytes = library.readBytes()
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
            require(sha == hashes.getProperty("${library.parentFile.name}.sha256")) {"IAMF checksum mismatch"}
            require(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort(18).toInt() == expected[library.parentFile.name]) {"IAMF CPU mismatch"}
        }
    }
}
tasks.named("preBuild") {dependsOn(verifyIamfNative)}

val verifyHdrSurface by tasks.registering {
    inputs.dir(layout.projectDirectory.dir("native/hdr-surface"))
    inputs.files(fileTree("src/main/jniLibs"){include("**/libember_surface.so")})
    doLast {
        val hashes=Properties().apply {file("native/hdr-surface/checksums.properties").inputStream().use {load(it)}}
        fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        require(digest(file("native/hdr-surface/hdr_surface.c").readBytes())==hashes.getProperty("source.sha256"))
        val expected=mapOf("arm64-v8a" to 183,"armeabi-v7a" to 40)
        val libraries=fileTree("src/main/jniLibs"){include("**/libember_surface.so")}.files
        require(libraries.map {it.parentFile.name}.toSet()==expected.keys)
        libraries.forEach {f->val b=f.readBytes();require(digest(b)==hashes.getProperty("${f.parentFile.name}.sha256"));require(ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getShort(18).toInt()==expected[f.parentFile.name])}
    }
}
tasks.named("preBuild"){dependsOn(verifyHdrSurface)}

android {
    namespace = "app.ember.tv"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.ember.player"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
    lint { abortOnError = true; disable += setOf("MissingTranslation"); lintConfig = file("lint.xml") }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") }
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    mpvUpstream("dev.jdtech.mpv:libmpv:1.0.0@aar")
    implementation(files(isolateMpvNative.flatMap { it.output }))
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.tv:tv-material:1.1.0")
    // Google's Material icons for the Settings pages (only the icons used stay in release builds; R8 drops the rest).
    // Compose 1.12's BOM no longer lists the icon libraries, so the last release is named here.
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.security:security-crypto:1.1.0")

    // Built-in Media3 playback engine
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.11.1")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("net.zetetic:sqlcipher-android:4.20.0")
    implementation("androidx.sqlite:sqlite:2.7.0")
    // FFmpeg audio decoders from Nextlib (TrueHD, DTS/DTS-HD core, AC3, E-AC3, FLAC, ALAC, MP3, AAC,
    // Opus, Vorbis), plus software video recovery through Nextlib DecoderManager.
    implementation("io.github.anilbeesetti:nextlib-media3ext:1.11.1-0.17.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil:2.7.0") // ImageView loading for the screensaver
    implementation("io.coil-kt:coil-gif:2.7.0") // animated collection covers
    implementation("io.coil-kt:coil-svg:2.7.0") // addon logos (many manifests link an SVG)
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Authenticated network file access; passwords stay in the existing Keystore-backed store.
    implementation("com.hierynomus:smbj:0.15.0")
    smbRpcUpstream("com.rapid7.client:dcerpc:0.12.13")
    implementation(files(prepareSmbRpc.flatMap {it.output}))
    implementation("commons-io:commons-io:2.21.0")
    implementation("org.apache.commons:commons-lang3:3.22.0")
    implementation("com.github.mwiede:jsch:2.28.8")
    implementation("commons-net:commons-net:3.13.0")
    implementation("me.jahnen.libaums:core:0.10.0")
    implementation("com.google.android.gms:play-services-cast-framework:23.0.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.fragment:fragment:1.8.9")

    testImplementation("androidx.sqlite:sqlite-framework:2.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
