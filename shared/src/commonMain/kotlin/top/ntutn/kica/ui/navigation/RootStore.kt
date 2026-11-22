package top.ntutn.kica.ui.navigation

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import top.ntutn.kica.data.sha256
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.UserSession
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal data class RootState(
    val restoring: Boolean = true,
    val sessionKey: String? = null,
    val locked: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val authenticating: Boolean = false,
    val loggingOut: Boolean = false,
    val loginError: String? = null,
    val serviceError: String? = null,
)

/** Retained across Activity recreation. Contains no Activity or callbacks into component shells. */
internal class RootStore(private val services: AppServices) : InstanceKeeper.Instance {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(RootState())
    val state: StateFlow<RootState> = mutable
    private var backgroundSince = 0L
    private var loginGeneration = 0
    private var reconcileGeneration = 0
    private var loginJob: Job? = null
    private var retryJob: Job? = null
    private var closing = false
    private var destroyed = false
    private var settingsKnown = false
    private var acceptedSession: UserSession? = null
    private var fallbackSessionId = Uuid.random().toString()

    init {
        scope.launch {
            try {
                try {
                    applySettings(services.library.settings().first(), prepareTranslation = false)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { serviceFailure(failure, "Unable to read application settings") }
                try {
                    services.pica.restoreSession()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { serviceFailure(failure, "Unable to restore session") }
                try {
                    services.downloads.restore()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { serviceFailure(failure, "Unable to restore downloads") }
                reconcileSession()
            } finally {
                if (!destroyed) mutable.value = mutable.value.copy(restoring = false)
            }
            launch { services.pica.session.collectLatest { reconcileSession() } }
            // Recreate the Flow on retry too: settings() itself may fail before returning a Flow.
            flow { emitAll(services.library.settings()) }
                .retryWhen { failure, attempt ->
                    if (failure is CancellationException) throw failure
                    if (destroyed) return@retryWhen false
                    serviceFailure(failure, "Unable to observe application settings")
                    delay((500L * (attempt + 1).coerceAtMost(10)).coerceAtMost(5_000L))
                    true
                }
                .collectLatest { updated ->
                    applySettings(updated, prepareTranslation = true)
                    reconcileSession()
                }
        }
    }

    private suspend fun applySettings(updated: AppSettings, prepareTranslation: Boolean) {
        val previous = mutable.value.settings
        val firstKnownSettings = !settingsKnown
        settingsKnown = true
        mutable.value = mutable.value.copy(
            settings = updated,
            locked = when {
                !requiresLock(updated) -> false
                firstKnownSettings || !requiresLock(previous) -> true
                else -> mutable.value.locked
            },
        )
        var failure: String? = null
        try { services.pica.updateNetworkSettings(updated.network) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message ?: "Unable to apply network settings" }
        // Recover an authenticated tree as soon as its safety settings are known; model
        // preparation is optional and must never hold the settings-recovery boundary hostage.
        if (firstKnownSettings && !mutable.value.restoring) reconcileSession()
        if (prepareTranslation) {
            try {
                services.translation.updateNetworkSettings(updated.network)
                if (updated.titleTranslationEnabled) services.translation.enable() else services.translation.disable()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure = failure ?: error.message ?: "Unable to prepare title translation" }
        }
        if (!destroyed) mutable.value = mutable.value.copy(serviceError = failure)
    }

    private suspend fun reconcileSession() {
        if (closing || destroyed || !settingsKnown) return
        val request = ++reconcileGeneration
        val authGeneration = loginGeneration
        val session = services.pica.session.value
        if (session !== acceptedSession && mutable.value.sessionKey != null) {
            // Do not display the previous account while a new credential identity is being read.
            mutable.value = mutable.value.copy(sessionKey = null)
        }
        val key = try {
            session?.let {
                val marker = services.platform.credentialStore.readSessionId() ?: fallbackSessionId
                // Token-only restore does not know the email. The non-secret marker already
                // identifies one explicit login and is rotated with the credential on login.
                sha256(marker)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (isCurrent(request, authGeneration, session)) serviceFailure(failure, "Unable to verify session identity")
            return
        }
        // CredentialStore is a suspend interface. An old read must not resurrect logout, replace
        // a newer login, or attach a result to a different immutable UserSession instance.
        if (!isCurrent(request, authGeneration, session)) return
        acceptedSession = session
        if (key != mutable.value.sessionKey) mutable.value = mutable.value.copy(sessionKey = key)
    }

    private fun isCurrent(request: Int, authGeneration: Int, session: UserSession?): Boolean =
        !closing && !destroyed && settingsKnown && request == reconcileGeneration &&
            authGeneration == loginGeneration && session === services.pica.session.value

    private fun serviceFailure(failure: Throwable, fallback: String) {
        if (!destroyed) mutable.value = mutable.value.copy(serviceError = failure.message ?: fallback)
    }

    /** Explicit retry for a readable service-error UI; observers also retry local Flow failures. */
    fun retryServices() {
        if (closing || destroyed || mutable.value.restoring || mutable.value.authenticating || retryJob?.isActive == true) return
        val generation = loginGeneration
        retryJob = scope.launch {
            try {
                applySettings(services.library.settings().first(), prepareTranslation = false)
                if (closing || destroyed || generation != loginGeneration) return@launch
                if (services.pica.session.value == null) services.pica.restoreSession()
                reconcileSession()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { serviceFailure(failure, "Unable to retry application services") }
        }
    }

    fun login(email: String, password: String) {
        if (mutable.value.restoring || mutable.value.authenticating || closing || destroyed || !settingsKnown) return
        retryJob?.cancel()
        fallbackSessionId = Uuid.random().toString()
        val generation = ++loginGeneration
        reconcileGeneration++
        mutable.value = mutable.value.copy(authenticating = true, loginError = null)
        loginJob = scope.launch {
            try {
                services.pica.login(email, password)
                if (generation == loginGeneration) reconcileSession()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (generation == loginGeneration) mutable.value = mutable.value.copy(loginError = failure.message ?: "Login failed")
            } finally {
                if (generation == loginGeneration) mutable.value = mutable.value.copy(authenticating = false)
            }
        }
    }

    fun logout(session: SessionData?) {
        if (closing || destroyed) return
        closing = true
        loginGeneration++
        reconcileGeneration++
        acceptedSession = null
        loginJob?.cancel()
        retryJob?.cancel()
        // Invalidate first. This command and its write drain belong to this retained store, not
        // the Activity shell; recreation cannot cancel logout halfway through closing a session.
        mutable.value = mutable.value.copy(sessionKey = null, authenticating = false, loggingOut = true, loginError = null)
        scope.launch {
            try {
                session?.close()
                services.pica.logout()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                mutable.value = mutable.value.copy(loginError = failure.message ?: "Logout failed")
            } finally {
                closing = false
                mutable.value = mutable.value.copy(loggingOut = false)
            }
        }
    }

    fun unlock() { mutable.value = mutable.value.copy(locked = false) }
    fun background() { if (requiresLock(mutable.value.settings)) backgroundSince = Clock.System.now().toEpochMilliseconds() }
    fun foreground() {
        if (requiresLock(mutable.value.settings) && backgroundSince > 0 &&
            Clock.System.now().toEpochMilliseconds() - backgroundSince >= 3_000L
        ) mutable.value = mutable.value.copy(locked = true)
        backgroundSince = 0
    }
    override fun onDestroy() {
        destroyed = true
        loginGeneration++
        reconcileGeneration++
        scope.cancel()
    }

    private fun requiresLock(settings: AppSettings) = settings.lockEnabled &&
        (settings.lockPasswordHash != null || settings.lockPatternHash != null)
}
