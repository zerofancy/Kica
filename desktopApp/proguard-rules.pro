# Optional integrations not used by the desktop application.
-dontwarn jakarta.servlet.**
-dontwarn jakarta.mail.**
-dontwarn org.jline.jansi.**
-dontwarn org.tukaani.xz.**
-dontwarn org.graalvm.nativeimage.**
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn android.os.**
-dontwarn android.annotation.**
-dontwarn javax.annotation.**

# Retrofit inspects service methods and generic types at runtime.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep interface top.ntutn.kica.network.PicaService { *; }
-keep class top.ntutn.kica.network.LoginBody { *; }
-keep class top.ntutn.kica.network.SearchBody { *; }
-keep class top.ntutn.kica.model.** { *; }
-keep class **$$serializer { *; }

# Logging is configured through logback.xml and service providers.
-keep class ch.qos.logback.** { *; }
-keep class org.slf4j.** { *; }

# Native bindings and file-dialog integrations use reflection.
-keep class com.sun.jna.** { *; }
-keep class org.freedesktop.dbus.** { *; }
-keep class org.sqlite.** { *; }
-keep class com.llamatik.** { *; }

# Avoid ProGuard return-type specialization across Kotlin multifile facades.
# https://github.com/Guardsquare/proguard/issues/533
-keep,allowshrinking,allowobfuscation class **Kt__* { *; }

# Okio uses the inverse facade naming pattern (Okio__OkioKt). Preserve its
# source/sink contracts to avoid a VerifyError after return-type optimization.
-keep class okio.** { *; }

# Coil discovers image fetchers/decoders through META-INF/services.
# Their implementations have no direct references and must survive shrinking.
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
