package top.ntutn.kica.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class UpdateDownloadExportTest {
    @Test
    fun collisionPreservesUserFileAndMovesCompletedFile() = withDirectory { root ->
        val source = root.resolve("sdk/package.deb")
        Files.createDirectories(source.parent)
        Files.writeString(source, "verified package")
        val downloads = Files.createDirectory(root.resolve("Downloads"))
        val existing = Files.writeString(downloads.resolve("package.deb"), "user file")
        val exported = moveDownloadToDownloads(source, downloads, "../../package.deb")
        assertNotEquals(existing, exported)
        assertEquals("user file", Files.readString(existing))
        assertEquals("verified package", Files.readString(exported))
        assertEquals(downloads, exported.parent)
        assertFalse(Files.exists(source))
    }

    @Test
    fun stagedCopyPublishesOnlyCompleteFileAndRemovesSource() = withDirectory { root ->
        val source = Files.writeString(root.resolve("sdk.exe"), "verified complete bytes")
        val downloads = root.resolve("Downloads")
        val exported = moveDownloadToDownloads(source, downloads, "Kica.exe", forceCopy = true)
        assertEquals("verified complete bytes", Files.readString(exported))
        assertFalse(Files.exists(source))
        Files.list(downloads).use { assertEquals(listOf(exported), it.toList()) }
    }

    @Test
    fun actualCrossFilesystemCopyWhenAvailable() = withDirectory { root ->
        val sharedMemory = Path.of("/dev/shm")
        if (!Files.isDirectory(sharedMemory) || !Files.isWritable(sharedMemory)) return@withDirectory
        if (Files.getFileStore(root) == Files.getFileStore(sharedMemory)) return@withDirectory
        val downloads = Files.createTempDirectory(sharedMemory, "kica-export-test-")
        try {
            val source = Files.writeString(root.resolve("sdk.deb"), "verified cross-filesystem bytes")
            val exported = moveDownloadToDownloads(source, downloads, "Kica.deb")
            assertEquals("verified cross-filesystem bytes", Files.readString(exported))
            assertFalse(Files.exists(source))
        } finally {
            Files.walk(downloads).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    @Test
    fun failedExportDoesNotDeleteOriginal() = withDirectory { root ->
        val source = Files.writeString(root.resolve("sdk.deb"), "verified complete bytes")
        val notDirectory = Files.writeString(root.resolve("Downloads"), "user file")
        assertFailsWith<Exception> {
            moveDownloadToDownloads(source, notDirectory, "Kica.deb", forceCopy = true)
        }
        assertEquals("verified complete bytes", Files.readString(source))
        assertEquals("user file", Files.readString(notDirectory))
    }

    @Test
    fun xdgExpansionIsDataNotShellCode() {
        val home = Path.of("/home/test")
        assertEquals(home.resolve("下载"), parseXdgDownloads("\"\$HOME/下载\"", home))
        assertEquals(Path.of("/media/downloads"), parseXdgDownloads("/media/downloads", home))
        assertNull(parseXdgDownloads("\$(touch /tmp/unwanted)", home))
        assertNull(parseXdgDownloads("relative/Downloads", home))
        assertNull(parseXdgDownloads("\$OTHER/Downloads", home))
    }

    @Test
    fun linuxUserDirsFileOverridesFallback() = withDirectory { root ->
        val config = Files.createDirectory(root.resolve("config"))
        Files.writeString(config.resolve("user-dirs.dirs"), "XDG_DOWNLOAD_DIR=\"\$HOME/Custom Downloads\"\n")
        assertEquals(root.resolve("Custom Downloads"), systemDownloadsDirectory(
            "Linux", root, mapOf("XDG_CONFIG_HOME" to config.toString()),
        ))
        assertEquals(root.resolve("Downloads"), systemDownloadsDirectory("Other", root, emptyMap()))
    }

    @Test
    fun concurrentPublicationNeverOverwrites() = withDirectory { root ->
        val downloads = Files.createDirectory(root.resolve("Downloads"))
        val sources = (0..7).map { Files.writeString(root.resolve("sdk-$it"), "package $it") }
        val executor = java.util.concurrent.Executors.newFixedThreadPool(8)
        try {
            val outputs = sources.map { source -> executor.submit<Path> {
                moveDownloadToDownloads(source, downloads, "Kica.deb")
            } }.map { it.get() }
            assertEquals(8, outputs.distinct().size)
            outputs.forEachIndexed { index, path -> assertEquals("package $index", Files.readString(path)) }
            assertTrue(sources.none { Files.exists(it) })
        } finally {
            executor.shutdownNow()
        }
    }

    private fun withDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("kica-export-test-")
        try { block(directory) } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
