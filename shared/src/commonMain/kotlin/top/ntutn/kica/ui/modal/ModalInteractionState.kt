package top.ntutn.kica.ui.modal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/** Identity of a feature's related modal windows, not a route or an account identity. */
internal class ModalOwner

/** UI-wide transient interactions. No knowledge of update checks or other business features. */
internal class ModalInteractionState {
    private val registrations = mutableStateMapOf<Any, ModalOwner?>()

    val activeCount: Int get() = registrations.size
    val hasActiveInteractions: Boolean get() = registrations.isNotEmpty()

    /** A feature may observe other modals without being blocked by its own window. */
    fun hasOtherInteractions(owner: ModalOwner): Boolean = registrations.values.any { it !== owner }

    fun register(owner: ModalOwner? = null): Registration {
        val token = Any()
        registrations[token] = owner
        return Registration { registrations.remove(token) }
    }

    /** Independent, idempotent leases avoid counter drift on nested/reordered disposal. */
    class Registration internal constructor(private var release: (() -> Unit)?) {
        fun dispose() {
            val action = release ?: return
            release = null
            action()
        }
    }
}

internal val LocalModalInteractionState = staticCompositionLocalOf<ModalInteractionState?> { null }
internal val LocalModalOwner = staticCompositionLocalOf<ModalOwner?> { null }

@Composable
internal fun rememberModalInteractionState(): ModalInteractionState = remember { ModalInteractionState() }

/** Used only by modal window wrappers; ordinary screens never register interactions manually. */
@Composable
internal fun ModalInteraction(
    owner: ModalOwner? = LocalModalOwner.current,
    content: @Composable () -> Unit,
) {
    val state = LocalModalInteractionState.current
    DisposableEffect(state, owner) {
        val registration = state?.register(owner)
        onDispose { registration?.dispose() }
    }
    CompositionLocalProvider(LocalModalOwner provides owner, content = content)
}
