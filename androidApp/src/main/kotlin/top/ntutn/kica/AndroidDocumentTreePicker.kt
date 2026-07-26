package top.ntutn.kica

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidDocumentTreePicker {
    private val guard = Any()
    private var launch: (() -> Unit)? = null
    private var pending: CompletableDeferred<String?>? = null

    fun bind(launch: () -> Unit) {
        synchronized(guard) {
            this.launch = launch
        }
    }

    fun unbind() {
        synchronized(guard) {
            launch = null
            pending?.complete(null)
            pending = null
        }
    }

    suspend fun choose(): String? {
        val request = CompletableDeferred<String?>()
        val action = synchronized(guard) {
            pending?.complete(null)
            pending = request
            launch
        }
        if (action == null) {
            deliver(null)
        } else {
            withContext(Dispatchers.Main.immediate) { action() }
        }
        return request.await()
    }

    fun deliver(uri: String?) {
        synchronized(guard) {
            pending?.complete(uri)
            pending = null
        }
    }
}
