# 技术设计：HMAC 与摘要扩展（#28）

## 方案

### 领域层

`domain/tools/HashTool.kt` 扩展：

- `HashAlgorithm` 增加 `macId` 字段（`HmacMD5` / `HmacSHA1` / …），枚举追加 SHA-384 与 SHA-512；既有枚举顺序不变，界面下标不受影响。
- 新增 `DigestOutput { HEX, BASE64 }`。
- `hash(text, algorithm, output)`：空文本返回空串；Hex 用 `%02x` 小写，Base64 用 `java.util.Base64`。
- `hmac(text, key, algorithm, output)`：密钥为空返回空串（没有密钥的 HMAC 没有意义）；文本允许为空。用 `Mac.getInstance(algorithm.macId)` + `SecretKeySpec`，UTF-8 字节。
- 保留原有 `hash(text, algorithm)` 两参重载，旧调用与旧测试不受影响。

### 界面

`ui/tools/HashToolScreen.kt` 重构：

- 顶部 `AppTabRow` 切换「摘要 / HMAC」。
- 文本输入框；HMAC 模式下追加密钥输入框。
- 算法改为 `AppDropdown`（5 项），不再用等宽 Tab。
- 输出编码 `AppTabRow`（Hex / Base64）。
- 结果区三态：HMAC 缺密钥提示、空输入提示、结果块（含复制按钮）。
- 三语新增 13 条文案，并把工具摘要改为「MD5 / SHA 系列摘要与 HMAC」。

## 测试

`HashToolTest` 从 4 项扩到 15 项：新增 SHA-384/512 已知向量、Base64 输出、HMAC-MD5/SHA-1 的经典向量、HMAC-SHA256/384/512 的 RFC 4231 case 2、HMAC 的 Base64 输出、空密钥返回空串、空文本 HMAC。
