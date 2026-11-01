package top.ntutn.kica.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.DarkDefaultContextMenuRepresentation
import androidx.compose.foundation.LightDefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.text.AnnotatedString
import top.ntutn.kica.model.ThemePreference
import top.ntutn.kica.ui.theme.KicaFluentTheme
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KicaFluentThemeTest {
    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun desktopThemeUsesTheCurrentComposeTextContextMenu() {
        assertSame(TextContextMenu.Default, composeCompatibleTextContextMenu())
    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun themeOverridesFluentMenusAndRendersCurrentTextActions() {
        val manager = object : TextContextMenu.TextManager {
            override val selectedText = AnnotatedString("Selected text")
            override val cut = TextContextMenu.Action(true) {}
            override val copy = TextContextMenu.Action(true) {}
            override val paste = TextContextMenu.Action(false) {}
            override val selectAll = TextContextMenu.Action(true) {}
            override fun selectWordAtPositionIfNotAlreadySelected(offset: Offset) = Unit
        }
        for (preference in listOf(ThemePreference.LIGHT, ThemePreference.DARK)) {
            var composed = false
            val state = ContextMenuState().apply {
                status = ContextMenuState.Status.Open(Rect(0f, 0f, 1f, 1f))
            }
            renderComposeScene(400, 300) {
                KicaFluentTheme(preference = preference) {
                    assertSame(TextContextMenu.Default, LocalTextContextMenu.current)
                    assertSame(
                        if (preference == ThemePreference.DARK) DarkDefaultContextMenuRepresentation
                        else LightDefaultContextMenuRepresentation,
                        LocalContextMenuRepresentation.current,
                    )
                    // Fluent 0.1.0 would fail here when it reads the new Action return types.
                    LocalTextContextMenu.current.Area(manager, state) {}
                    composed = true
                }
            }.close()
            assertTrue(composed)
        }
    }
}
