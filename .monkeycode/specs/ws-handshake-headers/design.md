# WebSocket 握手请求头与子协议

Feature Name: ws-handshake-headers
Updated: 2026-10-23

## 设计

`WsConfig` 与 `KtorWsEngine` 早在实现时就支持 `headers` 与 `subprotocols`，缺的只是数据模型、ViewModel 状态与界面。

**快照**：`WsSnapshot` 增加 `headers: List<KeyValueEntry>` 与 `subprotocols: List<String>`，两者都有默认值，旧快照反序列化时自动兜底为空。快照注释约定「字段只增不减」，这一改动符合既有兼容策略。

**子协议输入**：界面按逗号分隔的单个文本框编辑，`parseSubprotocols` 负责去空白、丢空段、去重并保持首次出现顺序。去重是必要的：服务端按顺序匹配，重复项会让部分实现拒绝握手。

**变量解析**：`connect()` 从「只解析 URL」改为一次性解析 URL、请求头键值、子协议三项，缺失键汇总到同一个 `missingVars` 提示，避免用户改一处报一次。请求头按 `enabled` 与键非空过滤后才进入配置。

**界面**：连接卡片内新增子协议输入框与 `CollapsibleSection` 包裹的 `KeyValueEditor`，请求头默认折叠，标题右侧显示已填条数；值列复用 `placeholderHints` 展示变量提示。

## 验证

单测覆盖子协议解析（空串、多段、重复）与快照往返、旧快照兼容。手工验证带 Authorization 头的连接与子协议协商。全量单测 300 项、Debug 编译与 Release 打包。
