package top.ntutn.kica.data

import java.security.MessageDigest

internal actual fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
