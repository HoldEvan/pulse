# 拨测后台调度

Feature Name: monitor-background-work
Updated: 2026-10-23

## 设计

**分层**：把原来揉在 `MonitorScheduler` 里的「检查 + 落库 + 告警」抽成 `MonitorCheckRunner`，前台循环与后台任务共用，保证两条路径的记录与通知语义完全一致。

**到期判断**：新增 `MonitorDue.kt`，`monitorDelayMs` 负责频率钳制（15 秒 ~ 6 小时），`isCheckDue` 用持久化的上次检查时间判断是否到期，并留 20% 提前量，让周期任务与前台循环不会互相等待。

**后台调度**：`MonitorBackgroundScheduler` 是接口，`WorkManagerMonitorScheduler` 用 `PeriodicWorkRequestBuilder` 注册唯一周期任务，周期取 `max(15 分钟, 最短目标频率)`，`ExistingPeriodicWorkPolicy.UPDATE` 让频率调整就地生效。没有启用目标时调用 `cancelUniqueWork`。

**并发去重**：`MonitorScheduler` 增加 `inFlight` 集合，前台循环与后台唤醒同时命中同一目标时只有第一个执行，第二个直接返回。

**状态回填**：`MonitorScheduler` 初始化时从 `store.historyFor` 反推每个目标最近一次结果，界面不必等下一轮才有状态；告警状态机也从持久化历史取「上一轮」，进程重启不会重复告警。

**混淆**：WorkManager 按类名反射实例化 Worker，`proguard-rules.pro` 里对 `MonitorWorker` 加 keep 规则。

## 验证

单测覆盖频率钳制与到期判断（含提前量、从未检查）。手工验证：启用 15 秒与 1 小时两个目标，前台观察频率不变；杀进程后等待周期任务，记录继续增长且无重复告警。全量单测 304 项、Release 构建与签名校验。
