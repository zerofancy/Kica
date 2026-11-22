# Kica 自动更新接入 Spec

- 状态：已实现并通过本地构建/自动测试；真实设备安装及 Windows 发行验证待后续。本次不上传、不发布。
- 调研日期：2026-11-22（Asia/Shanghai）。线上查询结果仅代表调研时快照。
- 目标服务：https://zp.ntutn.top；软件内部名：`kica`。
- 推荐 SDK：`com.github.zerofancy.kmp-updater:kmp-updater:v0.3.0`。
- 本文的“自动更新”指自动检测、提示和用户确认后的下载/安装，不是静默覆盖安装。

## 1. 调研结论

### 1.1 接入前项目基线

最初调研时，依赖、应用入口和设置页面中未发现应用更新 SDK、版本查询或安装流程。
已有程序发布约束不等于已有客户端更新能力。

| 项目 | 当前事实 | 接入影响 |
| --- | --- | --- |
| 平台 | shared 为 Android + desktop JVM | 覆盖 Android、Windows、Linux；不新增 iOS/macOS 发行支持 |
| UI | Compose Multiplatform + Fluent | 只接核心 SDK，使用 Fluent 自建更新 UI，不引入 Material 3 默认弹窗 |
| Android | minSdk 26、compile/targetSdk 37，JVM 17 | 满足 SDK minSdk 24、字节码 11 要求 |
| 桌面 | JVM/JDK 21；打包 EXE、DEB | 无桌面数值更新版本及安装来源元数据，必须补齐 |
| 版本 | Android versionCode=3、versionName=0.1.3；桌面 packageVersion=0.1.3 | 不能从 SemVer 猜数值版本，不能在运行时抄线上版本作为本地版本 |
| 依赖 | Kotlin 2.4.21、Compose 1.12.2、coroutines 1.11.0 | 接近 SDK 验证基线，但仍须真实 Android/JVM 消费验证 |
| 仓库 | JitPack 仅允许 Fluent 依赖组 | 需额外允许 SDK 的完整依赖组，插件仓库不能代替库仓库 |
| 导航 | Decompose Root/InstanceKeeper；前后台及应用锁 gate | 更新状态放根级，不绑定登录会话和单个设置页面 |
| 文件共享 | 已有独立 downloads/shared_images 白名单 | 不扩大已有 Provider；复核 SDK 合并后的独立 Provider |

### 1.2 服务只读实测

以下均为匿名 GET，未读取发布凭据，未下载完整安装包，也未做任何服务端写操作。

| 精确更新序列 | HTTP / 结果 | 备注 |
| --- | --- | --- |
| Android / ARM64 / apk | 200；code=3，name=0.1.3 | 声明 SHA-512，大小 141107385 字节 |
| Android / X86_64 / apk | 200；code=3，name=0.1.3 | 与 ARM64 指向相同通用 APK；未验证其真实 ABI 内容 |
| Linux / X86_64 / deb | 200；code=3，name=0.1.3 | 声明 SHA-512，大小 106288404 字节 |
| Linux / ARM64 / deb | 404 NO_MATCHING_PACKAGE | 不得回退到 X86_64 |
| Windows / X86_64 / exe | 404 NO_MATCHING_PACKAGE | 不得当作已是最新版；上线验收前需补发对应包 |

服务端从历史包中选择最大数值 versionCode；无通道、强更、最低系统和签名兼容性字段。
不传本地版本，比较由客户端完成；versionCode 是正数 Long 范围的十进制字符串。
404 无包、409 最高版本歧义、配置/网络错误都不等价于“无需更新”。

### 1.3 SDK 核验与边界

- v0.3.0 标签对应源码提交 `3d6ea89f5a6b0b2aa2561551ae6da0b0a103832d`。
- JitPack 构建状态 API 返回 `status=ok`，核心 POM 可访问且坐标正确；这不代替项目内编译验证。
- SDK 已有精确查询、Long 版本比较、安全 URL 检查、流式下载、摘要校验、取消和临时文件清理。
- Android 已有完整单 APK 安装交接、未知来源设置入口；桌面不负责 EXE/DEB 的覆盖安装。
- 架构为 ARM64/X86_64，32 位 Android 不支持。自动探测面向进程，不应按 CPU 能力强行升级架构。
- 查询默认 10 秒，无自动重试/重定向；下载默认连接 15 秒、读空闲 60 秒、上限 4 GiB。
- 不提供周期调度、持久忽略版本、后台保活、断点续传或静默安装。
- **源码差异**：当前 main README 声称安装会检查 APK 签名兼容性，但 v0.3.0 的
  `prepareAndDispatch` 只检查包名和更高版本，未做签名预检。系统安装器仍会最终拒绝不兼容签名。
  本项目不增加宿主签名预检，签名兼容性统一交由 Android 系统安装器判定；不得宣称客户端已验证签名。

## 2. 范围与推荐决策

### 本期必须交付

1. Release 启动时静默检测一次，发现更新后在安全时机提示。
2. 设置“关于”区域显示当前版本、检查更新入口和启动检查开关。
3. Android 应用内下载、进度/取消/重试、校验、用户主动交接系统安装器。
4. 非 Android 平台仅下载：完成后将安装包移入系统下载目录，提示手动安装；提供“退出并手动安装”和“稍后安装”，仅前者正常退出当前应用，不执行安装包。保留浏览器下载回退。
5. 完整错误语义、生命周期、隐私日志和平台测试。

### 不在本期

- 无人值守安装、自动提权、关闭应用后运行安装脚本、替换正在运行的桌面文件。
- 周期后台轮询、通知栏下载/WorkManager 保活、跨进程续传。
- 灰度/通道、强制更新、忽略某版本的持久规则、任意服务器配置 UI。
- 改造发版 API、自动发版、读取或注入发布凭据。

## 3. 配置与版本单一来源

```kotlin
// 示意：currentVersion 与 target/packageType 必须来自当前安装产物。
UpdaterConfig(
    baseUrl = "https://zp.ntutn.top", // 不能使用 /api 或 /api/v1/updates 子路径
    software = "kica",
    currentVersion = UpdateVersion(localVersionCode, localVersionName),
    target = installedTarget,
    packageType = installedPackageType,
)
```

SDK 自行拼接 `/api/v1/updates`。生产禁止开启 allowInsecureHttp。
所有查询都传四个维度，禁止宽查询后取第一项，禁止跨架构/包类型降级。

### 3.1 构建元数据

- 增加根级非敏感 release 元数据，维护 versionName 和各更新序列的正数 versionCode。
- 初次迁移：Android 和 Linux X86_64/deb 的本地 0.1.3 映射为 3；下一次实际发布必须大于线上最大值。
- 已由用户确认：Windows 当前 0.1.3 的本地 versionCode 初始化为 3，后续同序列递增；实际发布前仍须查询线上最大值。
- Android 从 PackageManager 读取实际已安装 versionCode/versionName；Gradle 的打包值由上述元数据生成。
- 桌面生成不可变 Kotlin/资源元数据：versionCode、versionName、os、arch、packageType、releaseBuild。
  nativeDistributions.packageVersion 从同一 versionName 派生，满足 jpackage 格式约束。
- 当前 EXE/DEB 构建任务分别显式注入 exe/deb；交叉构建/模拟运行使用构建产物声明架构，
  与进程探测明显不一致时停止检查并提示安装来源异常，不能查询另一条序列。
- 开发运行、来源未知的便携目录以及 Debug 默认禁用生产自动检测和安装；可显式启用隔离测试配置。
- 构建只接触非敏感元数据；发布 Token 不进入 BuildConfig、资源、生成源码、任务输出或包体。

### 3.2 目标矩阵

| 安装产物 | os | arch | packageType | 支持方式 |
| --- | --- | --- | --- | --- |
| Android 64 位 APK | Android | ARM64 / X86_64 | apk | 使用 SDK 探测，并与实际打包 ABI 核对 |
| Windows EXE | Windows | 实际构建架构 | exe | 不允许将 ZIP/MSI 安装来源混入该序列 |
| Linux DEB | Linux | 实际构建架构 | deb | 不允许替 AppImage/RPM 检查更新 |
| 32 位/未知/未发行平台 | — | — | — | 显示暂不支持，不发宽查询 |

## 4. 依赖与职责

仅引入核心坐标，锁定 `v0.3.0`，不使用动态版本或 main 分支。
在 dependencyResolutionManagement 的 JitPack content 中增加
`includeGroup("com.github.zerofancy.kmp-updater")`，保留原有依赖范围。
不引入 updater-compose：避免 Material UI 混用，并自行满足滚动条和返回键规范。

拟议拆分（路径为计划，不表示文件已经存在）：

```text
shared/.../update/UpdateService.kt          SDK 适配、领域状态、精确查询
shared/.../update/UpdateStore.kt            手动/静默触发、去重、节流与任务生命周期
shared/.../ui/update/UpdateHost.kt          根级 Fluent 弹窗与入口
shared/.../ui/update/UpdateDownloadUi.kt    下载/校验/完成状态
androidApp/.../AndroidUpdateRuntime.kt     applicationContext、实际本地版本、安装交接
desktopApp/.../DesktopUpdateRuntime.kt    生成元数据、下载目录及目录打开
```

- AppServices 增加可注入的更新服务接口；预览和测试使用 fake/no-op。
- 根级 UpdateStore 通过 InstanceKeeper 保留，不属于 MainComponent 登录 session。
- Android 适配器长期只保留 applicationContext；Activity 仅在当前前台用户动作中传入，不入容器。
- 桌面 Window 也不入共享容器；打开目录由当前平台入口提供短生命周期回调。
- SDK 使用独立匿名网络客户端，不复用漫画业务认证、Cookie、请求签名、详细 HTTP 日志拦截器。
- 本期更新网络走独立默认网络策略，不继承设置里仅面向业务的自定义代理；UI 说明这一边界。
- checker/downloader 由平台 runtime 拥有。最终销毁取消 Store scope，close checker/downloader，
  协程中 awaitIdle 后再关闭自行注入的客户端；不得阻塞主线程或因页面退出关闭 runtime。

## 5. 检测与呈现

### 5.1 自动检测策略

- Release 默认开启“启动时检查更新”；开关使用独立非账号偏好存储，无须变更业务数据库 schema。
- 每个运行进程最多一次自动检查：完成恢复、首次进入前台且未锁定时触发；无须等待业务登录。
- Activity 重建、登录/退出、窗口失焦恢复均不重复自动检查；进程重启允许再次检查。
- 自动检查不显示加载、无更新、无包和失败弹窗；只更新内部结果并在可呈现时提示新版本。
- 阅读器、其他模态交互、锁屏、恢复中和后台时不新弹窗；保留结果，回到非阅读的前台安全界面再提示。
- 手动检查立即显示反馈。重入合并，同一静默请求被手动触发时提升反馈，不另开请求。
- 关闭提示只记住本进程已关闭的 release 标识，避免同进程重复打扰；手动检查仍可再次展示。
- 检查不自动重试；429 遵守 retryAt，冷却中禁用重试并显示剩余等待。失败可由用户再次发起。

### 5.2 UI 与错误映射

设置的“关于”卡片显示版本名及数值 code、检测入口、自动检查开关；下载进行时也可重新打开进度。
已有已下载的待安装更新时，入口显示“查看已下载的更新”，恢复交接界面而不再查询，避免丢失已下载文件的操作入口。

| 结果 | 手动反馈 | 自动反馈 |
| --- | --- | --- |
| Available | 更新对话框 | 安全时机显示更新对话框 |
| UpToDate（含本地高于线上） | 当前无需更新 | 不弹窗 |
| NoMatchingPackage | 暂无适用于此安装包的更新，展示平台/架构/类型 | 不弹窗，不伪装成功 |
| 软件不存在/400 | 更新配置异常 | 不弹窗，保留错误分类 |
| 409 AmbiguousLatest | 发布数据冲突，请稍后重试或联系维护者 | 不弹窗 |
| 网络/超时/5xx | 检查失败，可重试 | 不弹窗 |
| 不安全 URL/响应非法 | 更新信息异常，不启用下载/安装 | 不弹窗 |

对话框展示当前/最新版本、平台、文件大小（未知则明示）、发布时间和更新说明。
说明优先纯文本，缺失时 Markdown 只原样作为文本，禁止 HTML、外部图片及自动执行链接。
摘要缺失时明确“发布方未提供校验和”，不得标为“已验证”。

所有长说明/长错误/地址的桌面滚动区域必须有可见可拖动且共用状态的对应滚动条，
仅内容实际可滚动时显示；说明区域限制高度，不撑出屏幕。
Android 返回/手势和桌面 Esc 使用 PlatformBackHandler：最上层临时交互优先，再页面导航。
更新 UI 不抢过应用锁；锁定立即隐藏更新 UI，解锁后可恢复。

## 6. 下载与桌面交接

- 用户点击下载后才执行；提供浏览器下载回退，但不在应用内失败后自动打开外部应用。
- 下载状态独立于检测：Idle → Downloading → Finalizing → Completed / Failed；取消回到 Idle。
- 已知大小显示百分比及字节数；未知大小显示已下载字节和不确定进度；校验/落盘时显示“正在校验”。
- 下载中禁用切换 release、重复下载和重复检查；同一 runtime 仅一个任务，进度更新切换到 UI 线程。
- 下载弹窗的关闭/返回仅隐藏 UI，不取消任务；设置入口可恢复。显式“取消下载”才取消并清理临时文件。
- 切后台不主动取消，但不承诺保活；进程重启不恢复任务，下次初始化清理残留临时文件。
- 取消不作为下载错误；失败显示可重试和浏览器下载。重试从头下载，不宣称支持断点续传。
- 默认接受 SDK 的 4 GiB 上限、7 天完成文件保留期；关闭弹窗不删除完成文件。
- 应用内成功仅指文件落盘、大小及声明摘要校验通过；不能显示安装已完成。
- Android 使用 SDK 内部更新目录；桌面指定用户数据目录下独立的 updates 根目录，
  不写 Program Files、/opt 等安装目录，不复用漫画/模型下载队列或目录。
- 非 Android 下载完成后将安装包移到系统下载目录，跨文件系统时先安全复制并发布目标，再删除 SDK 原文件；
  不覆盖已有同名文件，自动使用唯一文件名。移动失败保留原文件并反馈失败，允许仅重试移动，不显示交付完成。
  最终文件交付是不可取消的提交阶段；取消或退出必须等待交付完成，不丢失已发布的目标文件句柄。
- 桌面完成提示显示可选择复制的目标路径，提供“退出并手动安装”和“稍后安装”。
  点击前者先确认文件仍存在，再复用窗口关闭的正常保存/任务清理流程退出当前应用；后者只关闭提示。
  Windows 提示用户退出后运行安装程序；Linux 提示使用系统软件安装器安装 DEB，权限由系统处理。
  不自动运行 EXE、dpkg、sudo 或 shell；浏览器成功仅称“已交给浏览器”，不触发退出。

## 7. Android 安装安全

1. 宿主显式声明 REQUEST_INSTALL_PACKAGES；无须新增存储权限。
2. 使用 SDK 合并的 `${applicationId}.kmp-updater.install` Provider，exported=false，
   grantUriPermissions=true，仅公开 `files/kmp-updater/`；与现有 `${applicationId}.files` 分离。
3. 必须审查 release 合并 Manifest、最终路径 XML 与 getUriForFile 实际目录；禁止 `path="."`、
   暴露 files/cache 根目录或加入数据库、凭据、模型目录。
4. 仅接受 SDK 交付的完整单 APK。调用安装前确认前台、未锁定、文件存在，以及包名一致、实际版本更高。
5. **不做客户端签名兼容性预检**：不读取或比对归档与已安装包证书，不增加签名验证器。
   签名兼容性（含系统支持的签名轮换）交由 Android 系统安装器判定，不兼容时由系统阻止覆盖安装。
   客户端不将安装交接成功或下载摘要校验通过表述为签名验证通过。
6. 安装准备工作在非主线程；安装/设置启动在主线程。前台退出或锁定时取消未交接的准备任务。
   调用 SDK 安装 API 前再次检查当前 Activity 可用及 UI gate，禁止从后台弹出安装器。
7. PermissionRequired 时解释用途；用户再点“前往设置”才打开未知来源授权页。
   返回后不自动安装，用户再次点击“安装更新”，重新读取权限及文件信息。
8. 系统交接 Dispatched 仅显示“已交给系统安装器”，用户取消仍可重试；不得立即删除 APK。
   尊重 SDK 的交接文件保护期；实际成功通过下一次启动读取已安装版本确认。
9. Debug 与 Release 即使包名相同也可能签名不同；默认关闭 Debug 生产安装，测试使用隔离服务和同签名样本。
10. 摘要是发布方声明，不等于独立可信签名；系统安装器仍负责最终安装签名及平台兼容性检查。

## 8. 发布联动、安全和回退

- 首个接入版发布前核对所有目标的线上最大 versionCode，新的实际包版本严格递增，不能只改更新元数据。
- Android 核对实际 ABI、包名、签名、完整 APK；桌面核对实际系统/架构/包类型及 Windows upgradeUuid 稳定性。
- 每个文件单独发布，更新说明为用户可读纯文本；发布方从实际最终产物计算摘要（推荐 SHA-512）。
- Windows 当前无 exe 精确匹配包，客户端逻辑可实现，但 Windows 端到端上线验收不能据此宣称完成。
- 只读客户端不读取发布凭据。发版继续遵循现有程序发布规范，安装包内永不包含发布权限。
- 日志仅允许触发来源、结果分类、HTTP 状态和服务错误码；禁止记录响应体、完整下载/预签名 URL、
  Token、Cookie、认证头、带用户信息的本地完整路径。
- 更新服务故障不影响登录、阅读和已有下载；没有安装包时不阻塞主流程。
- 不实现版本降级。故障修复发布更高数值版本；紧急情况下用户可关闭启动检查，手动入口仍保留。

## 9. 实施顺序与验收

### 实施阶段

1. **依赖与版本**：解析 Android/JVM 平台变体、检查传递依赖与压缩规则，生成可审计版本元数据。
2. **检测闭环**：SDK 适配、fake 注入、根级 Store、设置入口、自动触发及 Fluent 弹窗。
3. **下载闭环**：平台下载器、独立状态、取消/重试、摘要与目录交接。
4. **Android 安装**：Provider/权限、前台 gate 和系统安装器；不实现客户端签名预检。
5. **发行验收**：真实安装包升级、Release 压缩产物、各目标公开序列和发布说明。

### 必须通过的测试

- 单元：本地小于/等于/大于服务端；超 Int 的 Long code；错误和无匹配包不能映射为 UpToDate。
- 协议：精确四参数、根 URL、未知字段、非法版本/摘要、404 两类、409、429 冷却、5xx、超时和取消。
- 状态：自动一次、开关持久化、手动提升、去重、页面离开/Activity 重建、不跨账号绑定、后台/锁屏挂起显示。
- UI：长说明与字体放大、桌面拖动滚动条、未知大小、重开下载进度、返回键/手势/Esc 只关闭最上层。
- 下载：已知/未知长度、摘要匹配/不匹配/缺失、大小不符、HTML 页面、磁盘不足、取消清理、重试冷却、
  HTTPS 降级拒绝、过多重定向、完成文件丢失及清理时不碰业务文件。
- Android：同签名更高版本完整升级；不同签名由系统安装器阻止覆盖，客户端不误报安装成功；
  不同包名/同低版本/无效及 split APK 拒绝；
  未授权→设置→返回→再次点击；安装器取消/不可用、准备中退后台/锁定、最终 Provider 最小路径。
- 桌面：Windows EXE 与 Linux DEB 的真实安装来源元数据、系统下载目录解析、同名避让、跨文件系统移动失败恢复、
  选择退出时正常保存/清理、稍后安装不退出、无匹配包、ARM64 不跨架构。
- Release：Android R8 和桌面 ProGuard/jpackage 实包验证；Windows 稳定 upgradeUuid、Linux DEB 依赖保持不变。
- 安全：包体和日志不含发布凭据、不外发业务认证头、默认只发匿名公开 GET；未授权用户动作不启动安装器。

最初调研仅验证了文档、只读接口、SDK 固定标签源码和 POM。
当前实现已通过 shared 桌面及 Android host 测试、桌面运行时/文件交付和无真实窗口的 Compose 交互测试、
Android Debug/未签名 R8 Release 构建、Linux DEB 打包及文件行数/空白检查。
尚未在真实设备安装升级，也未做 Windows 实包构建及手动安装验证；这些不能由自动测试结果替代。

## 10. 已确认决策与发行前风险

- 已按“核心 SDK + Fluent 自建 UI、应用内下载、Android 确认安装、桌面手动安装”实现。
- 启动检查默认开启、每进程一次、阅读时延后提示、独立默认网络且不继承业务代理。
- Windows 本地 code=3 已确认；缺失 exe 包、最终 Android ABI/发布签名验证是发行前置条件，不凭文档推断。
- 已确认决策：不增加客户端签名兼容性检查，依赖 Android 系统安装器阻止不兼容签名覆盖安装。
  发版时仍须遵守既有签名发布规范；这不要求在客户端实现证书比对。
- 已验证 Gradle 实际消费及 Android/Linux 本地打包；真实设备/桌面安装和 Windows 包兼容性仍须发行前验证。

## 11. 参考依据

- [服务 API 文档](https://zp.ntutn.top/api)
- [SDK README（main，可能领先固定版本）](https://github.com/zerofancy/kmp-updater)
- [SDK v0.3.0 固定源码树](https://github.com/zerofancy/kmp-updater/tree/v0.3.0)
- [固定版本配置/结果类型](https://github.com/zerofancy/kmp-updater/blob/v0.3.0/updater/src/commonMain/kotlin/top/ntutn/updater/UpdateModels.kt)
- [固定版本下载类型](https://github.com/zerofancy/kmp-updater/blob/v0.3.0/updater/src/commonMain/kotlin/top/ntutn/updater/DownloadModels.kt)
- [固定版本 Android 安装实现](https://github.com/zerofancy/kmp-updater/blob/v0.3.0/updater/src/androidMain/kotlin/top/ntutn/updater/AndroidInstaller.kt)
- [固定版本 Provider 路径](https://github.com/zerofancy/kmp-updater/blob/v0.3.0/updater/src/androidMain/res/xml/kmp_updater_install_paths.xml)
- [JitPack 构建状态](https://jitpack.io/api/builds/com.github.zerofancy/kmp-updater/v0.3.0)
- [核心 POM](https://jitpack.io/com/github/zerofancy/kmp-updater/kmp-updater/v0.3.0/kmp-updater-v0.3.0.pom)
- [Android ARM64 实测查询](https://zp.ntutn.top/api/v1/updates?software=kica&os=Android&arch=ARM64&packageType=apk)
- [Android X86_64 实测查询](https://zp.ntutn.top/api/v1/updates?software=kica&os=Android&arch=X86_64&packageType=apk)
- [Linux X86_64 实测查询](https://zp.ntutn.top/api/v1/updates?software=kica&os=Linux&arch=X86_64&packageType=deb)
- [Linux ARM64 实测查询](https://zp.ntutn.top/api/v1/updates?software=kica&os=Linux&arch=ARM64&packageType=deb)
- [Windows X86_64 实测查询](https://zp.ntutn.top/api/v1/updates?software=kica&os=Windows&arch=X86_64&packageType=exe)
