package top.ntutn.kica.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import top.ntutn.kica.data.TitleTranslationService
import top.ntutn.kica.data.TitleTranslationState


internal val LocalTitleTranslationService = staticCompositionLocalOf<TitleTranslationService?> { null }

@Composable
internal fun translatedTitle(originalTitle: String): String {
    val service = LocalTitleTranslationService.current ?: return originalTitle
    val enabled by service.enabled.collectAsState()
    val serviceState by service.state.collectAsState()
    val ready = serviceState == TitleTranslationState.Ready
    val translated by produceState(originalTitle, originalTitle, enabled, ready) {
        value = originalTitle
        if (enabled) service.translate(originalTitle)?.let { value = it }
    }
    return translated
}
