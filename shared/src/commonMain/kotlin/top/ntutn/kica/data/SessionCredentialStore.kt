package top.ntutn.kica.data

class SessionCredentialStore : CredentialStore {
    private var token: String? = null

    override suspend fun readToken(): String? = token

    override suspend fun writeToken(token: String) {
        this.token = token
    }

    override suspend fun clearToken() {
        token = null
    }
}

