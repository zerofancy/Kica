package top.ntutn.kica.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopUpdateRuntimeTest {
    @Test
    fun automaticLatchIsProcessRuntimeOwnedAndPreferenceSurvivesRecreation() = runBlocking {
        val directory = Files.createTempDirectory("kica-update-runtime-test-")
        try {
            val runtime = DesktopUpdateRuntime(directory)
            assertTrue(runtime.autoCheckEnabled.value)
            assertTrue(runtime.claimAutomaticCheck())
            assertFalse(runtime.claimAutomaticCheck())
            runtime.setAutoCheckEnabled(false)
            assertFalse(runtime.autoCheckEnabled.value)
            runtime.close()
            val recreated = DesktopUpdateRuntime(directory)
            assertFalse(recreated.autoCheckEnabled.value)
            assertTrue(recreated.claimAutomaticCheck())
            assertEquals(DesktopBuildMetadata.versionCode, recreated.info.versionCode)
            assertEquals(DesktopBuildMetadata.versionName, recreated.info.versionName)
            if (!DesktopBuildMetadata.releaseBuild) {
                assertFalse(recreated.info.enabled)
                assertEquals(null, recreated.checker)
                assertEquals(null, recreated.downloader)
            }
            recreated.close()
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
