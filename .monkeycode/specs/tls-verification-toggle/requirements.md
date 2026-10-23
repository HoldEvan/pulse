# Requirements Document

## Introduction

自签名证书与内网 HTTPS 服务目前无法调试：OkHttp 默认严格校验证书链与主机名，握手阶段直接失败，用户只能改用外部工具。需要在单个请求/连接范围内允许跳过校验，并持续提示风险。

## Glossary

- **System**: Pulse HTTP / WebSocket / SSE 协议页
- **跳过校验（Insecure TLS）**: 信任任意证书链并跳过主机名校验
- **引擎级配置（Engine-level Config）**: 无法按请求切换、必须整客户端生效的配置项

## Requirements

### Requirement 1: 按请求开关

**User Story:** AS 开发者, I want 对单个请求临时跳过证书校验, so that 我能调试内网自签名服务。

#### Acceptance Criteria

1. WHEN 用户在 HTTP 网络标签、WS 连接卡片或 SSE 网络标签，系统 SHALL 提供「跳过 TLS 证书校验」开关
2. WHEN 开关打开，系统 SHALL 在对应连接中信任任意证书并跳过主机名校验
3. WHEN 开关关闭，系统 SHALL 使用系统默认的证书与主机名校验
4. WHEN 开关状态改变，系统 SHALL 使用独立的网络客户端，不复用已建立的严格校验客户端

### Requirement 2: 风险持续可见

**User Story:** AS 开发者, I want 一眼看出当前处于不安全状态, so that 我不会忘记关掉。

#### Acceptance Criteria

1. WHEN 开关关闭，系统 SHALL 在开关下方说明该选项的用途
2. WHEN 开关打开，系统 SHALL 用警示色说明连接已无法防中间人攻击
3. WHEN 用户离开页面后重新进入，系统 SHALL 保持该请求自身的开关状态

### Requirement 3: 随请求保存

**User Story:** AS 用户, I want 这个开关随请求存进集合, so that 内网接口打开即可用。

#### Acceptance Criteria

1. WHEN 用户保存 HTTP / WS / SSE 请求，系统 SHALL 把开关状态写入快照
2. WHEN 从集合加载，系统 SHALL 恢复开关状态
3. WHEN 加载旧版本快照，系统 SHALL 默认关闭且不报错

### Requirement 4: 缓存隔离

**User Story:** AS 开发者, I want 同一地址的不同校验策略互不影响, so that 关闭开关后立刻恢复严格校验。

#### Acceptance Criteria

1. WHEN 客户端按「代理 + 校验策略」缓存，系统 SHALL 为四种组合分别持有客户端
2. WHEN 缓存数量达到上限，系统 SHALL 淘汰最早创建的客户端而不是全部重建
