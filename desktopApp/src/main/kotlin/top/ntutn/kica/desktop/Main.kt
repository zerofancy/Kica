package top.ntutn.kica.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.compose.resources.painterResource
import top.ntutn.kica.data.SqlLibraryRepository
import top.ntutn.kica.db.KicaDatabase
import top.ntutn.kica.network.HttpDownloadExecutor
import top.ntutn.kica.network.RealPicaRepository
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.kica_icon
import top.ntutn.kica.ui.KicaApp

fun main() = application {
    val dataDirectory = remember { applicationDataDirectory() }
    val platform = remember { DesktopPlatformServices(dataDirectory) }
    val library = remember {
        Files.createDirectories(dataDirectory)
        val databasePath = dataDirectory.resolve("kica.db")
        val newDatabase = Files.notExists(databasePath)
        val driver = JdbcSqliteDriver("jdbc:sqlite:$databasePath")
        if (newDatabase) KicaDatabase.Schema.create(driver)
        SqlLibraryRepository(KicaDatabase(driver))
    }
    val pica = remember { RealPicaRepository(platform.credentialStore) }
    val downloads = remember {
        DesktopDownloadCoordinator(library, HttpDownloadExecutor(pica))
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Kica",
        icon = painterResource(Res.drawable.kica_icon),
        state = WindowState(width = 1200.dp, height = 800.dp),
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(880, 600)
        }
        DisposableEffect(downloads) {
            onDispose(downloads::close)
        }
        KicaApp(
            picaRepository = pica,
            libraryRepository = library,
            downloadCoordinator = downloads,
            platformServices = platform,
        )
    }
}

private fun applicationDataDirectory(): Path {
    val osName = System.getProperty("os.name").lowercase()
    val userHome = Path.of(System.getProperty("user.home"))
    return when {
        osName.contains("windows") ->
            Path.of(System.getenv("APPDATA") ?: userHome.resolve("AppData/Roaming").toString()).resolve("Kica")
        osName.contains("mac") ->
            userHome.resolve("Library/Application Support/Kica")
        else ->
            Path.of(System.getenv("XDG_DATA_HOME") ?: userHome.resolve(".local/share").toString()).resolve("kica")
    }
}
