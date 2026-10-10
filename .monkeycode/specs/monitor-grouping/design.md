# Monitor Target Grouping

Feature Name: monitor-grouping
Updated: 2026-10-10

## Description

拨测目标增加一个一级分组字段 `group`。目标列表按该字段分块展示，每组带组头；分组名为空的目标归入「未分组」组头。分组名仅在目标编辑弹窗中自由填写/修改，不做单独的分组管理界面。

## Architecture

```
MonitorScreen (Column + verticalScroll)
  └── 目标列表 Card
       ├── GroupHeader("未分组")          ← 来自 targets.filter { it.group.isBlank() }
       │   └── MonitorTargetRow x N
       ├── GroupHeader("线上")            ← 按 { group } groupBy 剩余目标
       │   └── MonitorTargetRow x N
       └── ...（保持 createdAt 时间倒序）
```

数据流：

```
MonitorViewModel.draft()
  → MonitorTarget(group = groupInput.trim())
  → MonitorStore.create/update
  → PersistenceHandle.save() → AppStatePersistence.save()
      monitors[] 数组新增 "group" 字段
  → 读回 parseMonitorTarget: o.stringValue("group")，旧数据缺字段默认 ""
```

## Components and Interfaces

### Data Model（`data/model/MonitorModels.kt`）

`MonitorTarget` 增加：

```kotlin
data class MonitorTarget(
    ...
    val group: String = "",
    ...
)
```

默认值 `""` 保证旧构造调用不破坏。

### Persistence（`data/persistence/AppStatePersistence.kt`）

- 写（约 331 行 `put("monitors", ...)` 内）：新增 `put("group", m.group)`
- 读（`parseMonitorTarget`，424 行）：新增 `group = o.stringValue("group")`，配合现有 helper 对缺字段返回默认 `""`，天然兼容旧备份

### ViewModel（`ui/monitor/MonitorViewModel.kt`）

- 新增 `var groupInput by mutableStateOf("")`
- `openCreate()`：重置 `groupInput = ""`
- `openEdit(target)`：`groupInput = target.group`
- `draft()`：`group = groupInput.trim()`

### MonitorScreen（`ui/monitor/MonitorScreen.kt`）

- 目标列表 Card（129-170 行）：将现有 `targets.forEach` 改为按 `{ it.group }` 分组渲染：

```kotlin
val grouped = targets.groupBy { it.group }
// 组顺序：空分组（未分组）最前，其余按组内最新 createdAt 出现的顺序
```

组头组件复用现有的 `CardSectionTitle`（`ui/common/SectionTitle.kt`）。

- 编辑弹窗（184 行起）：在名称 TextField 下方新增一个 `TextField`，label 用新字符串 `monitor_group_label`

### Strings

新增字符串资源（三语言 values / values-zh-rTW / values-en）：

- `monitor_group_label`：分组（Group）
- `monitor_group_ungrouped`：未分组（Ungrouped）

## Data Models

持久化 JSON 示例（新增 `group` 字段）：

```json
{
  "id": 1,
  "projectId": 5,
  "name": "线上主站",
  "type": "HTTP",
  "host": "",
  "port": 0,
  "url": "https://example.com/health",
  "group": "线上",
  ...
}
```

旧数据无 `group` 字段时解析为 `""`，UI 进「未分组」组块，无需迁移。

## Correctness Properties

1. 分组名 trim 后持久化，保证「线上」与「 线上 」归为一组
2. 空字符串与纯空白分组名等价，均归入「未分组」
3. 组内目标保持现有 `sortedByDescending { createdAt }` 时间倒序不变
4. 修改分组名后的目标在下次渲染中立即归入新组且顺序正确
5. recordFailure / 删除目标 / 记录页逻辑均不依赖分组字段，行为不变

## Error Handling

- 分组名允许为空；不要求必填，无校验错误分支
- 输入极长分组名：沿用名称输入框现有行为，无长度上限，不做额外处理

## Test Strategy

- 模型层：构造含/不含/空白 `group` 的目标，验证 `groupBy` 分组结果与组顺序
- 持久化：`parseMonitorTarget` 对无 `group` 字段的 JSON 返回 `""`；save 后 reload 往返一致
- UI 手动验证：新增/编辑/清空分组名后，目标列表组头显示与目标归属正确；旧备份导入后目标全部出现在「未分组」

## References

[^1]: (MonitorScreen.kt#L129) - [目标列表 Card](/workspace/app/src/main/java/com/evan/pulse/ui/monitor/MonitorScreen.kt)
[^2]: (MonitorModels.kt) - [MonitorTarget 数据模型](/workspace/app/src/main/java/com/evan/pulse/data/model/MonitorModels.kt)
[^3]: (AppStatePersistence.kt#L331) - [monitors 持久化](/workspace/app/src/main/java/com/evan/pulse/data/persistence/AppStatePersistence.kt)
[^4]: (MonitorViewModel.kt#L173) - [draft() 构建目标](/workspace/app/src/main/java/com/evan/pulse/ui/monitor/MonitorViewModel.kt)