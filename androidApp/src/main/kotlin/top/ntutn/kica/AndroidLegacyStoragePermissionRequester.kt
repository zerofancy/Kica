package top.ntutn.kica

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidLegacyStoragePermissionRequester {
    private val guard = Any()
    private var launch: (() -> Unit)? = null
    private var pending: CompletableDeferred<Boolean>? = null

    fun bind(launch: () -> Unit) {
        synchronized(guard) {
            this.launch = launch
        }
    }

    fun unbind() {
        synchronized(guard) {
            launch = null
            pending?.complete(false)
            pending = null
        }
    }

    suspend fun request(): Boolean {
        val request = CompletableDeferred<Boolean>()
        val action = synchronized(guard) {
            pending?.complete(false)
            pending = request
            launch
        }
        if (action == null) {
            deliver(false)
        } else {
            withContext(Dispatchers.Main.immediate) { action() }
        }
        return request.await()
    }

    fun deliver(granted: Boolean) {
        synchronized(guard) {
            pending?.complete(granted)
            pending = null
        }
    }
}
