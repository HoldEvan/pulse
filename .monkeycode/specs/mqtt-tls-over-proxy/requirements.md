# 需求：MQTT over TLS 走代理（#25）

## 背景

路线图 P1 #25。此前 `ssl://` 与代理同时启用会直接报错，用户只能直连或降级到 `tcp://`；MQTT 侧也没有跳过证书校验的入口，自签名或内网 Broker 无法连接。

## EARS 需求

### TLS 走代理

- WHEN 用户使用 `ssl://` 地址并启用 HTTP 代理，THE 系统 SHALL 先经代理建立 CONNECT 隧道，再在隧道之上完成 TLS 握手。
- WHEN 用户使用 `ssl://` 地址并启用 SOCKS5 代理，THE 系统 SHALL 先完成 SOCKS5 握手再叠加 TLS。
- WHEN 代理拒绝建立隧道或认证失败，THE 系统 SHALL 中止连接并展示代理返回的原因，不发起 TLS 握手。
- WHEN Broker 地址未写端口，THE 系统 SHALL 使用 8883 作为目标端口。
- WHEN Broker 地址含 userinfo 或路径（如 `ssl://user:pass@host/mqtt`），THE 系统 SHALL 只用 host 与 port 建隧道。
- WHEN 用户使用 `tcp://` 或 `ws://`，THE 系统 SHALL 按明文处理，即使端口是 8883 也不尝试 TLS。

### 跳过证书校验

- WHEN 用户打开「跳过 TLS 证书校验」并连接 `ssl://` Broker，THE 系统 SHALL 使用信任任意证书的套接字工厂，并同时关闭主机名校验。
- WHEN 该开关关闭（默认），THE 系统 SHALL 使用系统默认信任链与主机名校验。
- WHEN 开关打开但地址是 `tcp://`，THE 系统 SHALL 忽略该开关，连接行为不变。
- WHEN 开关打开，THE 系统 SHALL 在开关下方常驻风险说明，提示放弃中间人防护。
- WHEN 用户保存或从集合加载 MQTT 请求，THE 系统 SHALL 一并保存与恢复该开关。

### 兼容

- WHEN 旧快照不含跳过校验字段，THE 系统 SHALL 以关闭状态启动。
- WHEN 明文 `tcp://` 走代理，THE 系统 SHALL 保持原有行为（隧道握手实现与 TLS 路径共用同一份代码）。

## 非目标

- 客户端证书（双向 TLS）配置。
- 自定义 CA 导入。
- WebSocket（`ws://` / `wss://`）传输。
