# Kica 导航、回退栈与页面状态保留对比方案

调研日期：2026-11-22。范围：当前仓库静态审计 + 官方文档与发布记录；未运行应用复现，也未做依赖集成或性能实测。本轮仅新增方案文档，不改业务实现。

> **后续决策：** 用户已选定 Decompose，并要求一次性完整迁移。正式实施范围与状态契约以 [Decompose 全量迁移 spec](../specs/decompose-navigation-migration.html) 为准；本文的 Nav3 优先、试点选型及分批建议保留为历史调研，不再作为实施决策。

## 1. 结论先行

**问题不是完全没有回退栈，而是“路由栈存在，页面数据与任务没有统一绑定到栈条目”。** 换一个导航库但继续在页面 `produceState` / `LaunchedEffect` 中无条件加载，返回时仍可能刷新。

建议采用：

1. **默认首选：官方 Navigation 3 + 栈条目级 ViewModel/StateHolder + 显式刷新策略。** 与当前 Compose Multiplatform 架构和手动栈模型最接近，避免为当前体验问题引入整套组件架构。
2. **长期架构备选：Decompose。** 如果愿意同时把页面业务逻辑迁出 Composable，建立可独立测试、与 UI 解耦的组件树，它比单纯导航库更完整，也很适合 Android + Desktop。
3. Voyager 可作为轻量备选；Navigation Compose 2.x 作为成熟保守路线；不建议继续把各页面补丁式缓存当作长期导航架构。

**决策门槛：** 先用“收藏 → 详情 → 阅读器 → 返回”做小范围验证。验证 Navigation 3 在本项目版本组合、Fluent 容器、Android 返回与 Desktop Esc 下的行为；未通过再评估 Decompose，不同时维护两套导航权威。

## 2. 当前项目实际情况

### 2.1 技术前提

- 当前构建目标只有 Android 和 Desktop JVM，不需要为了假设中的 Web/iOS 提前增加迁移成本。
- [shared 构建配置](../../shared/build.gradle.kts#L12-L44)使用共享 Compose UI；目前没有导航库或页面级 ViewModel 导航依赖。
- [版本目录](../../gradle/libs.versions.toml#L1-L24)：Kotlin 2.4.21、Compose Multiplatform 1.12.2、Compose Fluent 0.2.0-dev-20261114.1。
- 现有 Fluent `NavigationView` 是侧栏 UI，不等于生命周期感知的页面导航控制器；无需为引入导航库替换 Fluent 主题和控件。

### 2.2 问题证据

| 位置 | 代码事实 | 体验影响 / 判断 |
| --- | --- | --- |
| [KicaApp](../../shared/src/commonMain/kotlin/top/ntutn/kica/ui/KicaApp.kt#L104-L119) | `remember { mutableStateListOf(Home) }`，返回时移除栈顶 | 已有内存路由栈，但本身不是可恢复的导航快照 |
| [KicaApp](../../shared/src/commonMain/kotlin/top/ntutn/kica/ui/KicaApp.kt#L220-L248) | 只渲染 `backStack.last()`；外包 `SaveableStateProvider(route.toString())` | 非栈顶页面会退出组合；只能保留参与 saveable 机制的 UI 状态，不能自动保留普通 `remember` 和网络任务 |
| [主栏目切换](../../shared/src/commonMain/kotlin/top/ntutn/kica/ui/KicaApp.kt#L221-L227) | 主目的地导航先 `clear()` 再重建 `[Home, target]` | 切换栏目会丢失原栏目子栈；不是独立多栈 |
| [保留状态清理](../../shared/src/commonMain/kotlin/top/ntutn/kica/ui/KicaApp.kt#L109-L115) | 不在新栈里的状态被清理 | 清栈后旧栏目状态会被回收；单改保存方式不改变这个语义 |
| [首页](../../shared/src/commonMain/kotlin/top/ntutn/kica/ui/screen/HomeRandomScreens.kt#L87-L106) | `produceState` 读缓存后仍请求推荐 | 回到首页仍会重新请求；有缓存不等于“不刷新” |
| [发现](../../shared/src/commonMain/kotlin/top/ntutn/kica/ui/screen/DiscoverScreens.kt#L107-L131) | 榜单周期用普通 `remember`，分类/榜单在 `produceState` 加载 | 返回可能重置周期并重新加载 |
| [收藏](../../shared/src/commonMain/kotlin/top/ntutn/kica/ui/screen/LibraryScreens.kt#L76-L102) | 分页结果用普通 `remember`；进入 effect 重置分页并请求第一页 | 返回详情后，已加载分页不能靠现有 saveable holder 保住 |
| [详情](../../shared/src/commonMain/kotlin/top/ntutn/kica/ui/screen/DetailScreen.kt#L90-L118) | `produceState` 请求详情和章节；滚动状态在成功内容分支内创建 | 阅读器返回后会重新进入加载过程；滚动恢复还受内容分支与条目标识影响，需实测 |
| [搜索状态](../../shared/src/commonMain/kotlin/top/ntutn/kica/ui/screen/SearchPageState.kt#L10-L26)、[搜索加载](../../shared/src/commonMain/kotlin/top/ntutn/kica/ui/screen/SearchScreens.kt#L65-L119) | 已有根级内存状态表与 `completed` 去重 | 搜索并非完全未优化；但正在运行的任务仍由组合控制，且键基于路由字符串 |
| [阅读器入口](../../shared/src/commonMain/kotlin/top/ntutn/kica/ui/screen/ReaderEntryScreen.kt#L63-L75) | 已有根级 `ReaderSessionMemory`；章节仍在 `produceState` 加载 | 已有局部会话保留，但不是所有阅读状态与请求都纳入同一生命周期 |

补充：

- [历史/下载](../../shared/src/commonMain/kotlin/top/ntutn/kica/ui/screen/LibraryScreens.kt#L148-L214)主要订阅本地 Flow。重新订阅不等于重新发起远程加载，不应把所有页面一概归类为网络刷新。
- [现有导航测试](../../shared/src/commonTest/kotlin/top/ntutn/kica/ui/KicaNavigationTest.kt#L7-L33)明确规定非首页主栏目返回首页。独立栏目栈可以增加，但“栏目根页返回哪里”属于产品行为变更，不能暗中改掉。
- 上述是代码路径推导，不是已完成的端到端复现；下一阶段需要记录请求次数与 UI 状态验证。

### 2.3 必须分开的四层状态

| 层次 | 例子 | 正确归属 |
| --- | --- | --- |
| 导航状态 | 当前栏目、条目 ID、详情参数、子栈 | 导航宿主/组件树，必要时序列化恢复 |
| 页面业务状态 | 搜索结果、分页、已加载详情、加载任务 | 条目级 ViewModel / StateHolder / Decompose Component |
| UI 状态 | 滚动锚点、筛选值、输入草稿 | 条目级 saveable 状态；复杂状态明确 Saver |
| 长期业务数据 | 阅读进度、下载任务、实体缓存、收藏变动 | Repository、数据库、应用级服务 |

普通返回要求保留的是前 3 层；进程死亡不可能保留内存对象，要从小型快照与第 4 层恢复。桌面应用退出后重启恢复也需自行设计持久化，不能把 Android saved state 的承诺直接套过去。

## 3. 候选方案对比

下表是针对 Kica 的工程判断，不是基准测试成绩。所有候选均需要把加载状态从易销毁的组合作用域中提取出来。

| 维度 | Navigation 3 | Decompose | Voyager | Navigation Compose 2.x | 完善自研 |
| --- | --- | --- | --- | --- | --- |
| 核心模型 | 用户持有栈 + `NavDisplay` | 生命周期组件树 + `ChildStack` 等 | `Screen` + `Navigator` + `ScreenModel` | `NavController` + `NavHost` + 导航图 | 现有路由列表 + 自建宿主 |
| Android / Desktop | 官方多平台支持 | 原生 KMP，支持 JVM | 支持 | 官方多平台移植 | 已有基础 |
| 返回保留业务状态 | 条目级 ViewModel/状态容器，需配置 | 后台栈组件仍存在，UI 可销毁 | 用 ScreenModel 保留 | 条目级 ViewModel，需正确作用域 | 状态表、作用域与回收自己写 |
| 滚动/输入恢复 | 配置 saveable entry decorator 和稳定键 | 配置 Compose 状态保存及稳定组件键 | 保存 UI 状态，需稳定 screen key | saveable 状态 + entry owner | 当前有部分能力，需补齐 |
| 多栏目独立栈 | 显式建模，自己定义切换/回收策略 | 多 ChildStack / 嵌套组件，策略自定 | Tab + 嵌套 Navigator | `saveState` / `restoreState` 等组合 | 全部自己维护 |
| 业务逻辑与 UI 解耦 | 通过 ViewModel/StateHolder 达成 | 框架模型直接鼓励 | 通过 ScreenModel 达成 | 通过 ViewModel 达成 | 靠团队纪律 |
| 纯逻辑导航测试 | 用户持有栈，较直接 | 组件级测试优势明显 | 栈 API 可测 | 常需控制器/宿主环境 | 易起步，完整生命周期测试成本高 |
| 对现有页面改动 | 中：替换宿主并提取状态 | 中高：宿主接线 + 页面组件化 | 中：Screen 包装 + ScreenModel | 中：导航图 + ViewModel | 短期低，长期维护高 |
| 主要风险 | 底层积木多，多栈和资源策略仍归项目 | 额外生命周期体系、学习成本与宿主集成 | 发布节奏、旧版与当前工具链兼容需核验 | 新增一套图/控制器抽象，与现有栈差异较大 | 重复实现框架能力和边界条件 |
| 本项目定位 | **默认首选** | **架构升级首选** | 轻量备选 | 保守备选 | 只做短期止血 |

### 3.1 Navigation 3：为何本项目优先试它

[官方多平台文档](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)说明 Compose Multiplatform 从 1.10 起支持 Navigation 3，覆盖 Android、iOS、Desktop、Web；文档示例使用 `navigation3-ui:1.1.1`。**不能再按“Nav3 仅 Android”排除它。**

它的“用户持有栈”与当前 `SnapshotStateList<AppRoute>` 接近。Kica 可以保留类型安全路由、Fluent 壳层和现有 repository 注入方式，重点补上标准化的条目所有权。

实施要点：

- 为每次页面实例定义稳定且可恢复的 `entryId`，路由参数与页面实例身份分离。
- 配置条目 saveable 状态与 ViewModel 所有权，例如对应版本的 saveable-state / ViewModel-store entry decorators；不要误以为只换成 `NavDisplay` 就保留普通 `remember`。
- 主栏目多栈由项目显式管理；非活跃栏目必须确实保留对应 entry owner，而不只是把路由列表存到 Map。
- `NavDisplay` 是显示/导航基础设施，不要求把 Fluent 页面改为 Material 3。自适应附加库不是本轮必需依赖。
- 当前官方文档称 Web 浏览器历史集成仍需额外方案；本项目无 Web 目标，不为此增加依赖。

**不推荐场景：** 接下来确定要共享大量非 Compose UI 业务组件，希望导航逻辑完全独立于 Compose 宿主，此时 Decompose 更自然。

### 3.2 Decompose：不是“更换一个返回库”，而是组件生命周期方案

[官方概览](https://arkivanov.github.io/Decompose/)与 [ChildStack 文档](https://arkivanov.github.io/Decompose/navigation/stack/overview/)明确：栈中非活跃组件进入 stopped 状态但不销毁，因此业务状态可以不跟随 UI 退出而丢失。

推荐落地形态：

```text
RootComponent
├── Auth / Main 会话边界
└── MainComponent
    ├── activeSection
    ├── HomeSection：ChildStack<Home, Detail, Reader, Search...>
    ├── DiscoverSection：ChildStack<Discover, Search, Detail...>
    ├── FavoritesSection：ChildStack<Favorites, Detail, Reader...>
    └── 其他栏目：按实际访问惰性创建
```

- 页面组件拥有 `StateFlow<UiState>`、分页和请求任务；Composable 负责渲染及 UI 特有状态。
- [InstanceKeeper](https://arkivanov.github.io/Decompose/component/instance-retaining/)用于配置变更期间保留业务对象；[StateKeeper](https://arkivanov.github.io/Decompose/component/state-preservation/)用于可序列化的小型恢复状态。两者不能互相替代。
- 不要求同时引入 MVIKotlin、DI 框架或改写 Repository；先用现有构造器注入与协程即可。
- 栈内重复配置默认有唯一性约束。`详情 A → 标签搜索 → 详情 A` 是有效场景，应让配置包含独立 entryId，不直接把当前 `AppRoute` 等值对象作为唯一身份，也不依赖全局重复配置开关掩盖问题。
- Android 要把根 ComponentContext 正确接到 Activity 的恢复和实例保留；Desktop 要接到窗口生命周期，销毁时清理任务。不能仅在 `KicaApp` 内 `remember` 一个 root 就宣布完成恢复。
- **组件保留不等于协程自动暂停。** 后台请求、流订阅和预加载的继续/暂停策略要按生命周期明确实现。

[最新稳定版发布记录](https://github.com/arkivanov/Decompose/releases/tag/3.6.2)：本次查询为 3.6.2，发布于 2026-11-10；该版本修复 Android 14 三键导航的预测返回完成问题。其发布说明列出 Kotlin 2.3.21、Compose 1.9.3 等构建依赖，与本项目并非同一版本组合，仍需实际编译/运行验证，不能据此宣称已兼容。

**何时改选 Decompose：** 团队愿意把此次工作定义为“页面逻辑组件化”，而不是只解决返回刷新；或者试点表明多层导航与非 UI 生命周期逻辑占比明显增加。

### 3.3 Voyager：API 简洁，但不是不需要状态设计

[官方首页](https://voyager.adriel.cafe/)提供线性栈、Tab、嵌套 Navigator、转场；[ScreenModel](https://voyager.adriel.cafe/screenmodel/)提供独立于 Android 的页面模型。对少量页面迁移上手较快。

风险与边界：

- [恢复文档](https://voyager.adriel.cafe/state-restoration/)要求 Screen 参数/属性满足保存约束，依赖服务不要直接塞进可恢复的 Screen 属性。
- 同一个 Navigator 中多次出现的页面应自定义独立 key；仅使用类名或业务 ID 不足以区分页面实例。
- 本次 GitHub [`releases/latest`](https://api.github.com/repos/adrielcafe/voyager/releases/latest)返回的非预发布版本为 1.0.1，发布于 2024-12-19；官网又包含 1.1.0 预发布线能力。这里只能判断“应核验拟选版本和维护情况”，不能据此断言停更，也不能混用预发布能力作为 1.0.1 的能力承诺。
- 在项目当前 Kotlin/Compose 版本较新的前提下，其版本风险需要优先验证。综合下来，暂不比官方 Nav3 或组件化路线更有优势。

### 3.4 Navigation Compose 2.x：能解决，但不是默认新选型

[官方多平台导航文档](https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html)提供 `NavController`、`NavHost` 与导航条目生命周期；文档示例为 `org.jetbrains.androidx.navigation:navigation-compose:2.10.0-rc01`，这是文档示例，不是本报告核验的最新稳定版。

若团队已有成熟 NavController 经验，可以沿该路线实现保存/恢复与多栈。但当前项目没有既有导航图资产，手动栈改为 Nav3 更顺；不建议仅为解决此问题先引入 2.x 再规划二次迁移。

### 3.5 其他库的排除说明

[Compose Navigation Reimagined 官方文档](https://olshevski.github.io/compose-navigation-reimagined/)具备类型安全、独立 entry 生命周期和嵌套栈等优点，但首页展示的是 Jetpack Compose / Android Parcelable 路线，未在本次官方资料中确认满足本项目的共享 Desktop 需求。因此不列为优先候选；这不是断言其所有版本/分支均不支持多平台。

## 4. 无论选哪个库，都要实现的行为契约

### 4.1 主栏目与页面回退语义

建议默认采用“栏目独立子栈 + 保留已有返回首页约定”：

1. 侧栏切换栏目只切换 activeSection，不清空离开栏目的子栈。
2. 再次点击当前栏目默认不清栈、不刷新；若以后需要“回栏目首页”，提供明确操作，不复用为隐式刷新。
3. 子页面返回只 pop 当前栏目的栈；回到栏目根页后，非 Home 栏目返回 Home，保留现有约定。
4. Home 根页无临时交互时：Android 不拦截系统退出；Desktop Esc 不关闭应用。
5. “收藏深层页面切到 Home，再按返回”不自动恢复收藏深层页；切回收藏才恢复原子栈。栏目选择历史与页面回退历史不要混为一谈。
6. 阅读器全屏只是壳层显示方式变化，不应该迁移页面状态所有者或重建上一页。

### 4.2 唯一的返回分发链

现有 [Android 返回适配](../../shared/src/androidMain/kotlin/top/ntutn/kica/ui/PlatformBackHandler.android.kt#L6-L12)使用 `BackHandler`；[Desktop 适配](../../shared/src/desktopMain/kotlin/top/ntutn/kica/ui/PlatformBackHandler.desktop.kt#L54-L72)按最后注册且启用的处理器分发 Esc。

保持 `PlatformBackHandler` 作为项目级入口，底层接到唯一导航状态，优先级为：

```text
最上层弹窗/菜单/阅读器临时浮层
→ 侧栏抽屉等临时交互
→ 当前栏目子页面 pop
→ 非 Home 栏目根页转 Home
→ Home 根页系统默认行为
```

- UI 返回按钮与系统返回/Esc 复用同一个导航回调。
- Nav3 / Decompose 自带返回处理与项目入口之间只能有一个消费者，不能两个处理器各 pop 一次。
- 普通返回正确性与预测返回动画是两项工作。现有包装只是普通 BackHandler；新增库不意味着项目自动具备可交互的预测返回转场，需单独接线与验证。
- 锁屏/登录覆盖时不得在背后弹出业务页面；退出登录清理全部栏目栈、状态容器和用户内存缓存，并阻止旧异步结果写回新会话。

### 4.3 请求与刷新策略

| 触发 | 默认行为 |
| --- | --- |
| 首次创建页面条目 | `ensureLoaded()`：缓存/已有状态优先，最多发起一轮初始加载 |
| push 到详情后再 pop | 直接展示已保留状态，不清列表，不重置分页，不自动全量刷新 |
| 侧栏离开再回来 | 恢复该栏目页面栈与状态，不自动回第一页 |
| 用户显式刷新 | 可重载第一页，但明确是用户操作；失败时尽量保留旧数据 |
| 详情收藏/点赞成功 | 按漫画 ID 更新实体/列表，或只标记相关集合 dirty；不要所有页面一起刷新 |
| 阅读器返回详情 | 更新阅读进度投影，不重新请求全部详情和章节 |
| 后台数据过期 | 若产品需要 TTL，可后台静默校验；首次试点先不开启隐式返回刷新 |
| 进程死亡后恢复 | 先恢复栏目、路由、输入、筛选和锚点；数据从仓库缓存/网络重建，不保证零请求 |

任务由条目级业务对象管理：同一页请求去重；刷新与分页冲突用 generation/token 控制；page pop 销毁后取消任务并禁止晚到结果写回。离屏期间有限的一次性业务请求可继续，持续订阅/图片预加载按需暂停。

### 4.4 UI 保存与资源边界

- 优先保存稳定 item ID + offset 等逻辑锚点；列表更新、过滤、窗口列数变化后允许回退到最近有效位置，不只依赖 index。
- 不把全部分页数据、图片 Bitmap、完整章节图片列表、Repository 或平台 Context 序列化进 saved state。
- 栈条目销毁时回收其内存状态；栏目按首次访问惰性创建。大量页面的内存上限与淘汰策略在实测后确定，不武断指定“永不销毁”。
- 图片缓存由图片库管理，下载任务留在现有应用级协调器，不能因页面回退停止下载。
- 新增或改动滚动容器时，桌面可见可拖动滚动条必须与同一 ScrollState/LazyState 绑定；移动端不强制显示。
- 桌面退出重启的全栈恢复可后置，阅读进度持久化保留原有能力；不要把它与本轮“普通返回不刷新”捆绑扩大范围。

## 5. 分阶段落地与回滚

| 阶段 | 交付 | 通过标准 |
| --- | --- | --- |
| P0：建立基线 | 在可测试的假 Repository 中统计各接口调用；记录关键路径、分页、滚动、返回行为 | 明确哪些返回会重新加载，区分网络请求与本地 Flow 更新 |
| P1：Nav3 技术试点 | 收藏、详情、阅读器链路；条目 key、状态容器、saveable owner、单一返回分发；Android 与 Desktop 依赖验证 | 正常返回新增远程请求为 0；分页/筛选/锚点不丢；连续多层 pop 正确 |
| P2：全量页面迁移 | 首页、发现、搜索及其现有内存补丁，历史/下载/设置 UI 状态；栏目多栈 | 各栏目往返恢复原子栈，收藏修改能定向反映，普通返回无全页 Loading 闪烁 |
| P3：恢复与资源治理 | Android Activity 重建/进程恢复；会话隔离、条目释放、重复路由、内存验证 | 栈和小型快照可恢复；业务数据允许重建；退出登录不恢复旧页；无悬挂任务 |
| P4：可选体验增强 | 转场、预测返回、自适应双栏、桌面重启恢复 | 不改变已通过的基础回退与状态契约 |

P1 先验证依赖解析和两个目标编译，不因库的文档支持直接假定兼容。测试通过的解析版本与依赖树再写入版本目录；此方案不预先升级 Kotlin、Compose 或 Fluent。

**Decompose 替代路径：** 若选择组件化，将 P1 的宿主换成 Root/Main/Section Component；收藏与详情的数据加载移入子组件；其余验收标准不变。无需为了比较而把全项目分别迁移两遍。

**回滚边界：** 迁移前先把页面状态逻辑拆成不依赖导航库的对象，试点期间只保留一个活跃宿主。新宿主不通过验收时切回旧宿主，已提取的状态逻辑仍可复用。暂不修改数据库格式；确认稳定后再删除旧 route.toString 状态表与旧导航路径，避免双份状态长期并存。

不提供未经试点的精确工期、内存节省比例或包体增量；P1 完成后再据改动面和测量结果估算。

## 6. 验收清单

1. **收藏深分页：** 加载多页并滚动 → 详情 → 返回；页数、数据、位置不丢，favorites 请求计数不增加。
2. **发现筛选：** 改榜单周期 → 详情 → 返回；周期与位置不丢，不重新请求默认榜单。
3. **阅读器返回：** 详情 → 阅读器 → 详情；不出现整页重新 Loading，详情/章节不重复全量请求，阅读进度正确更新。
4. **搜索嵌套与重复参数：** 搜索 → 详情 A → 标签搜索 → 详情 A；各条目 key 独立，逐层返回恢复各自查询/分页，不能互相覆盖。
5. **栏目多栈：** 收藏深层页 → 发现 → 收藏；返回原深层页；非首页栏目根页返回 Home 的现有语义保持。
6. **UI/系统返回一致：** Android 界面返回、系统键、手势与 Desktop Esc 分别验证；弹窗/抽屉/阅读器菜单优先关闭，一次事件最多消费一层。
7. **根页与锁屏：** Android Home 根页默认退出、Desktop Esc 不退出；锁屏/登录覆盖下不偷偷 pop 背后页面。
8. **生命周期：** Android Activity 重建与系统进程回收恢复分开测；后者允许重新取数据，不能要求内存对象复活。
9. **异步竞态：** 初始加载/分页中离开页面、快速返回、pop、登出再登录；无重复加载、永久 loadingMore 或旧会话结果污染。
10. **资源释放：** 多次 push/pop 后 entry owner/组件与其任务被释放；下载继续；图片缓存不随每个页面实例重复持有。
11. **桌面布局：** 调整窗口大小、列表列数变化后恢复有效锚点；所有改动的可滚动区域保留同状态可拖动滚动条。
12. **局部数据变更：** 取消收藏、点赞、阅读进度、下载状态与屏蔽规则更新及时，不以“保留页面”为由显示永久过期数据。

自动化建议：共享层用假仓库和协程测试验证请求计数、加载去重、状态 reducer 与导航命令；Android/Desktop 集成层验证真实宿主的状态所有权、返回优先级和销毁；截图/人工验证只覆盖自动化不易确认的滚动与转场体验。

## 7. 最终决策建议

**当前建议批准 P0 + P1，而不是立即全量换库。**

- 默认实施方向：**Navigation 3**，解决栈条目所有权，同时提取页面状态。
- 若希望趁此机会做长期组件化：**Decompose 3.6.2 可进入同等严肃的候选验证**，其优势是组件生命周期，不是“装上以后自动不刷新”。
- 不论最终选择哪一个，成功标志都应是：**返回恢复原页状态、必要数据局部更新、请求不无故重跑、栈内状态保留而出栈资源及时释放。**

### 一手资料索引

- [Compose Multiplatform Navigation 3](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
- [Compose Multiplatform Navigation Compose](https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html)
- [Decompose 概览](https://arkivanov.github.io/Decompose/)、[ChildStack](https://arkivanov.github.io/Decompose/navigation/stack/overview/)
- [Decompose StateKeeper](https://arkivanov.github.io/Decompose/component/state-preservation/)、[InstanceKeeper](https://arkivanov.github.io/Decompose/component/instance-retaining/)
- [Decompose 3.6.2 发布说明](https://github.com/arkivanov/Decompose/releases/tag/3.6.2)
- [Voyager 概览](https://voyager.adriel.cafe/)、[ScreenModel](https://voyager.adriel.cafe/screenmodel/)、[恢复机制](https://voyager.adriel.cafe/state-restoration/)、[稳定发布查询](https://api.github.com/repos/adrielcafe/voyager/releases/latest)
- [Compose Navigation Reimagined](https://olshevski.github.io/compose-navigation-reimagined/)

资料快照仅代表调研当日抓取结果；版本示例、平台支持与 API 稳定性在正式实施前应再次核验。
