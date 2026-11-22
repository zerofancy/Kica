# Decompose 全量迁移：实施与验证记录

日期：2026-11-22。对应 [迁移 spec](decompose-navigation-migration.html)。

## 当前状态

全量代码迁移及本轮可执行回归已完成。最终构建命令成功退出，所有实际执行的测试均通过；这不等同于末节列出的人工/线上/真实杀进程验收已经完成。

## 已实施范围

- Decompose 3.6.2 / Essenty 2.7.1；Android 与 Desktop 共用 Root → Main → 已访问栏目 ChildPages → 页面 ChildStack。
- 已访问栏目显式保持 CREATED，不使用默认远端销毁策略；每次新进入采用独立 entryId，纯返回与栏目往返不重跑初始请求。
- Home、Discover、Favorites、History、Downloads、Settings、RandomComics、Search、Detail、Reader 均使用组件上下文绑定的保留 store。
- 搜索草稿/提交条件、分页恢复范围、列表业务锚点、代理草稿、阅读入口决定与实际阅读位置有明确恢复边界。
- 收藏/点赞使用会话确认投影；标签提交使用页面任务；进度与历史使用会话级 FIFO，页面 pop 不取消已接受的有效写入。
- Android 根上下文接入真实 Activity 生命周期、saved state 和 InstanceKeeper；Desktop 接入窗口最小化、恢复与关闭。
- AI 操作选项保持独立 Activity、原入口与 SAEP Activity 级策略，组件只管理其内部状态。
- 删除旧根级路由列表、Search/Reader 内存 Map 与旧组合级进度 writer；没有生产双轨开关。

## 实施中发现并修复的边界

1. 登录邮箱在真实 token-only restore 中为空：导航身份改由随凭据轮转的非秘密 session marker 派生，避免冷恢复丢栈。
2. 登出不能由可重建的 Activity shell 协程承接：退出意图、身份失效和写入收尾移到保留的 RootStore。
3. 凭据读取是挂起接口：结果回写前二次校验认证代次、读取代次、当前 session 引用及关闭状态。
4. 设置读取失败不永久停在启动页；未知安全设置 fail-closed，设置流退避重订阅；网络/模型配置失败独立展示并可重试。
5. 返回处理器必须在其自身 Compose 重启作用域读取已观察子栈，避免只更新 Children 而让根 BackHandler 的 enabled 值停留在旧状态。
6. StateKeeper 首次注册前不能无条件 unregister；保留页面重挂时仅注销实际已注册的供应器。
7. Reader UI 首次/重新绑定必须等待当前 lease 与 layoutGeneration 就绪，避免初始布局使用失效回调。
8. Native SerializableContainer 会压缩且 consume 会破坏性取出；预算测试使用确定性不可压缩数据，检查与恢复分别使用真实序列化深拷贝。
9. 超过 256 KiB 的导航快照先重算并剔除可选草稿；必要状态仍超限才安全放弃恢复，不在运行中淘汰页面。
10. 恢复保护覆盖外层压缩数据、路由 lazy decode、栏目/页面配置不变量、子状态数量及重复 key，并明确不吞取消异常。[恢复保护](../../shared/src/commonMain/kotlin/top/ntutn/kica/ui/navigation/NavigationRestore.kt)镜像了固定 Decompose 3.6.2 的路由保存帧；升级依赖时必须同步审查格式与腐坏快照回归。
11. 标签解析器迁入会话保留数据，退出登录取消其后台查询并清理内存；设置清缓存不再持有旧 Activity 组合里的回调。
12. Desktop 最小化由生命周期控制，失焦/恢复焦点另绑定当前真实窗口，以保留切到其他应用超过三秒后的锁屏语义；移除旧 Compose 生命周期适配。

## 验证环境及执行方式

- Linux / OpenJDK 21；Desktop 真实 X11 窗口，最终回归使用独立 Xephyr `:98` + 独立 D-Bus 会话的 KWin，避免用户桌面抢占测试焦点；测试 JVM 使用软件渲染与关闭 vsync，避免远程/无可用 GPU 环境的透明首帧。
- 连接的 Android 16 设备，使用独立 library instrumentation APK；不会覆盖、登录或修改用户安装的 Kica 应用数据。
- Android instrumentation 包含真实 Activity 重建、系统返回键及符合设备导航模式时的边缘返回手势；纯逻辑测试另验证 fresh InstanceKeeper 的序列化恢复。
- 变更测试源集树后遇到 Kotlin 增量编译内部 DirtyData/RankPeriod 错误，最终回归以 `-Pkotlin.incremental=false` 做全量编译，不通过删除用户缓存或调整工具链规避。

```bash
DISPLAY=:98 KICA_READER_WINDOW_TEST=1 KICA_TAG_WINDOW_TEST=1 \
./gradlew --no-configuration-cache -Pkotlin.incremental=false --continue \
  :shared:desktopTest :shared:testAndroidHostTest \
  :shared:connectedAndroidDeviceTest :networkJvm:test \
  :androidApp:assembleDebug :desktopApp:createDistributable \
  -Pandroid.testInstrumentationRunnerArguments.class=top.ntutn.kica.ui.NavigationHostDeviceTest
python3 scripts/check_file_lines.py
git diff --check
```

## 最终结果

| 项目 | 最终结果 |
| --- | --- |
| [Desktop 共享及真实窗口测试报告](../../shared/build/reports/tests/desktopTest/index.html) | 144 项通过，0 失败，0 跳过；包含全部窗口 opt-in 用例 |
| [Android 主机共享测试报告](../../shared/build/reports/tests/testAndroidHostTest/index.html) | 106 项通过，0 失败，0 跳过 |
| [Android 16 真机宿主报告](../../shared/build/reports/androidTests/connected/androidMain/index.html) | 3 项通过，0 跳过：Activity 重建、系统返回键、边缘返回手势 |
| 网络模块回归 | 15 项通过，0 失败，0 跳过 |
| Android 调试包 | `:androidApp:assembleDebug` 成功；未安装到用户的 Kica 应用、未发布 |
| Desktop 应用目录 | `:desktopApp:createDistributable` 成功；不是发行版签名/混淆发布验证 |
| 源码约束 | 文件行数检查、`git diff --check` 通过；旧根导航/搜索阅读器状态 Map 检索无残留 |

计数包含在 Desktop 与 Android 主机重复执行的共享用例，不应相加后宣称为独立测试场景数量。最终命令及结果见[完整验证日志](../../build/decompose-verification.log)。

隔离 Xephyr 显示、KWin 和独立 D-Bus 测试会话已在测试结束后停止，没有额外图形测试服务留在后台。此前失败用于修复，最终表格仅统计最后一次全量执行结果；未通过重试后丢弃测试或放宽关键断言来获得通过。

## 验收矩阵追踪

| Spec 场景 | 本轮证据 / 边界 |
| --- | --- |
| A01–A02 列表/发现保留 | 分页 store 去重与恢复、周期缓存、分页真实窗口锚点测试 |
| A03 阅读器返回 | 保留 store、请求计数与全部阅读器窗口测试；Android 完整阅读路径另需人工复核 |
| A04 重复路由 | 独立 entryId、搜索请求竞态、嵌套栈序列化测试 |
| A05 独立栏目栈 | 六栏目生命周期单测、真实 Desktop 根窗口及 Android Activity 重建 |
| A06 返回优先级 | 返回注册器单测、Desktop 菜单/帮助/Esc 真实窗口、Android 真机返回键及手势；Android 多层临时弹层仍需人工复核 |
| A07 锁屏 | Root 锁状态及 Reader 选择/跨章临时锁卸载测试；真实三秒失焦/后台计时未完整人工验收 |
| A08 重建 | 真机 Activity 重建及真实编码后的 fresh InstanceKeeper 恢复；系统真实杀进程恢复未验 |
| A09 异步竞态 | 旧查询/旧凭据读取、退出登录跨配置重建、后台标签查询取消测试 |
| A10 局部更新 | 同作品多个详情投影、收藏 dirty 校验、失败回滚、标签真实窗口测试 |
| A11 阅读进度 | FIFO/有界收尾、过期布局拒绝、跨章和键盘真实窗口回归 |
| A12 资源释放 | 出栈 destroy 恰一次、scope 取消与会话清理；未做长时内存 profiler 测量 |
| A13 快照边界 | 预算裁剪、必要状态超限、腐坏帧/配置/子状态、不吞取消及账户会话标记测试；深栈 Android 总 Bundle 压力待验 |
| A14 UI 恢复 | 业务 key/index/offset、临时过滤 clamp、分页追加位置与设置脏草稿测试；全布局组合仍需人工复核 |
| A15 其他页面 | 随机会话共享、独立 AI 组件提交/重建、标签会话清理；未操作真实 SAEP 策略开关或原生文件框 |


## 不应夸大的验证范围

- Activity 重建、fresh InstanceKeeper 序列化恢复，不等价于已完成“系统真实杀进程后由任务栈恢复”的端到端验证。
- 独立假仓库测试没有访问真实账号/服务器；未声称验证线上收藏接口或模型下载。
- 快照预算不等于全部 Android Bundle 的最终大小；UI 保存域另需深栈/长输入压力观察。
- 未量化长时间深栈内存峰值，也不宣称“零内存增长”；自动化校验出栈销毁、任务取消与写入排空。
- 未发布安装包、变更发布版本或凭据；Desktop 测试渲染设置不改变打包应用的渲染策略。
