# Dependency-specific keep and optional-integration rules are supplied by KeepKit.
# Avoid ProGuard return-type specialization across Kotlin multifile facades.
# https://github.com/Guardsquare/proguard/issues/533
-keep,allowshrinking,allowobfuscation class **Kt__* { *; }
