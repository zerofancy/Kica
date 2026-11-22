package top.ntutn.kica.ui.navigation

import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.arkivanov.essenty.statekeeper.StateKeeper
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One versioned, bounded source for the complete Decompose navigation / business snapshots. */
internal class NavigationStateKeeper(host: StateKeeper) {
    val dispatcher = restoreOrNull {
        val restored = host.consume(KEY, NavigationSnapshotV1.serializer())?.takeIf { it.schema == 1 }
            ?.data?.takeIf { it.encodeToByteArray().size <= MAX_BYTES }
            ?.let { Json.decodeFromString(SerializableContainer.serializer(), it) }
        // SerializableContainer only base64-decodes eagerly; decompression/type decoding
        // happens when the dispatcher consumes it and belongs inside the recovery boundary.
        StateKeeperDispatcher(restored)
    } ?: StateKeeperDispatcher()
    val safeStateKeeper: StateKeeper = RestoringStateKeeper(dispatcher)

    init {
        host.register(KEY, NavigationSnapshotV1.serializer()) {
            var encoded = encode()
            if (encoded.encodeToByteArray().size > MAX_BYTES) {
                encoded = SnapshotPolicy.withoutOptional { encode() }
            }
            NavigationSnapshotV1(data = encoded.takeIf { it.encodeToByteArray().size <= MAX_BYTES })
        }
    }

    private fun encode() = Json.encodeToString(SerializableContainer.serializer(), dispatcher.save())

    companion object {
        const val MAX_BYTES = 256 * 1024
        private const val KEY = "kica-navigation-v1"
    }
}

@Serializable
internal data class NavigationSnapshotV1(val schema: Int = 1, val data: String? = null)
