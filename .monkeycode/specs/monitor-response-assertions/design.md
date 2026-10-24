# 技术设计：拨测响应内容断言（#18）

## 方案

### 1. 模型

`data/model/MonitorModels.kt`：

```kotlin
data class MonitorAssertion(
    val type: String = "CONTAINS",   // CONTAINS / NOT_CONTAINS / JSON_PATH
    val expression: String = "",     // 关键字或 JSONPath，支持 {{变量}}
    val expected: String = ""        // 仅 JSON_PATH
)
```

`MonitorTarget` 新增 `assertions: List<MonitorAssertion> = emptyList()`（默认值兜底旧数据）。类型存 String 与 #17 method/bodyType/authType 策略一致。

### 2. 求值

`data/monitor/MonitorChecker.kt`：

- 新增 `internal fun evaluateAssertions(body, assertions, vars): String?`：逐条求值，返回第一条失败的本地化说明，全过返回 null。
- 表达式与期望值先经 `resolveText` 做变量解析，缺失变量返回 `monitor_assert_vars_missing`（带断言序号）。
- 判定：
  - CONTAINS：`body.contains(expression)`；
  - NOT_CONTAINS：`!body.contains(expression)`；
  - JSON_PATH：`JsonPath.eval(body, path)` 非空且首个元素 `extractString() == expected`；空结果或解析失败走 `monitor_assert_jsonpath_missing`，值不等走 `monitor_assert_jsonpath_failed`（实际值 `take(120)`）。
- `probeHttp` 接线：`statusPasses` 通过时才求值断言；`ok = statusOk && assertFail == null`；`detail = assertFail?.takeIf { statusOk } ?: 状态码说明`（复用 MonitorRun.detail，失败原因自然进记录）。
- 复用 `domain/JsonPath.kt` 既有求值器与 `extractString` 扩展，零新增解析依赖。

### 3. 持久化

`data/persistence/AppStatePersistence.kt`：parse/save 增 `assertions` 数组（type/expression/expected 三字段，默认值兜底）。

### 4. ViewModel / UI

- `MonitorViewModel.assertions: List<MonitorAssertion>`；openCreate 清空、openEdit 回填、draft 过滤空表达式。
- `MonitorScreen` HTTP 分支尾部 `CollapsibleSection`（显示条数）：每条断言一行类型下拉 + 删除图标按钮，表达式输入框（JSONPath 时标签切换为路径），JSONPath 追加期望值输入框；底部「添加断言」按钮。

### 5. 文案

三语新增 13 条：monitor_assertions_label、monitor_assert_add、monitor_assert_contains / not_contains / jsonpath、monitor_assert_keyword / path / expected、monitor_assert_contains_failed / not_contains_failed / jsonpath_failed / jsonpath_missing / vars_missing。

## 测试

`MonitorCheckerTest` 新增 4 项（L10n.t 在单测返回空串，只断言语义不断言文案）：

1. contains/not_contains 通过与失败；
2. JSONPath 取值相等、值不等、无匹配、非法 JSON；
3. 变量解析命中与缺失；
4. 端到端：状态码 200 但 JSONPath 不等 → run.ok=false。
