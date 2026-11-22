package top.ntutn.kica.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.model.AppSettings

/** Independent Activity only: SAEP's security unit is an Activity, not a navigation destination. */
class AiOperationOptionsComponent(context: ComponentContext, library: LibraryRepository) : ComponentContext by context {
    private val store = instanceKeeper.getOrCreate("ai-options-store") { Store(library) }
    val state: StateFlow<AiOptionsState> get() = store.state
    fun setEnabled(enabled: Boolean) = store.setEnabled(enabled)
    fun policyApplied(requestId: Long) = store.policyApplied(requestId)

    private class Store(private val library: LibraryRepository) : InstanceKeeper.Instance {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val mutable = MutableStateFlow(AiOptionsState())
        val state: StateFlow<AiOptionsState> = mutable
        private var nextRequest = 0L
        init { scope.launch { library.settings().collect { mutable.value = mutable.value.copy(settings = it) } } }
        fun setEnabled(enabled: Boolean) {
            if (mutable.value.busy) return
            mutable.value = mutable.value.copy(busy = true, error = null)
            scope.launch {
                try {
                    library.updateSettings(library.settings().first().copy(aiOperationEnabled = enabled))
                    mutable.value = mutable.value.copy(pendingPolicy = AiPolicyRequest(++nextRequest, enabled))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutable.value = mutable.value.copy(error = "设置保存失败，请重试") }
                finally { mutable.value = mutable.value.copy(busy = false) }
            }
        }
        fun policyApplied(id: Long) {
            if (mutable.value.pendingPolicy?.id == id) mutable.value = mutable.value.copy(pendingPolicy = null)
        }
        override fun onDestroy() { scope.cancel() }
    }
}

data class AiOptionsState(
    val settings: AppSettings = AppSettings(),
    val busy: Boolean = false,
    val pendingPolicy: AiPolicyRequest? = null,
    val error: String? = null,
)
data class AiPolicyRequest(val id: Long, val enabled: Boolean)
