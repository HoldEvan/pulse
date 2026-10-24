# Requirements Document

## Introduction

HTTP 方法目前固定为六个枚举值，OPTIONS、TRACE 与 WebDAV 的 PROPFIND/MKCOL 等扩展方法都无法发送。需要支持常用方法补全与任意自定义方法，并让保存、运行、导出、脚本各链路都能带上自定义方法。

## Glossary

- **System**: Pulse HTTP 与 SSE 请求编辑器
- **自定义方法（Custom Method）**: 不在常用列表中的 HTTP 方法名，如 PROPFIND
- **方法值类（HttpMethod）**: 用字符串承载方法名的值类型，取代原枚举

## Requirements

### Requirement 1: 常用方法补全

**User Story:** AS 开发者, I want 下拉里有 OPTIONS 与 TRACE, so that 我能调试预检与回显请求。

#### Acceptance Criteria

1. WHEN 用户打开方法下拉，系统 SHALL 依次提供 GET、POST、PUT、DELETE、PATCH、HEAD、OPTIONS、TRACE
2. WHEN 用户选择其中一项，系统 SHALL 使用该方法发送请求

### Requirement 2: 任意自定义方法

**User Story:** AS 开发者, I want 输入任意方法名, so that 我能调试 WebDAV 等扩展协议。

#### Acceptance Criteria

1. WHEN 用户选择下拉末尾的「自定义」，系统 SHALL 弹出方法名输入框
2. WHEN 输入只包含字母与连字符且非空，系统 SHALL 接受并切换到该方法
3. WHEN 输入为空或包含数字、空格、斜杠等字符，系统 SHALL 拒绝并提示原因
4. WHEN 当前方法是自定义方法，系统 SHALL 在下拉中把它作为额外条目显示，避免选中状态丢失

### Requirement 3: 全链路生效

**User Story:** AS 用户, I want 自定义方法在保存、运行、导出时都不丢, so that 我不必每次重填。

#### Acceptance Criteria

1. WHEN 用户保存请求，系统 SHALL 把方法名原样写入快照
2. WHEN 从集合或历史恢复，系统 SHALL 还原自定义方法
3. WHEN 集合运行器执行该请求，系统 SHALL 用自定义方法发送
4. WHEN 用户导出 cURL、HTTP 原文、Python、JS，系统 SHALL 输出自定义方法
5. WHEN 前置/后置脚本改写方法，系统 SHALL 接受脚本给出的任意合法方法名
6. WHEN SSE 页选择方法，系统 SHALL 与 HTTP 页共用同一套选择器

### Requirement 4: 兼容旧数据

**User Story:** AS 老用户, I want 升级后已有请求照常可用, so that 我不需要重新保存。

#### Acceptance Criteria

1. WHEN 读取旧快照，系统 SHALL 按方法名解析，大小写不敏感
2. WHEN 方法名为空或缺失，系统 SHALL 回落到 GET
