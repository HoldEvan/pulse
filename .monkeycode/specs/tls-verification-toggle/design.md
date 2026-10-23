# TLS 证书校验开关

Feature Name: tls-verification-toggle
Updated: 2026-10-23

## 设计

**引擎级配置**：TLS 策略与代理一样无法按请求切换，因此三个基于 OkHttp 的引擎把缓存键从 `ProxyConfig` 换成 `ClientKey(proxy, insecureTls)`。这是关键改动——若继续按代理单独缓存，打开开关后仍会复用已建立的严格校验客户端，表现为「开关无效」。

**跳过校验的实现**：`InsecureTls` 提供信任任意证书链的 `X509TrustManager`、复用的 `SSLSocketFactory`（`SSLContext` 只初始化一次）与 `hostnameVerifier { _, _ -> true }`，通过 `OkHttpClient.Builder.applyInsecureTls(enabled)` 挂载。扩展函数返回 Builder 本身，保持链式调用。

**配置传递**：`HttpCallConfig`、`WsConfig`、`SseConfig` 各加 `insecureTls: Boolean = false`，默认关闭。对应快照 `HttpSnapshot` / `WsSnapshot` / `SseSnapshot` 同步加字段，遵循「字段只增不减」，旧数据自动兜底。

**界面**：抽 `InsecureTlsRow` 开关组件，位于 HTTP 网络标签（代理开关下方）、WS 连接卡片（代理开关下方）、SSE 网络标签。关闭时用次要色说明用途，打开时用 `AppTheme.colors.error` 显示「连接无法防中间人攻击」的警示。

**范围**：MQTT 用 Paho 自己的 socket 工厂，`ssl://` 的信任链由 `SSLSocketFactory` 决定，本次不纳入，避免与既有的「ssl 走代理不支持」逻辑纠缠；路线图保留该项。

## 验证

单测覆盖扩展函数不替换 Builder、开启后接受任意主机名、信任管理器放行空证书链。手工验证：对自签名服务开关关闭时握手失败（SSL 错误分类）、打开后请求成功；关闭开关后同一地址恢复失败，证明缓存隔离生效。全量单测 314 项与 Release 构建。
