package top.ntutn.kica.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.TextContextMenu
import kotlin.test.Test
import kotlin.test.assertSame

class KicaFluentThemeTest {
    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun desktopThemeUsesTheCurrentComposeTextContextMenu() {
        assertSame(TextContextMenu.Default, composeCompatibleTextContextMenu())
    }
}
