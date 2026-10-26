# Requirements Document

## Introduction

为拨测 webhook 补充自定义请求头配置，并统一四个协议页与设置页的开关型交互：开关行铺满卡片宽度、水波纹贴合卡片圆角、每行配简短副标题；同时修复 WS 页面内容超长时无法滚动的问题，精简 MQTT 订阅输入的提示文案。

## Glossary

- **System**: Pulse Android 客户端
- **开关行（Switch Row）**: 带标题、可选副标题与右侧开关的整行控件，采用 MIUIX 偏好行样式
- **卡片裁剪（Card Clip）**: 卡片内容层按卡片圆角裁剪，使行内水波纹沿圆角收边
- **Webhook 请求头（Webhook Headers）**: 发送告警时附加到 HTTP 请求的自定义键值对

## Requirements

### Requirement 1: Webhook 自定义请求头

**User Story:** AS 拨测用户, I want 给告警请求加上自定义请求头, so that 公司网关或第三方平台的鉴权头能随告警一起发出。

#### Acceptance Criteria

1. WHEN 用户在设置页编辑 Webhook 请求头，系统 SHALL 提供键值对编辑器，支持新增与删除条目
2. WHEN 用户保存设置，系统 SHALL 持久化请求头，其中值加密存储、键明文存储
3. WHEN 发送告警，系统 SHALL 把配置的请求头附加到请求上
4. WHEN 用户配置的请求头与默认 `Content-Type` 同名（大小写不敏感），系统 SHALL 以用户配置的值为准
5. WHEN 存储的请求头数据损坏，系统 SHALL 回退为空列表而不影响其他设置读取

### Requirement 2: WS 页面滚动

**User Story:** AS 用户, I want 页面内容超出屏幕时能滚动, so that 下方的事件流与按钮不被裁掉。

#### Acceptance Criteria

1. WHEN WS 页面内容高度超过屏幕，系统 SHALL 允许整页纵向滚动
2. WHEN 事件流区域展示，系统 SHALL 使用固定高度并保持自身可滚动

### Requirement 3: 开关行统一为偏好行样式

**User Story:** AS 用户, I want 开关行看起来和系统偏好设置一致, so that 点击水波纹落在整行并沿卡片圆角收边。

#### Acceptance Criteria

1. WHEN HTTP、SSE、WS、MQTT 页面展示开关项，系统 SHALL 使用统一的开关行组件
2. WHEN 开关行位于卡片内，系统 SHALL 让该行铺满卡片宽度，并由卡片内容层按圆角裁剪
3. WHEN 开关项存在非直观语义，系统 SHALL 提供一行简短副标题
4. WHEN 跳过 TLS 证书校验项展示，系统 SHALL 用开关行承载说明：关闭说明适用场景，开启说明中间人风险

### Requirement 4: MQTT 订阅提示

**User Story:** AS 用户, I want 输入框标签简短, so that 提示文字不会挤占输入区域。

#### Acceptance Criteria

1. WHEN 订阅输入框展示，系统 SHALL 使用简短的标签文案
2. WHEN 需要说明通配符与 QoS 写法，系统 SHALL 把说明放在输入框外侧
