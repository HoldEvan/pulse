# 技术设计：存储迁移 Room（#31）

## 方案

### 分层

```
UI / ViewModel
   │  StateFlow（不变）
Store（内存态 + PersistenceHandle）
   │  save() → 单模块写库
AppStatePersistence（门面）
   ├─ StateMapper：领域模型 ⇄ Room 实体，收口加解密
   ├─ snapshotJson()：生成兼容旧版的备份 JSON
   └─ restoreFromJsonElement()：备份 JSON → 各 Store
PulseDatabase（Room）
```

关键改动是 `PersistenceHandle` 从「全局一个」改为「每 Store 一个」。Store 内部 47 处 `persist.save()` 调用点完全不动，只是句柄绑定的回调指向各自的保存函数，从根上消除了全量重写。

### 表结构

| 表 | 说明 |
|---|---|
| `projects` | 项目 |
| `environments` / `environment_vars` | 环境与其变量（变量按 `envId + position` 主键） |
| `collections` / `collection_vars` | 文件夹树与其变量 |
| `saved_requests` | 接口快照，`position` 保存同级顺序 |
| `history` | 请求历史，`createdAt` 倒序读取 |
| `cookies` | 按 `projectId + domain + name + path` 主键，天然去重 |
| `monitor_targets` / `monitor_records` / `monitor_history` | 拨测目标、记录与趋势 |
| `ws_sessions` / `mqtt_subscriptions` / `ws_templates` | 协议会话 |
| `store_meta` | 自增 id 与单例配置的键值表 |

设计取向：

- 需要按字段查询的数据用真实列（历史、Cookie、变量）；只需整体读写的嵌套结构（请求头列表、拨测断言）存 JSON 列，避免为每个小列表建表。
- 计数器统一放 `store_meta`，新增模块只加键，不新增表。
- `exportSchema = true`，schema 输出到 `app/schemas`，升级时据此写迁移。

### 加解密

`StateMapper` 构造参数是 `FieldCipher`（`seal`/`open`）。生产实现包一层 `SecretField` + `SecretBox`，失败时降级明文；单测用 `PlainCipher` 或带前缀的假实现，因此映射逻辑是纯 JVM 可测的。

加密位置与旧版一致：环境/文件夹变量值、请求快照 `data`、历史 URL/头/正文、Cookie 值、拨测 URL/请求头值/令牌/密码/API Key/消息。

### 启动与迁移

`load()` 用 `store_meta.dbInitialized` 标记判断是否首次启动。之所以不用「projects 表是否为空」，是因为用户可能只改环境、项目表仍为空，空表判断会导致下次启动跳过恢复并丢数据。

首次启动流程：尝试解析 `app_data.json` → 写回各 Store → `saveAll()` → 写初始化标记。旧文件保留，便于回滚。解析失败也走 `saveAll()` 落种子数据并标记，避免每次启动重复尝试。

### 备份

`app_data.json` 从「运行时存储」降级为「交换格式」：

- `exportJson()` 由当前内存快照生成，结构与旧版逐字段一致。
- `BackupService.copyToUri` / `exportToWebDav` 改用 `ServiceLocator.exportBackupJson()`，不再读磁盘文件。
- 恢复链路不变：SAF/WebDAV 读文本 → `restoreFromJson` → `restoreFromJsonElement` → `saveAll()`。

### 线程

现有写入路径都在调用方线程同步执行（StateFlow 更新后立即落盘），为保持行为一致，数据库开启 `allowMainThreadQueries()`。后续可收敛为单一写协程队列，本次不改调用时序以免引入新的竞态。

## 测试

`StateMapperTest` 12 项：

- 环境、文件夹变量、请求快照、历史、Cookie、拨测目标/记录/趋势、协议会话/模板、集合的往返一致；
- 写入实体后敏感明文不出现（请求头、令牌、密码、正文）；
- 空值保持空串；
- `PlainCipher` 直通保证无密钥环境可用。

## 后续扩展点

- 新增模块：加表 + DAO + `StateMapper` 方法 + 一个 `PersistenceHandle`，不触碰其他模块。
- 分页：`history`、`monitor_records` 已有 `createdAt` / `checkedAt` 索引，可直接接 Paging。
- 异步写入：把 `PersistenceHandle` 的回调改为投递到单线程队列即可，Store 无需改动。
