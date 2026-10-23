# Requirements Document

## Introduction

时间戳工具升级为「时间转换」，新增第三种用法「时间格式化」：按用户自定义的 `DateTimeFormatter` 模式把时间渲染成任意格式。

## Glossary

- **System**: 时间转换工具（Timestamp Tool）
- **方向（Direction）**: 页内三个标签：时间戳 → 日期、日期 → 时间戳、时间格式化
- **模式串（Pattern）**: `DateTimeFormatter` 的格式模板，如 `yyyy-MM-dd HH:mm:ss`
- **模式字母（Pattern Letter）**: 模式串中的占位符，如 `yyyy`、`MM`

## Requirements

### Requirement 1: 三种方向

**User Story:** AS 开发者, I want 时间戳、日期、自定义格式三者互转, so that 常用的时间处理都在一个页面完成。

#### Acceptance Criteria

1. 系统 SHALL 提供「时间戳 → 日期」「日期 → 时间戳」「时间格式化」三个标签
2. WHEN 用户切换方向，系统 SHALL 清空上一方向的输入，避免误用
3. WHEN 用户处理时间戳，系统 SHALL 支持秒与毫秒两种单位切换
4. WHEN 用户输入日期，系统 SHALL 接受 `yyyy-MM-dd HH:mm:ss`、`yyyy-MM-dd HH:mm`、`yyyy-MM-dd` 三种写法
5. WHEN 输入格式不匹配，系统 SHALL 显示「无效」提示，且不输出结果

### Requirement 2: 时区呈现

**User Story:** AS 开发者, I want 同时看到 UTC 与本地时间, so that 排查跨时区问题时不需要心算。

#### Acceptance Criteria

1. WHEN 时间戳转换成功，系统 SHALL 同时展示 UTC 与设备本地时区两种结果
2. WHEN 用户点击「现在」，系统 SHALL 按当前所选单位或格式填入当前时间
3. WHEN 用户复制任一结果行，系统 SHALL 只复制该行的值

### Requirement 3: 时间格式化

**User Story:** AS 开发者, I want 自定义输出格式, so that 我能直接生成日志、SQL 或接口需要的字符串。

#### Acceptance Criteria

1. WHEN 用户输入时间与模式串，系统 SHALL 用该模式渲染时间，并实时更新
2. WHEN 模式串非法，系统 SHALL 提示「无效的格式」且不输出结果
3. WHEN 时间文本无法解析，系统 SHALL 提示「无效的时间」
4. WHEN 时间文本为空，系统 SHALL 显示空输入提示
5. 系统 SHALL 在页面下方列出常用模式字母及其含义，并说明要用单引号包裹字面字母

### Requirement 4: 文案与入口

**User Story:** AS 用户, I want 工具名称与内容一致, so that 我能从工具箱一眼认出它的用途。

#### Acceptance Criteria

1. 系统 SHALL 将工具名称从「时间戳转换」改为「时间转换」
2. 系统 SHALL 提供简体中文、英文、繁体中文三套文案
3. WHEN 首次进入格式化标签，系统 SHALL 预填当前时间与默认模式串
