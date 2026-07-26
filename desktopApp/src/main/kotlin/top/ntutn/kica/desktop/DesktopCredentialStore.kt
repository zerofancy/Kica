package top.ntutn.kica.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.util.Base64
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.ntutn.kica.data.CredentialStore

internal class DesktopCredentialStore : CredentialStore {
    private val osName = System.getProperty("os.name").lowercase()
    private val preferences = Preferences.userRoot().node("top/ntutn/kica")
    private var sessionFallback: String? = null

    override suspend fun readToken(): String? = withContext(Dispatchers.IO) {
        runCatching {
            when {
                osName.contains("windows") -> readWindows()
                osName.contains("linux") -> readSecretTool()
                osName.contains("mac") -> readMacKeychain()
                else -> null
            }
        }.getOrNull() ?: sessionFallback
    }

    override suspend fun writeToken(token: String) {
        sessionFallback = token
        withContext(Dispatchers.IO) {
            runCatching {
                when {
                    osName.contains("windows") -> writeWindows(token)
                    osName.contains("linux") -> writeSecretTool(token)
                    osName.contains("mac") -> writeMacKeychain(token)
                }
            }
        }
    }

    override suspend fun clearToken() {
        sessionFallback = null
        withContext(Dispatchers.IO) {
            runCatching {
                when {
                    osName.contains("windows") -> preferences.remove(WINDOWS_TOKEN)
                    osName.contains("linux") -> {
                        ProcessBuilder("secret-tool", "clear", "application", SERVICE).start().waitFor()
                    }
                    osName.contains("mac") -> {
                        ProcessBuilder(
                            "/usr/bin/security",
                            "delete-generic-password",
                            "-s",
                            SERVICE,
                            "-a",
                            ACCOUNT,
                        ).start().waitFor()
                    }
                }
            }
        }
    }

    private fun readWindows(): String? {
        val encoded = preferences.get(WINDOWS_TOKEN, null) ?: return null
        return Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(encoded)).decodeToString()
    }

    private fun writeWindows(token: String) {
        val encrypted = Crypt32Util.cryptProtectData(token.encodeToByteArray())
        preferences.put(WINDOWS_TOKEN, Base64.getEncoder().encodeToString(encrypted))
        preferences.flush()
    }

    private fun readSecretTool(): String? {
        val process = ProcessBuilder("secret-tool", "lookup", "application", SERVICE).start()
        return process.inputStream.bufferedReader().readText().trim()
            .takeIf { process.waitFor() == 0 && it.isNotBlank() }
    }

    private fun writeSecretTool(token: String) {
        val process = ProcessBuilder(
            "secret-tool",
            "store",
            "--label=Kica session",
            "application",
            SERVICE,
        ).start()
        process.outputStream.bufferedWriter().use { it.write(token) }
        check(process.waitFor() == 0) { "The system credential store rejected the token." }
    }

    private fun readMacKeychain(): String? {
        val process = ProcessBuilder(
            "/usr/bin/security",
            "find-generic-password",
            "-s",
            SERVICE,
            "-a",
            ACCOUNT,
            "-w",
        ).start()
        return process.inputStream.bufferedReader().readText().trim()
            .takeIf { process.waitFor() == 0 && it.isNotBlank() }
    }

    private fun writeMacKeychain(token: String) {
        val process = ProcessBuilder(
            "/usr/bin/security",
            "add-generic-password",
            "-U",
            "-s",
            SERVICE,
            "-a",
            ACCOUNT,
            "-w",
            token,
        ).start()
        check(process.waitFor() == 0) { "The system credential store rejected the token." }
    }

    private companion object {
        const val SERVICE = "top.ntutn.kica"
        const val ACCOUNT = "session"
        const val WINDOWS_TOKEN = "session-token-dpapi"
    }
}

