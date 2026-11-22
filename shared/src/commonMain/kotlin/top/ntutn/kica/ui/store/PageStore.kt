package top.ntutn.kica.ui.store

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.doOnResume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import top.ntutn.kica.ui.navigation.PageComponent

internal abstract class PageStore(
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : InstanceKeeper.Instance {
    abstract fun ensureLoaded()
    override fun onDestroy() { scope.cancel() }
}

internal inline fun <reified T : PageStore> PageComponent.store(noinline create: () -> T): T {
    val key = T::class.qualifiedName!!
    val store = instanceKeeper.getOrCreate(key) { create() }
    if (boundStores.add(key)) lifecycle.doOnResume { store.ensureLoaded() }
    return store
}
