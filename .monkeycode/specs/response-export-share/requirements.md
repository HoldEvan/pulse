# Requirements Document

## Introduction

响应体目前只能整块复制，展示层还会把超过 65536 字符的内容截断。需要提供保存到本地与系统分享，导出的必须是完整内容而不是屏幕上的截断文本。

## Glossary

- **System**: Pulse 响应体查看器
- **原始字节（Raw Bytes）**: 引擎读到的完整响应字节
- **SAF**: Android 存储访问框架，让用户自己选择保存位置

## Requirements

### Requirement 1: 保存到本地

**User Story:** AS 开发者, I want 把响应体存成文件, so that 我能用外部工具分析或归档。

#### Acceptance Criteria

1. WHEN 用户在响应体区域点击保存，系统 SHALL 弹出系统文件选择器
2. WHEN 用户选定位置，系统 SHALL 写入完整响应内容
3. WHEN 实时响应带有原始字节，系统 SHALL 直接写原始字节
4. WHEN 只有文本（如历史记录），系统 SHALL 以 UTF-8 编码写入文本
5. WHEN 写入成功，系统 SHALL 提示已保存；失败时提示可换位置重试

### Requirement 2: 系统分享

**User Story:** AS 开发者, I want 把响应体分享到其他应用, so that 我能直接发到聊天工具或云盘。

#### Acceptance Criteria

1. WHEN 用户点击分享，系统 SHALL 先写入缓存目录再拉起系统分享面板
2. WHEN 分享的文件被其他应用读取，系统 SHALL 通过 content URI 授权读权限
3. WHEN 分享失败（无可用接收应用），系统 SHALL 提示而不是崩溃
4. WHEN 用户重复分享，系统 SHALL 复用同一缓存目录并清理旧文件

### Requirement 3: 文件类型推断

**User Story:** AS 开发者, I want 导出文件带正确后缀, so that 打开时能用对应的应用。

#### Acceptance Criteria

1. WHEN 响应带 `Content-Type`，系统 SHALL 去掉参数并按其推断后缀与 MIME
2. WHEN 类型未知，系统 SHALL 按大类（text/image/audio/video）推断，仍未命中用 `bin`
3. WHEN 生成文件名，系统 SHALL 使用时间戳命名，不把 URL 拼入文件名

### Requirement 4: 覆盖实时与历史

**User Story:** AS 用户, I want 两个入口行为一致, so that 我不必区分响应来自哪里。

#### Acceptance Criteria

1. WHEN 用户查看实时响应，系统 SHALL 提供保存与分享
2. WHEN 用户查看历史详情，系统 SHALL 提供同样的保存与分享，并从响应头推断类型
3. WHEN 内容被展示层截断，系统 SHALL 在入口处标注完整字节数
