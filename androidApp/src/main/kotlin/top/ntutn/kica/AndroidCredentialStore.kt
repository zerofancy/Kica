package top.ntutn.kica

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import top.ntutn.kica.data.CredentialStore

internal class AndroidCredentialStore(context: Context) : CredentialStore {
    private val preferences = context.getSharedPreferences("secure-session", Context.MODE_PRIVATE)

    override suspend fun readToken(): String? {
        val encoded = preferences.getString(TOKEN, null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            val ivSize = payload.first().toInt() and 0xff
            val iv = payload.copyOfRange(1, ivSize + 1)
            val ciphertext = payload.copyOfRange(ivSize + 1, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).decodeToString()
        }.getOrNull()
    }

    override suspend fun writeToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(token.encodeToByteArray())
        val payload = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + ciphertext
        preferences.edit().putString(TOKEN, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    override suspend fun clearToken() {
        preferences.edit().remove(TOKEN).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "top.ntutn.kica.session"
        const val TOKEN = "token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

