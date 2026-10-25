# 技术设计：WebSocket 二进制帧（#21）

## 方案

### 1. 纯逻辑

新文件 `domain/net/BinaryPayload.kt`：

- `BinaryEncoding { HEX, BASE64 }`。
- `decode(text, encoding): Result<ByteArray>`：Hex 允许空白、冒号与 `0x` 前缀，奇数长度或非法字符失败；Base64 用 `java.util.Base64`（minSdk 26），空白忽略、非法失败。
- `hex(bytes)`：大写无分隔；`base64(bytes)`：标准 Base64。
- `preview(bytes, encoding)`：取前 `PREVIEW_LIMIT=256` 字节编码，超出追加 `…`。

### 2. 模型与引擎

- `WsEvent` 新增 `BinarySent(preview, sizeBytes, timestamp)` 与 `BinaryReceived(preview, sizeBytes, timestamp)`。
- `WsEngine` 接口新增 `suspend fun sendBinary(bytes: ByteArray)`。
- `KtorWsEngine`：
  - `sendBinary` 发送 `Frame.Binary(true, bytes)`，成功后发 `BinarySent`（Hex 预览），失败发 `Failed`；
  - `receiveLoop` 新增 `is Frame.Binary -> readBytes()` 分支，发 `BinaryReceived`（Hex 预览）；
  - 补 `io.ktor.websocket.readBytes` 导入。

### 3. ViewModel

`WsViewModel` 新增 `binaryMode`、`binaryEncoding`、`binaryError`：

- `send()` 在变量解析成功后按 `binaryMode` 分流：文本走原路径，二进制调用 `sendBinary(resolved)`。
- `sendBinary`：`BinaryPayload.decode` 成功则 `engine.sendBinary` 并清空输入与错误；失败保留输入并设置 `binaryError`。
- `dismissBinaryError()` 供模式/编码切换与错误展示关闭。
- 事件收集新增两个二进制分支，通过 `ws_log_binary_sent/received` 文案格式化日志（`%1$d B`、`%2$s` 预览）。

### 4. UI

`WsScreen` 发送区改为 Column：

- 首行「发送二进制帧」文案 + `AppSwitch`；
- 开启时显示 `AppDropdown` 编码（Hex / Base64）；
- 输入框标签随模式切换（文本 / Hex 或 Base64 内容），发送按钮不变；
- 输入框下方用 error 色显示 `binaryError`。

### 5. 文案

三语新增 9 条：ws_send_text、ws_send_binary、ws_binary_encoding、ws_binary_hex、ws_binary_base64、ws_binary_input_label、ws_binary_invalid、ws_log_binary_sent、ws_log_binary_received。

## 测试

新增 `BinaryPayloadTest` 7 项：Hex 大写编码、含分隔符与前缀解码、奇数长度/非法字符/空白拒绝、Base64 往返、Base64 非法拒绝、预览截断长度、短帧不截断。
