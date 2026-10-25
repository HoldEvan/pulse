# 技术设计：诊断日志导出（#36）

## 方案

### 领域层（纯 JVM，可单测）

`domain/diagnostics/Redactor.kt`：统一脱敏出口，三组函数各自可测。

- `maskUrl(raw)`：手工切分而非依赖 `java.net.URI`——诊断里常见不合法地址（缺协议、含中文、占位符 `{{host}}`），`URI` 会直接抛异常。切分顺序为 scheme → fragment → query → userinfo → path。查询串按 `&` 拆分后只保留键名；路径按 `/` 分段，超过 32 字符的段退化为 `前4字符…(长度)`，覆盖把 token 塞进路径的机器人接口（如 Telegram Bot API）。
- `maskHeaderLine(line)`：冒号前后切分，头名小写命中 `SECRET_HEADERS` 时只留 `名称: ***`，否则对值走 `maskText`。
- `maskText(text)`：按「结构化令牌 → 敏感键值 → 邮箱 → 手机号」顺序替换。顺序很关键：先替换 Bearer/JWT 可以避免 `token=` 规则与令牌内部特征互相干扰；邮箱放在键值之后，避免被键值规则先行切断。

`domain/diagnostics/DiagnosticReport.kt`：

- 输入用 `DiagnosticInput` 四个扁平 Map/List 表达（meta、settings、counts、两类失败样本），脱敏与渲染都在此处收口，采集侧可以放心传原始值。
- 输出固定四段 `meta` / `settings` / `counts` / `failures`，顶层再带 `schemaVersion = 1` 与 `generatedAt`，便于后续脚本按版本解析。
- `MAX_SAMPLES = 20` 截断样本；`suggestFileName` 生成带时间戳的文件名；`MIME_TYPE = application/json`。

### 数据层

`data/diagnostics/DiagnosticCollector.kt`：

- `collect(settings, now)` 读取 `ServiceLocator` 各 Store 的公开快照（`historyStore.entries`、`monitorStore.records/targets/history`、`collectionStore`、`envStore`、`projectStore`、`cookieStore`），只取计数与失败样本字段。
- 密码类设置（`proxyPassword`、`webdavPassword`、`webdavUsername`）不进入诊断包，改为 `proxyAuthSet` / `webdavAuthSet` 两个布尔标记。
- `meta` 中的版本信息来自 `BuildConfig`（`VERSION_NAME`、`VERSION_CODE`、`BUILD_TIME_MS`、`DEBUG`），设备信息来自 `Build` 与 `Runtime`。
- `exportTo(uri, settings)` 在 `Dispatchers.IO` 上用 `openOutputStream(uri, "wt")` 写入，失败抛异常给上层提示。

### 界面层

- `SettingsViewModel` 新增 `DiagnosticCollector`、`diagnosticFileName()`、`exportDiagnostics()`、`onDiagnosticsPicked(uri)`、`onDiagnosticsCancelled()` 与 `setDiagnosticsLauncherHandler`，复用既有 `BackupStatus` 的 busy/消息状态，界面无需新增状态源。
- `MainActivity` 注册 `CreateDocument("application/json")` 启动器，与本地备份的两个启动器并列，回调分别落到诊断与备份入口，互不干扰。
- 设置页「备份与恢复」分组下新增诊断卡片：标题、说明（明确写出「会先脱敏，不含请求正文」）与导出按钮，按钮复用以 `status.busy` 作为禁用条件。

### 文案

三语新增 4 条：`diagnostics_title`、`diagnostics_export`、`diagnostics_hint`、`diagnostics_export_done`。

## 测试

`RedactorTest` 12 项：查询值掩码、认证信息移除、锚点丢弃、长路径段截断、空串与相对路径直通、敏感头整体替换、普通头保留但值脱敏、Bearer 替换、JWT 替换、邮箱与手机号替换、键值对敏感字段替换、无敏感内容原样返回。

`DiagnosticReportTest` 7 项：四段结构与计数、设置值查询串脱敏、失败历史字段精简、拨测信息令牌脱敏、样本数受 `MAX_SAMPLES` 约束、建议文件名格式、MIME 类型。
