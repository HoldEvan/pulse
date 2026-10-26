# Webhook 二级页与设置页结构

Feature Name: webhook-secondary-page
Updated: 2026-10-26

## 设计

**数据层**：`AppSettings` 新增 `webhookMethodIndex`（0 = POST，1 = GET，落盘时收拢到 0..1）与 `webhookParams`（查询参数列表）。`SettingsStore` 把上一轮的请求头编解码泛化为 `decodeKeyValueList` / `encodeKeyValueList`，请求头与参数共用同一套 JSON 结构：值经 `SecretField` 加密、键明文，坏数据整体回退空列表。

**发送器**：`WebhookSender.methodOf()` 按下标返回 `HttpMethod.POST` / `HttpMethod.GET`。`buildUrl()` 把启用的参数按顺序编码为查询串：地址无 `?` 时补 `?`，已有查询时补 `&`，以 `?`/`&` 结尾时不重复分隔符；空格编码为 `%20`。GET 请求 `bodyType = NONE`、请求体为空串，`buildHeaders()` 同步跳过内置 `Content-Type`（用户自定义头仍然附加）。POST 链路与原行为一致。`sendTest` 的事件地址改用 `buildUrl` 结果，便于核对最终 URL。

**Webhook 二级页**：新增 `WebhookScreen`（`ui/settings`）与 `PulseRoute.Webhook`，沿用二级页大标题模板。页面分三个区块：「请求」卡片放下拉方法、地址输入与参数编辑器；方法为 POST 时显示「消息内容」卡片（平台格式 + 模板）；「请求头」卡片复用 `KeyValueEditor`；底部测试按钮与结果提示。编辑器沿用「进入时取一次本地状态」的模式，避免持久化回写收敛输入中的空行。

**设置页入口**：通用区块保留启用开关，其下新增 `AppArrowRow` 入口，副标题显示已配置地址（空时显示未配置提示）。原嵌在设置页里的 URL、格式、模板、请求头与测试整块移除。

**设置页结构**：诊断卡片移到「关于」标题之下、关于卡片之上。WebDAV 卡片内容列拆为「字段与按钮（内缩 12dp）— 自动备份开关（卡片级）— 周期与份数（内缩 12dp，仅启用时）」三段，开关行铺满卡片宽度使水波纹贴合圆角；独立的自动备份卡片删除。

## 验证

新增 `WebhookRequestTest` 8 项：方法下标映射、GET 无内置 Content-Type、无参数原样返回、参数追加与合并、结尾分隔符去重、空键与停用项忽略。原 `WebhookHeadersTest` 改用泛化后的编解码函数，语义不变。全量单测 594 项通过；Release 构建成功并覆盖根目录 APK。

手工验证：设置页开启 Webhook 后点入口进入二级页；方法切到 GET 时消息格式区块消失，测试发送命中 `地址?参数` 形式的 GET 请求；自动备份开关在 WebDAV 卡片底部，关闭后周期与份数隐藏；诊断卡片出现在「关于」上方。
