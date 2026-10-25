# 需求：敏感字段加密（#32）

## 背景

路线图 P2 #32。Token、密码与代理凭据此前以明文写在 `app_data.json` 与 DataStore 里，备份文件（本地导出、WebDAV）也是同一份明文；手机丢失或备份外泄即等于凭据泄露。

## EARS 需求

### 加密范围

- WHEN 写盘，THE 系统 SHALL 加密环境变量值、文件夹变量值、集合内请求快照、Cookie 值。
- WHEN 写盘，THE 系统 SHALL 加密拨测目标的 Bearer 令牌、Basic 密码、API Key 值与自定义请求头的值。
- WHEN 写盘，THE 系统 SHALL 加密历史记录的请求/响应头每一行与请求/响应正文。
- WHEN 保存设置，THE 系统 SHALL 加密代理密码与 WebDAV 密码。
- WHEN 备份导出（本地或 WebDAV），THE 系统 SHALL 输出加密后的同一份数据，不再出现明文凭据。

### 密钥

- WHEN 首次加密，THE 系统 SHALL 在 Android Keystore 中生成 AES-256-GCM 密钥。
- WHEN 加密，THE 系统 SHALL 为每次加密使用新的随机 IV，并把 IV 与密文一起存放。
- WHEN 读取密文，THE 系统 SHALL 通过 GCM 认证标签校验完整性，密文被改动视为解密失败。
- WHEN 进程重启，THE 系统 SHALL 复用同一密钥，无需用户输入口令。

### 兼容与降级

- WHEN 读到旧数据或旧备份中的明文值，THE 系统 SHALL 原样使用，并在下次写盘时加密。
- WHEN 密文无法解密（换机恢复、密钥失效、数据损坏），THE 系统 SHALL 返回空值而不是展示密文。
- WHEN 设备不支持 Keystore 或加密运行期失败，THE 系统 SHALL 退回明文写入并保证本次数据不丢失。
- WHEN 空字符串，THE 系统 SHALL 不生成密文，避免无意义的加密开销。

## 非目标

- 用户口令派生的密钥（PBKDF2/Argon2）与口令解锁流程。
- 数据库或文件整体加密（当前仍是单个 JSON 文件，仅字段级加密）。
- WebDAV 传输层之外的额外端到端加密。
