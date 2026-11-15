# 项目开发约束

## 桌面端滚动区域

- 桌面端所有可能发生内容溢出并支持滚动的区域，都必须提供可见、可拖动且与内容共用同一滚动状态的滚动条。
- 垂直滚动区域使用垂直滚动条，水平滚动区域使用水平滚动条；仅在内容实际可滚动时显示。
- 新增或修改 `LazyColumn`、`LazyRow`、`LazyVerticalGrid`、`verticalScroll`、`horizontalScroll` 等滚动实现时，必须同步检查并实现桌面端滚动条。
- 移动端可以继续使用触摸手势，不强制显示滚动条；分页器等具有明确翻页语义的组件不视为普通滚动区域。

## Android FileProvider 路径安全

- `FileProvider` 的路径配置必须遵循最小权限原则，只允许公开业务实际需要共享的具体子目录。
- 禁止在 `<files-path>`、`<cache-path>`、`<external-files-path>` 等配置中使用 `path="."`，或以其他方式暴露内部文件、缓存、外部应用目录等特殊目录的根目录。
- 新增文件共享能力时，必须同步检查 `getUriForFile` 的目标位置，并在 `file_paths.xml` 中为该用途配置独立、范围最小的子目录白名单；数据库、凭据、模型及其他敏感文件所在目录不得加入白名单。

## 文件规模

- 项目文本文件默认不超过 500 行，包含空行与注释；检查范围为 Git 已跟踪文件及未被忽略的新增文件，跳过二进制文件、符号链接和已删除文件。
- 优先按职责拆分，不通过压缩代码、删除必要说明或修改忽略规则规避限制。
- 个别文件可在 `.codex/file-line-exceptions.json` 按精确相对路径登记 `reason` 和 `max_lines`；豁免仍有上限，禁止目录或通配豁免。存量豁免不意味着允许继续增长。
- 完成修改前运行 `python3 scripts/check_file_lines.py`。Codex 项目级 Stop hook 使用同一检查器；超限时要求继续修复。
- 首次启用或修改 `.codex/hooks.json` 后，在可信项目中通过 Codex CLI 的 `/hooks` 审阅并信任；未信任的 hook 不会执行。已有会话可能需要重新加载配置或重开。

## 程序发布

- 发版 agent 从根目录 `local.properties` 读取 `updaterPublishBaseUrl` 和 `updaterPublishToken`。前者为完整发布前缀 `https://zp.ntutn.top/api/v1/publish/kica`，后者为仅绑定软件 `kica` 的发布凭据，包含 R2 上传权限。到期时间记录在该文件的注释中，到期后在服务后台重新签发并替换。
- `local.properties` 已被 Git 忽略，文件权限设为 `0600`。凭据仅供发版客户端读取，禁止输出到日志、命令行参数、提交、文档或聊天，也不得加入安装包、资源文件或 `BuildConfig`。
- Android 签名发布使用 `./gradlew --no-configuration-cache :androidApp:verifyReleaseSigning :androidApp:assembleRelease`。发布前核对 APK 包名为 `top.ntutn.kica`、签名兼容性、实际版本和支持的 ABI，并查询线上版本，保证各目标的实际 `versionCode` 递增。软件包类型使用 `apk`，平台配对按实际 Android ABI 填写。
- Windows 安装包使用 `:desktopApp:packageReleaseExe`，Linux Debian 包使用 `:desktopApp:packageDeb`。每个文件单独作为软件包，分别使用 `exe` 或 `deb` 类型及实际系统和架构。各平台和包类型独立维护递增的数值 `versionCode`，`versionName` 使用实际发布版本。填写面向用户的纯文本更新说明。
- 通过 HTTP API 发版。以发布前缀下的 `POST /uploads` 登记文件名和字节数，直接 PUT 文件到返回的预签名 URL，再调用 `POST /uploads/{uploadId}/complete` 并确认状态为 `READY`。最后用 `POST /distributions` 一次提交全部包及更新说明，R2 包填写 `source=r2` 和 `uploadId`。保存成功后立即公开。
- 对本服务的请求使用 `Authorization: Bearer <token>`。R2 PUT 仅带返回的上传请求头。为每个上传登记和分发提交分别生成小写 UUID 作为 `Idempotency-Key`，在首次发送前保存键和请求内容。超时或结果未知时以原键、原内容重试，遵守 `Retry-After`。分发首次成功返回 `201`，原请求重放返回 `200`，同键不同内容返回 `409`。
- 完整字段、外链发布及错误处理见本仓库的 [发布 API 使用说明](docs/publish-api.html)。
