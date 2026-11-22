package top.ntutn.kica.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.create
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.arkivanov.essenty.lifecycle.destroy
import kotlinx.coroutines.launch
import top.ntutn.kica.ui.navigation.AppServices
import top.ntutn.kica.ui.navigation.RootComponent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import java.awt.AWTException
import java.awt.Dimension
import java.awt.Robot
import java.awt.Toolkit
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess
import org.jetbrains.compose.resources.painterResource
import top.ntutn.kica.data.DefaultTitleTranslationService
import top.ntutn.kica.data.SqlTitleTranslationCache
import top.ntutn.kica.data.SqlLibraryRepository
import top.ntutn.kica.data.createKicaDatabase
import top.ntutn.kica.network.HttpDownloadExecutor
import top.ntutn.kica.network.JvmTitleTranslationModelStore
import top.ntutn.kica.network.RealPicaRepository
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.kica_icon
import top.ntutn.kica.ui.KicaApp

fun main() {
    // Let Compose dispose the window while keeping the JVM alive to drain X11 events.
    application(exitProcessOnExit = false) { KicaDesktopApp() }
    drainX11Events()
    exitProcess(0)
}

private fun drainX11Events() {
    val toolkit = Toolkit.getDefaultToolkit()
    if (toolkit.javaClass.name != "sun.awt.X11.XToolkit") return

    // JVM shutdown can pause AWT in an X11 error callback while it holds the Xlib display lock.
    // GLX needs that lock for native cleanup, so wait for both the X11 and AWT queues before exiting.
    try {
        Robot().waitForIdle()
    } catch (_: AWTException) {
        // If Robot is unavailable, still flush pending requests to the X server.
        toolkit.sync()
    }
}

@Composable
private fun ApplicationScope.KicaDesktopApp() {
    val dataDirectory = remember {
        applicationDataDirectory().also {
            FileKit.init(appId = "top.ntutn.kica", filesDir = it.toFile())
        }
    }
    val platform = remember { DesktopPlatformServices(dataDirectory) }
    val database = remember {
        Files.createDirectories(dataDirectory)
        val databasePath = dataDirectory.resolve("kica.db")
        val newDatabase = Files.notExists(databasePath)
        val driver = JdbcSqliteDriver("jdbc:sqlite:$databasePath")
        createKicaDatabase(driver, newDatabase)
    }
    val library = remember { SqlLibraryRepository(database) }
    val titleTranslation = remember {
        DefaultTitleTranslationService(
            modelStore = JvmTitleTranslationModelStore(dataDirectory.resolve("models").toFile()),
            cache = SqlTitleTranslationCache(database),
        )
    }
    val pica = remember { RealPicaRepository(platform.credentialStore) }
    val downloads = remember {
        DesktopDownloadCoordinator(library, HttpDownloadExecutor(pica))
    }

    val lifecycle = remember { LifecycleRegistry().apply { create() } }
    val root = remember {
        RootComponent(DefaultComponentContext(lifecycle), AppServices(pica, library, downloads, platform, titleTranslation))
    }
    val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
    val scope = rememberCoroutineScope()
    val closing = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    LaunchedEffect(windowState.isMinimized) {
        if (windowState.isMinimized) lifecycle.stop() else lifecycle.resume()
    }
    Window(
        onCloseRequest = {
            if (closing.compareAndSet(false, true)) scope.launch {
                try { root.close() } finally { lifecycle.destroy(); exitApplication() }
            }
        },
        title = "Kica",
        icon = painterResource(Res.drawable.kica_icon),
        state = windowState,
    ) {
        DisposableEffect(platform, window) {
            platform.dialogParent = FileKitDialogParent.awt(window)
            onDispose { platform.dialogParent = null }
        }
        DisposableEffect(root, window) {
            val listener = object : java.awt.event.WindowFocusListener {
                override fun windowLostFocus(event: java.awt.event.WindowEvent?) = root.onHostBackground()
                override fun windowGainedFocus(event: java.awt.event.WindowEvent?) = root.onHostForeground()
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(880, 600)
        }
        DisposableEffect(downloads, titleTranslation) {
            onDispose {
                lifecycle.destroy()
                downloads.close()
                titleTranslation.close()
            }
        }
        KicaApp(root)
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
