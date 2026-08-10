# Third-Party Notices

Kica includes or links against third-party open-source software. This document
is a convenience summary and does not replace the license text distributed by
each project.

## Behavioral reference

### picacg-qt

- Project: <https://github.com/tonquer/picacg-qt>
- Pinned revision: `5a736b8235c93d580cf03f44d15cfb6501f47098`
- License: GNU Lesser General Public License v3.0

Kica reimplements portions of its observable PicACG client behavior and
protocol compatibility. See `UPSTREAM.md` for the reviewed scope.

## Runtime and build dependencies

| Component | License |
| --- | --- |
| Kotlin and Kotlin Gradle Plugin | Apache License 2.0 |
| Kotlin Coroutines, Serialization, and DateTime | Apache License 2.0 |
| Compose Multiplatform | Apache License 2.0 |
| Compose Fluent UI | Apache License 2.0 |
| AndroidX Activity and WorkManager | Apache License 2.0 |
| Retrofit | Apache License 2.0 |
| OkHttp and MockWebServer | Apache License 2.0 |
| kotlinx.serialization Retrofit converter | Apache License 2.0 |
| SQLDelight | Apache License 2.0 |
| Llamatik 1.9.1 | MIT License (repository LICENSE; published POM identifies Apache License 2.0) |
| Qwen2.5-0.5B-Instruct model | Apache License 2.0 |
| Coil | Apache License 2.0 |
| FileKit | MIT License |
| Java Native Access (JNA) | LGPL-2.1-or-later or Apache License 2.0 |
| SLF4J | MIT License |
| Logback | Eclipse Public License 1.0 or GNU Lesser General Public License 2.1 |

Dependencies can bring additional transitive components. Refer to Gradle
dependency metadata and the corresponding upstream distributions for complete
copyright statements and license texts.

Compose Fluent UI: <https://github.com/compose-fluent/compose-fluent-ui>

Kotlin: <https://github.com/JetBrains/kotlin>

Compose Multiplatform: <https://github.com/JetBrains/compose-multiplatform>

AndroidX: <https://source.android.com/docs/setup/about/licenses>

Retrofit and OkHttp: <https://github.com/square/retrofit> and
<https://github.com/square/okhttp>

SQLDelight: <https://github.com/sqldelight/sqldelight>

Llamatik: <https://github.com/ferranpons/Llamatik> (tag `1.9.1`)

Qwen2.5-0.5B-Instruct: <https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct>

Coil: <https://github.com/coil-kt/coil>

FileKit: <https://github.com/vinceglb/FileKit>

JNA: <https://github.com/java-native-access/jna>

SLF4J: <https://www.slf4j.org/>

Logback: <https://logback.qos.ch/>
