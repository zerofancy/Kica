# Dependency-specific keep and optional-integration rules are supplied by KeepKit.
# Avoid ProGuard return-type specialization across Kotlin multifile facades.
# https://github.com/Guardsquare/proguard/issues/533
-keep,allowshrinking,allowobfuscation class **Kt__* { *; }

# Fluent 0.1.0 references the Compose 1.8 TextManager ABI. KicaFluentTheme replaces
# both LocalTextContextMenu and LocalContextMenuRepresentation with Compose's
# current implementations; this legacy menu is never used by application content.
# https://github.com/compose-fluent/compose-fluent-ui/issues/164#issuecomment-5622011230
-dontwarn io.github.composefluent.component.FluentTextContextMenu

# Haze 1.6.6 references the old ShaderBrush.createShader ABI in its brush path.
# Kica uses Fluent's solid-color Mica tints, with no brush tints, mask or progressive
# blur. Keep this suppression scoped to that implementation; adding brush effects
# requires upgrading Haze to a version compatible with the resolved Compose UI.
-dontwarn dev.chrisbanes.haze.RenderEffect_skikoKt
