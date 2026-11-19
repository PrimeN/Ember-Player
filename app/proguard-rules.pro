# Ember Player release build: R8 shrinks and optimises the code, which makes Compose start and scroll
# noticeably faster on TV boxes. Most libraries ship their own rules; these cover the rest.

# Keep source locations available in Android crash reports.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# kotlinx.serialization: our @Serializable models and their generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class app.ember.tv.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.ember.tv.**$$serializer { *; }
-keepclassmembers class app.ember.tv.** {
    *** Companion;
}
-keepclasseswithmembers class app.ember.tv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Nextlib FFmpeg decoders: native methods and the classes they call back are looked up by name from the .so.
-keep class io.github.anilbeesetti.nextlib.** { *; }
# Media3 discovers the MPEG-H renderer by name; JNI also accesses decoder fields.
-keep class androidx.media3.decoder.mpegh.** { *; }
# Media3 discovers the renderer builder by reflection; JNI registers methods by name.
-keep class androidx.media3.decoder.iamf.** { *; }
-keep class androidx.media3.decoder.SimpleDecoderOutputBuffer { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Optional platform classes from network dependencies.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# libmpv JNI observers are called by their original class/member names.
-keep class dev.jdtech.mpv.** { *; }

# SFTP selects its own crypto implementations by class name.
-keep class com.jcraft.jsch.** { *; }
# JSch and SMBJ call Bouncy Castle's lightweight APIs directly. R8 retains their
# reachable algorithms and can remove unused JCA providers, TLS and ASN.1 code.
# SMBJ's event bus invokes annotated handlers, which need to survive release shrinking.
-keepattributes *Annotation*
-keepclassmembers class * {
    @net.engio.mbassy.listener.Handler <methods>;
}

# Not offered by Ember: desktop Kerberos/SSH agents, Log4j or event-bus EL conditions.
# SMB uses NTLM authentication; SFTP uses the credentials selected for the saved folder.
-dontwarn com.sun.jna.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.apache.logging.log4j.**
-dontwarn javax.el.**

# Cast discovers its options provider by its manifest name.
-keep class app.ember.player.EmberCastOptions { public <init>(); }
