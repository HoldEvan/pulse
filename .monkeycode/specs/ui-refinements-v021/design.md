# v0.2.1 界面打磨

Feature Name: ui-refinements-v021
Updated: 2026-10-23

## 设计

**折叠体系**：新增两个共用组件。`CollapsibleSection` 负责分组容器（标题 + 条目数 + `remember(title)` 的展开态），`CollapsibleKeyValueTable` 负责两列键值（键列定宽、值列 `weight(1f)` 换行），两者共用同一套标题行样式与图标。默认展开态由调用方决定：请求头折叠、响应头展开、监控分组展开。

**响应体标签**：`ResponseBodyView` 把标签最小宽度从 44dp 提到 84dp、上限 160dp 并开启 `scrollable`。整排因此必然溢出，两种皮肤都得到一致的横滑行为，文字不再被压成两三个字符。

**JSON 树**：展开态在键名后补 `openBracket(element)`，容器结束处插入 `JsonCloseRow` 渲染配对右括号，缩进与左括号对齐；折叠态保留预览。括号用 `onSurfaceSummary`，与键名的 `onSurface` 区分层级。

**监控记录**：`MonitorViewModel` 增加 `recordTargetId`，`openRecordsForTarget` / `openAllRecords` 两个入口分别设置与清空。`MonitorRecordsScreen` 把 `targetId` 加入筛选 `remember` 的 key，并在列表首项插入「仅看 X」卡片，用 `animateItem` 的淡出与 `spring` 位移实现取消时的平滑过渡。`MonitorTargetRow` 整行可点击进入记录页，行内动作菜单重排为趋势在前、编辑在后。

**Cookie 删除**：`CookieViewModel` 把三种删除改为「先 `requestDelete` 记录意图、再 `confirmDelete` 落地」，意图用 `sealed interface CookieDeleteRequest`（单条 / 域名 / 项目）。界面统一渲染一个确认对话框，文案按意图分支。这一批改动单独打出了 `Pulse-v0.2.1-cookie-confirm-release.apk` 用于先行验证。

**主题顺序**：新增 `ThemeModeDisplayOrder = listOf(0, 2, 1)`，展示顺序与持久化下标解耦：下拉项按该顺序取名，选中下标经 `indexOf` 反查，回写时再映射回真实下标，已存设置不受影响。

**环境入口**：`ProjectBar` 增加可选 `onAddEnv`，在资源页环境标签时由 `PulseApp` 注入 `envViewModel.openCreate()`；`EnvViewModel` 从页面内部 `viewModel()` 改为外部传入，顶栏按钮与页面共享实例。`SettingsViewModel.resourceTab` 默认值改为 1（环境标签）。

**启动图标**：新增 `values-v31/ic_launcher_background.xml`，Android 12+ 取 `@android:color/system_accent1_200`，低版本沿用原有背景色。

## 验证

手工核对六个界面的折叠、横滑、展开括号、记录限定、删除确认与主题顺序；确认三语资源无缺失。运行全量单测与 Release 构建，核验 APK 版本 0.2.1 / versionCode 39 与签名。
