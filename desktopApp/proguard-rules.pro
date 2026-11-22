# Dependency-specific keep and optional-integration rules are supplied by KeepKit.
# Avoid ProGuard return-type specialization across Kotlin multifile facades.
# https://github.com/Guardsquare/proguard/issues/533
-keep,allowshrinking,allowobfuscation class **Kt__* { *; }
# Decompose discovers the Swing main-thread checker reflectively.
-keep class com.arkivanov.decompose.extensions.compose.mainthread.SwingMainThreadChecker
