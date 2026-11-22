package top.ntutn.kica.desktop

import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.ntutn.updater.DownloadedFile

internal fun systemDownloadsDirectory(
    os: String = System.getProperty("os.name"),
    home: Path = Path.of(System.getProperty("user.home")),
    environment: Map<String, String> = System.getenv(),
): Path {
    if (os.startsWith("Windows", true)) {
        return runCatching { Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Downloads)) }
            .getOrElse { home.resolve("Downloads") }
    }
    if (os == "Linux") {
        // XDG's file is data, never source it or invoke a shell. No other substitutions are accepted.
        val config = environment["XDG_CONFIG_HOME"]?.let(Path::of)?.takeIf { it.isAbsolute }
            ?: home.resolve(".config")
        val configured = environment["XDG_DOWNLOAD_DIR"] ?: runCatching {
            Files.readAllLines(config.resolve("user-dirs.dirs"))
                .firstOrNull { it.trimStart().startsWith("XDG_DOWNLOAD_DIR=") }
                ?.substringAfter('=')?.trim()
        }.getOrNull()
        parseXdgDownloads(configured, home)?.let { return it }
    }
    return home.resolve("Downloads")
}

internal fun parseXdgDownloads(value: String?, home: Path): Path? {
    val raw = value?.trim()?.removeSurrounding("\"") ?: return null
    val expanded = when {
        raw == "\$HOME" -> home.toString()
        raw.startsWith("\$HOME/") -> home.resolve(raw.removePrefix("\$HOME/")).toString()
        else -> raw
    }
    if (expanded.any { it == '$' || it == '`' || it == '\u0000' }) return null
    return runCatching { Path.of(expanded).takeIf { it.isAbsolute }?.normalize() }.getOrNull()
}

/** Atomic no-replace publication via a hard link, then unlink the SDK name (same filesystem).
 * Cross-filesystem copies are staged completely before publication. The original survives failure.
 */
internal fun moveDownloadToDownloads(
    source: Path,
    downloads: Path,
    requestedName: String,
    forceCopy: Boolean = false,
): Path {
    require(Files.isRegularFile(source, NOFOLLOW_LINKS)) { "下载文件不可用" }
    Files.createDirectories(downloads)
    val safe = requestedName.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[^\\p{L}\\p{N}._() -]"), "_").trim(' ', '.').take(160)
        .ifBlank { "Kica-update" }
    val dot = safe.lastIndexOf('.').takeIf { it > 0 } ?: safe.length
    val stem = safe.take(dot)
    val extension = safe.drop(dot)
    fun publish(staged: Path): Path {
        for (index in 0..10000) {
            val name = if (index == 0) safe else "$stem ($index)$extension"
            val destination = downloads.resolve(name)
            try {
                // Unlike ATOMIC_MOVE, createLink never replaces a pre-existing user file.
                Files.createLink(destination, staged)
                return destination
            } catch (_: FileAlreadyExistsException) {
                continue
            }
        }
        error("无法创建唯一下载文件名")
    }
    if (!forceCopy) {
        try {
            val destination = publish(source)
            // Publication succeeded; even an unlink failure must not lose the exported handle.
            runCatching { Files.delete(source) }
            return destination
        } catch (_: UnsupportedOperationException) {
            // Filesystem does not implement hard links; use complete staging below.
        } catch (_: FileSystemException) {
            // EXDEV, or a filesystem disallowing hardlinks. Never delete the original here.
        }
    }
    val temporary = Files.createTempFile(downloads, ".kica-update-", ".part")
    try {
        Files.newInputStream(source).use { input ->
            Files.newOutputStream(temporary).use { output -> input.copyTo(output) }
        }
        java.nio.channels.FileChannel.open(temporary, java.nio.file.StandardOpenOption.WRITE).use { it.force(true) }
        check(Files.size(temporary) == Files.size(source)) { "导出下载文件大小不匹配" }
        val destination = try {
            publish(temporary)
        } catch (_: UnsupportedOperationException) {
            publishWithoutLinks(temporary, downloads, stem, extension)
        } catch (_: FileSystemException) {
            publishWithoutLinks(temporary, downloads, stem, extension)
        }
        runCatching { Files.delete(source) }
        return destination
    } finally {
        // A cleanup failure must not report a failed export after successful publication/unlink.
        runCatching { Files.deleteIfExists(temporary) }
    }
}

private fun publishWithoutLinks(staged: Path, directory: Path, stem: String, extension: String): Path {
    // Reserve an unused filename ourselves, so atomic replacement can only replace our empty marker.
    val destination = Files.createTempFile(directory, "$stem-", extension)
    try {
        try {
            Files.move(staged, destination, ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(staged, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        return destination
    } catch (failure: Exception) {
        Files.deleteIfExists(destination)
        throw failure
    }
}

internal class ExportedUpdateFile(private val path: Path, override val sizeBytes: Long) : DownloadedFile {
    override val localPath: String = path.toAbsolutePath().toString()
    override val fileName: String = path.fileName.toString()
    override suspend fun exists(): Boolean = withContext(Dispatchers.IO) { Files.isRegularFile(path) }
}
