package top.ntutn.kica.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.data.TitleTranslationState
import top.ntutn.kica.data.sha256
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.NetworkSettings
import top.ntutn.kica.model.ProxyMode
import top.ntutn.kica.model.ThemePreference
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.about
import top.ntutn.kica.resources.about_text
import top.ntutn.kica.resources.cache
import top.ntutn.kica.resources.cancel
import top.ntutn.kica.resources.choose_export_location
import top.ntutn.kica.resources.clear_cache
import top.ntutn.kica.resources.confirm
import top.ntutn.kica.resources.download_location
import top.ntutn.kica.resources.lock_change_password
import top.ntutn.kica.resources.lock_change_pattern
import top.ntutn.kica.resources.lock_clear_password
import top.ntutn.kica.resources.lock_clear_password_confirm
import top.ntutn.kica.resources.lock_screen
import top.ntutn.kica.resources.lock_screen_description
import top.ntutn.kica.resources.lock_set_password
import top.ntutn.kica.resources.lock_set_pattern
import top.ntutn.kica.resources.logout
import top.ntutn.kica.resources.network
import top.ntutn.kica.resources.password
import top.ntutn.kica.resources.prevent_screenshots
import top.ntutn.kica.resources.proxy_direct
import top.ntutn.kica.resources.proxy_host
import top.ntutn.kica.resources.proxy_http
import top.ntutn.kica.resources.proxy_port
import top.ntutn.kica.resources.proxy_socks5
import top.ntutn.kica.resources.proxy_system
import top.ntutn.kica.resources.retry
import top.ntutn.kica.resources.settings
import top.ntutn.kica.resources.theme
import top.ntutn.kica.resources.theme_dark
import top.ntutn.kica.resources.theme_light
import top.ntutn.kica.resources.theme_system
import top.ntutn.kica.resources.title_translation
import top.ntutn.kica.resources.title_translation_description
import top.ntutn.kica.resources.translation_model_downloading
import top.ntutn.kica.resources.translation_model_error
import top.ntutn.kica.resources.translation_model_loading
import top.ntutn.kica.resources.translation_model_missing
import top.ntutn.kica.resources.translation_model_ready
import top.ntutn.kica.ui.component.FluentButton
import top.ntutn.kica.ui.component.FluentCard
import top.ntutn.kica.ui.component.FluentChip
import top.ntutn.kica.ui.component.FluentPrimaryButton
import top.ntutn.kica.ui.component.FluentProgressBar
import top.ntutn.kica.ui.component.FluentTextButton
import top.ntutn.kica.ui.component.FluentTextField
import top.ntutn.kica.ui.component.SectionTitle
import top.ntutn.kica.ui.progress
import top.ntutn.kica.ui.state.LocalTitleTranslationService
import top.ntutn.kica.ui.screen.SettingCard
import top.ntutn.kica.ui.screen.ModelDownloadConfirmationDialog
import top.ntutn.kica.ui.PlatformHorizontalScrollbar
import top.ntutn.kica.ui.PlatformVerticalScrollbar
import top.ntutn.kica.ui.PlatformBackHandler


@Composable
internal fun SettingsScreen(
    library: LibraryRepository,
    platformServices: PlatformServices,
    onLogout: () -> Unit,
) {
    val settingsValue: AppSettings by library.settings().collectAsState(initial = AppSettings())
    val titleTranslationService = LocalTitleTranslationService.current
    val translationState = titleTranslationService?.state?.collectAsState()?.value
        ?: TitleTranslationState.Disabled
    val translationModelAvailable by produceState(false, titleTranslationService, translationState) {
        value = titleTranslationService?.isModelAvailable() == true
    }
    val scope = rememberCoroutineScope()
    val downloadLocation by produceState("") {
        value = platformServices.fileLocationProvider.defaultDownloadLocation()
    }
    var proxyHost by remember(settingsValue.network.proxyHost) {
        mutableStateOf(settingsValue.network.proxyHost)
    }
    var proxyPort by remember(settingsValue.network.proxyPort) {
        mutableStateOf(settingsValue.network.proxyPort.takeIf { it > 0 }?.toString().orEmpty())
    }
    var exportLocation: String? by remember { mutableStateOf<String?>(null) }
    var showModelConfirmation by remember { mutableStateOf(false) }
    var showLockDialog by remember { mutableStateOf(false) }
    var showClearLockDialog by remember { mutableStateOf(false) }
    val settingsScrollState = rememberScrollState()
    val proxyModeScrollState = rememberScrollState()
    val translationBusy = translationState is TitleTranslationState.Downloading ||
        translationState == TitleTranslationState.LoadingModel
    val enableTitleTranslation: () -> Unit = {
        titleTranslationService?.let { service ->
            scope.launch {
                runCatching { service.enable() }
                    .onSuccess {
                        library.updateSettings(
                            library.settings().first().copy(titleTranslationEnabled = true),
                        )
                    }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(settingsScrollState).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        SectionTitle(stringResource(Res.string.settings))
        SettingCard(stringResource(Res.string.theme)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePreference.entries.forEach { preference ->
                    val label = when (preference) {
                        ThemePreference.SYSTEM -> Res.string.theme_system
                        ThemePreference.LIGHT -> Res.string.theme_light
                        ThemePreference.DARK -> Res.string.theme_dark
                    }
                    FluentChip(
                        selected = settingsValue.theme == preference,
                        onClick = { scope.launch { library.updateSettings(settingsValue.copy(theme = preference)) } },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }
        SettingCard(stringResource(Res.string.lock_screen)) {
            Text(
                stringResource(Res.string.lock_screen_description),
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.secondary,
            )
            Switcher(
                checked = settingsValue.lockEnabled,
                onCheckStateChange = { enabled ->
                    val hasAnyPassword = settingsValue.lockPasswordHash != null || settingsValue.lockPatternHash != null
                    if (enabled && !hasAnyPassword) {
                        showLockDialog = true
                    } else if (enabled) {
                        scope.launch {
                            library.updateSettings(settingsValue.copy(lockEnabled = true))
                        }
                    } else {
                        scope.launch {
                            library.updateSettings(settingsValue.copy(lockEnabled = false))
                        }
                    }
                },
            )
            val hasPassword = settingsValue.lockPasswordHash != null || settingsValue.lockPatternHash != null
            if (hasPassword) {
                FluentTextButton(
                    onClick = { showLockDialog = true },
                ) {
                    Text(
                        stringResource(
                            if (platformServices.isDesktop) Res.string.lock_change_password
                            else Res.string.lock_change_pattern,
                        ),
                    )
                }
            } else {
                FluentTextButton(
                    onClick = { showLockDialog = true },
                ) {
                    Text(
                        stringResource(
                            if (platformServices.isDesktop) Res.string.lock_set_password
                            else Res.string.lock_set_pattern,
                        ),
                    )
                }
            }
            if (hasPassword) {
                FluentTextButton(
                    onClick = { showClearLockDialog = true },
                ) {
                    Text(
                        stringResource(Res.string.lock_clear_password),
                        color = FluentTheme.colors.system.critical,
                    )
                }
            }
        }
        if (!platformServices.isDesktop) {
            SettingCard(stringResource(Res.string.prevent_screenshots)) {
                Switcher(
                    checked = settingsValue.preventScreenshots,
                    onCheckStateChange = { enabled ->
                        scope.launch {
                            library.updateSettings(settingsValue.copy(preventScreenshots = enabled))
                        }
                    },
                )
            }
        }
        SettingCard(stringResource(Res.string.title_translation)) {
            Text(
                stringResource(Res.string.title_translation_description),
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.secondary,
            )
            Switcher(
                checked = settingsValue.titleTranslationEnabled,
                onCheckStateChange = { enabled ->
                    if (translationBusy || titleTranslationService == null) return@Switcher
                    if (!enabled) {
                        scope.launch {
                            library.updateSettings(
                                library.settings().first().copy(titleTranslationEnabled = false),
                            )
                            titleTranslationService.disable()
                        }
                    } else {
                        scope.launch {
                            if (titleTranslationService.isModelAvailable()) {
                                enableTitleTranslation()
                            } else {
                                showModelConfirmation = true
                            }
                        }
                    }
                },
            )
            when (val current = translationState) {
                TitleTranslationState.Disabled -> Text(
                    stringResource(
                        if (translationModelAvailable) Res.string.translation_model_ready
                        else Res.string.translation_model_missing,
                    ),
                    style = FluentTheme.typography.caption,
                )
                is TitleTranslationState.Downloading -> {
                    val progress = if (current.totalBytes <= 0L) 0f
                    else current.downloadedBytes.toFloat() / current.totalBytes
                    Text(
                        stringResource(
                            Res.string.translation_model_downloading,
                            (progress * 100).toInt().coerceIn(0, 100),
                        ),
                        style = FluentTheme.typography.caption,
                    )
                    FluentProgressBar(progress.coerceIn(0f, 1f), Modifier.fillMaxWidth())
                    FluentTextButton(onClick = { titleTranslationService?.cancelPreparation() }) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
                TitleTranslationState.LoadingModel -> {
                    Text(stringResource(Res.string.translation_model_loading))
                    FluentProgressBar(Modifier.fillMaxWidth())
                }
                TitleTranslationState.Ready -> Text(
                    stringResource(Res.string.translation_model_ready),
                    style = FluentTheme.typography.caption,
                )
                is TitleTranslationState.Error -> {
                    Text(
                        stringResource(Res.string.translation_model_error, current.message),
                        color = FluentTheme.colors.system.critical,
                    )
                    FluentButton(onClick = enableTitleTranslation) {
                        Text(stringResource(Res.string.retry))
                    }
                }
            }
        }
        SettingCard(stringResource(Res.string.network)) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(proxyModeScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProxyMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ProxyMode.DIRECT -> Res.string.proxy_direct
                            ProxyMode.SYSTEM -> Res.string.proxy_system
                            ProxyMode.HTTP -> Res.string.proxy_http
                            ProxyMode.SOCKS5 -> Res.string.proxy_socks5
                        }
                        FluentChip(
                            selected = settingsValue.network.proxyMode == mode,
                            onClick = {
                                scope.launch {
                                    library.updateSettings(
                                        settingsValue.copy(network = settingsValue.network.copy(proxyMode = mode)),
                                    )
                                }
                            },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                PlatformHorizontalScrollbar(proxyModeScrollState)
            }
            if (settingsValue.network.proxyMode in setOf(ProxyMode.HTTP, ProxyMode.SOCKS5)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FluentTextField(
                        value = proxyHost,
                        onValueChange = { value ->
                            proxyHost = value
                            scope.launch {
                                library.updateSettings(
                                    settingsValue.copy(network = settingsValue.network.copy(proxyHost = value)),
                                )
                            }
                        },
                        label = { Text(stringResource(Res.string.proxy_host)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    FluentTextField(
                        value = proxyPort,
                        onValueChange = { value ->
                            if (value.all(Char::isDigit) && value.length <= 5) {
                                proxyPort = value
                                scope.launch {
                                    library.updateSettings(
                                        settingsValue.copy(
                                            network = settingsValue.network.copy(
                                                proxyPort = value.toIntOrNull() ?: 0,
                                            ),
                                        ),
                                    )
                                }
                            }
                        },
                        label = { Text(stringResource(Res.string.proxy_port)) },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }
        SettingCard(stringResource(Res.string.download_location)) {
            Text(downloadLocation.ifBlank { platformServices.platformName })
            if (!platformServices.isDesktop) {
                FluentButton(
                    onClick = {
                        scope.launch {
                            exportLocation = platformServices.fileLocationProvider.chooseExportLocation()
                        }
                    },
                ) {
                    Text(stringResource(Res.string.choose_export_location))
                }
                exportLocation?.let { Text(it, style = FluentTheme.typography.caption) }
            }
        }
        SettingCard(stringResource(Res.string.cache)) {
            FluentButton(onClick = {
                scope.launch {
                    library.clearCache()
                    titleTranslationService?.clearCache()
                }
            }) {
                Text(stringResource(Res.string.clear_cache))
            }
        }
        SettingCard(stringResource(Res.string.about)) {
            Text(stringResource(Res.string.about_text))
        }
        FluentButton(onClick = onLogout) {
            Text(stringResource(Res.string.logout))
        }
        }
        PlatformVerticalScrollbar(settingsScrollState, Modifier.align(Alignment.CenterEnd))
    }
    if (showModelConfirmation) {
        ModelDownloadConfirmationDialog(
            onConfirm = {
                showModelConfirmation = false
                enableTitleTranslation()
            },
            onDismiss = { showModelConfirmation = false },
        )
    }
    if (showLockDialog) {
        if (platformServices.isDesktop) {
            LockSettingsDialog(
                onConfirm = { password ->
                    showLockDialog = false
                    scope.launch {
                        library.updateSettings(
                            settingsValue.copy(
                                lockEnabled = true,
                                lockPasswordHash = sha256(password),
                                lockPatternHash = null,
                            ),
                        )
                    }
                },
                onDismiss = { showLockDialog = false },
            )
        } else {
            PatternLockSettingsDialog(
                onConfirm = { pattern ->
                    showLockDialog = false
                    scope.launch {
                        library.updateSettings(
                            settingsValue.copy(
                                lockEnabled = true,
                                lockPatternHash = sha256(pattern),
                                lockPasswordHash = null,
                            ),
                        )
                    }
                },
                onDismiss = { showLockDialog = false },
            )
        }
    }
    if (showClearLockDialog) {
        Dialog(onDismissRequest = { showClearLockDialog = false }) {
            PlatformBackHandler(enabled = true, onBack = { showClearLockDialog = false })
            FluentCard(Modifier.width(360.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        stringResource(Res.string.lock_clear_password_confirm),
                        style = FluentTheme.typography.body,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        FluentTextButton(onClick = { showClearLockDialog = false }) {
                            Text(stringResource(Res.string.cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        FluentPrimaryButton(
                            onClick = {
                                showClearLockDialog = false
                                scope.launch {
                                    library.updateSettings(
                                        settingsValue.copy(
                                            lockEnabled = false,
                                            lockPasswordHash = null,
                                            lockPatternHash = null,
                                        ),
                                    )
                                }
                            },
                        ) {
                            Text(stringResource(Res.string.confirm))
                        }
                    }
                }
            }
        }
    }
}
