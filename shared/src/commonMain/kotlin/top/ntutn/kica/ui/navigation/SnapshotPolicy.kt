package top.ntutn.kica.ui.navigation

/**
 * Snapshot suppliers are evaluated synchronously on the UI thread. A second budget pass may omit
 * draft-only fields, but must never mutate live state or truncate submitted query/navigation data.
 * The flag is restored even if a supplier fails; it is not a runtime page eviction policy.
 */
internal object SnapshotPolicy {
    var includeOptional: Boolean = true
        private set

    fun <T> withoutOptional(block: () -> T): T {
        val previous = includeOptional
        includeOptional = false
        return try { block() } finally { includeOptional = previous }
    }
}
