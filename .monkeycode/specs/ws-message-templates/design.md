# 技术设计：WS 消息模板（#22）

## 方案

### 1. 模型

`data/model/ProtocolSessionModels.kt`：

- `WsMessageTemplate(id, name, content, binary=false, encoding="HEX", createdAt=0)`。
- `WS_TEMPLATE_LIMIT = 50`。

`encoding` 存字符串而非枚举，避免持久化层依赖 `domain.net.BinaryEncoding`，读取时按名称匹配、未知值回退 HEX。

### 2. Store

`ProtocolSessionStore` 新增：

- `wsTemplates: StateFlow<List<WsMessageTemplate>>` 与 `nextTemplateId`。
- `saveWsTemplate(name, content, binary, encoding)`：名称 trim；同名覆盖（保留 id 与 createdAt）；新增时 `takeLast(WS_TEMPLATE_LIMIT)`；写入后 `persist.save()` 并返回条目。
- `deleteWsTemplate(id)`：过滤后比对，无变化直接返回，避免无效落盘。
- `snapshot()` / `restore()` 携带 `wsTemplates` 与 `nextTemplateId`；`restore` 重新计算 `nextTemplateId` 防止 id 冲突。

`ProtocolSessionStoreSnapshot` 追加两个带默认值的字段，保证旧调用点与旧数据兼容。

### 3. 持久化

`AppStatePersistence`：

- 读取 `wsTemplates` 数组（id、name、content、binary、encoding、createdAt），丢弃 `id < 0` 或名称为空的项；`wsTemplateNextId` 缺失时默认 1。
- 写入时逐条序列化并输出 `wsTemplateNextId`。

### 4. ViewModel

`WsViewModel`：

- 暴露 `templates = sessions.wsTemplates` 与 `templateLimitReached`。
- `saveTemplate(name, content): Boolean`：空名称/空内容返回 false；新增且已达上限返回 false；否则落库。
- `deleteTemplate(id)` 转发 Store。
- `applyTemplate(template)`：写入 `input`；二进制模板同时设置 `binaryMode=true` 与匹配编码（未知回退 HEX）；清除 `binaryError`。

### 5. UI

`WsScreen` 发送区顶部新增 `CollapsibleSection`（默认收起，itemCount 为模板数）：

- 每行 `WsTemplateRow`：左侧名称 + 内容预览（二进制模板显示「编码 · 内容」徽标），点击填充；右侧删除图标按钮。
- 底部「保存为模板」`AppTextButton`，达上限时禁用。
- 保存对话框：名称输入框、字符数提示、错误文案、取消/保存按钮。

### 6. 文案

三语新增 7 条：ws_templates_title、ws_template_save、ws_template_name_label、ws_template_hint、ws_template_invalid、ws_template_delete、ws_template_binary_badge。

## 测试

`ProtocolSessionStoreTest` 新增 5 项：同名覆盖保留 id、名称 trim 与快照往返、超限淘汰最旧、删除无变化不落盘、快照恢复后 id 递增。
