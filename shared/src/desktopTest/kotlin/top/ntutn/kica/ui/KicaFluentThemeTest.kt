package top.ntutn.kica.ui

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.DarkDefaultContextMenuRepresentation
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LightDefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLocalization
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.text.AnnotatedString
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.FluentContextMenuItem
import top.ntutn.kica.model.ThemePreference
import top.ntutn.kica.ui.theme.KicaFluentTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
class KicaFluentThemeTest {
    @Test
    fun themePreservesFluentMenuProvidersInBothThemes() {
        for (preference in listOf(ThemePreference.LIGHT, ThemePreference.DARK)) {
            var composed = false
            renderComposeScene(400, 300) {
                KicaFluentTheme(preference = preference) {
                    assertEquals(preference == ThemePreference.DARK, FluentTheme.colors.darkMode)
                    assertNotSame(TextContextMenu.Default, LocalTextContextMenu.current)
                    assertNotSame(LightDefaultContextMenuRepresentation, LocalContextMenuRepresentation.current)
                    assertNotSame(DarkDefaultContextMenuRepresentation, LocalContextMenuRepresentation.current)
                    composed = true
                }
            }.close()
            assertTrue(composed)
        }
    }

    @Test
    fun fluentMenusFilterAndDispatchCurrentTextActions() {
        for (preference in listOf(ThemePreference.LIGHT, ThemePreference.DARK)) {
            verifyTextActions(preference, editable = true)
            verifyTextActions(preference, editable = false)
        }
    }

    private fun verifyTextActions(preference: ThemePreference, editable: Boolean) {
        val executed = mutableListOf<String>()
        val manager = object : TextContextMenu.TextManager {
            override val selectedText = AnnotatedString("选中文本")
            override val cut = if (editable) TextContextMenu.Action(true) { executed += "cut" } else null
            override val copy = TextContextMenu.Action(true) { executed += "copy" }
            override val paste = if (editable) TextContextMenu.Action(false) { executed += "paste" } else null
            override val selectAll = if (editable) TextContextMenu.Action(true) { executed += "selectAll" } else null
            override fun selectWordAtPositionIfNotAlreadySelected(offset: Offset) = Unit
        }
        val state = ContextMenuState().apply {
            status = ContextMenuState.Status.Open(Rect(0f, 0f, 1f, 1f))
        }
        var capturedItems: List<ContextMenuItem>? = null
        var expectedLabels = emptyList<String>()
        val captureRepresentation = object : ContextMenuRepresentation {
            @Composable
            override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
                // Evaluate the real Fluent text-menu callback, including its Action ABI accesses,
                // without depending on a popup animation or an active native window.
                capturedItems = items()
            }
        }
        renderComposeScene(400, 300) {
            KicaFluentTheme(preference = preference) {
                val localization = LocalLocalization.current
                expectedLabels = if (editable) {
                    listOf(localization.cut, localization.copy, localization.selectAll)
                } else {
                    listOf(localization.copy)
                }
                CompositionLocalProvider(LocalContextMenuRepresentation provides captureRepresentation) {
                    LocalTextContextMenu.current.Area(manager, state) {}
                }
            }
        }.close()

        val items = requireNotNull(capturedItems)
        assertEquals(expectedLabels, items.map { it.label })
        items.forEach {
            assertIs<FluentContextMenuItem>(it)
            assertTrue(it.enabled)
            it.onClick()
        }
        assertEquals(if (editable) listOf("cut", "copy", "selectAll") else listOf("copy"), executed)
    }
}
