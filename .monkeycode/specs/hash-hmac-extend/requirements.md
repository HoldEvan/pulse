# 需求：HMAC 与摘要扩展（#28）

## 背景

路线图 P1 #28。Hash 工具只有 MD5 / SHA-1 / SHA-256 三种裸摘要，且固定十六进制输出。联调第三方回调签名时需要 HMAC-SHA256，对账时还需要 SHA-384/512 与 Base64 输出，只能换到别的工具。

## EARS 需求

### 摘要

- WHEN 用户在摘要模式输入文本，THE 系统 SHALL 按所选算法输出摘要。
- WHEN 用户选择 MD5、SHA-1、SHA-256、SHA-384 或 SHA-512，THE 系统 SHALL 使用对应算法。
- WHEN 文本为空，THE 系统 SHALL 不输出摘要。
- WHEN 用户选择 Hex 或 Base64 输出，THE 系统 SHALL 用对应编码输出，默认 Hex 小写。

### HMAC

- WHEN 用户切到 HMAC 模式，THE 系统 SHALL 显示密钥输入框，算法集合与摘要模式一致。
- WHEN 用户输入文本与密钥，THE 系统 SHALL 输出对应算法的 HMAC 结果。
- WHEN 密钥为空，THE 系统 SHALL 不输出结果，并提示需要先输入密钥。
- WHEN 文本为空但密钥非空，THE 系统 SHALL 照常输出对空消息的 HMAC。
- WHEN 用户切换输出编码，THE 系统 SHALL 同时作用于 HMAC 结果。

### 交互

- WHEN 用户切换模式或算法，THE 系统 SHALL 保留已输入的文本与密钥。
- WHEN 结果非空，THE 系统 SHALL 提供一键复制。
- WHEN 输入或参数变化，THE 系统 SHALL 立即重算，无需点击按钮。

## 非目标

- 文件流式摘要与超大输入。
- 增量/多段 HMAC。
- 密钥以 Hex 或 Base64 字节形式输入。
