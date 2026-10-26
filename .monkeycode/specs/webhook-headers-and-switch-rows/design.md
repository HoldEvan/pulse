# Webhook 请求头与开关行统一

Feature Name: webhook-headers-and-switch-rows
Updated: 2026-10-26

## 设计

**请求头存储**：`AppSettings` 新增 `webhookHeaders: List<KeyValueEntry>`。`SettingsStore` 用独立的 `headersSerializer` 把列表序列化为 JSON 存入 `KEY_WEBHOOK_HEADERS`，值经 `SecretField` 加密、键保持明文以便排查；解析失败整体回退空列表，避免坏数据阻塞整份设置读取。该字段不进备份导出链路，与既有 Webhook 密钥字段保持一致。

**请求头合并**：`WebhookSender.buildHeaders(settings)` 先放默认 `Content-Type`，再用用户头按大小写不敏感的方式覆盖同名项。抽成 `internal` 函数以便单测直接验证合并语义，同时兼容 `OkHttpEngine.declaredContentType()` 的既有约定。

**编辑器**：设置页 Webhook 区块复用 `KeyValueEditor`，键值在 `KeyValueEntry` 与 UI 层 `KeyValue` 之间互转。编辑器用无 key 的 `remember { }` 持有本地列表，避免持久化回写导致输入中的空键行被收敛掉。

**卡片圆角裁剪**：Compose 的 `Modifier.padding` 不接受负值，无法用负边距抵消卡片内边距。改为在 `AppCard` 内容层统一挂 `cardContentClip()`：MIUIX 皮肤走 `squircleClip(16.dp)`，Material 皮肤走 `clip(MaterialTheme.shapes.extraLarge)`。开关行作为 `AppCard` 的直接子级铺满宽度，水波纹随内容层一起被裁剪到圆角内。

**开关行提升**：HTTP 网络标签、WS 连接卡片、SSE 网络标签、MQTT 连接与发布卡片的开关行全部提到卡片级；原本与字段同处内缩 `Column` 的布局拆成「字段列（内缩 12dp）— 开关行（无内缩）— 操作列（内缩 12dp）」三段。`InsecureTlsRow` 改用 `AppSwitchRow` 的 `summary`，关闭时说明适用场景、开启时以警示色说明中间人风险，替代原先常驻的风险提示行。

**副标题**：为本轮涉及的开关补齐三语短副标题（代理、跟随重定向、WS 自动重连与二进制模式、MQTT retain、跳过证书校验）。WS 二进制模式的标题本身随状态切换，副标题仅在二进制模式开启时出现。

**WS 滚动**：根 `Column` 挂 `subPageOverScroll().verticalScroll(subPageScrollState())`，事件流卡片从 `weight(1f)` 改为固定 `320.dp` 并保留内部滚动，与 SSE、MQTT 两页的滚动策略对齐。

**MQTT 订阅提示**：`mqtt_topic_label` 精简为 `Topic`，把通配符与 `@QoS` 写法说明移到输入框上方的 12sp 次要色小字，避免长提示塞进浮动标签。

## 验证

新增 `WebhookHeadersTest` 7 项，覆盖编解码往返、值加密标记、坏数据回退、默认头与用户头同名覆盖、大小写不敏感、空列表保持默认头。全量单测 586 项通过；Release 构建成功，versionCode 39 / 0.2.1，签名校验通过。

手工验证：设置页增删请求头后重启应用配置仍在；Webhook 测试发送在服务端看到自定义头；WS 页面长内容可滚动到最底部；四个协议页开关行点击水波纹沿卡片圆角收边；MQTT 订阅输入框标签不再被长提示挤压。
