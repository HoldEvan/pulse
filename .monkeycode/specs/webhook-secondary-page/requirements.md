# Requirements Document

## Introduction

设置页结构调整与告警 Webhook 能力扩展：诊断信息移到「关于」区块、定时自动备份并入备份卡片、Webhook 配置迁到独立二级页并支持 POST/GET 请求方法与查询参数。

## Glossary

- **System**: Pulse Android 客户端
- **Webhook 二级页**: 设置页内通过导航进入的「告警 Webhook」配置页
- **查询参数（Query Params）**: 以 `key=value` 追加到 Webhook 地址后的键值对
- **卡片级开关行**: 铺满卡片宽度、由卡片内容层按圆角裁剪水波纹的偏好行

## Requirements

### Requirement 1: 诊断信息位置

**User Story:** AS 用户, I want 诊断导出放在「关于」附近, so that 备份区块只保留与备份相关的操作。

#### Acceptance Criteria

1. WHEN 用户打开设置页，系统 SHALL 把「诊断信息」卡片放在「关于」标题之下、「关于」卡片之上

### Requirement 2: 定时自动备份并入备份卡片

**User Story:** AS 用户, I want 自动备份开关与 WebDAV 操作放在一起, so that 备份相关设置集中在一处。

#### Acceptance Criteria

1. WHEN 用户查看 WebDAV 卡片，系统 SHALL 在「备份」「浏览」两个按钮下方展示「定时自动备份」开关行
2. WHEN WebDAV 未配置，系统 SHALL 让开关不可用并用副标题提示先配置
3. WHEN 自动备份开启，系统 SHALL 在开关下方展示周期与保留份数设置
4. WHEN 自动备份关闭，系统 SHALL 隐藏周期与保留份数设置

### Requirement 3: Webhook 二级页

**User Story:** AS 用户, I want Webhook 配置有独立页面, so that 通用设置列表保持简洁且配置项可以展开得更完整。

#### Acceptance Criteria

1. WHEN 用户在设置页开启 Webhook，系统 SHALL 在开关下方提供「Webhook 配置」入口行，副标题显示已填地址或未配置提示
2. WHEN 用户进入 Webhook 二级页，系统 SHALL 展示请求方法、地址与查询参数设置
3. WHEN 用户选择请求方法，系统 SHALL 提供 POST 与 GET 两项
4. WHEN 用户配置查询参数，系统 SHALL 按顺序以 `key=value` 追加到地址后，键与值做 URL 编码
5. WHEN 方法为 GET，系统 SHALL 只发送查询参数，请求体与消息格式设置隐藏
6. WHEN 方法为 POST，系统 SHALL 展示消息格式与自定义模板设置
7. WHEN 用户在二级页发送测试，系统 SHALL 按当前方法与参数构造请求
