package top.ntutn.kica.ui.store

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.cancellableResult

internal data class TagActionState(val busy: Boolean = false, val version: Int = 0, val error: String? = null)

/** Page-owned writes continue when a temporary tag dialog leaves composition. */
internal class TagActionStore(private val library: LibraryRepository) : PageStore() {
    private val mutable = MutableStateFlow(TagActionState())
    val state = mutable.asStateFlow()
    override fun ensureLoaded() = Unit
    fun update(transform: (Set<String>) -> Set<String>) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        scope.launch {
            try {
                cancellableResult { library.updateBlockedTags(transform) }.fold({
                    mutable.value = mutable.value.copy(version = mutable.value.version + 1)
                }, { mutable.value = mutable.value.copy(error = it.message ?: "Tag update failed") })
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}

internal val LocalTagActions = staticCompositionLocalOf<TagActionStore?> { null }
