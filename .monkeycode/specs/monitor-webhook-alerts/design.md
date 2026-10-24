# 技术设计：拨测 Webhook 告警（#20）

## 方案

### 1. 纯逻辑

新文件 `domain/webhook/WebhookPayload.kt`：

- `WebhookFormat`：GENERIC / WECOM / DINGTALK / FEISHU / SLACK。
- `WebhookEvent(name, status, detail, durationMs, checkedAt, type, address)` + `placeholders()`。
- `defaultTemplateFor(format)`：通用返回 JSON 模板 `{"name":"{{name}}",...}`，平台返回文本模板 `{{status}}｜{{name}}（{{address}}）{{detail}}`。
- `render(template, event)`：逐个替换已知占位符，再用正则清除残留未知占位符。
- `escape(text)`：JSON 字符串转义（引号、反斜杠、\n\r\t、控制字符 \\uXXXX）。
- `build(format, template, event)`：通用直接用渲染结果；平台按各自结构包裹并转义内容。

### 2. 发送器

新文件 `data/webhook/WebhookSender.kt`：注入 `HttpEngine`，`send(settings, event)` 校验启用与地址后 POST（RAW body、10s 超时、设置里的代理），返回是否 2xx；异常吞掉返回 false。`sendTest(settings)` 构造固定测试事件并强制启用走同一路径。

### 3. 设置

`AppSettings` 新增 `webhookEnabled / webhookUrl / webhookFormatIndex / webhookTemplate`；`SettingsStore` 增 4 个 Preferences 键与读写映射，index 越界由读取端 coerce。

### 4. 接线

- `ServiceLocator.webhookSender`（lazy，复用 `httpEngine`）。
- `MonitorScheduler` 构造 `MonitorCheckRunner` 时传入 sender。
- `MonitorCheckRunner.report` 改为 suspend，在三种状态变化分支中除系统通知外调用 `sendWebhook(target, run, statusRes)`；`sendWebhook` 检查 `webhookEnabled`，事件状态取 `L10n.t(webhook_status_*)`，地址取 `target.addressText()`。

### 5. UI

`SettingsViewModel`：`setWebhookEnabled/Url/FormatIndex/Template`、`sendWebhookTest()`（空地址直接失败，结果写 `webhookTest: StateFlow<WebhookTestState?>`）。

`SettingsScreen`：通知开关下方新增 Webhook 开关；启用后展开地址输入、格式下拉（5 项，下标以 `WebhookFormat.entries.lastIndex` 约束）、模板输入、变量提示、发送测试按钮与结果文案（失败用 error 色）。

### 6. 文案

三语共 17 条：webhook_status_down/slow/recover、settings_webhook、_summary、_url、_format、_template、_template_hint、_test、_test_ok、_test_failed、webhook_format_generic/wecom/dingtalk/feishu/slack。

## 测试

新增 `WebhookPayloadTest` 8 项：占位符替换、未知占位符清除、通用模板即整体、通用默认模板字段、企微/钉钉结构、飞书/Slack 结构、特殊字符转义往返、默认模板按格式区分。
