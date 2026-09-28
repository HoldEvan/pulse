# Requirements Document — API Tester

Feature Name: api-tester
Updated: 2026-09-28
Status: Draft

## Introduction

一个基于 Jetpack Compose + Miuix UI 库的 Android API 测试工具，提供 HTTP 请求调试（GET/POST/PUT/DELETE 等）、WebSocket 连接测试、MQTT 连接测试三大能力，并通过环境变量系统实现多环境（开发/测试/生产）参数切换。数据通过 Room 持久化到本地。

## Glossary

- **App**: 本 Android 应用程序 API Tester。
- **HTTP 请求 (HttpRequest)**: 用户定义的一次 HTTP 调用配置，包含方法、URL、请求头、查询参数、请求体。
- **环境 (Environment)**: 一组命名变量的集合，例如 dev / test / prod。
- **变量 (Variable)**: 环境内的键值对，键为变量名，值为替换文本。
- **占位符 (Placeholder)**: 文本中 `{{变量名}}` 形式的引用，发送前由 App 替换为实际值。
- **项目 (Project)**: 顶层组织单元，包含环境、集合与历史记录，可创建多个并随时切换。
- **激活环境 (Active Environment)**: 当前项目内占位符替换所使用的环境，每个项目各自维护一个。
- **会话 (Session)**: 一次 WebSocket 或 MQTT 的连接实例及其消息流。
- **消息流 (Message Log)**: 会话中按时间排列的收发消息列表。

## Requirements

### R1 — HTTP 请求发送

**User Story:** AS 一个开发者，I want 发送常见方法的 HTTP 请求并查看响应, so that 我可以在手机上调试后端接口。

#### Acceptance Criteria

1. The App SHALL 支持 GET、POST、PUT、DELETE、PATCH、HEAD 六种 HTTP 方法。
2. WHEN 用户点击"发送"，the App SHALL 解析 URL、查询参数与请求头中的占位符后发出请求，并显示状态码、耗时（毫秒）与响应体大小。
3. WHILE 请求进行中，the App SHALL 显示加载状态并允许取消请求。
4. The App SHALL 支持以下请求体类型：JSON、纯文本、x-www-form-urlencoded、form-data（含文件字段）。
5. IF 响应体为 JSON，the App SHALL 提供格式化与语法高亮两种查看模式。
6. IF 请求失败（网络错误、超时、DNS 失败），the App SHALL 显示错误类型描述，同时保留请求配置供重新编辑。

### R2 — 请求配置编辑

**User Story:** AS 一个开发者，I want 编辑请求头、查询参数与认证信息, so that 我可以构造任意复杂度的请求。

#### Acceptance Criteria

1. The App SHALL 以表格形式编辑请求头与查询参数的键值对，支持多行增删。
2. The App SHALL 支持 Bearer Token 与 Basic Auth 两种认证方式，认证信息在发送时转换为对应请求头。
3. IF 响应包含 Set-Cookie，the App SHALL 在会话内保存 Cookie 并在后续请求中自动携带。
4. The App SHALL 提供常用 Content-Type 的快捷选择，选择后自动填充对应请求头。

### R3 — 环境变量管理

**User Story:** AS 一个开发者，I want 按项目维护多套环境变量并在请求中引用, so that 我可以在项目的开发、测试、生产环境间快速切换。

#### Acceptance Criteria

1. The App SHALL 支持创建、重命名、删除项目，环境与请求集合归属于项目。
2. The App SHALL 支持在项目内创建、重命名、删除环境，每个环境包含多个变量键值对。
3. The App SHALL 提供激活环境切换器，切换后所有占位符引用立即生效；每个项目各自维护一个激活环境。
4. WHEN 用户发送请求，the App SHALL 将请求配置中的 `{{变量名}}` 替换为当前项目激活环境中对应变量的值。
5. IF 占位符引用的变量在激活环境中不存在，the App SHALL 在发送前警告并列出缺失变量名。
6. The App SHALL 在 URL、请求头、查询参数、请求体文本、WebSocket 消息与 MQTT 负载中均支持占位符替换。
7. IF 用户删除项目，the App SHALL 级联删除该项目的环境、集合、请求与历史记录，删除前需用户二次确认。

### R4 — 请求持久化与历史

**User Story:** AS 一个开发者，I want 按项目组织保存的请求并查看历史记录, so that 我可以复用调试成果并追溯问题。

#### Acceptance Criteria

1. The App SHALL 支持将当前请求配置保存为命名请求，组织结构为：项目 → 集合（文件夹）→ 请求。
2. The App SHALL 在顶栏提供项目切换器，切换项目后集合、环境与历史记录按当前项目过滤显示。
3. WHEN 用户发送一次 HTTP 请求，the App SHALL 将方法、URL、耗时、状态码与时间戳写入当前项目的历史记录。
4. IF 用户点击历史条目，the App SHALL 将该请求的配置载入编辑器（含请求头与请求体）。
5. The App SHALL 支持对历史记录按关键字搜索与按集合浏览。
6. The App SHALL 将当前项目的集合导出为 Postman Collection v2.1 格式的 JSON 文件。
7. The App SHALL 支持从 Postman Collection v2.1 格式的 JSON 文件导入到指定项目。

### R5 — WebSocket 测试

**User Story:** AS 一个开发者，I want 连接 WebSocket 服务并收发消息, so that 我可以调试实时通信接口。

#### Acceptance Criteria

1. The App SHALL 支持 ws:// 与 wss:// 两种 WebSocket URL，并支持自定义子协议与请求头。
2. WHEN 用户点击"连接"，the App SHALL 建立连接并实时显示连接状态（连接中 / 已连接 / 已断开 / 失败）。
3. WHILE 连接已建立，the App SHALL 将收到的每条消息按时间戳写入消息流，并区分收/发方向。
4. The App SHALL 支持发送文本消息，URL 与消息内容均支持占位符替换。
5. WHEN 用户点击"断开"或发生异常断连，the App SHALL 记录断开原因并保留消息流供查看。
6. The App SHALL 支持 JSON 格式化显示消息内容。

### R6 — MQTT 测试

**User Story:** AS 一个开发者，I want 连接 MQTT Broker 进行订阅与发布, so that 我可以调试物联网消息链路。

#### Acceptance Criteria

1. The App SHALL 支持 tcp:// 与 ssl:// 两种 MQTT Broker 连接方式，支持配置客户端 ID、用户名、密码与 Keep-Alive 间隔。
2. WHEN 用户点击"连接"，the App SHALL 建立 MQTT 会话并在 UI 显示连接状态与连接耗时。
3. The App SHALL 支持订阅指定 Topic 并将收到的消息按时间戳写入消息流，消息流显示 Topic、QoS 与负载内容。
4. The App SHALL 支持向指定 Topic 发布文本消息，发布时可选择 QoS 0/1/2。
5. The App SHALL 支持通配符订阅（`+` 单层、`#` 多层）。
6. WHEN 用户点击"断开"，the App SHALL 关闭会话并保留消息流。
7. IF Broker 连接失败或意外断开，the App SHALL 显示错误原因（认证失败、网络不可达等）。

### R7 — 数据持久化

**User Story:** AS 一个用户，I want 环境与请求在重启后仍然可用, so that 我不必重复配置。

#### Acceptance Criteria

1. The App SHALL 使用 Room 数据库持久化：环境及变量、保存的请求与集合、HTTP 历史、WebSocket/MQTT 会话配置与收发消息流。
2. WHILE HTTP 历史记录超过 500 条，the App SHALL 在新增记录后自动删除最旧的记录，使历史记录数量保持在上限以内。
3. WHILE 单个 WebSocket/MQTT 会话的消息流超过 1000 条，the App SHALL 自动删除该会话最旧的消息。
4. The App SHALL 提供"清空历史"与"清空消息流"操作，操作前需用户二次确认。
5. WHILE 数据库操作失败，the App SHALL 显示错误提示且保留 UI 中未保存的编辑内容。

### R8 — Miuix 界面风格

**User Story:** AS 一个用户，I want 界面符合 MIUI 设计语言, so that 使用体验与现代 Android 应用一致。

#### Acceptance Criteria

1. The App SHALL 使用 Miuix 组件库构建全部界面，遵循 Miuix 的色彩、圆角与排版规范。
2. The App SHALL 提供底部导航切换五个主页面：HTTP、WebSocket、MQTT、环境、历史。
3. The App SHALL 支持跟随系统深色模式切换主题。
4. WHILE 用户执行不可逆操作（删除环境、清空历史），the App SHALL 弹出 Miuix 风格确认对话框。

## Out of Scope（本期不做）

- GraphQL、gRPC、SOAP 协议调试
- 请求脚本（Pre-request Script / 断言脚本）
- 云端同步、团队协作
- MQTT 5.0 专有特性（Session Expiry、Shared Subscription 等）
- WebSocket 二进制帧发送
