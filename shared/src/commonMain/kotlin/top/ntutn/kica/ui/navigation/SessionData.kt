package top.ntutn.kica.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock
import top.ntutn.kica.ui.store.RandomComicsStore

internal data class ComicMutation(val favorite: Boolean? = null, val liked: Boolean? = null)

internal class SessionData(context: ComponentContext, services: AppServices) {
    private class Data(services: AppServices) : InstanceKeeper.Instance {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val random = RandomComicsStore(services)
        val tagResolver = top.ntutn.kica.data.ComicTagResolver(services.pica, services.library, scope)
        val mutations = MutableStateFlow<Map<String, ComicMutation>>(emptyMap())
        val mutationLock = kotlinx.coroutines.sync.Mutex()
        val writer = SessionProgressCoordinator(scope, services.library)
        private var closing = false
        private val closeCompleted = CompletableDeferred<Unit>()
        suspend fun close() {
            if (closing) { closeCompleted.await(); return }
            closing = true
            try { withContext(NonCancellable) { writer.close() } } finally {
                random.onDestroy()
                scope.cancel()
                tagResolver.clearMemoryCache()
                closeCompleted.complete(Unit)
            }
        }
        override fun onDestroy() { if (!closing) scope.launch { close() } }
    }
    private val data = context.instanceKeeper.getOrCreate("session-data") { Data(services) }
    private val services = services
    val random get() = data.random
    val tagResolver get() = data.tagResolver
    val progressWriter get() = data.writer
    val mutations: StateFlow<Map<String, ComicMutation>> get() = data.mutations
    fun confirmFavorite(id: String, favorite: Boolean) {
        val previous = data.mutations.value[id] ?: ComicMutation()
        data.mutations.value = data.mutations.value + (id to previous.copy(favorite = favorite))
    }
    fun confirmLike(id: String, liked: Boolean) {
        val previous = data.mutations.value[id] ?: ComicMutation()
        data.mutations.value = data.mutations.value + (id to previous.copy(liked = liked))
    }
    suspend fun toggleFavorite(id: String, initial: Boolean) = data.mutationLock.withLock {
        val previous = data.mutations.value[id]?.favorite ?: initial
        if (services.pica.toggleFavorite(id)) { confirmFavorite(id, !previous); true } else false
    }
    suspend fun toggleLike(id: String, initial: Boolean) = data.mutationLock.withLock {
        val previous = data.mutations.value[id]?.liked ?: initial
        if (services.pica.like(id)) { confirmLike(id, !previous); true } else false
    }
    suspend fun close() = data.close()
}
