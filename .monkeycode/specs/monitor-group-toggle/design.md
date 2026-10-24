# 技术设计：拨测分组级启停（#19）

## 方案

### 1. 纯逻辑

新文件 `data/monitor/MonitorGroupPolicy.kt`：

- `applyEnabledByGroup(targets, group, enabled)`：`group == null` 作用于全部，空串表示未分组；其余目标原样返回，返回新列表。
- `isGroupFullyEnabled(targets, group)`：分组非空且成员全部启用。
- `isAllEnabled(targets)`：非空且全部启用（空列表返回 false，避免菜单项误置灰）。

### 2. 存储

`MonitorStore.setEnabledForGroup(group: String?, enabled: Boolean): Int`（`di/ServiceLocator.kt`）：

- 复用纯函数生成新列表，统计差异条数；为 0 直接返回，不落盘。
- 有变更时写入 `_targets` 并 `persist.save()`，返回变更条数。

### 3. ViewModel

`MonitorViewModel`：

- `setGroupEnabled(group, enabled)`：调用 store，变更后 `scheduler.sync()`。
- `setAllEnabled(enabled)`：等价 `setEnabledForGroup(null, enabled)`。

### 4. UI

- `CollapsibleSection` 新增可选 `trailing` 插槽（标题行内、计数左侧），用于分组级按钮；默认 null 不影响既有调用方。
- `MonitorScreen` 分组渲染处：计算 `isGroupFullyEnabled`，`trailing` 内放 32dp `AppIconButton`，图标 `MiuixIcons.Light.Pause/Play`，点击切换整组。
- 新增「拨测目标」`SectionTitleRow` + `AppIconDropdownMenu`：「全部启用」在已全启用时置灰、「全部暂停」在无启用项时置灰；菜单触发区为 `More` 图标。
- 目标为空时不渲染菜单。

### 5. 文案

三语新增 6 条：monitor_targets_title、monitor_targets_more、monitor_enable_all、monitor_pause_all、monitor_group_enable、monitor_group_pause。

## 测试

新增 `MonitorGroupPolicyTest` 6 项：分组暂停只影响本组、空串作用未分组、null 作用全部、未知分组无变化、整组启用判定、全量判定含空列表。
