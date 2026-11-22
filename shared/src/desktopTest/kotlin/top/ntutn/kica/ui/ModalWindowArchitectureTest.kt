package top.ntutn.kica.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/** Prevent new screens from silently bypassing automatic modal registration. */
class ModalWindowArchitectureTest {
    @Test
    fun nativeWindowsStayInsideTheGenericWrapperAndOldUpdateMarkersStayRemoved() {
        val root = Path.of("src/commonMain/kotlin/top/ntutn/kica/ui")
        assertTrue(Files.isDirectory(root), "Expected shared source root: $root")
        val wrapper = root.resolve("modal/ModalWindows.kt")
        val nativeWindow = Regex("""\b(?:Dialog|Popup)\s*\(""")
        val oldMarker = Regex("""\b(?:UpdateModalBlocker|LocalUpdatePresentation|UpdatePresentation)\b""")
        val violations = mutableListOf<String>()
        Files.walk(root).use { files ->
            files.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.forEach { file ->
                val source = Files.readString(file)
                if (file != wrapper && nativeWindow.containsMatchIn(source)) {
                    violations += "${root.relativize(file)} bypasses AppDialog/AppPopup"
                }
                if (oldMarker.containsMatchIn(source)) {
                    violations += "${root.relativize(file)} still uses update-specific modal state"
                }
                if ((file.startsWith(root.resolve("modal")) || file.startsWith(root.resolve("screen"))) &&
                    Regex("""top\.ntutn\.kica\.(?:ui\.)?update\b""").containsMatchIn(source)
                ) {
                    violations += "${root.relativize(file)} makes generic modal state or business screens depend on updates"
                }
            }
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
        val wrappers = Files.readString(wrapper)
        assertEquals(2, Regex("""ModalInteraction\(owner\)""").findAll(wrappers).count(),
            "Both native wrappers must register their lifetime")
    }
}
