package top.ntutn.kica.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Root-installed About section extensions; settings does not depend on the features it hosts. */
internal val LocalSettingsAboutContent = staticCompositionLocalOf<@Composable () -> Unit> { {} }
