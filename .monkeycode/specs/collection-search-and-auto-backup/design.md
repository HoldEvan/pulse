# 集合搜索与定时自动备份

Feature Name: collection-search-and-auto-backup
Updated: 2026-10-23

## 设计

### 集合搜索

不新增导航目标：搜索框常驻集合页顶部，关键词非空时整页切换为结果列表，清空后回到面包屑浏览。这样返回键语义不变，也不会有「搜索页从哪进来」的问题。

`CollectionsViewModel.searchRequests` 只把当前项目的接口纳入范围，匹配名称与 `targetUrl()`（HTTP/WS/SSE 的地址、MQTT 的主题），结果带 `folderPath`（`CollectionTree.pathOf` 拼接）用于区分同名接口。界面把 `if (searching) … else …` 作为两个互斥分支，搜索结果点击复用既有的 `load()`，因此加载与切页行为与正常浏览完全一致。

### 定时自动备份

`AppSettings` 增加 `autoBackupEnabled`、`autoBackupIntervalIndex`、`autoBackupKeepCount`，周期选项集中在 `AUTO_BACKUP_INTERVAL_HOURS = [6, 12, 24, 72, 168]`，越界下标回落到每天一次，保留份数收敛到 1–30。

`AutoBackupRunner` 负责单次执行：导出 → 按保留份数清理。清理时向 `listRemote` 多要一份（且不少于 10），否则无法判断是否还有更旧的备份。`BackupService.listRemote` 与 `WebDavClient.list` 增加 `limit` 参数，默认仍是 10，手动浏览行为不变。

`BackupBackgroundScheduler` 是接口，`WorkManagerBackupScheduler` 用唯一周期任务实现，周期取 `max(15 分钟, 设置周期)`，关闭时 `cancelUniqueWork`。`AutoBackupScheduler` 订阅 `SettingsStore`，把开关与周期变化经 `distinctUntilChanged` 推给后台调度，设置改动无需重启即刻生效。

`AutoBackupWorker` 在执行时重新读取设置：任务可能在排队后设置已被改掉，以执行时刻为准；未配置 WebDAV 时直接成功返回，避免无意义重试。

### 混淆

`AutoBackupWorker` 与已有的 `MonitorWorker` 一样由 WorkManager 反射实例化，`proguard-rules.pro` 补 keep 规则。

## 验证

单测覆盖：搜索路径与后代排除（3 项）、自动备份配置映射与周期回落（5 项）。手工验证：跨文件夹搜索命中名称与 URL、点击结果切页；开启自动备份后修改周期，远端按份数裁剪。全量单测 322 项与 Release 构建。
