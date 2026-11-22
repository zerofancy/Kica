package top.ntutn.kica.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.resources.*
import top.ntutn.kica.ui.navigation.SectionId
import top.ntutn.kica.ui.state.WindowLayout
import top.ntutn.kica.ui.state.classifyWindow

private data class SectionDestination(val id: SectionId, val label: StringResource, val icon: ImageVector)
private val destinations = listOf(
    SectionDestination(SectionId.HOME, Res.string.home, Icons.Regular.Home),
    SectionDestination(SectionId.DISCOVER, Res.string.discover, Icons.Regular.Navigation),
    SectionDestination(SectionId.FAVORITES, Res.string.favorites, Icons.Regular.Heart),
    SectionDestination(SectionId.HISTORY, Res.string.history, Icons.Regular.History),
    SectionDestination(SectionId.DOWNLOADS, Res.string.downloads, Icons.Regular.ArrowDownload),
    SectionDestination(SectionId.SETTINGS, Res.string.settings, Icons.Regular.Settings),
)

@Composable
internal fun MainShell(
    selected: SectionId,
    onSelect: (SectionId) -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = classifyWindow(maxWidth.value.toInt())
        val displayMode = when (layout) {
            WindowLayout.PHONE -> NavigationDisplayMode.LeftCollapsed
            WindowLayout.TABLET -> NavigationDisplayMode.LeftCompact
            WindowLayout.DESKTOP -> NavigationDisplayMode.Left
        }
        val navigation = rememberNavigationState(initialExpanded = layout == WindowLayout.DESKTOP)
        PlatformBackHandler(layout != WindowLayout.DESKTOP && navigation.expanded) {
            navigation.expanded = false
        }
        val select: (SectionId) -> Unit = {
            onSelect(it)
            if (layout != WindowLayout.DESKTOP) navigation.expanded = false
        }
        NavigationView(
            modifier = Modifier.fillMaxSize(), displayMode = displayMode, state = navigation,
            contentPadding = if (layout == WindowLayout.PHONE) PaddingValues(top = 48.dp) else PaddingValues(),
            title = { Text("Kica", style = FluentTheme.typography.subtitle) },
            menuItems = {
                destinations.dropLast(1).forEach { destination ->
                    menuItem(
                        selected = selected == destination.id,
                        onClick = { select(destination.id) },
                        text = { Text(stringResource(destination.label)) },
                        icon = { Icon(destination.icon, contentDescription = null) }, key = destination.id,
                    )
                }
            },
            footerItems = {
                val destination = destinations.last()
                menuItem(
                    selected = selected == destination.id,
                    onClick = { select(destination.id) },
                    text = { Text(stringResource(destination.label)) },
                    icon = { Icon(destination.icon, contentDescription = null) }, key = destination.id,
                )
            },
            pane = content,
        )
    }
}
