# 自动更新接入：实现与验证记录

日期：2026-11-22。仅本地开发、测试与打包，未上传文件或调用发布接口。

## 已实现

- 核心 SDK 固定 v0.3.0；服务 origin 为 `https://zp.ntutn.top`，软件名 `kica`。
- 非敏感根级版本元数据集中维护：当前 0.1.3，Android/Linux/Windows 数值版本均为 3。
- 根级保留更新状态：每进程一次启动检查、持久化开关、手动检查、请求合并、错误分类及重试冷却。
- Fluent 设置入口与更新提示；长内容有同状态的桌面滚动条；模态交互、阅读器、锁屏和后台延后提示。
- 应用内流式下载、未知大小进度、摘要校验、取消、重试和浏览器下载回退。
- Android 读取实际已安装版本，使用独立更新目录、最小权限 Provider、未知来源设置及系统安装交接。
  不增加客户端签名兼容性预检；由 Android 系统安装器阻止不兼容签名覆盖安装。
- 非 Android 下载完成后移动至系统下载目录；识别 Windows Known Folder 和 Linux XDG 下载目录，
  同名避让，不覆盖用户文件，支持跨文件系统交付。移动失败保留原文件，重试只需重新移动。
- 桌面完成提示提供“退出并手动安装”和“稍后安装”；前者检查文件存在后复用正常关闭流程，
  后者不退出。不自动执行安装程序、不提权。
- 最终文件交付为不可取消的提交阶段；正常关闭等待交付和更新任务清理完成。
- Android 暂停 Activity 或锁定应用时取消未交接的安装准备，不仅依赖 Compose 重组后的取消。
- Debug、开发运行和未知桌面安装来源默认禁用生产更新；单元/界面测试使用注入的隔离 fake。

## 关键文件职责

```text
release.properties                         非敏感版本来源
shared/.../update/UpdateRuntime.kt          平台边界及当前前台宿主回调
shared/.../update/UpdateStore.kt            根级更新状态、任务及恢复规则
shared/.../ui/update/                       Fluent 界面、设置、读取通用模态状态
shared/.../ui/modal/                        通用状态及 AppDialog/AppPopup 自动登记
androidApp/.../AndroidUpdateRuntime.kt      实际安装版本、匿名客户端及内部目录
androidApp/.../MainActivity.kt              前台安装与权限设置交接、暂停取消
desktopApp/.../DesktopUpdateRuntime.kt     构建元数据、偏好及关闭等待
desktopApp/.../UpdateDownloadExport.kt     系统下载目录解析及安全移动
```

构建生成桌面 os/arch/packageType/releaseBuild 元数据，只将明确的 Linux `packageDeb`、
Windows `packageReleaseExe` 任务识别为支持的安装来源；普通开发运行重新生成 disabled 元数据。
当前实现不新增 macOS 安装包格式或发行任务。

## 验证范围

使用关闭本机签名读取的本地命令：

```sh
./gradlew --no-configuration-cache -PkeyringSigning=false \
  :shared:desktopTest :shared:testAndroidHostTest :desktopApp:test \
  :androidApp:assembleDebug :androidApp:assembleRelease :desktopApp:packageDeb
python3 scripts/check_file_lines.py
git diff --check
```

验证内容包含：

- 共享状态：自动一次/配置重建、手动提升、冷却、错误不误报、关闭提示不重弹、取消和延迟进度、
  保留导出失败源文件、重新导出、不丢失最终文件、完成后重开、缺失文件重下、偏好写入顺序。
- 无真实窗口的 Compose 交互：稍后安装不退出、用户确认才退出、缺失文件不退出并可重新下载、
  Android 授权设置不自动重试安装、移除前台界面取消安装准备、长说明在小窗口内可滚动。
- 根级安装 gate：恢复、锁屏、前后台和退出时的同步拦截。
- 桌面：同名/并发避让、模拟及实际跨文件系统导出、失败保留源文件、XDG 非 shell 解析、偏好和进程 latch。
- Android Debug 及未签名 R8 Release 构建；Linux DEB 本地打包及既有 `libsecret-tools` 依赖补写。
- 最终 APK 审计：实际包名 `top.ntutn.kica`、code=3/name=0.1.3；声明 REQUEST_INSTALL_PACKAGES；
  更新 Provider 非导出、可临时授权且仅公开 `files/kmp-updater/`；现有业务共享白名单未扩大。
- Linux 打包元数据：code=3/name=0.1.3、Linux/X86_64/deb、releaseBuild=true。

## 通用模态交互重构

- 模态登记放在通用 UI 层，不依赖更新服务；业务弹窗统一使用 AppDialog/AppPopup，不再手工登记更新专用标记。
  设置页的附加“关于”内容也改为由根层装配到通用扩展槽，业务页面不再直接引用更新模块。
- 每个窗口持有独立、可重复释放的登记凭据；重组不重复计数，嵌套窗口和任意关闭顺序不造成计数漂移。
- 功能可用独立 ModalOwner 观察其他模态窗口；嵌套窗口继承所属功能，更新弹窗正常登记但不会阻挡自己。
- 保留原来的原生窗口参数、PlatformBackHandler 及滚动条，不改变返回、Esc、点击外部或下载生命周期。
- 回归覆盖登记/释放、嵌套继承、重复重组、宿主销毁、Provider/owner 切换、业务弹窗优先及更新不自阻塞。
  源码架构测试禁止业务代码绕过统一窗口封装，防止以后遗漏登记。
- 本次重构运行 shared 桌面/Android host 测试、桌面测试及 Android Debug/Release 本地构建；不发布。

## 尚未验证与发行约束

- 未在真实 Android 设备上安装/覆盖应用，也未做实际未知来源授权或返回手势操作；不触碰已有安装及数据。
- 无真实 Windows EXE 构建/安装和 DEB 安装升级验证；未自动启动安装器。
- 旧的 opt-in 原生窗口测试不等于本次已执行鼠标拖动滚动条、Esc 或设备返回手势验证。
- 调研时 Windows 精确 EXE 序列无匹配包；未来发版前必须再次查询并补齐包，而不是显示“已是最新版”。
- 本地当前版本未升号，也未签名/发布新版；实际发行须从最终产物核对 ABI、签名和版本，
  保证每个线上目标序列的数值版本严格递增。未签名 Release 仅用于构建验证，不可用于正式更新。
- 摘要只验证发布方声明的内容，不代替软件签名；浏览器接受请求、文件保存、系统安装交接均不表示安装成功。
