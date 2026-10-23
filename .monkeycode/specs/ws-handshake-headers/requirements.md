# Requirements Document

## Introduction

WebSocket 握手阶段常需要自定义请求头（鉴权 Token、Origin 等）与子协议协商，引擎侧已支持这两个字段，界面与集合快照没有入口，导致鉴权型 WebSocket 无法在应用内调试。

## Glossary

- **System**: Pulse WebSocket 协议页
- **握手头（Handshake Header）**: 连接建立时随 HTTP Upgrade 请求发送的额外请求头
- **子协议（Subprotocol）**: `Sec-WebSocket-Protocol` 头里按优先级排列的协议名列表

## Requirements

### Requirement 1: 编辑握手请求头

**User Story:** AS 接口调试用户, I want 给 WebSocket 连接加自定义请求头, so that 我能通过 Token 鉴权的网关。

#### Acceptance Criteria

1. WHEN 用户打开 WebSocket 页，系统 SHALL 在连接卡片内提供「请求头」可折叠区块，默认折叠
2. WHEN 区块标题展示，系统 SHALL 显示已填写的请求头条数
3. WHEN 用户编辑请求头，系统 SHALL 按 HTTP 页同一套键值编辑器行为自动补空行、支持删除
4. WHEN 请求头值包含 `{{变量}}`，系统 SHALL 在输入框下方提示该变量当前取值
5. WHEN 请求头键为空，系统 SHALL 在连接时跳过该行

### Requirement 2: 子协议协商

**User Story:** AS 接口调试用户, I want 指定 Sec-WebSocket-Protocol, so that 服务端能选中我期望的协议实现。

#### Acceptance Criteria

1. WHEN 用户在子协议输入框填写逗号分隔的协议名，系统 SHALL 去空白、丢弃空段、保留首次出现的顺序
2. WHEN 子协议输入为空，系统 SHALL 不发送 `Sec-WebSocket-Protocol` 头
3. WHEN 子协议包含 `{{变量}}`，系统 SHALL 在连接前解析

### Requirement 3: 变量解析与校验

**User Story:** AS 用户, I want 地址、请求头、子协议共用同一套变量校验, so that 缺变量时能一次看全。

#### Acceptance Criteria

1. WHEN 任一字段存在未定义变量，系统 SHALL 汇总全部缺失键，弹出一次提示并中止连接
2. WHEN 变量齐全，系统 SHALL 用解析后的值建立连接

### Requirement 4: 保存与恢复

**User Story:** AS 用户, I want 请求头与子协议随请求一起保存, so that 从集合打开即可复用。

#### Acceptance Criteria

1. WHEN 用户保存请求，系统 SHALL 把请求头与子协议写入集合快照
2. WHEN 从集合加载，系统 SHALL 恢复请求头与子协议
3. WHEN 加载旧版本快照（无这两个字段），系统 SHALL 以空列表兜底且不报错
