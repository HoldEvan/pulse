# 技术设计：敏感字段加密（#32）

## 方案

### 领域层

新文件 `domain/crypto/SecretField.kt`（纯 JVM，可单测）：

- `SecretBox` 接口：`encrypt(ByteArray): ByteArray`、`decrypt(ByteArray): ByteArray`，密钥来源交给实现。
- `SecretField.PREFIX = "enc1:"`，格式为 `enc1:` + Base64(iv + 密文 + GCM 标签)。
- `seal(plain, box)`：空串直接返回，其余加密后加前缀。
- `open(value, box)`：非密文原样返回；密文但无密钥、Base64 损坏、解密抛异常一律返回空串——宁可让用户重填，也不把密文当内容显示。

自描述前缀让明文与密文可以共存于同一字段：旧数据、旧备份无需迁移脚本，下一次写盘自然升级。

### Android 实现

新文件 `data/crypto/KeystoreSecretBox.kt`：

- 别名 `pulse_secret_fields_v1`，`AES/GCM/NoPadding`、256 位、`setUserAuthenticationRequired(false)`（后台自动备份与定时拨测也会写盘）。
- 密钥惰性创建并存入 `AndroidKeyStore`，不可导出。
- 加密输出 `iv(12B) || 密文 || 标签(16B)`；解密用 `GCMParameterSpec(128, ...)` 校验。

### 接入

`data/persistence/AppStatePersistence.kt`：

- 构造参数 `secrets: SecretBox?`，默认 `runCatching { KeystoreSecretBox() }.getOrNull()`。
- 私有 `seal` / `open` 两个收口函数，`seal` 内部 `runCatching` 兜底为明文（加密失败不能让整次写盘失败）。
- 写入侧对敏感字段调用 `seal`，读取侧调用 `open`。
- 顺手把 `load()` 与 `restoreFromJsonElement()` 中重复的 projects / environments / requests 解析抽成 `parseProjects`、`parseEnvironments`、`parseSavedRequests`，保证两条链路解密逻辑一致。

`data/settings/SettingsStore.kt`：

- `Preferences.toAppSettings(secrets: SecretBox? = null)`：代理密码、WebDAV 密码读取时解封，默认参数保证既有单测与无密钥环境行为不变。
- `update()` 写入前 `seal`，同样带降级兜底。

备份链路不变：`BackupService` 直接复制 `app_data.json` 或上传其文本，落盘即密文。

## 测试

`SecretFieldTest` 13 项：前缀生成、明文不外露、往返、同明文不同密文、空串不加密、明文直通、中文与长文本往返、无密钥置空、密钥失效置空、非法 Base64 置空、截断密文置空、前缀只认开头。
