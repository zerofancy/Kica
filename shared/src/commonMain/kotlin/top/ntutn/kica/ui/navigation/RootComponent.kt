package top.ntutn.kica.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.create
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Platform hosts own this shell; InstanceKeeper owns all configuration-retained business stores. */
class RootComponent(
    private val context: ComponentContext,
    val services: AppServices,
) {
    private val store = context.instanceKeeper.getOrCreate("root-store") { RootStore(services) }
    internal val state = store.state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = LifecycleRegistry().apply { create() }
    private val stateKeeper = NavigationStateKeeper(context.stateKeeper)
    private val gatedContext = DefaultComponentContext(
        lifecycle = gate,
        stateKeeper = stateKeeper.safeStateKeeper,
        instanceKeeper = context.instanceKeeper,
        backHandler = context.backHandler,
    )
    private val navigation = SlotNavigation<SessionConfig>()
    internal val main = gatedContext.childSlot(
        source = navigation,
        saveConfiguration = NavigationRestore::saveSession,
        restoreConfiguration = NavigationRestore::session,
        key = "authenticated-session-v1",
        handleBackButton = false,
    ) { _, childContext -> MainComponent(childContext, services) }
    private var loggingOut = false

    init {
        context.lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onResume() { store.foreground(); updateGate() }
            override fun onPause() { updateGate() }
            override fun onStop() { store.background(); gate.stop() }
            override fun onDestroy() { scope.cancel(); gate.destroy() }
        })
        scope.launch {
            state.collect { value ->
                if (!value.restoring) {
                    val key = value.sessionKey
                    val current = main.value.child?.configuration?.sessionKey
                    if (key == null) navigation.dismiss()
                    else if (current != key) {
                        main.value.child?.instance?.close()
                        navigation.activate(SessionConfig(key))
                    }
                }
                updateGate()
            }
        }
    }

    private fun updateGate() {
        if (gate.state == Lifecycle.State.DESTROYED) return
        val value = state.value
        if (context.lifecycle.state == Lifecycle.State.RESUMED && !value.restoring &&
            value.sessionKey != null && !value.locked && !loggingOut
        ) gate.resume() else gate.stop()
    }

    fun login(email: String, password: String) = store.login(email, password)
    fun unlock() = store.unlock()
    fun retryServices() = store.retryServices()
    fun onHostBackground() = store.background()
    fun onHostForeground() = store.foreground()
    fun logout() {
        store.logout(main.value.child?.instance?.session)
    }

    /** Called before the desktop host destroys its lifecycle. */
    suspend fun close() {
        loggingOut = true
        updateGate()
        main.value.child?.instance?.close()
    }
}

@Serializable
internal data class SessionConfig(val sessionKey: String)
