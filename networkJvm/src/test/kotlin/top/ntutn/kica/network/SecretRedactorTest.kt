package top.ntutn.kica.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretRedactorTest {
    @Test
    fun redactsHeaderJsonAndQuerySecrets() {
        val input =
            """Authorization: Bearer abc.def
               |{"email":"reader@example.com","password":"open-sesame","token":"secret","signature":"signed","api-key":"public-protocol-key"}
               |proxy_password=hunter2&safe=value
            """.trimMargin()

        val redacted = SecretRedactor.redact(input)

        listOf("abc.def", "reader", "open-sesame", "secret", "signed", "public-protocol-key", "hunter2").forEach {
            assertFalse(redacted.contains(it))
        }
        assertTrue(redacted.contains("@example.com"))
        assertTrue(redacted.contains("safe=value"))
        assertTrue(redacted.contains("***REDACTED***"))
    }
}
