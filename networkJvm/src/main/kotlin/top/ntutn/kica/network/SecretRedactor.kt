package top.ntutn.kica.network

object SecretRedactor {
    const val REDACTED = "***REDACTED***"

    private val tokenPatterns = listOf(
        Regex("""(?i)(authorization\s*[:=]\s*)(?:Bearer\s+)?[A-Za-z0-9._~+/=-]+"""),
        Regex("""(?i)("(?:api-key|password|token|signature)"\s*:\s*")[^"]*(")"""),
        Regex("""(?i)((?:api-key|password|token|signature)\s*=\s*)[^\s&,]+"""),
        Regex("""(?i)("email"\s*:\s*")[^"@]*(@[^"]*")"""),
    )

    fun redact(value: String): String =
        tokenPatterns.fold(value) { current, pattern ->
            pattern.replace(current) { match ->
                match.groupValues.getOrNull(1).orEmpty() + REDACTED +
                    match.groupValues.getOrNull(2).orEmpty()
            }
        }
}
