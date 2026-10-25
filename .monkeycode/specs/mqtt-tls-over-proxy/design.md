# 技术设计：MQTT over TLS 走代理（#25）

## 方案

### 1. 代理握手复用

新文件 `domain/net/ProxyTunnel.kt`：

- `open(proxy, host, port, timeoutMs)`：新建 Socket 连代理并握手，返回可直接通信的 Socket。
- `handshake(socket, proxy, host, port, timeoutMs)`：在已连到代理的 Socket 上完成 HTTP CONNECT 或 SOCKS5 握手。
- 把原 `PahoMqttEngine.kt` 内 `ProxySocket` 的握手逻辑整体搬入，`ProxySocket` 改为调用 `ProxyTunnel.handshake`，明文与 TLS 两条路径共用一份实现。

### 2. TLS 判定

新文件 `domain/net/MqttTls.kt`：

- `SECURE_SCHEME = "ssl"`、`DEFAULT_SECURE_PORT = 8883`。
- `isSecure(url)` 只按 scheme 判断，不做端口推断（避免把 8883 的明文连接误当 TLS）。
- `endpoint(url)` 用 `URI` 解析 host/port，失败时退回手工切分，兼容含下划线等非标准主机名。
- `socketFactory(insecure)`：跳过校验时返回 `InsecureTls.insecureSocketFactory`，否则用默认信任链新建 `SSLContext`。

`InsecureTls` 把内部 `socketFactory` 提升为公开 `insecureSocketFactory`，OkHttp 与 MQTT 共用同一个实例。

### 3. 隧道 + TLS 工厂

新文件 `domain/net/ProxyTlsSocketFactory.kt`：

- `ProxyTlsSocketFactory(proxy, delegate, targetHost, targetPort) : SSLSocketFactory`：满足 Paho 对 `ssl://` 必须拿到 `SSLSocketFactory` 的要求。
- 无参 `createSocket()` 返回延迟建立的 `ProxyTlsSocket`：Paho 的调用顺序是「无参建套接字 → `connect(目标地址)` → 强转 `SSLSocket` 握手」，因此隧道与 TLS 都在 `connect()` 内完成。
- 其余 `createSocket(host, port, ...)` 重载直接 `ProxyTunnel.open` + `delegate.createSocket(tunnel, host, port, true)`。
- `ProxyTlsSocket` 把 `SSLSocket` 的加密相关方法与 `Socket` 的通用方法全部转发给内层套接字，未连接时抛出 `IOException("TLS 套接字尚未连接")`。

### 4. 引擎

`PahoMqttEngine.connect`：

- 计算 `secure = MqttTls.isSecure(brokerUrl)`，删除原先「ssl + 代理即报错」的分支。
- `secure` 时按 `insecureTls` 设置 `httpsHostnameVerificationEnabled = false` 与放行 `sslHostnameVerifier`（两者缺一都会让自签名证书被拒），并选择工厂：
  - 代理启用 → `ProxyTlsSocketFactory(proxy, MqttTls.socketFactory(insecure), host, port)`；
  - 直连 → `MqttTls.socketFactory(insecure)`。
- 明文分支保持 `ProxySocketFactory`。

### 5. 模型与 UI

- `MqttConfig` 新增 `insecureTls: Boolean = false`；`MqttSnapshot` 新增同名默认字段。
- `MqttViewModel` 新增 `insecureTls` 状态，随快照往返并传入 `MqttConfig`。
- `MqttScreen` 连接卡片在代理开关下方插入共用组件 `InsecureTlsRow`（常驻风险说明）。

## 测试

- `MqttTlsTest` 10 项：scheme 大小写、tcp/ws 不算 TLS、scheme 提取、端口显式/缺省/userinfo 与路径、非法输入、工厂差异、代理工厂密码套件转发、未连接状态。
- `ProxyTunnelTest` 3 项：用本机临时 ServerSocket 冒充 HTTP 代理，验证 CONNECT 请求行与 Basic 认证头、407 拒绝时报错、`ProxyTlsSocket` 在 `connect()` 后建立隧道。
