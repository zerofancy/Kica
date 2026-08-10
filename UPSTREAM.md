# Upstream References

Kica is a clean Kotlin/Compose implementation informed by two pinned upstream
repositories.

## Visual reference

- Repository: <https://github.com/zerofancy/KZAgent>
- Pinned commit: `b39fc5344ae1dae210b75e1109c125fb551697f6`
- Scope reviewed:
  - `src/main/kotlin/com/kzagent/kagent/desktop/DesktopApp.kt`
  - `src/main/kotlin/com/kzagent/kagent/desktop/KZAgentFluentTheme.kt`
  - `src/main/kotlin/com/kzagent/kagent/desktop/SettingsPanel.kt`
  - `src/test/kotlin/com/kzagent/kagent/desktop/NavigationViewLayoutTest.kt`

KZAgent was used only as a high-level visual and interaction reference for the
Fluent color treatment, desktop navigation proportions, surface hierarchy, and
responsive layout. Kica does not copy KZAgent branding, icons, or application
source. The pinned KZAgent tree does not contain a repository license file, so
no KZAgent code or assets are redistributed.

## Behavioral reference

- Repository: <https://github.com/tonquer/picacg-qt>
- Pinned commit: `5a736b8235c93d580cf03f44d15cfb6501f47098`
- Upstream license: GNU Lesser General Public License v3.0
- Scope reviewed:
  - `src/server/req.py` and related request code for API paths, headers, and
    protocol-compatible signing behavior
  - `src/server/server.py` and `src/server/res.py` for response behavior
  - `src/view/user/login_view.py`, `favorite_view.py`, and `history_view.py`
  - `src/view/info/book_info_view.py` and `book_eps_view.py`
  - reader views and `src/task/task_download.py`
  - settings and proxy behavior under `src/config` and `src/view/setting`

Kica reimplements the core login, browsing, search, detail, reader, favorites,
history, and download flows in Kotlin. Protocol compatibility constants and
observable request behavior are retained where required to interoperate with
the service. The implementation, UI architecture, persistence layer, platform
services, and project assets are otherwise original.

## Excluded scope

The following upstream areas are intentionally not ported in the core release:

- registration and password recovery
- comments, chat, and games
- NAS, WebDAV, and SMB integrations
- local comic library management
- archive or ebook conversion
- super-resolution and image enhancement

Future upstream changes should be isolated in `networkJvm`; shared domain and UI
code should not depend on wire DTOs.

## Local inference dependency

- Repository: <https://github.com/ferranpons/Llamatik>
- Version: `1.9.1`
- Scope: local GGUF model loading, chat-template application, and constrained
  JSON generation for optional Simplified Chinese work-title translation.
