-keep class top.ntutn.kica.network.** { *; }
-keepclassmembers,allowobfuscation class * {
    @kotlinx.serialization.SerialName <fields>;
}
-dontwarn org.slf4j.**

