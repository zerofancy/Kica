# Kica

Kica is a Fluent-style, cross-platform PicACG client built with Kotlin and
Compose Multiplatform. It shares its UI, navigation, domain models, state
management, and SQLite persistence between Android and desktop while keeping
networking and platform integrations in focused modules.

The application ID, Android namespace, Gradle group, and root Kotlin package are
all `top.ntutn.kica`.

> [!IMPORTANT]
> Kica is an unofficial client created for technical research and personal use.
> It is not affiliated with, endorsed by, or operated by PicACG. Users are
> responsible for complying with applicable law, service terms, and content
> restrictions. No account, content, or service availability is provided by
> this project.

## Features

- Login with secure token storage; passwords are never persisted
- Recommendations, random comics, categories, rankings, search, and favorites
- Comic details, episodes, favorite and like actions
- History and per-episode reading progress
- Vertical, paged left-to-right, paged right-to-left, and desktop double-page
  reading modes with zoom
- Persistent chapter downloads with retry, pause, resume, cancellation, and
  offline files
- Responsive navigation:
  - bottom navigation on phones
  - navigation rail on tablets
  - Fluent-style left navigation on desktop
- Light and dark Fluent themes
- Direct, system, HTTP, and SOCKS5 proxy modes in the networking core
- Android background downloads through WorkManager and desktop download resume

The first release intentionally excludes registration, password recovery,
comments, chat, games, NAS/WebDAV/SMB, local comic libraries, format conversion,
and image enhancement.

## Project layout

| Module | Responsibility |
| --- | --- |
| `shared` | Compose UI, navigation, models, repositories, download state, and SQLDelight database |
| `networkJvm` | Retrofit/OkHttp service, request signing, DTO mapping, proxy support, and file downloads |
| `androidApp` | Android entry point, Keystore credentials, WorkManager, notifications, and Android storage |
| `desktopApp` | Compose Desktop entry point, OS credential integration, downloads, and native packaging |

## Requirements

- JDK 17
- Android Studio or Android SDK command-line tools
- Android SDK Platform 36 and Build Tools compatible with AGP 9.1.1
- Windows x64 or Linux x64 for first-tier desktop builds

The shared desktop sources remain compatible with macOS, but macOS packaging is
not part of the supported release or CI matrix.

## Build

Use the checked-in Gradle 9.3.1 wrapper:

```bash
./gradlew build
```

On Windows PowerShell:

```powershell
.\gradlew.bat build
```

If the Android SDK is not discovered automatically, create an untracked
`local.properties` file:

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
```

## Run

Run the desktop application:

```bash
./gradlew :desktopApp:run
```

Install the Android debug build on a connected device:

```bash
./gradlew :androidApp:installDebug
```

## Logging

The desktop application uses SLF4J with Logback and writes INFO-level logs to
the console. HTTP request summaries and API errors are logged by the networking
module. Email usernames, passwords, tokens, API keys, authorization headers,
signatures, cookies, and proxy authorization values are redacted.

## Test

Run JVM and multiplatform unit tests:

```bash
./gradlew :shared:desktopTest :networkJvm:test :androidApp:testDebugUnitTest
```

Run Android lint:

```bash
./gradlew :androidApp:lintDebug
```

Real-service tests are not enabled by default and must never embed credentials.

## Package

Create a Windows EXE on Windows:

```powershell
.\gradlew.bat :desktopApp:packageExe
```

Create a Debian package on Linux:

```bash
./gradlew :desktopApp:packageDeb
```

Create Android debug and unsigned release artifacts:

```bash
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:bundleRelease
```

Release APK/AAB files are intentionally unsigned. Supply signing configuration
outside the repository for production distribution.

## Security

- Passwords are used only for the login request and are not saved.
- Tokens use Android Keystore on Android and an OS credential service on
  desktop. When no desktop credential service is available, the session remains
  process-local.
- Logs and surfaced protocol errors redact email usernames, API keys,
  authorization, passwords, tokens, cookies, proxy authorization, and
  signature values.
- Accounts, test credentials, proxy credentials, signing keys, certificates,
  and `local.properties` must not be committed.

## Upstream and license

Kica is licensed under the GNU Lesser General Public License v3.0. See
[`LICENSE`](LICENSE), [`UPSTREAM.md`](UPSTREAM.md), and
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
