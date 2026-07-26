# Contributing

Thank you for helping improve Kica.

## Development rules

- Use JDK 17 and the checked-in Gradle wrapper.
- Keep the root package and all platform identifiers under `top.ntutn.kica`.
- Keep repository documentation, release notes, KDoc, and code comments in
  English.
- Put user-facing strings in Compose resources. The initial application locale
  is Simplified Chinese.
- Keep wire DTOs and protocol handling in `networkJvm`.
- Never commit credentials, tokens, proxy passwords, signing materials,
  `local.properties`, or downloaded content.
- Do not reuse upstream branding or artwork.

## Before opening a change

Run:

```bash
./gradlew :shared:desktopTest :networkJvm:test :androidApp:testDebugUnitTest
./gradlew :androidApp:lintDebug
./gradlew :desktopApp:compileKotlin :androidApp:compileDebugKotlin
```

Add focused tests for signing, mapping, pagination, persistence, reader
progress, download transitions, retries, cancellation, and redaction whenever
those areas change.

## Commits and pull requests

Use concise imperative commit messages. Explain user-visible behavior,
platform differences, verification performed, and any known limitations in the
pull request. Keep unrelated refactors separate.

