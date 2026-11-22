package top.ntutn.kica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import io.github.composefluent.background.Mica
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.ui.component.FluentProgressRing
import top.ntutn.kica.ui.navigation.MainComponent
import top.ntutn.kica.ui.navigation.RootComponent
import top.ntutn.kica.ui.screen.LockScreen
import top.ntutn.kica.ui.screen.PatternLockScreen
import top.ntutn.kica.ui.screen.ReaderScreen
import top.ntutn.kica.ui.state.LocalTitleTranslationService
import top.ntutn.kica.ui.theme.KicaFluentTheme

/** Rendering only: navigation and page lifetimes belong to the platform-owned root. */
@Composable
fun KicaApp(root: RootComponent) {
    val state by root.state.collectAsState()
    val slot by root.main.subscribeAsState()
    val main = slot.child?.takeIf { it.configuration.sessionKey == state.sessionKey }?.instance
    val services = root.services
    val tagResolver = main?.session?.tagResolver
    val sections = main?.sections?.subscribeAsState()?.value
    val section = sections?.let { it.items[it.selectedIndex].instance }
    val stack = section?.stack?.subscribeAsState()?.value
    val reader = stack?.active?.instance?.route is AppRoute.Reader && !state.locked

    CompositionLocalProvider(
        LocalTitleTranslationService provides services.translation,
        LocalTagResolver provides tagResolver,
        LocalTagLibrary provides services.library,
    ) {
        KicaFluentTheme(preference = state.settings.theme, forceDarkSystemBars = reader) {
            Mica(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                // Key the UI saving domain by validated session identity, never by a token or route string.
                key(state.sessionKey) {
                    val unlockedUi = rememberSaveableStateHolder()
                    when {
                        state.restoring || state.loggingOut -> Box(Modifier.fillMaxSize()) { FluentProgressRing() }
                        state.locked && state.settings.lockPasswordHash != null -> {
                            PlatformBackHandler(true) { /* Never navigate behind the lock. */ }
                            LockScreen(state.settings.lockPasswordHash!!, root::unlock)
                        }
                        state.locked && state.settings.lockPatternHash != null -> {
                            PlatformBackHandler(true) { }
                            PatternLockScreen(state.settings.lockPatternHash!!, root::unlock)
                        }
                        state.sessionKey == null -> LoginScreen(state.authenticating, state.loginError, root::login)
                        main != null -> unlockedUi.SaveableStateProvider("unlocked-main") {
                            MainContent(main, root::logout)
                        }
                        else -> FluentProgressRing()
                    }
                }
                if (!state.restoring && !state.locked) state.serviceError?.let { error ->
                    ServiceErrorBanner(error, root::retryServices,
                        Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp))
                }
                }
            }
        }
    }
}

@Composable
private fun MainContent(main: MainComponent, onLogout: () -> Unit) {
    val sections by main.sections.subscribeAsState()
    val section = sections.items[sections.selectedIndex].instance ?: return
    val stack by section.stack.subscribeAsState()
    val columnState = rememberSaveableStateHolder()
    // Read the observed stack in this restart scope, not only inside Children: otherwise Compose
    // may recompose the child content while leaving the root BackHandler's enabled flag stale.
    val canGoBack = stack.backStack.isNotEmpty() || section.id != top.ntutn.kica.ui.navigation.SectionId.HOME
    PlatformBackHandler(canGoBack, main::back)
    columnState.SaveableStateProvider(section.id.name) {
        // The Children host stays in one location when reader shell/full-screen presentation changes.
        Children(stack = stack) { child ->
            val page = child.instance
            val render: @Composable () -> Unit = {
                if (page.route is AppRoute.Reader) ReaderScreen(page, main::back)
                else BrowseRouteContent(page, main::navigate, main::back, onLogout)
            }
            if (page.route is AppRoute.Reader) render()
            else Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                MainShell(section.id, main::selectSection, render)
            }
        }
    }
}
