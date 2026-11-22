package top.ntutn.kica.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.ntutn.kica.ui.theme.KicaFluentTheme
import top.ntutn.kica.ui.update.CompletedDownloadContent
import top.ntutn.kica.ui.update.UpdateDialogSurface
import io.github.composefluent.component.Text
import top.ntutn.kica.update.FakeUpdateFile
import top.ntutn.kica.update.FakeUpdateRuntime
import top.ntutn.kica.update.UpdateHostActions
import top.ntutn.kica.update.UpdateStore
import top.ntutn.kica.update.UpdateRuntime
import top.ntutn.kica.update.UpdateInstallResult
import top.ntutn.updater.DownloadResult
import top.ntutn.updater.DownloadedFile

/** Headless Compose scenes: no real desktop focus, browser, installer or shutdown is used. */
@OptIn(ExperimentalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UpdateDownloadUiTest {
    @Test
    fun laterDoesNotExitAndExplicitConfirmationExitsOnce() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = UpdateStore(FakeUpdateRuntime(), parentScope = backgroundScope)
        var exits = 0
        val file = FakeUpdateFile()
        val scene = ImageComposeScene(600, 500, coroutineContext = backgroundScope.coroutineContext) {
            KicaFluentTheme { Column { CompletedDownloadContent(store, file, UpdateHostActions(exitForManualInstall = { exits++ })) } }
        }
        try {
            scene.render()
            click(scene, "稍后安装")
            runCurrent()
            advanceUntilIdle()
            assertEquals(0, exits)
            click(scene, "退出并手动安装")
            runCurrent()
            advanceUntilIdle()
            assertEquals(1, exits)
        } finally {
            scene.close()
            store.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun missingFileDoesNotExitAndOffersRedownload() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val runtime = FakeUpdateRuntime()
        val store = UpdateStore(runtime, parentScope = backgroundScope)
        var exits = 0
        val file = object : DownloadedFile by FakeUpdateFile() { override suspend fun exists() = false }
        runtime.downloadBlock = { DownloadResult.Completed(file) }
        store.check()
        runCurrent()
        store.download()
        runCurrent()
        assertEquals(1, runtime.downloads)
        val scene = ImageComposeScene(400, 600, coroutineContext = backgroundScope.coroutineContext) {
            KicaFluentTheme { Column { CompletedDownloadContent(store, file, UpdateHostActions(exitForManualInstall = { exits++ })) } }
        }
        try {
            scene.render()
            click(scene, "退出并手动安装")
            runCurrent()
            advanceUntilIdle()
            Snapshot.sendApplyNotifications()
            scene.render()
            assertEquals(0, exits)
            assertTrue(nodes(scene).any { node -> node.text().contains("重新下载安装包") })
            runtime.downloadBlock = { DownloadResult.Completed(FakeUpdateFile()) }
            click(scene, "重新下载安装包")
            runCurrent()
            assertEquals(2, runtime.downloads)
            assertEquals(0, runtime.deletes)
        } finally {
            scene.close()
            store.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun androidPermissionSettingsNeverAutoInstalls() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val runtime = FakeUpdateRuntime()
        val android = object : UpdateRuntime by runtime { override val info = runtime.info.copy(android = true) }
        val store = UpdateStore(android, parentScope = backgroundScope)
        val file = FakeUpdateFile()
        var installs = 0
        var settings = 0
        val actions = UpdateHostActions(
            install = { installs++; if (installs == 1) UpdateInstallResult.PermissionRequired else UpdateInstallResult.Dispatched },
            openInstallSettings = { settings++; true },
        )
        val scene = ImageComposeScene(500, 600, coroutineContext = backgroundScope.coroutineContext) {
            KicaFluentTheme { Column { CompletedDownloadContent(store, file, actions) } }
        }
        try {
            scene.render()
            click(scene, "安装更新")
            runCurrent()
            Snapshot.sendApplyNotifications()
            scene.render()
            assertEquals(1, installs)
            click(scene, "前往设置")
            runCurrent()
            assertEquals(1, settings)
            assertEquals(1, installs, "Opening settings cannot automatically retry installation")
            click(scene, "安装更新")
            runCurrent()
            Snapshot.sendApplyNotifications()
            scene.render()
            assertEquals(2, installs)
            assertTrue(nodes(scene).any { it.text().contains("已交给系统安装器") })
        } finally {
            scene.close()
            store.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun removingForegroundUiCancelsPendingInstallPreparation() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val runtime = FakeUpdateRuntime()
        val android = object : UpdateRuntime by runtime { override val info = runtime.info.copy(android = true) }
        val store = UpdateStore(android, parentScope = backgroundScope)
        val file = FakeUpdateFile()
        val preparation = CompletableDeferred<Unit>()
        var cancelled = false
        var dispatched = false
        val scene = ImageComposeScene(500, 600, coroutineContext = backgroundScope.coroutineContext) {
            KicaFluentTheme { Column {
                CompletedDownloadContent(store, file, UpdateHostActions(install = {
                    try { preparation.await(); dispatched = true; UpdateInstallResult.Dispatched }
                    catch (failure: CancellationException) { cancelled = true; throw failure }
                }))
            } }
        }
        try {
            scene.render()
            click(scene, "安装更新")
            runCurrent()
            scene.setContent { } // Same disposal as foreground/lock gate unmounting UpdateHost.
            scene.render()
            runCurrent()
            preparation.complete(Unit)
            runCurrent()
            assertTrue(cancelled)
            assertEquals(false, dispatched)
        } finally {
            scene.close()
            store.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun longReleaseNotesRemainScrollableInSmallWindow() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val scene = ImageComposeScene(360, 300, coroutineContext = backgroundScope.coroutineContext) {
            KicaFluentTheme { UpdateDialogSurface {
                Text("更新说明\n" + (1..100).joinToString("\n") { "第 $it 项更新：这里是长文本说明。" })
                Text("底部操作仍可通过滚动访问")
            } }
        }
        try {
            scene.render(0L)
            val node = nodes(scene).first { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
            val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue(range.maxValue() > 0f)
            assertEquals(0f, range.value())
            assertTrue(node.config[SemanticsActions.ScrollBy].action?.invoke(0f, 200f) == true)
            // ScrollBy animates; drive virtual Compose frames instead of waiting on wall time.
            repeat(60) { frame ->
                runCurrent()
                scene.render((frame + 1) * 16_666_667L)
            }
            assertTrue(range.value() > 0f)
        } finally {
            scene.close()
            Dispatchers.resetMain()
        }
    }

    private fun click(scene: ImageComposeScene, label: String) {
        val target = nodes(scene).firstOrNull {
            it.config.getOrNull(SemanticsActions.OnClick) != null && it.text().contains(label)
        } ?: error("Missing button: $label")
        assertTrue(target.config[SemanticsActions.OnClick].action?.invoke() == true)
    }

    private fun SemanticsNode.text(): String =
        (config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + children.map { it.text() }).joinToString(" ")

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> {
        fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::visit)
        return scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
    }
}
