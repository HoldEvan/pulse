# 集合请求原地覆盖

Feature Name: collection-request-overwrite
Updated: 2026-10-23

## 设计

把绑定关系抽成独立类 `LoadedRequestTracker`，由 `CollectionStore` 驱动，内部只有一个 `mutableStateOf<Long?>` 的 `requestId`，避免四个 ViewModel 各写一份状态机。

两个入口：`track(id)` 在加载集合条目、或保存对话框新建成功后调用；`saveOver(snapshot)` 在有绑定时调用 `store.updateRequest`，成功则弹「已更新」并返回 true。`updateRequest` 返回 false（条目已不存在）时把 `requestId` 清空并返回 false，调用方据此回退到保存对话框。

`CollectionStore.saveRequest` 的返回类型从 Unit 改为 Long（名称为空返回 -1），让保存对话框能把新建 id 交给页面。`CollectionRunner` 的 `run` 增加 `onStep` 回调，与本次覆盖逻辑同批改动，服务于运行器边跑边显示。

`HttpViewModel` / `WsViewModel` / `SseViewModel` / `MqttViewModel` 各自持有一个 tracker，暴露 `saveOverLoaded()` 与 `markSaved(id)`；界面侧把保存按钮的点击从「直接弹窗」改为「先尝试覆盖，返回 false 再弹窗」。

## 验证

单测覆盖 `updateRequest` 的命中与未命中分支、`saveRequest` 返回 id。手工核对四个协议页的加载 → 修改 → 保存 → 再保存流程。运行全量单测与 Release 构建。
