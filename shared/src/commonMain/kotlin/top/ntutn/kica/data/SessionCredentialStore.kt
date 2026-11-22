package top.ntutn.kica.data

class SessionCredentialStore : CredentialStore {
    private var token: String? = null
    private var sessionId: String? = null
    override suspend fun readSessionId(): String? = sessionId

    override suspend fun readToken(): String? = token

    override suspend fun writeToken(token: String) {
        this.token = token
        sessionId = kotlin.uuid.Uuid.random().toString()
    }

    override suspend fun clearToken() {
        token = null
        sessionId = null
    }
}

