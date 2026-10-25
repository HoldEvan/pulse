# 技术设计：SSE 断点续连与事件统计（#23）

## 方案

### 1. 纯逻辑

新文件 `domain/sse/SseResume.kt`：

- `SseResume.HEADER = "Last-Event-ID"`。
- `lastEventId(manual, seen): String?`：优先本次会话最后见到的 id，其次手动值；空白视为未设置。
- `SseStreamStats(total, byEvent, lastEventId)` 不可变数据：
  - `record(event, id)`：事件名 trim 后为空回退 `message`；空 id 不覆盖已有断点。
  - `breakdown()`：次数倒序 + 名称升序，保证渲染顺序稳定。
  - `reset()`。

### 2. 模型

- `SseConfig` 新增 `lastEventId: String = ""`（手动断点）。
- `SseSnapshot` 新增 `lastEventId: String = ""`，旧快照缺字段时默认空串。

### 3. 引擎

`KtorSseEngine.runStream`：

- 循环外维护 `resumeId: String?`，每轮用 `SseResume.lastEventId(config.lastEventId, resumeId)` 计算待发送断点。
- 若用户请求头里已有 `Last-Event-ID`（忽略大小写），不再追加，避免重复头。
- `parseStream` 增加 `onEventId: (String) -> Unit` 回调，在 `flush()` 开头把非空 id 回传给循环，实现「收到即更新断点」。
- 断点只在收到 id 时更新，符合 SSE 规范的重连语义。

### 4. ViewModel

`SseViewModel`：

- 新增 `lastEventIdInput`（输入框）与 `stats`（`SseStreamStats`，private set）。
- `FrameReceived` 分支先 `stats.record(event.event, event.id)` 再追加预览与日志。
- `connect()` 重置 `stats` 并透传 `lastEventId`；`clearLog()` 同时重置统计。
- `adoptLastEventId()` 把当前断点写回输入框。
- 快照 `snapshot()`/`restore()` 携带 `lastEventId`。

### 5. UI

`SseScreen`：

- 配置区新增「断点 Last-Event-ID」输入框与变量提示。
- 事件流上方新增统计卡片：标题 + 总帧数、事件名/次数列表、当前断点与「回填断点」按钮（无断点时禁用）。

### 6. 文案

三语新增 8 条：sse_last_event_id_label、sse_stats_title、sse_stats_total、sse_stats_count、sse_stats_empty、sse_resume_current、sse_resume_none、sse_resume_adopt。

## 测试

新增 `SseStreamStatsTest` 9 项：总帧与分类计数、空事件名回退 message、breakdown 排序、断点取最新非空 id、无 id 帧保留断点、reset 清空、`SseResume` 三种优先级分支。
